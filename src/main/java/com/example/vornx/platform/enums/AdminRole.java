package com.example.vornx.platform.enums;

/** The VORNX team's roles. The names are what the invite email shows. */
public enum AdminRole {
    SUPER_ADMIN("مدير عام", "Super admin"),
    ACCOUNT_MANAGER("مدير حسابات", "Account manager"),
    SUPPORT_AGENT("دعم فني", "Support agent"),
    AUDITOR("مدقق", "Auditor");

    private final String arabic;
    private final String english;

    AdminRole(String arabic, String english) {
        this.arabic = arabic;
        this.english = english;
    }

    public String arabic() {
        return arabic;
    }

    public String english() {
        return english;
    }
}
