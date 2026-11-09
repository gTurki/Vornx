package com.example.vornx.access.dto;

import com.example.vornx.access.entity.AccessPoint;
import com.example.vornx.access.enums.AccessMethod;
import com.example.vornx.access.enums.AccessPointStatus;
import com.example.vornx.access.enums.AccessPointType;

import java.util.Set;
import java.util.TreeSet;

public record AccessPointResponse(
        Long id,
        Long siteId,
        String code,
        String name,
        AccessPointType type,
        String floor,
        AccessPointStatus status,
        boolean online,
        Set<AccessMethod> supportedMethods
) {
    public static AccessPointResponse from(AccessPoint ap) {
        return new AccessPointResponse(ap.getId(), ap.getSite().getId(), ap.getCode(), ap.getName(), ap.getType(),
                ap.getFloor(), ap.getStatus(), ap.isOnline(), new TreeSet<>(ap.getSupportedMethods()));
    }
}
