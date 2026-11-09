package com.example.vornx.subscription.dto;

import jakarta.validation.constraints.NotNull;

public record SubscribeRequest(@NotNull Long planId) {
}
