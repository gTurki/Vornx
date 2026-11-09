package com.example.vornx.billing.service;
import java.util.Map;

import com.example.vornx.notification.enums.NotificationType;
import com.example.vornx.notification.service.NotificationService;

import com.example.vornx.billing.dto.BillingSummaryResponse;
import com.example.vornx.billing.dto.ContractRequest;
import com.example.vornx.billing.dto.ContractResponse;
import com.example.vornx.billing.dto.InvoiceResponse;
import com.example.vornx.billing.entity.Contract;
import com.example.vornx.billing.entity.Invoice;
import com.example.vornx.billing.enums.ContractStatus;
import com.example.vornx.billing.enums.InvoiceStatus;
import com.example.vornx.billing.repository.ContractRepository;
import com.example.vornx.billing.repository.InvoiceRepository;
import com.example.vornx.common.dto.PageResponse;
import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.common.exception.ResourceNotFoundException;
import com.example.vornx.common.util.RandomCodes;
import com.example.vornx.email.EmailService;
import com.example.vornx.email.EmailTemplates;
import com.example.vornx.email.EmailType;
import com.example.vornx.partner.service.PartnerUserService;
import com.example.vornx.partner.enums.PartnerStatus;
import com.example.vornx.partner.service.PartnerService;
import com.example.vornx.support.dto.SupportTicketResponse;
import com.example.vornx.support.enums.TicketCategory;
import com.example.vornx.support.service.SupportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Contracts and invoices between VORNX and its partners. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BillingService {

    private static final int PAYMENT_TERMS_DAYS = 15;

    private final ContractRepository contractRepository;
    private final InvoiceRepository invoiceRepository;
    private final PartnerService partnerService;
    private final SupportService supportService;
    private final Clock clock;
    private final ZoneId businessZone;
    private final NotificationService notificationService;
    private final PartnerUserService partnerUserService;
    private final EmailService emailService;
    private final EmailTemplates emailTemplates;

    // =============== Contracts (admin) ===============

    /** Sorted by nearest end date, like "العقود مرتبة حسب أقرب تاريخ انتهاء". */
    public List<ContractResponse> listContracts(boolean onlyNeedingAttention) {
        LocalDate today = today();
        return contractRepository.findByStatusOrderByEndDateAsc(ContractStatus.ACTIVE).stream()
                .map(c -> ContractResponse.from(c, today))
                .filter(c -> !onlyNeedingAttention || c.status() != ContractStatus.ACTIVE)
                .toList();
    }

    public List<ContractResponse> partnerContracts(Long partnerId) {
        partnerService.getEntity(partnerId);
        LocalDate today = today();
        return contractRepository.findByPartner_IdOrderByStartDateDesc(partnerId).stream()
                .map(c -> ContractResponse.from(c, today))
                .toList();
    }

    @Transactional
    public ContractResponse createContract(Long partnerId, ContractRequest req) {
        if (!req.endDate().isAfter(req.startDate())) {
            throw new BusinessException("INVALID_PERIOD", "endDate must be after startDate");
        }
        LocalDate today = today();
        if (contractRepository.existsByPartner_IdAndStatusAndEndDateGreaterThanEqual(partnerId, ContractStatus.ACTIVE, today)) {
            throw new BusinessException("CONTRACT_EXISTS", "This partner already has a running contract. Renew it instead.");
        }
        String number;
        do {
            number = "CT-" + RandomCodes.digits(6);
        } while (contractRepository.existsByNumber(number));
        Contract contract = new Contract(number, partnerService.getEntity(partnerId), req.planName().trim(), req.tier(),
                req.monthlyFee(), req.startDate(), req.endDate());
        contract.setMaxBeneficiaries(req.maxBeneficiaries());
        contract.setNotes(StringUtils.hasText(req.notes()) ? req.notes().trim() : null);
        return ContractResponse.from(contractRepository.save(contract), today);
    }

    @Transactional
    public ContractResponse renew(Long contractId, Integer months) {
        Contract contract = getContract(contractId);
        if (contract.getStatus() == ContractStatus.TERMINATED) {
            throw new BusinessException("CONTRACT_TERMINATED", "A terminated contract cannot be renewed");
        }
        contract.renew(months != null ? months : 12, today());
        return ContractResponse.from(contract, today());
    }

    @Transactional
    public ContractResponse terminate(Long contractId) {
        Contract contract = getContract(contractId);
        contract.setStatus(ContractStatus.TERMINATED);
        return ContractResponse.from(contract, today());
    }

    // =============== Invoices (admin) ===============

    /**
     * Creates the invoices of one month for every contract that runs during it.
     * Safe to run twice: contracts already invoiced for that month are skipped (also enforced by a UNIQUE key).
     */
    @Transactional
    public List<InvoiceResponse> generateInvoices(String period) {
        YearMonth month = YearMonth.parse(period);
        LocalDate today = today();
        List<InvoiceResponse> created = new ArrayList<>();
        for (Contract c : contractRepository.findActiveDuring(ContractStatus.ACTIVE, month.atDay(1), month.atEndOfMonth())) {
            if (invoiceRepository.existsByContract_IdAndPeriod(c.getId(), month.toString())) {
                continue;
            }
            String number;
            do {
                number = "VX-" + month.getYear() + "-" + RandomCodes.digits(6);
            } while (invoiceRepository.existsByNumber(number));
            Invoice invoice = invoiceRepository.save(
                    new Invoice(number, c, month, today, today.plusDays(PAYMENT_TERMS_DAYS)));
            created.add(InvoiceResponse.from(invoice, today));
            notificationService.notifyPartner(c.getPartner().getId(), NotificationType.INVOICE_ISSUED,
                    Map.of("number", invoice.getNumber(), "period", invoice.getPeriod(), "amount", invoice.getAmount(),
                            "dueDate", invoice.getDueDate()),
                    NotificationService.Ref.of("INVOICE", invoice.getId()));
            for (String adminEmail : partnerUserService.activeAdminEmails(c.getPartner().getId())) {
                emailService.queue(adminEmail, EmailType.INVOICE_ISSUED, emailTemplates.invoiceIssued(
                        c.getPartner().getName(), invoice.getNumber(), invoice.getPeriod(), invoice.getAmount(),
                        invoice.getDueDate()));
            }
        }
        return created;
    }

    /** status=OVERDUE is translated to "DUE with a passed due date". */
    public PageResponse<InvoiceResponse> listInvoices(InvoiceStatus status, Pageable pageable) {
        LocalDate today = today();
        Page<Invoice> page;
        if (status == null) {
            page = invoiceRepository.findAll(pageable);
        } else if (status == InvoiceStatus.OVERDUE) {
            page = invoiceRepository.findByStatusAndDueDateBefore(InvoiceStatus.DUE, today, pageable);
        } else {
            page = invoiceRepository.findByStatus(status, pageable);
        }
        return PageResponse.from(page.map(i -> InvoiceResponse.from(i, today)));
    }

    @Transactional
    public InvoiceResponse markPaid(Long invoiceId) {
        Invoice invoice = invoiceRepository.findWithPartnerById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            throw new BusinessException("ALREADY_PAID", "This invoice is already paid");
        }
        invoice.markPaid(Instant.now(clock));
        return InvoiceResponse.from(invoice, today());
    }

    public BillingSummaryResponse summary() {
        LocalDate today = today();
        return new BillingSummaryResponse(
                partnerService.countByStatus(PartnerStatus.ACTIVE),
                contractRepository.countRunning(ContractStatus.ACTIVE, today),
                contractRepository.sumMonthlyFees(ContractStatus.ACTIVE, today),
                contractRepository.countNeedingAttention(ContractStatus.ACTIVE, today.plusDays(Contract.EXPIRING_SOON_DAYS)),
                invoiceRepository.countOverdue(InvoiceStatus.DUE, today),
                invoiceRepository.sumOverdue(InvoiceStatus.DUE, today),
                invoiceRepository.sumByStatus(InvoiceStatus.DUE),
                supportService.countUnresolved());
    }

    // =============== Partner portal ===============

    public ContractResponse currentContract(Long partnerId) {
        Contract contract = contractRepository.findFirstByPartner_IdAndStatusOrderByEndDateDesc(partnerId, ContractStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("Contract for partner", partnerId));
        return ContractResponse.from(contract, today());
    }

    public List<InvoiceResponse> partnerInvoices(Long partnerId) {
        LocalDate today = today();
        return invoiceRepository.findByPartner_IdOrderByPeriodDesc(partnerId).stream()
                .map(i -> InvoiceResponse.from(i, today))
                .toList();
    }

    /** "اطلب تجديد العقد" -> opens a support ticket for the account manager. */
    @Transactional
    public SupportTicketResponse requestRenewal(Long partnerId, Long partnerUserId) {
        ContractResponse contract = currentContract(partnerId);
        return supportService.openFromSystem(partnerId, partnerUserId, TicketCategory.BILLING_AND_CONTRACT,
                "Contract renewal request - " + contract.number(),
                "We would like to renew contract " + contract.number() + " (" + contract.planName()
                        + "), which ends on " + contract.endDate() + ".");
    }

    // =============== helpers ===============

    private Contract getContract(Long id) {
        return contractRepository.findWithPartnerById(id).orElseThrow(() -> new ResourceNotFoundException("Contract", id));
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(businessZone));
    }
}
