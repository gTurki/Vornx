package com.example.vornx.access.service;

import com.example.vornx.access.dto.SiteRequest;
import com.example.vornx.access.dto.SiteResponse;
import com.example.vornx.access.entity.Site;
import com.example.vornx.access.repository.SiteRepository;
import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.common.exception.ResourceNotFoundException;
import com.example.vornx.partner.service.PartnerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/** Every method takes the partnerId (from the token) so one partner can never see another's sites. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SiteService {

    private final SiteRepository siteRepository;
    private final PartnerService partnerService;

    public List<SiteResponse> list(Long partnerId) {
        return siteRepository.findByPartner_IdOrderByName(partnerId).stream().map(SiteResponse::from).toList();
    }

    public SiteResponse get(Long partnerId, Long siteId) {
        return SiteResponse.from(getOwnedSite(partnerId, siteId));
    }

    @Transactional
    public SiteResponse create(Long partnerId, SiteRequest req) {
        Site site = new Site(partnerService.getEntity(partnerId), req.name().trim(), req.type());
        apply(site, req);
        return SiteResponse.from(siteRepository.save(site));
    }

    @Transactional
    public SiteResponse update(Long partnerId, Long siteId, SiteRequest req) {
        Site site = getOwnedSite(partnerId, siteId);
        site.setName(req.name().trim());
        site.setType(req.type());
        apply(site, req);
        return SiteResponse.from(site);
    }

    /** For other services (access points, permits, plans). 404 (not 403) for other partners' sites: we don't reveal they exist. */
    public Site getOwnedSite(Long partnerId, Long siteId) {
        return siteRepository.findByIdAndPartner_Id(siteId, partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Site", siteId));
    }

    private static void apply(Site site, SiteRequest req) {
        if ((req.opensAt() == null) != (req.closesAt() == null)) {
            throw new BusinessException("HOURS_INCOMPLETE", "Set both opensAt and closesAt, or neither for 24 hours");
        }
        site.setAddress(blankToNull(req.address()));
        site.setCity(blankToNull(req.city()));
        site.setCountry(blankToNull(req.country()));
        site.setLatitude(req.latitude());
        site.setLongitude(req.longitude());
        site.setOpensAt(req.opensAt());
        site.setClosesAt(req.closesAt());
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
