package com.example.vornx.access.dto;

import com.example.vornx.access.enums.AccessMethod;
import com.example.vornx.access.enums.AccessPointType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;

/** Body for creating/updating an access point, e.g. {"code":"1204","name":"Room 1204","type":"ROOM","floor":"12"} */
public record AccessPointRequest(
        @NotBlank @Size(max = 30)
        @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Code may only contain letters, digits, - and _")
        String code,
        @NotBlank @Size(max = 100) String name,
        @NotNull AccessPointType type,
        @Size(max = 20) String floor,
        Set<AccessMethod> supportedMethods
) {
}
