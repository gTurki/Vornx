package com.example.vornx.platform.customers;

import com.example.vornx.access.dto.AccessEventResponse;
import com.example.vornx.access.dto.PermitResponse;
import com.example.vornx.access.service.AccessDecisionService;
import com.example.vornx.access.service.PermitService;
import com.example.vornx.common.dto.PageResponse;
import com.example.vornx.common.enums.AccountStatus;
import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.common.exception.ResourceNotFoundException;
import com.example.vornx.customer.dto.CardResponse;
import com.example.vornx.customer.entity.Customer;
import com.example.vornx.customer.enums.LoyaltyTier;
import com.example.vornx.customer.repository.CustomerRepository;
import com.example.vornx.customer.service.CardService;
import com.example.vornx.events.dto.TicketResponse;
import com.example.vornx.events.service.TicketService;
import com.example.vornx.security.UserType;
import com.example.vornx.security.passkey.PasskeyService;
import com.example.vornx.security.session.SessionService;
import com.example.vornx.subscription.dto.ServiceRequestResponse;
import com.example.vornx.subscription.dto.SubscriptionResponse;
import com.example.vornx.subscription.service.SubscriptionService;
import com.example.vornx.wallet.dto.WalletResponse;
import com.example.vornx.wallet.dto.WalletTransactionResponse;
import com.example.vornx.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

/**
 * Admin dashboard -> "العملاء": find a customer, and see everything about them on one page
 * (card, wallet, keys, subscriptions, tickets, recent doors, security). Reuses the same services as the customer app,
 * so the admin sees exactly what the customer sees.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminCustomerService {

    private final CustomerRepository customers;
    private final CardService cards;
    private final WalletService wallets;
    private final PermitService permits;
    private final SubscriptionService subscriptions;
    private final TicketService tickets;
    private final AccessDecisionService access;
    private final PasskeyService passkeys;
    private final SessionService sessions;

    /** One row of the list. */
    public record CustomerRow(Long id, String vornxId, String firstName, String lastName, String email, String phone,
                              AccountStatus status, LoyaltyTier tier, int points, boolean emailVerified,
                              boolean phoneVerified, Instant createdAt) {
        static CustomerRow from(Customer c) {
            return new CustomerRow(c.getId(), c.getVornxId(), c.getFirstName(), c.getLastName(), c.getEmail(), c.getPhone(),
                    c.getStatus(), c.getTier(), c.getPoints(), c.isEmailVerified(), c.isPhoneVerified(), c.getCreatedAt());
        }
    }

    /** The drawer: the customer and everything they have. */
    public record CustomerDetails(
            CustomerRow customer,
            boolean totpEnabled,
            int passkeys,
            Instant termsAcceptedAt,
            List<CardResponse> cards,
            WalletResponse wallet,
            List<WalletTransactionResponse> recentTransactions,
            List<PermitResponse> permits,
            List<SubscriptionResponse> subscriptions,
            List<ServiceRequestResponse> requests,
            List<TicketResponse> tickets,
            List<AccessEventResponse> recentAccess) {
    }

    public PageResponse<CustomerRow> search(String q, AccountStatus status, Pageable pageable) {
        String query = StringUtils.hasText(q) ? q.trim() : null;
        return PageResponse.from(customers.search(query, status, pageable).map(CustomerRow::from));
    }

    public CustomerDetails details(String vornxId) {
        Customer c = get(vornxId);
        Long id = c.getId();
        Pageable recent = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        return new CustomerDetails(
                CustomerRow.from(c),
                c.isTotpEnabled(),
                passkeys.list(id).size(),
                c.getTermsAcceptedAt(),
                cards.listForCustomer(id),
                wallets.getWallet(id),
                wallets.history(id, recent).content(),
                permits.listForCustomer(id),
                subscriptions.mySubscriptions(id),
                subscriptions.myRequests(id),
                tickets.myTickets(id),
                access.customerLog(id, PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "occurredAt"))).content());
    }

    /**
     * Suspend (can't sign in, signed out everywhere, doors refuse their card) or reactivate.
     * The money stays in the wallet: suspending is not closing the account.
     */
    @Transactional
    public CustomerRow setStatus(String vornxId, AccountStatus status) {
        if (status == AccountStatus.INVITED) {
            throw new BusinessException("INVALID_STATUS", "Status can only be ACTIVE or SUSPENDED");
        }
        Customer c = get(vornxId);
        c.setStatus(status);
        if (status == AccountStatus.SUSPENDED) {
            sessions.revokeAll(UserType.CUSTOMER, c.getId(), null);
        }
        return CustomerRow.from(c);
    }

    private Customer get(String vornxId) {
        return customers.findByVornxId(vornxId.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Customer", vornxId));
    }
}
