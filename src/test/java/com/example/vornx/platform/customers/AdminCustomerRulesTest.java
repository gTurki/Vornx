package com.example.vornx.platform.customers;

import com.example.vornx.access.service.AccessDecisionService;
import com.example.vornx.access.service.PermitService;
import com.example.vornx.common.enums.AccountStatus;
import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.customer.entity.Customer;
import com.example.vornx.customer.repository.CustomerRepository;
import com.example.vornx.customer.service.CardService;
import com.example.vornx.events.service.TicketService;
import com.example.vornx.security.UserType;
import com.example.vornx.security.passkey.PasskeyService;
import com.example.vornx.security.session.SessionService;
import com.example.vornx.subscription.service.SubscriptionService;
import com.example.vornx.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Admin "العملاء": search, suspend / reactivate. Database = Mockito fakes. */
class AdminCustomerRulesTest {

    private CustomerRepository customers;
    private SessionService sessions;
    private AdminCustomerService service;
    private Customer khalid;

    @BeforeEach
    void setUp() {
        customers = mock(CustomerRepository.class);
        sessions = mock(SessionService.class);
        service = new AdminCustomerService(customers, mock(CardService.class), mock(WalletService.class), mock(PermitService.class),
                mock(SubscriptionService.class), mock(TicketService.class), mock(AccessDecisionService.class),
                mock(PasskeyService.class), sessions);
        khalid = mock(Customer.class);
        when(khalid.getId()).thenReturn(7L);
        when(khalid.getVornxId()).thenReturn("VX1234567890");
        when(customers.findByVornxId("VX1234567890")).thenReturn(Optional.of(khalid));
    }

    @Test
    void anEmptySearchListsEveryoneAndTheTextIsTrimmed() {
        when(customers.search(any(), any(), any())).thenReturn(Page.empty());
        service.search("   ", null, PageRequest.of(0, 15));
        verify(customers).search(isNull(), isNull(), any());
        service.search("  0551  ", AccountStatus.ACTIVE, PageRequest.of(0, 15));
        verify(customers).search(eq("0551"), eq(AccountStatus.ACTIVE), any());
    }

    @Test
    void suspendingSignsTheCustomerOutEverywhere() {
        service.setStatus("vx1234567890", AccountStatus.SUSPENDED); // lower case is fine
        verify(khalid).setStatus(AccountStatus.SUSPENDED);
        verify(sessions).revokeAll(UserType.CUSTOMER, 7L, null);
    }

    @Test
    void reactivatingDoesNotTouchSessionsAndInvitedIsRefused() {
        service.setStatus("VX1234567890", AccountStatus.ACTIVE);
        verify(khalid).setStatus(AccountStatus.ACTIVE);
        verify(sessions, never()).revokeAll(any(), anyLong(), any());
        assertEquals("INVALID_STATUS", assertThrows(BusinessException.class,
                () -> service.setStatus("VX1234567890", AccountStatus.INVITED)).getCode());
    }
}
