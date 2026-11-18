package com.example.vornx.demo;

import com.example.vornx.access.dto.AccessPointRequest;
import com.example.vornx.access.dto.PermitIssueRequest;
import com.example.vornx.access.dto.PermitResponse;
import com.example.vornx.access.dto.SiteRequest;
import com.example.vornx.access.entity.AccessDecision;
import com.example.vornx.access.entity.AccessPoint;
import com.example.vornx.access.entity.Site;
import com.example.vornx.access.repository.SiteRepository;
import com.example.vornx.access.entity.Permit;
import com.example.vornx.access.enums.AccessMethod;
import com.example.vornx.access.enums.AccessPointType;
import com.example.vornx.access.enums.Decision;
import com.example.vornx.access.enums.DenyReason;
import com.example.vornx.access.enums.Direction;
import com.example.vornx.access.enums.PermitType;
import com.example.vornx.access.enums.SiteType;
import com.example.vornx.access.repository.AccessDecisionRepository;
import com.example.vornx.access.repository.AccessPointRepository;
import com.example.vornx.access.repository.PermitRepository;
import com.example.vornx.access.service.AccessPointService;
import com.example.vornx.access.service.PermitService;
import com.example.vornx.access.service.SiteService;
import com.example.vornx.billing.dto.ContractRequest;
import com.example.vornx.billing.dto.ContractResponse;
import com.example.vornx.billing.entity.Contract;
import com.example.vornx.billing.entity.Invoice;
import com.example.vornx.billing.enums.PlanTier;
import com.example.vornx.billing.repository.ContractRepository;
import com.example.vornx.billing.repository.InvoiceRepository;
import com.example.vornx.billing.service.BillingService;
import com.example.vornx.common.enums.Language;
import com.example.vornx.common.util.RandomCodes;
import com.example.vornx.common.util.SecureTokens;
import com.example.vornx.customer.dto.CustomerRegisterRequest;
import com.example.vornx.customer.entity.Card;
import com.example.vornx.customer.entity.Customer;
import com.example.vornx.customer.repository.CardRepository;
import com.example.vornx.customer.service.CardService;
import com.example.vornx.customer.service.CustomerService;
import com.example.vornx.events.dto.EventRequest;
import com.example.vornx.events.dto.EventRequest.AgendaItemDto;
import com.example.vornx.events.entity.Event;
import com.example.vornx.events.enums.EventStatus;
import com.example.vornx.events.repository.EventRepository;
import com.example.vornx.events.service.EventService;
import com.example.vornx.events.service.TicketService;
import com.example.vornx.partner.dto.PartnerRequest;
import com.example.vornx.partner.entity.Partner;
import com.example.vornx.partner.entity.PartnerUser;
import com.example.vornx.partner.enums.PartnerRole;
import com.example.vornx.partner.enums.PartnerStatus;
import com.example.vornx.partner.enums.Sector;
import com.example.vornx.partner.repository.PartnerRepository;
import com.example.vornx.partner.repository.PartnerUserRepository;
import com.example.vornx.partner.service.PartnerService;
import com.example.vornx.platform.entity.AdminUser;
import com.example.vornx.platform.repository.AdminUserRepository;
import com.example.vornx.subscription.dto.PlanRequest;
import com.example.vornx.subscription.entity.SubscriptionPlan;
import com.example.vornx.subscription.repository.SubscriptionPlanRepository;
import com.example.vornx.subscription.enums.BillingPeriod;
import com.example.vornx.subscription.service.PlanService;
import com.example.vornx.subscription.service.SubscriptionService;
import com.example.vornx.support.dto.OpenTicketRequest;
import com.example.vornx.support.enums.TicketCategory;
import com.example.vornx.support.service.SupportService;
import com.example.vornx.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Demo data to PRESENT the user flow: 3 partners (a hotel group, a sports club, a conference center) with contracts,
 * invoices, sites, doors, staff, plans, events, and 3 customers with cards, keys, subscriptions, tickets, wallet
 * money and 7 days of door activity.
 *
 * Off by default. Turn on with DEMO_SEED=true, DEMO_EMAIL (YOUR inbox) and DEMO_PASSWORD (10+ characters):
 *  - every demo account's email is an alias of DEMO_EMAIL ("you+khalid@gmail.com"), so the customers' login codes
 *    arrive in your own inbox; all demo accounts log in with DEMO_PASSWORD;
 *  - runs ONCE: if the demo hotel (CR 7012345601) exists, it does nothing;
 *  - one transaction: if anything fails, nothing is saved;
 *  - invoices are created for the demo contracts ONLY (the monthly invoice job would bill real partners too).
 * YOUR OWN customer account (the one registered with DEMO_EMAIL itself, not an alias) also gets demo data, so you
 * can present the flow while logged in as yourself: a card (if you have none), hotel keys, a club membership, a
 * parking request waiting for approval, event tickets and some door activity. The demo partners are paid from your
 * wallet like any customer, so first a "رصيد تجريبي" top-up of exactly that amount is added: the balance ends where
 * it started. Done once per account (skipped when you already hold a demo hotel key). Not signed up yet with
 * DEMO_EMAIL? Sign up in the customer app, then restart the server (DEMO_SEED still true).
 * Afterwards remove DEMO_SEED (or set false).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vornx.demo.seed", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    static final String HOTEL_CR = "7012345601";

    private final DemoProperties props;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final ZoneId businessZone;
    private final PasswordEncoder passwordEncoder;
    private final AdminUserRepository admins;
    private final PartnerRepository partners;
    private final PartnerService partnerService;
    private final PartnerUserRepository partnerUsers;
    private final BillingService billing;
    private final ContractRepository contracts;
    private final InvoiceRepository invoices;
    private final SiteService sites;
    private final AccessPointService accessPoints;
    private final AccessPointRepository accessPointRepository;
    private final CustomerService customers;
    private final CardService cards;
    private final CardRepository cardRepository;
    private final WalletService wallets;
    private final PermitService permits;
    private final PermitRepository permitRepository;
    private final PlanService plans;
    private final SubscriptionService subscriptions;
    private final EventService events;
    private final TicketService tickets;
    private final SupportService support;
    private final AccessDecisionRepository decisions;
    private final SiteRepository siteRepository;
    private final SubscriptionPlanRepository planRepository;
    private final EventRepository eventRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(props.email()) || !props.email().contains("@")
                || props.password() == null || props.password().length() < 10) {
            log.warn("DEMO_SEED=true but DEMO_EMAIL / DEMO_PASSWORD (10+ characters) are missing: no demo data created");
            return;
        }
        if (partners.existsByCrNumber(HOTEL_CR)) {
            log.info("Demo partners already exist: not created again");
        } else {
            tx.executeWithoutResult(s -> seed());
            log.info("Demo data created. Accounts (password = DEMO_PASSWORD): partners {} / {} / {} / {}, customers {} / {} / {}",
                    alias("hotel"), alias("frontdesk"), alias("club"), alias("events"),
                    alias("khalid"), alias("noura"), alias("sarah"));
        }
        tx.executeWithoutResult(s -> seedOwnAccount());
    }

    // ================= your own account (DEMO_EMAIL itself) =================

    /** Demo keys, membership, tickets and door activity on the account registered with DEMO_EMAIL. Once per account. */
    void seedOwnAccount() {
        Optional<Customer> found = customers.findByEmail(props.email().trim().toLowerCase());
        if (found.isEmpty()) {
            log.info("No customer account with DEMO_EMAIL yet: sign up with it in the customer app, then restart to get demo data on it");
            return;
        }
        Customer me = found.get();
        Partner hotel = partners.findByCrNumber(HOTEL_CR).orElseThrow();
        Partner club = partners.findByCrNumber("7012345602").orElseThrow();
        Partner venue = partners.findByCrNumber("7012345603").orElseThrow();
        if (permitRepository.findByCustomer_IdOrderByValidUntilDesc(me.getId()).stream()
                .anyMatch(p -> p.getPartner().getId().equals(hotel.getId()))) {
            log.info("Your account already has the demo data");
            return;
        }
        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.ofInstant(now, businessZone);

        if (cardRepository.findByCustomer_IdOrderByIssuedAtDescIdDesc(me.getId()).isEmpty()) {
            cards.issue(me.getVornxId());
        }
        Card card = card(me);

        // what the demo partners will charge, added first so the wallet balance is unchanged at the end
        Optional<SubscriptionPlan> monthly = activePlan(club, "العضوية الشهرية");
        Optional<SubscriptionPlan> parking = activePlan(hotel, "موقف شهري — العليا");
        List<Event> upcoming = eventRepository.findByPartner_IdOrderByStartsAtDesc(venue.getId()).stream()
                .filter(e -> e.getStatus() == EventStatus.PUBLISHED && e.getStartsAt().isAfter(now)).toList();
        BigDecimal cost = monthly.map(SubscriptionPlan::getPrice).orElse(BigDecimal.ZERO)
                .add(parking.map(SubscriptionPlan::getPrice).orElse(BigDecimal.ZERO))
                .add(upcoming.stream().map(Event::getFee).reduce(BigDecimal.ZERO, BigDecimal::add));
        if (cost.signum() > 0) {
            wallets.creditTopUp(me.getId(), cost, "رصيد تجريبي للعرض");
        }

        // hotel keys: staying now in Riyadh, and an upcoming stay in Jeddah
        Map<String, Long> riyadh = doorsOf(hotel, "الواحة — العليا");
        Map<String, Long> jeddah = doorsOf(hotel, "الواحة — كورنيش جدة");
        Permit stay = permit(hotel, me, PermitType.HOTEL_KEY, siteOf(hotel, "الواحة — العليا"),
                Set.of(riyadh.get("R-1201"), riyadh.get("LOBBY"), riyadh.get("P1"), riyadh.get("GYM"), riyadh.get("POOL")),
                now.minus(Duration.ofDays(2)), now.plus(Duration.ofDays(3)), null, null);
        permit(hotel, me, PermitType.HOTEL_KEY, siteOf(hotel, "الواحة — كورنيش جدة"), Set.of(jeddah.get("R-0801"), jeddah.get("P1")),
                now.plus(Duration.ofDays(10)), now.plus(Duration.ofDays(13)), null, null);

        // club membership (active straight away) + a parking subscription the hotel still has to approve
        monthly.ifPresent(p -> subscriptions.subscribe(me.getId(), p.getId()));
        parking.ifPresent(p -> subscriptions.subscribe(me.getId(), p.getId()));

        // tickets for the events that haven't started yet
        upcoming.forEach(e -> tickets.register(me.getId(), e.getId()));

        // 3 days of door activity with your card
        for (int d = 2; d >= 0; d--) {
            LocalDate day = today.minusDays(d);
            entry(day, LocalTime.of(9, 10 + d), riyadh.get("P1"), me, card, stay, Decision.ALLOW, null, now);
            entry(day, LocalTime.of(9, 14 + d), riyadh.get("LOBBY"), me, card, stay, Decision.ALLOW, null, now);
            entry(day, LocalTime.of(9, 16 + d), riyadh.get("R-1201"), me, card, stay, Decision.ALLOW, null, now);
            entry(day, LocalTime.of(18, 30 + d), riyadh.get("GYM"), me, card, stay, Decision.ALLOW, null, now);
        }
        entry(today.minusDays(1), LocalTime.of(20, 5), riyadh.get("R-1205"), me, card, null, Decision.DENY, DenyReason.NO_PERMIT, now);
        log.info("Demo data added to your own account ({})", me.getVornxId());
    }

    private Optional<SubscriptionPlan> activePlan(Partner p, String name) {
        return planRepository.findByPartner_IdOrderByName(p.getId()).stream()
                .filter(pl -> pl.isActive() && pl.getName().equals(name)).findFirst();
    }

    private Long siteOf(Partner p, String name) {
        return siteRepository.findByPartner_IdOrderByName(p.getId()).stream()
                .filter(x -> x.getName().equals(name)).map(Site::getId).findFirst().orElseThrow();
    }

    /** door code -> id, for one of a partner's sites */
    private Map<String, Long> doorsOf(Partner p, String siteName) {
        return accessPointRepository.findBySite_IdOrderByCode(siteOf(p, siteName)).stream()
                .collect(Collectors.toMap(AccessPoint::getCode, AccessPoint::getId));
    }

    /** "you@gmail.com" + "khalid" -> "you+khalid@gmail.com" (delivered to your own inbox). */
    String alias(String tag) {
        String e = props.email().trim().toLowerCase();
        int at = e.indexOf('@');
        return e.substring(0, at) + "+" + tag + e.substring(at);
    }

    private void seed() {
        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.ofInstant(now, businessZone);
        Long managerId = admins.findAll().stream().min(Comparator.comparing(AdminUser::getId)).map(AdminUser::getId).orElse(null);

        // ---------------- partners + contracts + invoices + staff ----------------
        Partner hotel = partner("فنادق الواحة الذهبية", Sector.HOTEL, HOTEL_CR, "0112345601", "الرياض", managerId,
                "مجموعة فندقية في الرياض وجدة. المفتاح الرقمي للغرف والمواقف والنادي الصحي.");
        Partner club = partner("نادي نبض الرياضي", Sector.CLUB, "7012345602", "0112345602", "الرياض", managerId,
                "نادٍ رياضي بعضويات شهرية وربع سنوية، دخول بالبطاقة عبر البوابات.");
        Partner venue = partner("مركز أفق للمؤتمرات", Sector.EVENTS, "7012345603", "0112345603", "الرياض", managerId,
                "قاعات مؤتمرات ومعارض، تسجيل الحضور والدخول بالبطاقة.");

        contract(hotel, "باقة الضيافة", PlanTier.PROFESSIONAL, 12000, 5000, today.minusMonths(8), today.plusMonths(4), true);
        // ends in under 60 days: shows under "تحتاج متابعة", and last month's invoice is left unpaid (overdue)
        contract(club, "باقة الأندية", PlanTier.BASIC, 4500, 1500, today.minusMonths(11), today.plusDays(25), false);
        // a new partner since last month: the revenue chart rises ("▲ ...% عن الشهر الماضي")
        contract(venue, "باقة الفعاليات", PlanTier.PROFESSIONAL, 9000, 20000, today.minusDays(36), today.plusMonths(11), true);

        PartnerUser hotelAdmin = staff(hotel, "سارة القحطاني", "hotel", PartnerRole.PARTNER_ADMIN);
        staff(hotel, "محمد العتيبي", "frontdesk", PartnerRole.FRONT_DESK);
        staff(club, "عبدالله الحربي", "club", PartnerRole.PARTNER_ADMIN);
        staff(venue, "ريم الدوسري", "events", PartnerRole.PARTNER_ADMIN);

        // ---------------- sites + doors ----------------
        Long riyadh = site(hotel, "الواحة — العليا", SiteType.HOTEL, "طريق الملك فهد، العليا", "الرياض", 24.6907, 46.6853);
        Map<String, Long> r = doors(hotel, riyadh, List.of(
                door("R-1201", "الغرفة 1201", AccessPointType.ROOM, "12"), door("R-1202", "الغرفة 1202", AccessPointType.ROOM, "12"),
                door("R-1203", "الغرفة 1203", AccessPointType.ROOM, "12"), door("R-1204", "الغرفة 1204", AccessPointType.ROOM, "12"),
                door("R-1205", "الغرفة 1205", AccessPointType.ROOM, "12"), door("R-1206", "الغرفة 1206", AccessPointType.ROOM, "12"),
                door("GYM", "النادي الصحي", AccessPointType.DOOR, "2"), door("POOL", "المسبح", AccessPointType.DOOR, "2"),
                door("P1", "بوابة المواقف", AccessPointType.PARKING_BARRIER, null), door("LOBBY", "المدخل الرئيسي", AccessPointType.GATE, "G")));
        Long jeddah = site(hotel, "الواحة — كورنيش جدة", SiteType.HOTEL, "طريق الكورنيش، الشاطئ", "جدة", 21.5810, 39.1069);
        Map<String, Long> j = doors(hotel, jeddah, List.of(
                door("R-0801", "الغرفة 0801", AccessPointType.ROOM, "8"), door("R-0802", "الغرفة 0802", AccessPointType.ROOM, "8"),
                door("R-0803", "الغرفة 0803", AccessPointType.ROOM, "8"), door("P1", "بوابة المواقف", AccessPointType.PARKING_BARRIER, null)));
        Long hittin = site(club, "نبض — حطين", SiteType.CLUB, "طريق الأمير محمد بن سعد، حطين", "الرياض", 24.7636, 46.6011);
        Map<String, Long> c = doors(club, hittin, List.of(
                door("GATE-1", "البوابة الرئيسية", AccessPointType.TURNSTILE, "G"), door("POOL", "المسبح", AccessPointType.DOOR, "G"),
                door("STUDIO", "استوديو التمارين", AccessPointType.DOOR, "1")));
        Long hall = site(venue, "أفق — قاعة الأمير", SiteType.VENUE, "طريق الملك عبدالعزيز، الربيع", "الرياض", 24.7940, 46.6520);
        Map<String, Long> v = doors(venue, hall, List.of(
                door("MAIN", "البوابة الرئيسية", AccessPointType.GATE, "G"), door("HALL-A", "القاعة A", AccessPointType.DOOR, "1"),
                door("HALL-B", "القاعة B", AccessPointType.DOOR, "1")));

        // ---------------- customers: card, verified email + mobile, wallet ----------------
        Customer khalid = customer("خالد", "المطيري", "khalid", "0500000101", 1500, now);
        Customer noura = customer("نورة", "الشمري", "noura", "0500000102", 900, now);
        Customer sarah = customer("Sarah", "Collins", "sarah", "0500000103", 400, now);

        // ---------------- keys and memberships (permits) ----------------
        Permit khalidRoom = permit(hotel, khalid, PermitType.HOTEL_KEY, riyadh, Set.of(r.get("R-1204"), r.get("P1"), r.get("LOBBY")),
                now.minus(Duration.ofDays(6)), now.plus(Duration.ofDays(2)), null, null);
        Permit sarahRoom = permit(hotel, sarah, PermitType.HOTEL_KEY, riyadh, Set.of(r.get("R-1203"), r.get("GYM"), r.get("POOL"), r.get("LOBBY")),
                now.minus(Duration.ofHours(3)), now.plus(Duration.ofDays(3)), null, null);       // arrived today
        permit(hotel, noura, PermitType.HOTEL_KEY, jeddah, Set.of(j.get("R-0802"), j.get("P1")),
                now.plus(Duration.ofDays(1)), now.plus(Duration.ofDays(4)), null, null);          // arrives tomorrow
        Permit nouraClub = permit(club, noura, PermitType.CLUB, hittin, Set.of(),
                now.minus(Duration.ofDays(20)), now.plus(Duration.ofDays(10)), LocalTime.of(5, 30), LocalTime.of(23, 30));

        // ---------------- plans + subscriptions ----------------
        Long monthly = plan(club, hittin, "العضوية الشهرية", "دخول البوابة والمسبح والاستوديو طوال الشهر.", PermitType.CLUB,
                BillingPeriod.MONTHLY, 350, false, Set.of());
        Long quarterly = plan(club, hittin, "عضوية 3 أشهر", "أوفر: ثلاثة أشهر بسعر شهرين ونصف.", PermitType.CLUB,
                BillingPeriod.QUARTERLY, 900, true, Set.of());
        plan(hotel, riyadh, "موقف شهري — العليا", "موقف للمقيمين والموظفين، دخول بالبطاقة.", PermitType.PARKING,
                BillingPeriod.MONTHLY, 250, true, Set.of(r.get("P1")));
        subscriptions.subscribe(khalid.getId(), monthly);   // instant: active membership
        subscriptions.subscribe(noura.getId(), quarterly);  // needs approval: waits for the club

        // ---------------- events + tickets ----------------
        Long forum = event(venue, hall, "ملتقى الابتكار في الضيافة 2026", "مؤتمر", today.plusDays(7), LocalTime.of(16, 0), 4,
                BigDecimal.ZERO, 200, "رسمي", Set.of(v.get("MAIN"), v.get("HALL-A")),
                List.of(new AgendaItemDto(LocalTime.of(16, 0), "التسجيل والاستقبال"), new AgendaItemDto(LocalTime.of(16, 30), "الكلمة الافتتاحية"),
                        new AgendaItemDto(LocalTime.of(17, 30), "جلسة: المفتاح الرقمي وتجربة النزيل")));
        Long expo = event(venue, hall, "معرض التقنيات الذكية", "معرض", today.plusDays(14), LocalTime.of(10, 0), 8,
                new BigDecimal("150"), 500, null, Set.of(v.get("MAIN"), v.get("HALL-B")),
                List.of(new AgendaItemDto(LocalTime.of(10, 0), "افتتاح المعرض"), new AgendaItemDto(LocalTime.of(14, 0), "عروض حية")));
        tickets.register(khalid.getId(), forum);
        tickets.register(sarah.getId(), expo);

        // ---------------- a support ticket ----------------
        support.open(hotel.getId(), hotelAdmin.getId(), new OpenTicketRequest(TicketCategory.DIGITAL_KEY_ISSUE,
                "مفتاح الغرفة 1205 لا يستجيب للجوال", null,
                "أحد النزلاء أبلغ أن باب الغرفة 1205 لا يفتح بالجوال، ويفتح بالبطاقة. نرجو التحقق من القارئ."));

        // ---------------- 7 days of door activity (for the charts and the live feed) ----------------
        Card kc = card(khalid);
        Card nc = card(noura);
        Card sc = card(sarah);
        for (int d = 6; d >= 0; d--) {
            LocalDate day = today.minusDays(d);
            entry(day, LocalTime.of(8, 5 + d), r.get("P1"), khalid, kc, khalidRoom, Decision.ALLOW, null, now);
            entry(day, LocalTime.of(13, 20 + d), r.get("LOBBY"), khalid, kc, khalidRoom, Decision.ALLOW, null, now);
            entry(day, LocalTime.of(13, 22 + d), r.get("R-1204"), khalid, kc, khalidRoom, Decision.ALLOW, null, now);
            entry(day, LocalTime.of(22, 40 - d), r.get("R-1204"), khalid, kc, khalidRoom, Decision.ALLOW, null, now);
            entry(day, LocalTime.of(6, 10 + d), c.get("GATE-1"), noura, nc, nouraClub, Decision.ALLOW, null, now);
            if (d % 2 == 0) {
                entry(day, LocalTime.of(6, 50), c.get("POOL"), noura, nc, nouraClub, Decision.ALLOW, null, now);
            }
            if (d % 3 == 1) {
                entry(day, LocalTime.of(19, 15), c.get("STUDIO"), noura, nc, nouraClub, Decision.ALLOW, null, now);
            }
        }
        // denied examples: the gym is not in Khalid's key, and an unknown card at the lobby
        entry(today.minusDays(3), LocalTime.of(18, 45), r.get("GYM"), khalid, kc, null, Decision.DENY, DenyReason.NO_PERMIT, now);
        entry(today.minusDays(1), LocalTime.of(23, 55), r.get("LOBBY"), null, null, null, Decision.DENY, DenyReason.CARD_INVALID, now);
        // today: Sarah arrived and opened her room, the gym and the pool
        Instant arrived = now.minus(Duration.ofHours(2));
        decisions.save(decision(r.get("LOBBY"), sarah, sc, sarahRoom, Decision.ALLOW, null, arrived));
        decisions.save(decision(r.get("R-1203"), sarah, sc, sarahRoom, Decision.ALLOW, null, arrived.plusSeconds(300)));
        decisions.save(decision(r.get("GYM"), sarah, sc, sarahRoom, Decision.ALLOW, null, arrived.plusSeconds(3600)));
    }

    // ---------------- helpers ----------------

    private Partner partner(String name, Sector sector, String cr, String phone, String city, Long managerId, String notes) {
        Long id = partnerService.create(new PartnerRequest(name, sector, cr, phone, city, "SA", notes, managerId)).id();
        partnerService.changeStatus(id, PartnerStatus.ACTIVE);
        return partnerService.getEntity(id);
    }

    /** The contract, and its monthly invoices up to last month (at most 6: the chart); the last one unpaid when !allPaid. */
    private void contract(Partner p, String plan, PlanTier tier, int fee, int beneficiaries, LocalDate start, LocalDate end,
                          boolean allPaid) {
        ContractResponse cr = billing.createContract(p.getId(), new ContractRequest(plan, tier, BigDecimal.valueOf(fee),
                beneficiaries, start, end, null));
        Contract contract = contracts.findById(cr.id()).orElseThrow();
        YearMonth last = YearMonth.from(LocalDate.now(clock.withZone(businessZone))).minusMonths(1);
        YearMonth first = YearMonth.from(start).isAfter(last.minusMonths(5)) ? YearMonth.from(start) : last.minusMonths(5);
        for (YearMonth m = first; !m.isAfter(last); m = m.plusMonths(1)) {
            LocalDate issued = m.plusMonths(1).atDay(1);
            String number;
            do {
                number = "VX-" + m.getYear() + "-" + RandomCodes.digits(6);
            } while (invoices.existsByNumber(number));
            Invoice invoice = invoices.save(new Invoice(number, contract, m, issued, issued.plusDays(14)));
            if (allPaid || m.isBefore(last)) {
                invoice.markPaid(issued.plusDays(5).atTime(11, 0).atZone(businessZone).toInstant());
            }
        }
    }

    private PartnerUser staff(Partner p, String name, String tag, PartnerRole role) {
        PartnerUser u = PartnerUser.invite(p, name, alias(tag), role, SecureTokens.sha256(SecureTokens.generate()),
                Instant.now(clock).plus(Duration.ofDays(1)));
        u.acceptInvite(passwordEncoder.encode(props.password())); // no invite email: the account is ready
        return partnerUsers.save(u);
    }

    private Long site(Partner p, String name, SiteType type, String address, String city, double lat, double lng) {
        return sites.create(p.getId(), new SiteRequest(name, type, address, city, "السعودية", lat, lng, null, null)).id();
    }

    private record Door(String code, String name, AccessPointType type, String floor) {
    }

    private static Door door(String code, String name, AccessPointType type, String floor) {
        return new Door(code, name, type, floor);
    }

    private Map<String, Long> doors(Partner p, Long siteId, List<Door> list) {
        Map<String, Long> ids = new java.util.HashMap<>();
        for (Door d : list) {
            ids.put(d.code(), accessPoints.create(p.getId(), siteId, new AccessPointRequest(d.code(), d.name(), d.type(), d.floor(),
                    Set.of(AccessMethod.CARD, AccessMethod.MOBILE, AccessMethod.QR))).id());
        }
        return ids;
    }

    private Customer customer(String first, String last, String tag, String phone, int balance, Instant now) {
        String pw = props.password();
        Customer c = customers.register(new CustomerRegisterRequest(first, last, alias(tag), phone, pw, pw, Language.AR, true));
        c.markEmailVerified(now);
        c.markPhoneVerified(now);
        cards.issue(c.getVornxId());
        wallets.creditTopUp(c.getId(), BigDecimal.valueOf(balance), "رصيد تجريبي");
        return c;
    }

    private Permit permit(Partner p, Customer c, PermitType type, Long siteId, Set<Long> doors, Instant from, Instant until,
                          LocalTime allowedFrom, LocalTime allowedTo) {
        PermitResponse res = permits.issue(p.getId(), new PermitIssueRequest(c.getVornxId(), type, siteId, doors, null, from, until,
                Set.<DayOfWeek>of(), allowedFrom, allowedTo, null));
        return permitRepository.findById(res.id()).orElseThrow();
    }

    private Long plan(Partner p, Long siteId, String name, String description, PermitType type, BillingPeriod period,
                      int price, boolean approval, Set<Long> doors) {
        return plans.create(p.getId(), new PlanRequest(siteId, name, description, type, period, BigDecimal.valueOf(price),
                approval, doors, Set.of(), null, null, true)).id();
    }

    private Long event(Partner p, Long siteId, String title, String category, LocalDate day, LocalTime start, int hours,
                       BigDecimal fee, int capacity, String dress, Set<Long> doors, List<AgendaItemDto> agenda) {
        Instant startsAt = day.atTime(start).atZone(businessZone).toInstant();
        Long id = events.create(p.getId(), new EventRequest(siteId, title,
                "فعالية تجريبية لعرض رحلة VORNX: سجّل، وتصبح بطاقتك تذكرة الدخول.", category,
                startsAt, startsAt.plus(Duration.ofHours(hours)), startsAt.minus(Duration.ofMinutes(30)), fee, capacity, false,
                dress, "0112345603", doors, agenda)).id();
        events.publish(p.getId(), id);
        return id;
    }

    private Card card(Customer c) {
        return cardRepository.findByCustomer_IdOrderByIssuedAtDescIdDesc(c.getId()).getFirst();
    }

    /** One door event at a local time; skipped if that time is still in the future today. */
    private void entry(LocalDate day, LocalTime time, Long doorId, Customer c, Card card, Permit permit, Decision decision,
                       DenyReason reason, Instant now) {
        Instant at = day.atTime(time).atZone(businessZone).toInstant();
        if (at.isAfter(now)) {
            return;
        }
        decisions.save(decision(doorId, c, card, permit, decision, reason, at));
    }

    private AccessDecision decision(Long doorId, Customer c, Card card, Permit permit, Decision decision, DenyReason reason,
                                    Instant at) {
        AccessPoint door = accessPointRepository.findById(doorId).orElseThrow();
        return new AccessDecision("AD-" + RandomCodes.alphanumeric(10), door, c, card, permit, null, AccessMethod.CARD,
                Direction.ENTRY, decision, reason, at);
    }
}
