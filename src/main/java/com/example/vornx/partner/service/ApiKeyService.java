package com.example.vornx.partner.service;

import com.example.vornx.common.exception.ResourceNotFoundException;
import com.example.vornx.common.util.SecureTokens;
import com.example.vornx.partner.dto.ApiKeyCreatedResponse;
import com.example.vornx.partner.dto.ApiKeyResponse;
import com.example.vornx.partner.entity.ApiKey;
import com.example.vornx.partner.enums.PartnerStatus;
import com.example.vornx.partner.repository.ApiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ApiKeyService {

    private static final String KEY_START = "vx_";

    private final ApiKeyRepository apiKeyRepository;
    private final PartnerService partnerService;
    private final Clock clock;

    public List<ApiKeyResponse> list(Long partnerId) {
        return apiKeyRepository.findByPartner_IdOrderByCreatedAtDesc(partnerId).stream()
                .map(ApiKeyResponse::from)
                .toList();
    }

    @Transactional
    public ApiKeyCreatedResponse create(Long partnerId, String name) {
        String rawKey = KEY_START + SecureTokens.generate();
        ApiKey key = new ApiKey(partnerService.getEntity(partnerId), name.trim(),
                rawKey.substring(0, 11), SecureTokens.sha256(rawKey));
        apiKeyRepository.save(key);
        return new ApiKeyCreatedResponse(ApiKeyResponse.from(key), rawKey);
    }

    @Transactional
    public ApiKeyResponse revoke(Long partnerId, Long keyId) {
        ApiKey key = apiKeyRepository.findByIdAndPartner_Id(keyId, partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("API key", keyId));
        key.setActive(false);
        return ApiKeyResponse.from(key);
    }

    /**
     * Called by ApiKeyAuthenticationFilter on every device request.
     * Valid only if the key exists, is active, and its partner is not suspended.
     */
    @Transactional
    public Optional<ApiKey> authenticate(String rawKey) {
        if (!rawKey.startsWith(KEY_START)) {
            return Optional.empty();
        }
        return apiKeyRepository.findByKeyHash(SecureTokens.sha256(rawKey))
                .filter(ApiKey::isActive)
                .filter(k -> k.getPartner().getStatus() != PartnerStatus.SUSPENDED)
                .map(k -> {
                    k.setLastUsedAt(Instant.now(clock));
                    return k;
                });
    }
}
