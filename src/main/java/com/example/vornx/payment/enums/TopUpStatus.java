package com.example.vornx.payment.enums;

public enum TopUpStatus {
    PENDING,  // invoice created, customer is (maybe) paying on Moyasar's page
    PAID,     // verified with Moyasar and the wallet was credited
    FAILED,   // Moyasar says failed/canceled, or the paid amount did not match (needs a human)
    EXPIRED   // nobody paid before the invoice expired
}
