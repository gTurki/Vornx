package com.example.vornx.subscription.service;

import com.example.vornx.access.entity.Site;
import com.example.vornx.access.service.AccessPointService;
import com.example.vornx.access.service.SiteService;
import com.example.vornx.common.dto.PageResponse;
import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.common.exception.ResourceNotFoundException;
import com.example.vornx.partner.enums.PartnerStatus;
import com.example.vornx.subscription.dto.PlanRequest;
import com.example.vornx.subscription.dto.PlanResponse;
import com.example.vornx.subscription.entity.SubscriptionPlan;
import com.example.vornx.subscription.repository.SubscriptionPlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlanService {

    private final SubscriptionPlanRepository planRepository;
    private final SiteService siteService;
    private final AccessPointService accessPointService;

    // ---------- Partner ----------

    public List<PlanResponse> listForPartner(Long partnerId) {
        return planRepository.findByPartner_IdOrderByName(partnerId).stream().map(PlanResponse::from).toList();
    }

    public PlanResponse getForPartner(Long partnerId, Long planId) {
        return PlanResponse.from(getOwned(partnerId, planId));
    }

    @Transactional
    public PlanResponse create(Long partnerId, PlanRequest req) {
        Site site = siteService.getOwnedSite(partnerId, req.siteId());
        SubscriptionPlan plan = new SubscriptionPlan(site.getPartner(), site, req.name().trim());
        apply(plan, site, req);
        return PlanResponse.from(planRepository.save(plan));
    }

    /** Price changes apply to NEW requests only; requests already paid keep their amount. */
    @Transactional
    public PlanResponse update(Long partnerId, Long planId, PlanRequest req) {
        SubscriptionPlan plan = getOwned(partnerId, planId);
        Site site = siteService.getOwnedSite(partnerId, req.siteId());
        plan.setSite(site);
        plan.setName(req.name().trim());
        apply(plan, site, req);
        return PlanResponse.from(plan);
    }

    // ---------- Customer catalog ----------

    public PageResponse<PlanResponse> catalog(Long partnerId, Long siteId, Pageable pageable) {
        return PageResponse.from(planRepository.catalog(PartnerStatus.ACTIVE, partnerId, siteId, pageable)
                .map(PlanResponse::from));
    }

    // ---------- helpers ----------

    private void apply(SubscriptionPlan plan, Site site, PlanRequest req) {
        if ((req.allowedFrom() == null) != (req.allowedTo() == null)) {
            throw new BusinessException("HOURS_INCOMPLETE", "Set both allowedFrom and allowedTo, or neither");
        }
        plan.setDescription(StringUtils.hasText(req.description()) ? req.description().trim() : null);
        plan.setPermitType(req.permitType());
        plan.setBillingPeriod(req.billingPeriod());
        plan.setPrice(req.price());
        plan.setRequiresApproval(req.requiresApproval() == null || req.requiresApproval());
        plan.setActive(req.active() == null || req.active());
        plan.setAllowedFrom(req.allowedFrom());
        plan.setAllowedTo(req.allowedTo());
        plan.getAllowedDays().clear();
        if (req.allowedDays() != null) {
            plan.getAllowedDays().addAll(req.allowedDays());
        }
        plan.getAccessPoints().clear();
        plan.getAccessPoints().addAll(accessPointService.findInSite(site, req.accessPointIds()));
    }

    private SubscriptionPlan getOwned(Long partnerId, Long planId) {
        return planRepository.findByIdAndPartner_Id(planId, partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan", planId));
    }
}
