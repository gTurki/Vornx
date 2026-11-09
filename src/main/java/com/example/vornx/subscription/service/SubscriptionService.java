package com.example.vornx.subscription.service;
import java.util.Map;

import com.example.vornx.notification.enums.NotificationType;
import com.example.vornx.notification.service.NotificationService;

import com.example.vornx.access.entity.Permit;
import com.example.vornx.access.enums.PermitStatus;
import com.example.vornx.access.service.PermitService;
import com.example.vornx.common.dto.PageResponse;
import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.common.exception.ResourceNotFoundException;
import com.example.vornx.common.util.RandomCodes;
import com.example.vornx.customer.entity.Customer;
import com.example.vornx.customer.service.CustomerService;
import com.example.vornx.partner.enums.PartnerStatus;
import com.example.vornx.subscription.dto.ServiceRequestResponse;
import com.example.vornx.subscription.dto.SubscriptionResponse;
import com.example.vornx.subscription.entity.ServiceRequest;
import com.example.vornx.subscription.entity.Subscription;
import com.example.vornx.subscription.entity.SubscriptionPlan;
import com.example.vornx.subscription.enums.RequestStatus;
import com.example.vornx.subscription.enums.RequestType;
import com.example.vornx.subscription.enums.SubscriptionStatus;
import com.example.vornx.subscription.repository.ServiceRequestRepository;
import com.example.vornx.subscription.repository.SubscriptionPlanRepository;
import com.example.vornx.subscription.repository.SubscriptionRepository;
import com.example.vornx.wallet.entity.WalletTransaction;
import com.example.vornx.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;

/**
 * The subscription flow ("طلب تفعيل" / "طلب تجديد"):
 *
 *   customer subscribes/renews -> pays from wallet -> request PENDING
 *        |-> partner APPROVES -> subscription ACTIVE + permit issued/extended (the card now opens the door)
 *        |-> partner REJECTS  -> automatic refund
 *        '-> customer CANCELS -> automatic refund
 *
 * Plans with requiresApproval = false are approved automatically right after payment.
 * Every method is ONE transaction: payment + request + permit all succeed together or not at all.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SubscriptionService {

    private final SubscriptionPlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final ServiceRequestRepository requestRepository;
    private final CustomerService customerService;
    private final WalletService walletService;
    private final PermitService permitService;
    private final Clock clock;
    private final ZoneId businessZone;
    private final NotificationService notificationService;

    // =============== Customer ===============

    /** "اشترك" - pay and ask for activation. */
    public ServiceRequestResponse subscribe(Long customerId, Long planId) {
        SubscriptionPlan plan = planRepository.findWithSiteAndPartnerById(planId)
                .filter(SubscriptionPlan::isActive)
                .filter(p -> p.getPartner().getStatus() == PartnerStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", planId));
        Customer customer = customerService.getEntity(customerId);

        if (subscriptionRepository.existsByCustomer_IdAndPlan_IdAndStatusIn(customerId, planId,
                EnumSet.of(SubscriptionStatus.PENDING, SubscriptionStatus.ACTIVE))) {
            throw new BusinessException("ALREADY_SUBSCRIBED",
                    "You already have this subscription. Renew it instead of subscribing again.");
        }

        Subscription subscription = subscriptionRepository.save(
                Subscription.pending(newSubscriptionNumber(), customer, plan));
        ServiceRequest request = createPaidRequest(subscription, RequestType.ACTIVATION, "Subscription: " + plan.getName());

        if (!plan.isRequiresApproval()) {
            approveInternal(request, null);
        } else {
            notifyPartnerOfRequest(request);
        }
        return ServiceRequestResponse.from(request);
    }

    /** "طلب التجديد" - pay and ask for renewal (works for active and expired subscriptions). */
    public ServiceRequestResponse renew(Long customerId, Long subscriptionId) {
        Subscription subscription = subscriptionRepository.findByIdAndCustomer_Id(subscriptionId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription", subscriptionId));
        SubscriptionPlan plan = subscription.getPlan();

        if (subscription.getStatus() != SubscriptionStatus.ACTIVE) { // stored ACTIVE = active or expired
            throw new BusinessException("CANNOT_RENEW", "Only an active or expired subscription can be renewed");
        }
        if (!plan.isActive() || subscription.getPartner().getStatus() != PartnerStatus.ACTIVE) {
            throw new BusinessException("PLAN_UNAVAILABLE", "This plan is no longer available");
        }
        if (requestRepository.existsBySubscription_IdAndStatus(subscriptionId, RequestStatus.PENDING)) {
            throw new BusinessException("REQUEST_PENDING", "A request for this subscription is already under review");
        }

        ServiceRequest request = createPaidRequest(subscription, RequestType.RENEWAL, "Renewal: " + plan.getName());
        if (!plan.isRequiresApproval()) {
            approveInternal(request, null);
        } else {
            notifyPartnerOfRequest(request);
        }
        return ServiceRequestResponse.from(request);
    }

    /** The customer changes their mind while the request is still PENDING -> refund. */
    public ServiceRequestResponse cancel(Long customerId, Long requestId) {
        ServiceRequest request = requestRepository.lockForCustomer(requestId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Request", requestId));
        requirePending(request);
        request.cancel(Instant.now(clock), refundIfPaid(request, "Cancelled: "));
        if (request.getType() == RequestType.ACTIVATION) {
            request.getSubscription().setStatus(SubscriptionStatus.CANCELLED);
        }
        return ServiceRequestResponse.from(request);
    }

    @Transactional(readOnly = true)
    public List<SubscriptionResponse> mySubscriptions(Long customerId) {
        Instant now = Instant.now(clock);
        return subscriptionRepository.findByCustomer_IdOrderByCreatedAtDesc(customerId).stream()
                .map(s -> SubscriptionResponse.from(s, now))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ServiceRequestResponse> myRequests(Long customerId) {
        return requestRepository.findByCustomer_IdOrderByCreatedAtDesc(customerId).stream()
                .map(ServiceRequestResponse::from)
                .toList();
    }

    // =============== Partner ===============

    @Transactional(readOnly = true)
    public PageResponse<ServiceRequestResponse> partnerRequests(Long partnerId, RequestStatus status, Pageable pageable) {
        Page<ServiceRequest> page = status != null
                ? requestRepository.findByPartner_IdAndStatus(partnerId, status, pageable)
                : requestRepository.findByPartner_Id(partnerId, pageable);
        return PageResponse.from(page.map(ServiceRequestResponse::from));
    }

    @Transactional(readOnly = true)
    public PageResponse<SubscriptionResponse> partnerSubscriptions(Long partnerId, Pageable pageable) {
        Instant now = Instant.now(clock);
        return PageResponse.from(subscriptionRepository.findByPartner_Id(partnerId, pageable)
                .map(s -> SubscriptionResponse.from(s, now)));
    }

    public ServiceRequestResponse approve(Long partnerId, Long partnerUserId, Long requestId) {
        ServiceRequest request = requestRepository.lockForPartner(requestId, partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Request", requestId));
        requirePending(request);
        approveInternal(request, partnerUserId);
        return ServiceRequestResponse.from(request);
    }

    public ServiceRequestResponse reject(Long partnerId, Long partnerUserId, Long requestId, String reason) {
        ServiceRequest request = requestRepository.lockForPartner(requestId, partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Request", requestId));
        requirePending(request);
        request.reject(reason.trim(), Instant.now(clock), partnerUserId, refundIfPaid(request, "Refund: "));
        notificationService.notifyCustomer(request.getCustomer().getId(), NotificationType.SUBSCRIPTION_REJECTED,
                Map.of("partner", request.getPartner().getName(), "plan", request.getSubscription().getPlan().getName(),
                        "reason", reason.trim(), "amount", request.getAmount()),
                NotificationService.Ref.of("SERVICE_REQUEST", request.getId()));
        if (request.getType() == RequestType.ACTIVATION) {
            request.getSubscription().setStatus(SubscriptionStatus.REJECTED);
        }
        return ServiceRequestResponse.from(request);
    }

    // =============== The core ===============

    /** Approval = activate/extend the subscription AND issue/extend its permit. */
    private void approveInternal(ServiceRequest request, Long partnerUserId) {
        Subscription sub = request.getSubscription();
        SubscriptionPlan plan = sub.getPlan();
        Instant now = Instant.now(clock);

        if (request.getType() == RequestType.ACTIVATION) {
            Instant end = plan.getBillingPeriod().addTo(now, businessZone);
            sub.setStartAt(now);
            sub.setEndAt(end);
            sub.setStatus(SubscriptionStatus.ACTIVE);
            sub.setPermit(issuePermit(sub, now, end));
        } else {
            // Renewing early adds time after the current end. Renewing after expiry starts from now.
            Instant base = sub.getEndAt().isAfter(now) ? sub.getEndAt() : now;
            Instant newEnd = plan.getBillingPeriod().addTo(base, businessZone);
            sub.setEndAt(newEnd);
            Permit permit = sub.getPermit();
            if (permit != null && permit.getStatus() != PermitStatus.REVOKED) {
                permit.setValidUntil(newEnd);
            } else {
                sub.setPermit(issuePermit(sub, now, newEnd)); // partner had revoked it: start a fresh one
            }
        }
        request.approve(now, partnerUserId);
        notificationService.notifyCustomer(sub.getCustomer().getId(), NotificationType.SUBSCRIPTION_APPROVED,
                Map.of("plan", plan.getName(), "partner", sub.getPartner().getName(), "until", sub.getEndAt()),
                NotificationService.Ref.of("SUBSCRIPTION", sub.getId()));
    }

    private void notifyPartnerOfRequest(ServiceRequest request) {
        Customer c = request.getCustomer();
        notificationService.notifyPartner(request.getPartner().getId(), NotificationType.NEW_SUBSCRIPTION_REQUEST,
                Map.of("customer", c.getFirstName() + " " + c.getLastName(),
                        "plan", request.getSubscription().getPlan().getName(), "requestType", request.getType()),
                NotificationService.Ref.of("SERVICE_REQUEST", request.getId()));
    }

    private Permit issuePermit(Subscription sub, Instant from, Instant until) {
        SubscriptionPlan plan = sub.getPlan();
        return permitService.issueFromTemplate(sub.getCustomer(), plan.getSite(), plan.getAccessPoints(),
                plan.getPermitType(), from, until, plan.getAllowedDays(), plan.getAllowedFrom(), plan.getAllowedTo(),
                "Subscription " + sub.getNumber() + " - " + plan.getName());
    }

    private ServiceRequest createPaidRequest(Subscription sub, RequestType type, String description) {
        SubscriptionPlan plan = sub.getPlan();
        WalletTransaction payment = plan.getPrice().signum() > 0
                ? walletService.charge(sub.getCustomer().getId(), plan.getPrice(), plan.getPartner(), description)
                : null; // free plan: nothing to pay
        return requestRepository.save(new ServiceRequest(newRequestNumber(), sub, type, plan.getPrice(), payment));
    }

    private WalletTransaction refundIfPaid(ServiceRequest request, String prefix) {
        if (request.getPaymentTransaction() == null) {
            return null;
        }
        return walletService.refund(request.getCustomer().getId(), request.getAmount(), request.getPartner(),
                prefix + request.getRequestNumber());
    }

    private static void requirePending(ServiceRequest request) {
        if (request.getStatus() != RequestStatus.PENDING) {
            throw new BusinessException("REQUEST_NOT_PENDING", "This request was already " + request.getStatus());
        }
    }

    private String newSubscriptionNumber() {
        String n;
        do {
            n = "SUB-" + RandomCodes.digits(8);
        } while (subscriptionRepository.existsByNumber(n));
        return n;
    }

    private String newRequestNumber() {
        String n;
        do {
            n = "REQ-" + RandomCodes.digits(8);
        } while (requestRepository.existsByRequestNumber(n));
        return n;
    }
}
