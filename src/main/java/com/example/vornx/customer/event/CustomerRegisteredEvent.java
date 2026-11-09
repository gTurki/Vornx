package com.example.vornx.customer.event;

/**
 * Published when a customer signs up. Other modules react to it (the wallet module creates a wallet)
 * WITHOUT the customer module knowing about them. This keeps modules independent.
 */
public record CustomerRegisteredEvent(Long customerId) {
}
