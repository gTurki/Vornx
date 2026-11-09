package com.example.vornx.access.service;

import com.example.vornx.access.dto.AccessPointRequest;
import com.example.vornx.access.dto.AccessPointResponse;
import com.example.vornx.access.entity.AccessPoint;
import com.example.vornx.access.entity.Site;
import com.example.vornx.access.enums.AccessPointStatus;
import com.example.vornx.access.repository.AccessPointRepository;
import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccessPointService {

    private final AccessPointRepository accessPointRepository;
    private final SiteService siteService;

    public List<AccessPointResponse> list(Long partnerId, Long siteId) {
        siteService.getOwnedSite(partnerId, siteId); // 404 unless this partner owns the site
        return accessPointRepository.findBySite_IdOrderByCode(siteId).stream()
                .map(AccessPointResponse::from)
                .toList();
    }

    @Transactional
    public AccessPointResponse create(Long partnerId, Long siteId, AccessPointRequest req) {
        Site site = siteService.getOwnedSite(partnerId, siteId);
        String code = req.code().trim().toUpperCase();
        if (accessPointRepository.existsBySite_IdAndCode(siteId, code)) {
            throw new BusinessException("CODE_TAKEN", "This site already has an access point with code " + code);
        }
        AccessPoint ap = new AccessPoint(site, code, req.name().trim(), req.type());
        ap.setFloor(blankToNull(req.floor()));
        ap.replaceSupportedMethods(req.supportedMethods());
        return AccessPointResponse.from(accessPointRepository.save(ap));
    }

    @Transactional
    public AccessPointResponse update(Long partnerId, Long accessPointId, AccessPointRequest req) {
        AccessPoint ap = getOwned(partnerId, accessPointId);
        String code = req.code().trim().toUpperCase();
        if (accessPointRepository.existsBySite_IdAndCodeAndIdNot(ap.getSite().getId(), code, ap.getId())) {
            throw new BusinessException("CODE_TAKEN", "This site already has an access point with code " + code);
        }
        ap.setCode(code);
        ap.setName(req.name().trim());
        ap.setType(req.type());
        ap.setFloor(blankToNull(req.floor()));
        ap.replaceSupportedMethods(req.supportedMethods());
        return AccessPointResponse.from(ap);
    }

    /** Front desk marks a room OCCUPIED / CLEANING / AVAILABLE... */
    @Transactional
    public AccessPointResponse changeStatus(Long partnerId, Long accessPointId, AccessPointStatus status) {
        AccessPoint ap = getOwned(partnerId, accessPointId);
        ap.setStatus(status);
        return AccessPointResponse.from(ap);
    }

    /**
     * For permits and plans: load these access points and check every one exists AND belongs to the site.
     * Empty/null ids = "the whole site" -> empty list.
     */
    public List<AccessPoint> findInSite(Site site, Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Set<Long> unique = new HashSet<>(ids);
        List<AccessPoint> found = accessPointRepository.findAllById(unique);
        boolean allInSite = found.size() == unique.size()
                && found.stream().allMatch(ap -> ap.getSite().getId().equals(site.getId()));
        if (!allInSite) {
            throw new BusinessException("ACCESS_POINT_NOT_IN_SITE", "Every access point must belong to the chosen site");
        }
        return found;
    }

    private AccessPoint getOwned(Long partnerId, Long accessPointId) {
        return accessPointRepository.findByIdAndSite_Partner_Id(accessPointId, partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Access point", accessPointId));
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
