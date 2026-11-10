package com.example.vornx.notification.enums;

import java.util.Map;

/**
 * Every notification VORNX can send, with its text in English and Arabic.
 * {placeholders} are filled from the params given when sending.
 * Keeping ALL texts here (not spread across modules) makes them easy to review and translate.
 */
public enum NotificationType {

    // ---------- To customers ----------
    PERMIT_ISSUED(
            "Your access is ready", "تصريحك جاهز",
            "{partner} gave you access to {site}.", "منحتك {partner} صلاحية الدخول إلى {site}."),
    PERMIT_REVOKED(
            "Access cancelled", "أُلغي التصريح",
            "{partner} cancelled your access to {site}.", "ألغت {partner} صلاحية دخولك إلى {site}."),
    SUBSCRIPTION_APPROVED(
            "Subscription approved", "تمت الموافقة على اشتراكك",
            "{plan} at {partner} is active until {until}.", "اشتراك {plan} لدى {partner} فعّال حتى {until}."),
    SUBSCRIPTION_REJECTED(
            "Request rejected", "تم رفض طلبك",
            "{partner} rejected your request for {plan}: {reason}. {amount} SAR was refunded to your wallet.",
            "رفضت {partner} طلبك لاشتراك {plan}: {reason}. أُعيد {amount} ر.س إلى محفظتك."),
    TICKET_CONFIRMED(
            "Your ticket is confirmed", "تم تأكيد تذكرتك",
            "{event} starts {startsAt}. Your VORNX card opens the venue.",
            "تبدأ {event} في {startsAt}. بطاقة VORNX تفتح لك المكان."),
    TICKET_REJECTED(
            "Registration rejected", "تم رفض التسجيل",
            "Your registration for {event} was rejected: {reason}. Any fee was refunded.",
            "تم رفض تسجيلك في {event}: {reason}. أُعيدت الرسوم إن وُجدت."),
    EVENT_CANCELLED(
            "Event cancelled", "أُلغيت الفعالية",
            "{event} was cancelled. Any fee was refunded to your wallet.",
            "أُلغيت {event}. أُعيدت الرسوم إلى محفظتك إن وُجدت."),
    EVENT_REMINDER(
            "Reminder: {event}", "تذكير: {event}",
            "Starts {startsAt} at {venue}.", "تبدأ في {startsAt} في {venue}."),
    TRANSFER_RECEIVED(
            "Money received", "وصلتك حوالة",
            "{from} sent you {amount} SAR.", "أرسل لك {from} مبلغ {amount} ر.س."),
    WALLET_TOPPED_UP(
            "Wallet topped up", "تم شحن محفظتك",
            "{amount} SAR was added to your wallet.", "أُضيف {amount} ر.س إلى محفظتك."),

    // ---------- To a partner's team ----------
    NEW_SUBSCRIPTION_REQUEST(
            "New subscription request", "طلب اشتراك جديد",
            "{customer} asked for {plan} ({requestType}).", "طلب {customer} اشتراك {plan} ({requestType})."),
    NEW_EVENT_REGISTRATION(
            "Registration to review", "تسجيل بانتظار المراجعة",
            "{customer} registered for {event}.", "سجّل {customer} في {event}."),
    SUPPORT_REPLY(
            "VORNX replied", "رد فريق VORNX",
            "New reply on {ticket}: {subject}", "رد جديد على {ticket}: {subject}"),
    INVOICE_ISSUED(
            "New invoice", "فاتورة جديدة",
            "Invoice {number} for {period}: {amount} SAR, due {dueDate}.",
            "فاتورة {number} عن {period}: {amount} ر.س، تستحق في {dueDate}."),

    // ---------- To the VORNX team ----------
    NEW_SUPPORT_TICKET(
            "New support ticket", "طلب دعم جديد",
            "{partner}: {subject} ({priority})", "{partner}: {subject} ({priority})"),
    SUPPORT_PARTNER_REPLY(
            "Partner replied", "رد من الجهة",
            "{partner} replied on {ticket}: {subject}", "ردّت {partner} على {ticket}: {subject}");

    private final String titleEn;
    private final String titleAr;
    private final String bodyEn;
    private final String bodyAr;

    NotificationType(String titleEn, String titleAr, String bodyEn, String bodyAr) {
        this.titleEn = titleEn;
        this.titleAr = titleAr;
        this.bodyEn = bodyEn;
        this.bodyAr = bodyAr;
    }

    public record Rendered(String titleEn, String titleAr, String bodyEn, String bodyAr) {
    }

    public Rendered render(Map<String, String> params) {
        return new Rendered(fill(titleEn, params), fill(titleAr, params), fill(bodyEn, params), fill(bodyAr, params));
    }

    private static String fill(String template, Map<String, String> params) {
        String out = template;
        for (Map.Entry<String, String> e : params.entrySet()) {
            out = out.replace("{" + e.getKey() + "}", e.getValue() != null ? e.getValue() : "");
        }
        return out;
    }
}
