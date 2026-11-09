package com.example.vornx.billing.controller;

import com.example.vornx.audit.Audited;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.vornx.billing.dto.ContractResponse;
import com.example.vornx.billing.dto.InvoiceResponse;
import com.example.vornx.billing.service.BillingService;
import com.example.vornx.security.AuthUser;
import com.example.vornx.support.dto.SupportTicketResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Partner portal -> "العقد والفواتير" */
@Tag(name = "Partner - Contract & invoices", description = "The partner's contract with VORNX and its invoices")
@RestController
@RequestMapping("/api/partner")
@RequiredArgsConstructor
public class PartnerBillingController {

    private final BillingService billingService;

    @GetMapping("/contract")
    public ContractResponse contract(@AuthenticationPrincipal Jwt jwt) {
        return billingService.currentContract(AuthUser.from(jwt).partnerId());
    }

    @GetMapping("/invoices")
    public List<InvoiceResponse> invoices(@AuthenticationPrincipal Jwt jwt) {
        return billingService.partnerInvoices(AuthUser.from(jwt).partnerId());
    }

    /** "اطلب تجديد العقد" */
    @PostMapping("/contract/renewal-request")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('PARTNER_ADMIN')")
    @Audited(action = "CONTRACT_RENEWAL_REQUESTED", target = "SUPPORT_TICKET")
    public SupportTicketResponse requestRenewal(@AuthenticationPrincipal Jwt jwt) {
        AuthUser me = AuthUser.from(jwt);
        return billingService.requestRenewal(me.partnerId(), me.id());
    }
}
