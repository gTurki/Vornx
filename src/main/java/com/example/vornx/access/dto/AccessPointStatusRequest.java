package com.example.vornx.access.dto;

import com.example.vornx.access.enums.AccessPointStatus;
import jakarta.validation.constraints.NotNull;

public record AccessPointStatusRequest(@NotNull AccessPointStatus status) {
}
