package com.example.vornx.pass;

import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.common.exception.ResourceNotFoundException;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

/** The cards a customer added by scanning them. One customer only ever sees and changes their own. */
@Service
@RequiredArgsConstructor
@Transactional
public class CustomerPassService {

    static final int MAX_PER_CUSTOMER = 50;

    private final CustomerPassRepository passes;

    public record PassRequest(
            @NotBlank @Size(max = 80) String name,
            @NotBlank @Size(max = 2048) String code,
            @NotNull CustomerPass.Format format,
            @Size(max = 80) String shownNumber,
            @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "must be a color like #1A2B3C") String color,
            @Size(max = 120) String placeName,
            @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @DecimalMin("-180") @DecimalMax("180") Double longitude) {
    }

    public record PassView(Long id, String name, String code, CustomerPass.Format format, String shownNumber, String color,
                           String placeName, Double latitude, Double longitude, Instant createdAt) {
        static PassView from(CustomerPass p) {
            return new PassView(p.getId(), p.getName(), p.getCode(), p.getFormat(), p.getShownNumber(), p.getColor(),
                    p.getPlaceName(), p.getLatitude(), p.getLongitude(), p.getCreatedAt());
        }
    }

    @Transactional(readOnly = true)
    public List<PassView> list(Long customerId) {
        return passes.findByCustomerIdOrderByCreatedAtAsc(customerId).stream().map(PassView::from).toList();
    }

    public PassView add(Long customerId, PassRequest req) {
        if (passes.countByCustomerId(customerId) >= MAX_PER_CUSTOMER) {
            throw new BusinessException("PASS_LIMIT", "You can add up to " + MAX_PER_CUSTOMER + " cards");
        }
        CustomerPass p = new CustomerPass(customerId, req.name().trim(), req.code(), req.format(),
                blankToNull(req.shownNumber()), req.color().toUpperCase());
        place(p, req);
        return PassView.from(passes.save(p));
    }

    /** Rename, recolor, or set / clear where it is used. The code itself stays (scan again for a new one). */
    public PassView update(Long customerId, Long id, PassRequest req) {
        CustomerPass p = get(customerId, id);
        p.setName(req.name().trim());
        p.setShownNumber(blankToNull(req.shownNumber()));
        p.setColor(req.color().toUpperCase());
        place(p, req);
        return PassView.from(p);
    }

    public void remove(Long customerId, Long id) {
        passes.delete(get(customerId, id));
    }

    private static void place(CustomerPass p, PassRequest req) {
        if ((req.latitude() == null) != (req.longitude() == null)) {
            throw new BusinessException("PASS_LOCATION_INVALID", "Send both latitude and longitude, or neither");
        }
        p.setPlaceName(blankToNull(req.placeName()));
        p.setLatitude(req.latitude());
        p.setLongitude(req.longitude());
    }

    private CustomerPass get(Long customerId, Long id) {
        return passes.findByIdAndCustomerId(id, customerId).orElseThrow(() -> new ResourceNotFoundException("Card", id));
    }

    private static String blankToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
