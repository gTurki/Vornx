package com.example.vornx.customer.controller;

import com.example.vornx.audit.Audited;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.vornx.customer.dto.CardResponse;
import com.example.vornx.customer.dto.CustomerResponse;
import com.example.vornx.customer.service.CardService;
import com.example.vornx.customer.service.CustomerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin dashboard: look up a customer by VORNX ID and issue their card. */
@Tag(name = "Admin - Customers & cards", description = "Look up customers by VORNX ID and issue cards")
@RestController
@RequestMapping("/api/admin/customers/{vornxId}")
@RequiredArgsConstructor
public class AdminCustomerController {

    private final CustomerService customerService;
    private final CardService cardService;

    @GetMapping
    public CustomerResponse get(@PathVariable String vornxId) {
        return customerService.getProfileByVornxId(vornxId);
    }

    @GetMapping("/cards")
    public List<CardResponse> cards(@PathVariable String vornxId) {
        return cardService.listForVornxId(vornxId);
    }

    @PostMapping("/cards")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SUPPORT_AGENT')")
    @Audited(action = "CARD_ISSUED", target = "CARD", details = "#vornxId")
    public CardResponse issueCard(@PathVariable String vornxId) {
        return cardService.issue(vornxId);
    }
}
