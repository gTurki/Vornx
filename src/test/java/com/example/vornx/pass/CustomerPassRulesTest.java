package com.example.vornx.pass;

import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.common.exception.ResourceNotFoundException;
import com.example.vornx.pass.CustomerPassService.PassRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Cards added by scanning: limits, ownership, location. Database = Mockito fakes. */
class CustomerPassRulesTest {

    private CustomerPassRepository repo;
    private CustomerPassService service;

    @BeforeEach
    void setUp() {
        repo = mock(CustomerPassRepository.class);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        service = new CustomerPassService(repo);
    }

    private static PassRequest lavin(Double lat, Double lng) {
        return new PassRequest(" Lavin ", "4558382", CustomerPass.Format.QR_CODE, " 4558382 ", "#1a1a1a", "Lavin — العليا", lat, lng);
    }

    @Test
    void aScannedCardIsSavedTrimmedWithItsPlace() {
        CustomerPassService.PassView v = service.add(7L, lavin(24.69, 46.68));
        assertEquals("Lavin", v.name());
        assertEquals("4558382", v.shownNumber());
        assertEquals("#1A1A1A", v.color());
        assertEquals(24.69, v.latitude());
    }

    @Test
    void halfALocationIsRefused() {
        assertEquals("PASS_LOCATION_INVALID", assertThrows(BusinessException.class, () -> service.add(7L, lavin(24.69, null))).getCode());
    }

    @Test
    void thereIsALimitOf50() {
        when(repo.countByCustomerId(7L)).thenReturn(50L);
        assertEquals("PASS_LIMIT", assertThrows(BusinessException.class, () -> service.add(7L, lavin(null, null))).getCode());
        verify(repo, never()).save(any());
    }

    @Test
    void nobodyTouchesAnotherCustomersCard() {
        when(repo.findByIdAndCustomerId(5L, 8L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.remove(8L, 5L));
        assertThrows(ResourceNotFoundException.class, () -> service.update(8L, 5L, lavin(null, null)));
    }

    @Test
    void theLocationCanBeCleared() {
        CustomerPass p = new CustomerPass(7L, "Lavin", "4558382", CustomerPass.Format.QR_CODE, null, "#000000");
        p.setLatitude(24.0);
        p.setLongitude(46.0);
        when(repo.findByIdAndCustomerId(1L, 7L)).thenReturn(Optional.of(p));
        service.update(7L, 1L, lavin(null, null));
        assertNull(p.getLatitude());
    }
}
