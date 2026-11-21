package com.example.vornx.pass;

import com.example.vornx.pass.CustomerPassService.PassRequest;
import com.example.vornx.pass.CustomerPassService.PassView;
import com.example.vornx.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Customer app: cards added by scanning (shown under the VORNX card, by location). */
@Tag(name = "Customer - Added cards", description = "Loyalty cards, memberships and tickets scanned into VORNX")
@RestController
@RequestMapping("/api/customer/passes")
@RequiredArgsConstructor
public class CustomerPassController {

    private final CustomerPassService passes;

    @GetMapping
    public List<PassView> list(@AuthenticationPrincipal Jwt jwt) {
        return passes.list(AuthUser.from(jwt).id());
    }

    @Operation(summary = "Add a scanned card", description = "409 PASS_LIMIT (50 cards) / PASS_LOCATION_INVALID.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PassView add(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PassRequest req) {
        return passes.add(AuthUser.from(jwt).id(), req);
    }

    @PutMapping("/{id}")
    public PassView update(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id, @Valid @RequestBody PassRequest req) {
        return passes.update(AuthUser.from(jwt).id(), id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        passes.remove(AuthUser.from(jwt).id(), id);
    }
}
