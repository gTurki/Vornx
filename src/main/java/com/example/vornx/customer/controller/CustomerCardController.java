package com.example.vornx.customer.controller;

import com.example.vornx.audit.Audited;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.vornx.customer.dto.CardResponse;
import com.example.vornx.customer.service.CardService;
import com.example.vornx.security.AuthUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Customer app -> my VORNX card. */
@Tag(name = "Customer - Cards", description = "My VORNX card, report it lost")
@RestController
@RequestMapping("/api/customer/cards")
@RequiredArgsConstructor
public class CustomerCardController {

    private final CardService cardService;

    @GetMapping
    public List<CardResponse> myCards(@AuthenticationPrincipal Jwt jwt) {
        return cardService.listForCustomer(AuthUser.from(jwt).id());
    }

    @PostMapping("/{cardId}/report-lost")
    @Audited(action = "CARD_REPORTED_LOST", target = "CARD", details = "'**** ' + #result.last4()")
    public CardResponse reportLost(@AuthenticationPrincipal Jwt jwt, @PathVariable Long cardId) {
        return cardService.reportLost(AuthUser.from(jwt).id(), cardId);
    }
}
