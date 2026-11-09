package com.example.vornx.subscription.enums;

public enum RequestStatus {
    PENDING,   // "قيد المراجعة"
    APPROVED,
    REJECTED,  // money refunded
    CANCELLED  // cancelled by the customer, money refunded
}
