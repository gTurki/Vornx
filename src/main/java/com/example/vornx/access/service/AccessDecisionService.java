package com.example.vornx.access.service;

import com.example.vornx.access.dto.AccessDecisionResponse;
import com.example.vornx.access.dto.AccessEventResponse;
import com.example.vornx.access.dto.CardAccessRequest;
import com.example.vornx.access.entity.AccessDecision;
import com.example.vornx.access.entity.AccessPoint;
import com.example.vornx.access.entity.Permit;
import com.example.vornx.access.enums.AccessMethod;
import com.example.vornx.access.enums.AccessPointStatus;
import com.example.vornx.access.enums.Decision;
import com.example.vornx.access.enums.DenyReason;
import com.example.vornx.access.enums.Direction;
import com.example.vornx.access.enums.EnforcementResult;
import com.example.vornx.access.event.AccessGrantedEvent;
import com.example.vornx.access.repository.AccessDecisionRepository;
import com.example.vornx.access.repository.AccessPointRepository;
import com.example.vornx.access.repository.PermitRepository;
import com.example.vornx.common.dto.PageResponse;
import com.example.vornx.common.enums.AccountStatus;
import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.common.exception.ResourceNotFoundException;
import com.example.vornx.customer.entity.Card;
import com.example.vornx.customer.entity.Customer;
import com.example.vornx.customer.service.CardService;
import com.example.vornx.customer.service.CustomerService;
import com.example.vornx.security.device.DevicePrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * THE ACCESS ENGINE. Answers "can this person open this access point right now?"
 *
 *   1. Present   - a card is tapped (or "open" pressed in the app)
 *   2. Verify    - is the card valid? is the account active?
 *   3. Evaluate  - does the door accept this method? is it in service? which permits match?
 *                  are their dates, days and hours OK?
 *   4. Decide    - ALLOW or DENY + reason
 *   5. Record    - every decision is saved (audit trail), allowed or not
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AccessDecisionService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();

    private final AccessPointRepository accessPointRepository;
    private final PermitRepository permitRepository;
    private final AccessDecisionRepository accessDecisionRepository;
    private final CardService cardService;
    private final CustomerService customerService;
    private final Clock clock;
    private final ZoneId businessZone;
    private final ApplicationEventPublisher events;

    /** The result of the evaluation, before it is saved. */
    private record Outcome(DenyReason reason, Permit permit) {
        static Outcome allow(Permit permit) {
            return new Outcome(null, permit);
        }

        static Outcome deny(DenyReason reason) {
            return new Outcome(reason, null);
        }

        static Outcome deny(DenyReason reason, Permit permit) {
            return new Outcome(reason, permit);
        }

        Decision decision() {
            return reason == null ? Decision.ALLOW : Decision.DENY;
        }
    }

    // ---------- Entry point 1: a reader (card tap) ----------

    public AccessDecisionResponse decideByCard(DevicePrincipal device, CardAccessRequest req) {
        // The reader can only ask about access points of ITS partner.
        AccessPoint ap = accessPointRepository.findByIdAndSite_Partner_Id(req.accessPointId(), device.partnerId())
                .orElseThrow(() -> new ResourceNotFoundException("Access point", req.accessPointId()));
        AccessMethod method = req.method() != null ? req.method() : AccessMethod.CARD;
        Direction direction = req.direction() != null ? req.direction() : Direction.ENTRY;
        Instant now = Instant.now(clock);

        Card card = cardService.findByNumber(req.cardNumber().replaceAll("\\s", "")).orElse(null);
        Outcome outcome;
        Customer customer = null;
        if (card == null || !card.isUsable(LocalDate.ofInstant(now, businessZone))) {
            outcome = Outcome.deny(DenyReason.CARD_INVALID);
            customer = card != null ? card.getCustomer() : null;
        } else {
            customer = card.getCustomer();
            outcome = evaluate(ap, customer, method, now);
        }

        AccessDecision saved = record(ap, customer, card, outcome, device.apiKeyId(), method, direction, now);
        return AccessDecisionResponse.from(saved);
    }

    // ---------- Entry point 2: the customer app ("افتح الباب عن بُعد") ----------

    public AccessDecisionResponse decideRemote(Long customerId, Long accessPointId, Direction direction) {
        AccessPoint ap = accessPointRepository.findWithSiteById(accessPointId)
                .orElseThrow(() -> new ResourceNotFoundException("Access point", accessPointId));
        Customer customer = customerService.getEntity(customerId);
        Instant now = Instant.now(clock);

        Outcome outcome = evaluate(ap, customer, AccessMethod.REMOTE, now);
        AccessDecision saved = record(ap, customer, null, outcome, null, AccessMethod.REMOTE,
                direction != null ? direction : Direction.ENTRY, now);
        if (saved.getDecision() == Decision.ALLOW) {
            // No lock integration yet, so nothing physically opens. Later: send the open command to the lock here.
            saved.setEnforcementResult(EnforcementResult.NOT_EXECUTED);
        }
        return AccessDecisionResponse.from(saved);
    }

    // ---------- The reader reports whether the door actually opened ----------

    public AccessDecisionResponse reportResult(DevicePrincipal device, String decisionNumber, EnforcementResult result) {
        AccessDecision d = accessDecisionRepository
                .findByDecisionNumberAndAccessPoint_Site_Partner_Id(decisionNumber, device.partnerId())
                .orElseThrow(() -> new ResourceNotFoundException("Decision", decisionNumber));
        if (d.getDecision() != Decision.ALLOW) {
            throw new BusinessException("NOT_ALLOWED_DECISION", "Only an ALLOW decision can have an enforcement result");
        }
        d.setEnforcementResult(result);
        return AccessDecisionResponse.from(d);
    }

    // ---------- Logs ----------

    @Transactional(readOnly = true)
    public PageResponse<AccessEventResponse> customerLog(Long customerId, Pageable pageable) {
        return PageResponse.from(accessDecisionRepository.findByCustomer_Id(customerId, pageable)
                .map(AccessEventResponse::from));
    }

    @Transactional(readOnly = true)
    public PageResponse<AccessEventResponse> partnerLog(Long partnerId, Long siteId, Pageable pageable) {
        Page<AccessDecision> page = siteId != null
                ? accessDecisionRepository.findByAccessPoint_Site_IdAndAccessPoint_Site_Partner_Id(siteId, partnerId, pageable)
                : accessDecisionRepository.findByAccessPoint_Site_Partner_Id(partnerId, pageable);
        return PageResponse.from(page.map(AccessEventResponse::from));
    }

    // ---------- The rules ----------

    private Outcome evaluate(AccessPoint ap, Customer customer, AccessMethod method, Instant now) {
        if (customer.getStatus() != AccountStatus.ACTIVE) {
            return Outcome.deny(DenyReason.ACCOUNT_INACTIVE);
        }
        if (!ap.getSupportedMethods().contains(method)) {
            return Outcome.deny(DenyReason.METHOD_NOT_SUPPORTED);
        }
        if (ap.getStatus() == AccessPointStatus.OUT_OF_SERVICE) {
            return Outcome.deny(DenyReason.ACCESS_POINT_UNAVAILABLE);
        }

        List<Permit> candidates = permitRepository.findCandidates(customer.getId(), ap.getSite().getId(), ap);
        if (candidates.isEmpty()) {
            return Outcome.deny(DenyReason.NO_PERMIT);
        }

        // One valid permit is enough. If none is valid, report the reason of the one that got furthest.
        Permit closest = null;
        PermitRules.Result closestResult = null;
        for (Permit permit : candidates) {
            PermitRules.Result result = PermitRules.check(permit, now, businessZone);
            if (result.allowed()) {
                return Outcome.allow(permit);
            }
            if (closestResult == null || result.rank() > closestResult.rank()) {
                closest = permit;
                closestResult = result;
            }
        }
        return Outcome.deny(closestResult.reason(), closest);
    }

    private AccessDecision record(AccessPoint ap, Customer customer, Card card, Outcome outcome, Long apiKeyId,
                                  AccessMethod method, Direction direction, Instant now) {
        AccessDecision decision = new AccessDecision(generateDecisionNumber(), ap, customer, card, outcome.permit(),
                apiKeyId, method, direction, outcome.decision(), outcome.reason(), now);
        log.info("Access {} {} at access point {} (reason: {})", decision.getDecisionNumber(), outcome.decision(),
                ap.getId(), outcome.reason());
        accessDecisionRepository.save(decision);
        if (decision.getDecision() == Decision.ALLOW) {
            // e.g. the events module marks the ticket as checked in
            events.publishEvent(new AccessGrantedEvent(decision.getId(), outcome.permit().getId(),
                    customer.getId(), now));
        }
        return decision;
    }

    /** e.g. "AD-K7Q2M9XH4P" (no 0/O/1/I to avoid confusion when read aloud). */
    private static String generateDecisionNumber() {
        StringBuilder sb = new StringBuilder("AD-");
        for (int i = 0; i < 10; i++) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
