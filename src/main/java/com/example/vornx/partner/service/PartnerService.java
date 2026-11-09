package com.example.vornx.partner.service;

import com.example.vornx.common.dto.PageResponse;
import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.common.exception.ResourceNotFoundException;
import com.example.vornx.partner.dto.PartnerRequest;
import com.example.vornx.partner.dto.PartnerResponse;
import com.example.vornx.partner.entity.Partner;
import com.example.vornx.partner.enums.PartnerStatus;
import com.example.vornx.partner.enums.Sector;
import com.example.vornx.partner.repository.PartnerRepository;
import com.example.vornx.platform.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartnerService {

    private final PartnerRepository partnerRepository;
    private final AdminUserService adminUserService;

    public PageResponse<PartnerResponse> search(Sector sector, PartnerStatus status, String q, Pageable pageable) {
        String query = StringUtils.hasText(q) ? q.trim() : null;
        return PageResponse.from(partnerRepository.search(sector, status, query, pageable).map(PartnerResponse::from));
    }

    public PartnerResponse get(Long id) {
        return PartnerResponse.from(findWithManager(id));
    }

    @Transactional
    public PartnerResponse create(PartnerRequest req) {
        String cr = blankToNull(req.crNumber());
        if (cr != null && partnerRepository.existsByCrNumber(cr)) {
            throw new BusinessException("CR_TAKEN", "A partner with this CR number already exists");
        }
        Partner partner = new Partner(req.name().trim(), req.sector());
        applyDetails(partner, req, cr);
        return PartnerResponse.from(partnerRepository.save(partner));
    }

    @Transactional
    public PartnerResponse update(Long id, PartnerRequest req) {
        Partner partner = findWithManager(id);
        String cr = blankToNull(req.crNumber());
        if (cr != null && partnerRepository.existsByCrNumberAndIdNot(cr, id)) {
            throw new BusinessException("CR_TAKEN", "A partner with this CR number already exists");
        }
        partner.setName(req.name().trim());
        partner.setSector(req.sector());
        applyDetails(partner, req, cr);
        // No save() needed: the entity is "managed", Hibernate writes the changes at the end of the transaction.
        return PartnerResponse.from(partner);
    }

    @Transactional
    public PartnerResponse changeStatus(Long id, PartnerStatus status) {
        Partner partner = findWithManager(id);
        partner.setStatus(status);
        return PartnerResponse.from(partner);
    }

    public long countByStatus(PartnerStatus status) {
        return partnerRepository.countByStatus(status);
    }

    /** For other modules (sites, staff...) that need the Partner entity. */
    public Partner getEntity(Long id) {
        return partnerRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Partner", id));
    }

    private void applyDetails(Partner partner, PartnerRequest req, String cr) {
        partner.setCrNumber(cr);
        partner.setPhone(blankToNull(req.phone()));
        partner.setCity(blankToNull(req.city()));
        partner.setCountry(blankToNull(req.country()));
        partner.setNotes(blankToNull(req.notes()));
        partner.setAccountManager(req.accountManagerId() != null
                ? adminUserService.getAccountManager(req.accountManagerId())
                : null);
    }

    private Partner findWithManager(Long id) {
        return partnerRepository.findWithAccountManagerById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Partner", id));
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
