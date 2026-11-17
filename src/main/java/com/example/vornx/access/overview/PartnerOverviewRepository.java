package com.example.vornx.access.overview;

import com.example.vornx.access.entity.AccessDecision;
import com.example.vornx.access.enums.AccessPointType;
import com.example.vornx.access.enums.Decision;
import com.example.vornx.access.enums.PermitStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/** Read-only counts for the partner portal. Every query is scoped to ONE partner and aggregated in the database. */
public interface PartnerOverviewRepository extends Repository<AccessDecision, Long> {

    /**
     * Entries per local day: rows of [date "2026-11-18", decision "ALLOW"/"DENY", count].
     * offsetSeconds = the business time zone's UTC offset (Riyadh +3h, no daylight saving).
     */
    @Query(value = """
            SELECT DATE(DATE_ADD(d.occurred_at, INTERVAL :offset SECOND)) AS day, d.decision, COUNT(*)
            FROM access_decisions d
            JOIN access_points ap ON ap.id = d.access_point_id
            JOIN sites s ON s.id = ap.site_id
            WHERE s.partner_id = :partnerId AND d.occurred_at >= :from
            GROUP BY day, d.decision
            """, nativeQuery = true)
    List<Object[]> entriesPerDay(@Param("partnerId") Long partnerId, @Param("from") Instant from,
                                 @Param("offset") int offsetSeconds);

    /** Rows of [siteId, decision, count] since `from`. */
    @Query("""
            SELECT d.accessPoint.site.id, d.decision, COUNT(d) FROM AccessDecision d
            WHERE d.accessPoint.site.partner.id = :partnerId AND d.occurredAt >= :from
            GROUP BY d.accessPoint.site.id, d.decision
            """)
    List<Object[]> entriesPerSite(@Param("partnerId") Long partnerId, @Param("from") Instant from);

    /** Rows of [siteId, all access points, rooms]. */
    @Query("""
            SELECT ap.site.id, COUNT(ap), SUM(CASE WHEN ap.type = :room THEN 1 ELSE 0 END) FROM AccessPoint ap
            WHERE ap.site.partner.id = :partnerId
            GROUP BY ap.site.id
            """)
    List<Object[]> accessPointsPerSite(@Param("partnerId") Long partnerId, @Param("room") AccessPointType room);

    /** Rows of [siteId, valid permits right now]. */
    @Query("""
            SELECT p.site.id, COUNT(p) FROM Permit p
            WHERE p.partner.id = :partnerId AND p.status = :active AND p.validFrom <= :now AND p.validUntil > :now
            GROUP BY p.site.id
            """)
    List<Object[]> activeKeysPerSite(@Param("partnerId") Long partnerId, @Param("active") PermitStatus active,
                                     @Param("now") Instant now);

    /** Rows of [siteId, rooms covered by a valid permit] = occupancy. */
    @Query("""
            SELECT ap.site.id, COUNT(DISTINCT ap.id) FROM Permit p JOIN p.accessPoints ap
            WHERE p.partner.id = :partnerId AND p.status = :active AND p.validFrom <= :now AND p.validUntil > :now
              AND ap.type = :room
            GROUP BY ap.site.id
            """)
    List<Object[]> occupiedRoomsPerSite(@Param("partnerId") Long partnerId, @Param("active") PermitStatus active,
                                        @Param("now") Instant now, @Param("room") AccessPointType room);

    /** Keys (not revoked) that start in [from, to). */
    @Query("""
            SELECT COUNT(p) FROM Permit p
            WHERE p.partner.id = :partnerId AND p.status <> :revoked AND p.validFrom >= :from AND p.validFrom < :to
            """)
    long startingBetween(@Param("partnerId") Long partnerId, @Param("revoked") PermitStatus revoked,
                         @Param("from") Instant from, @Param("to") Instant to);

    /** Keys (not revoked) that end in [from, to). */
    @Query("""
            SELECT COUNT(p) FROM Permit p
            WHERE p.partner.id = :partnerId AND p.status <> :revoked AND p.validUntil >= :from AND p.validUntil < :to
            """)
    long endingBetween(@Param("partnerId") Long partnerId, @Param("revoked") PermitStatus revoked,
                       @Param("from") Instant from, @Param("to") Instant to);

    /** Shared by the queries' callers: the decision column as an enum (native rows give a String). */
    static Decision decision(Object value) {
        return value instanceof Decision d ? d : Decision.valueOf(value.toString());
    }
}
