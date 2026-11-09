package com.example.vornx.billing.controller;

import com.example.vornx.audit.Audited;
import org.springdoc.core.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.vornx.billing.dto.BillingSummaryResponse;
import com.example.vornx.billing.dto.ContractRequest;
import com.example.vornx.billing.dto.ContractResponse;
import com.example.vornx.billing.dto.GenerateInvoicesRequest;
import com.example.vornx.billing.dto.InvoiceResponse;
import com.example.vornx.billing.dto.RenewContractRequest;
import com.example.vornx.billing.enums.InvoiceStatus;
import com.example.vornx.billing.service.BillingService;
import com.example.vornx.common.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin dashboard -> "العقود والتجديدات", "الفواتير", "الإيرادات". Changes: SUPER_ADMIN / ACCOUNT_MANAGER. */
@Tag(name = "Admin - Billing", description = "Dashboard summary, contracts, renewals and invoices")
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminBillingController {

    private static final String CAN_MANAGE = "hasAnyRole('SUPER_ADMIN','ACCOUNT_MANAGER')";

    private final BillingService billingService;

    @GetMapping("/billing/summary")
    public BillingSummaryResponse summary() {
        return billingService.summary();
    }

    /** GET /api/admin/contracts?attention=true -> only expiring soon / expired */
    @GetMapping("/contracts")
    public List<ContractResponse> contracts(@RequestParam(defaultValue = "false") boolean attention) {
        return billingService.listContracts(attention);
    }

    @GetMapping("/partners/{partnerId}/contracts")
    public List<ContractResponse> partnerContracts(@PathVariable Long partnerId) {
        return billingService.partnerContracts(partnerId);
    }

    @PostMapping("/partners/{partnerId}/contracts")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize(CAN_MANAGE)
    @Audited(action = "CONTRACT_CREATED", target = "CONTRACT", details = "#result.number()")
    public ContractResponse createContract(@PathVariable Long partnerId, @Valid @RequestBody ContractRequest req) {
        return billingService.createContract(partnerId, req);
    }

    @PostMapping("/contracts/{contractId}/renew")
    @PreAuthorize(CAN_MANAGE)
    @Audited(action = "CONTRACT_RENEWED", target = "CONTRACT", partnerId = "#result.partnerId()", details = "'until ' + #result.endDate()")
    public ContractResponse renew(@PathVariable Long contractId,
                                  @Valid @RequestBody(required = false) RenewContractRequest req) {
        return billingService.renew(contractId, req != null ? req.months() : null);
    }

    @PostMapping("/contracts/{contractId}/terminate")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Audited(action = "CONTRACT_TERMINATED", target = "CONTRACT", partnerId = "#result.partnerId()")
    public ContractResponse terminate(@PathVariable Long contractId) {
        return billingService.terminate(contractId);
    }

    /** GET /api/admin/invoices?status=OVERDUE */
    @GetMapping("/invoices")
    public PageResponse<InvoiceResponse> invoices(@RequestParam(required = false) InvoiceStatus status,
                                                  @ParameterObject @PageableDefault(size = 20, sort = "dueDate", direction = Sort.Direction.ASC)
                                                  Pageable pageable) {
        return billingService.listInvoices(status, pageable);
    }

    @PostMapping("/invoices/generate")
    @PreAuthorize(CAN_MANAGE)
    @Audited(action = "INVOICES_GENERATED", target = "INVOICE", details = "#req.period() + ': ' + #result.size() + ' invoice(s)'")
    public List<InvoiceResponse> generate(@Valid @RequestBody GenerateInvoicesRequest req) {
        return billingService.generateInvoices(req.period());
    }

    @PostMapping("/invoices/{invoiceId}/mark-paid")
    @PreAuthorize(CAN_MANAGE)
    @Audited(action = "INVOICE_PAID", target = "INVOICE", partnerId = "#result.partnerId()", details = "#result.number()")
    public InvoiceResponse markPaid(@PathVariable Long invoiceId) {
        return billingService.markPaid(invoiceId);
    }
}
