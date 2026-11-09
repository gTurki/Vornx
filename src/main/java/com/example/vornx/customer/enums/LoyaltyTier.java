package com.example.vornx.customer.enums;

/**
 * Membership level from index_76 (Bronze 0, Silver 1000, Gold 2500, Platinum 5000 points).
 * Not stored in the DB: it is always calculated from points, so it can never be wrong.
 */
public enum LoyaltyTier {
    BRONZE(0),
    SILVER(1000),
    GOLD(2500),
    PLATINUM(5000);

    private final int minPoints;

    LoyaltyTier(int minPoints) {
        this.minPoints = minPoints;
    }

    public static LoyaltyTier fromPoints(int points) {
        LoyaltyTier result = BRONZE;
        for (LoyaltyTier tier : values()) {
            if (points >= tier.minPoints) {
                result = tier;
            }
        }
        return result;
    }
}
