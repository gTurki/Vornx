package com.example.vornx.partner.service;

import com.example.vornx.common.enums.AccountStatus;
import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.common.exception.ResourceNotFoundException;
import com.example.vornx.common.util.SecureTokens;
import com.example.vornx.email.EmailService;
import com.example.vornx.email.EmailTemplates;
import com.example.vornx.email.EmailType;
import com.example.vornx.partner.dto.InvitePartnerUserRequest;
import com.example.vornx.partner.dto.PartnerUserInviteResponse;
import com.example.vornx.partner.dto.PartnerUserResponse;
import com.example.vornx.partner.dto.PartnerUserUpdateRequest;
import com.example.vornx.partner.entity.Partner;
import com.example.vornx.partner.entity.PartnerUser;
import com.example.vornx.partner.enums.PartnerRole;
import com.example.vornx.partner.enums.PartnerStatus;
import com.example.vornx.partner.repository.PartnerUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartnerUserService {

    private static final Duration INVITE_VALIDITY = Duration.ofDays(7);

    private final PartnerUserRepository partnerUserRepository;
    private final PartnerService partnerService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final EmailTemplates emailTemplates;

    @Value("${vornx.dev.expose-tokens:false}")
    private boolean exposeTokens;

    public Optional<PartnerUser> findByEmail(String email) {
        return partnerUserRepository.findByEmail(email);
    }

    /** For the auth module (refresh re-checks the user and their partner). */
    public PartnerUser getEntity(Long partnerUserId) {
        return partnerUserRepository.findWithPartnerById(partnerUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Partner user", partnerUserId));
    }

    public PartnerUserResponse getProfile(Long partnerUserId) {
        return partnerUserRepository.findWithPartnerById(partnerUserId)
                .map(PartnerUserResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Partner user", partnerUserId));
    }

    public List<PartnerUserResponse> listTeam(Long partnerId) {
        return partnerUserRepository.findByPartner_IdOrderByFullName(partnerId).stream()
                .map(PartnerUserResponse::from)
                .toList();
    }

    /** Used both by VORNX admins (first staff member) and by a partner's own PARTNER_ADMIN. */
    @Transactional
    public PartnerUserInviteResponse invite(Long partnerId, InvitePartnerUserRequest req) {
        Partner partner = partnerService.getEntity(partnerId);
        if (partner.getStatus() == PartnerStatus.SUSPENDED) {
            throw new BusinessException("PARTNER_SUSPENDED", "Cannot invite staff to a suspended partner");
        }
        String email = req.email().trim().toLowerCase();
        if (partnerUserRepository.existsByEmail(email)) {
            throw new BusinessException("EMAIL_TAKEN", "This email already has a partner account");
        }

        String rawToken = SecureTokens.generate();
        Instant expiresAt = Instant.now().plus(INVITE_VALIDITY);
        PartnerUser user = PartnerUser.invite(partner, req.fullName().trim(), email, req.role(),
                SecureTokens.sha256(rawToken), expiresAt);
        partnerUserRepository.save(user);

        emailService.queue(email, EmailType.PARTNER_INVITE,
                emailTemplates.partnerInvite(user.getFullName(), partner.getName(), req.role().name(), rawToken));
        // The token travels ONLY in the email. In development (expose-tokens=true) it is also returned, to test easily.
        return new PartnerUserInviteResponse(PartnerUserResponse.from(user), exposeTokens ? rawToken : null, expiresAt);
    }

    /** E-mail addresses of a partner's active PARTNER_ADMINs (they receive billing emails). */
    public List<String> activeAdminEmails(Long partnerId) {
        return partnerUserRepository.findByPartner_IdAndRoleAndStatus(partnerId, PartnerRole.PARTNER_ADMIN,
                AccountStatus.ACTIVE).stream().map(PartnerUser::getEmail).toList();
    }

    /** The invited person opens the link and chooses a password. */
    @Transactional
    public PartnerUser acceptInvite(String rawToken, String password) {
        PartnerUser user = partnerUserRepository.findByInviteTokenHash(SecureTokens.sha256(rawToken))
                .filter(u -> u.getStatus() == AccountStatus.INVITED)
                .orElseThrow(() -> new BusinessException("INVITE_INVALID", "This invite link is invalid or already used"));
        if (user.getInviteExpiresAt().isBefore(Instant.now())) {
            throw new BusinessException("INVITE_EXPIRED", "This invite link has expired. Ask for a new one.");
        }
        user.acceptInvite(passwordEncoder.encode(password));
        return user;
    }

    /** A PARTNER_ADMIN changes someone's role or suspends them. */
    @Transactional
    public PartnerUserResponse updateTeamMember(Long partnerId, Long userId, Long actingUserId,
                                                PartnerUserUpdateRequest req) {
        if (userId.equals(actingUserId)) {
            throw new BusinessException("CANNOT_CHANGE_SELF", "You cannot change your own role or status");
        }
        // Searching by id AND partnerId: a partner can never touch another partner's staff.
        PartnerUser user = partnerUserRepository.findByIdAndPartner_Id(userId, partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Partner user", userId));

        if (req.role() != null) {
            user.setRole(req.role());
        }
        if (req.status() != null) {
            if (req.status() == AccountStatus.INVITED) {
                throw new BusinessException("INVALID_STATUS", "Status can only be ACTIVE or SUSPENDED");
            }
            if (req.status() == AccountStatus.ACTIVE && user.getStatus() == AccountStatus.INVITED) {
                throw new BusinessException("INVITE_PENDING", "This person has not accepted the invite yet");
            }
            user.setStatus(req.status());
        }
        return PartnerUserResponse.from(user);
    }
}
