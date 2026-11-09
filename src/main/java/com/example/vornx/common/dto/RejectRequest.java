package com.example.vornx.common.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The customer sees this reason ("تفاصيل الرفض"), so it is required. */
public record RejectRequest(@NotBlank @Size(max = 500) String reason) {
}
