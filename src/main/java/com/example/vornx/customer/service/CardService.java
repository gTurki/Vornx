package com.example.vornx.customer.service;

import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.common.exception.ResourceNotFoundException;
import com.example.vornx.customer.dto.CardResponse;
import com.example.vornx.customer.entity.Card;
import com.example.vornx.customer.entity.Customer;
import com.example.vornx.customer.enums.CardStatus;
import com.example.vornx.customer.repository.CardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CardService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int VALIDITY_YEARS = 5;

    private final CardRepository cardRepository;
    private final CustomerService customerService;
    private final Clock clock;
    private final ZoneId businessZone;

    /** Called by the access engine on every tap. */
    public Optional<Card> findByNumber(String cardNumber) {
        return cardRepository.findByCardNumber(cardNumber);
    }

    public List<CardResponse> listForCustomer(Long customerId) {
        return cardRepository.findByCustomer_IdOrderByIssuedAtDescIdDesc(customerId).stream()
                .map(CardResponse::from)
                .toList();
    }

    public List<CardResponse> listForVornxId(String vornxId) {
        return listForCustomer(customerService.getByVornxId(vornxId).getId());
    }

    /** Admin issues a new card. Only one ACTIVE card per customer. */
    @Transactional
    public CardResponse issue(String vornxId) {
        Customer customer = customerService.getActiveByVornxId(vornxId);
        if (cardRepository.existsByCustomer_IdAndStatus(customer.getId(), CardStatus.ACTIVE)) {
            throw new BusinessException("CARD_ALREADY_ACTIVE", "This customer already has an active card");
        }
        LocalDate today = LocalDate.now(clock.withZone(businessZone));
        Card card = new Card(customer, generateCardNumber(), today, today.plusYears(VALIDITY_YEARS));
        return CardResponse.from(cardRepository.save(card));
    }

    /** Customer reports the card lost: it stops working immediately. */
    @Transactional
    public CardResponse reportLost(Long customerId, Long cardId) {
        Card card = cardRepository.findByIdAndCustomer_Id(cardId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Card", cardId));
        if (card.getStatus() != CardStatus.ACTIVE) {
            throw new BusinessException("CARD_NOT_ACTIVE", "Only an active card can be reported lost");
        }
        card.setStatus(CardStatus.LOST);
        return CardResponse.from(card);
    }

    /** 16 digits starting with 9 (so it can never be confused with a bank card number). */
    private String generateCardNumber() {
        String number;
        do {
            number = "9" + String.format("%015d", RANDOM.nextLong(1_000_000_000_000_000L));
        } while (cardRepository.existsByCardNumber(number));
        return number;
    }
}
