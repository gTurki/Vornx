package com.example.vornx.customer.service;

import com.example.vornx.common.enums.AccountStatus;
import com.example.vornx.common.enums.Language;
import com.example.vornx.common.exception.BusinessException;
import com.example.vornx.common.exception.ResourceNotFoundException;
import com.example.vornx.customer.dto.CustomerRegisterRequest;
import com.example.vornx.customer.dto.CustomerResponse;
import com.example.vornx.customer.entity.Customer;
import com.example.vornx.customer.event.CustomerRegisteredEvent;
import com.example.vornx.customer.repository.CustomerRepository;
import com.example.vornx.email.EmailService;
import com.example.vornx.email.EmailTemplates;
import com.example.vornx.email.EmailType;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CustomerRepository customerRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final EmailService emailService;
    private final EmailTemplates emailTemplates;

    public Optional<Customer> findByEmail(String email) {
        return customerRepository.findByEmail(email);
    }

    public CustomerResponse getProfile(Long customerId) {
        return CustomerResponse.from(getEntity(customerId));
    }

    public CustomerResponse getProfileByVornxId(String vornxId) {
        return CustomerResponse.from(getByVornxId(vornxId));
    }

    /** For other modules (cards, permits, access engine). */
    public Customer getEntity(Long customerId) {
        return customerRepository.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
    }

    public Customer getByVornxId(String vornxId) {
        String id = vornxId == null ? "" : vornxId.trim().toUpperCase();
        return customerRepository.findByVornxId(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
    }

    public Customer getActiveByVornxId(String vornxId) {
        Customer customer = getByVornxId(vornxId);
        if (customer.getStatus() != AccountStatus.ACTIVE) {
            throw new BusinessException("CUSTOMER_INACTIVE", "This customer account is not active");
        }
        return customer;
    }

    @Transactional // overrides readOnly: this method writes
    public Customer register(CustomerRegisterRequest req) {
        String email = req.email().trim().toLowerCase();

        if (!req.password().equals(req.confirmPassword())) {
            throw new BusinessException("PASSWORD_MISMATCH", "Passwords do not match");
        }
        if (customerRepository.existsByEmail(email)) {
            throw new BusinessException("EMAIL_TAKEN", "This email is already registered");
        }
        if (customerRepository.existsByPhone(req.phone())) {
            throw new BusinessException("PHONE_TAKEN", "This mobile number is already registered");
        }

        Customer customer = new Customer(
                generateVornxId(),
                req.firstName().trim(),
                req.lastName().trim(),
                req.phone(),
                email,
                passwordEncoder.encode(req.password()), // NEVER store the real password
                req.language() != null ? req.language() : Language.AR
        );
        customerRepository.save(customer);
        // Listeners run in this same transaction: if creating the wallet fails, the sign-up is rolled back too.
        events.publishEvent(new CustomerRegisteredEvent(customer.getId()));
        emailService.queue(customer.getEmail(), EmailType.CUSTOMER_WELCOME,
                emailTemplates.customerWelcome(customer.getFirstName(), customer.getVornxId()));
        return customer;
    }

    /** "VX" + 10 random digits, retried until unused. */
    private String generateVornxId() {
        String id;
        do {
            id = "VX" + String.format("%010d", RANDOM.nextLong(10_000_000_000L));
        } while (customerRepository.existsByVornxId(id));
        return id;
    }
}
