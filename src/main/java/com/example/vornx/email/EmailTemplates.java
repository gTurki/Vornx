package com.example.vornx.email;

import com.example.vornx.security.UserType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/** The text of every VORNX email, in Arabic and English. One method per email. */
@Component
@RequiredArgsConstructor
public class EmailTemplates {

    private final FrontendProperties frontend;

    public EmailContent partnerInvite(String fullName, String partnerName, String role, String rawToken) {
        String url = frontend.partnerUrl() + "/accept-invite?token=" + encode(rawToken);
        return new EmailContent(
                "دعوة للانضمام إلى " + partnerName + " على VORNX · You're invited to " + partnerName,
                "مرحبًا " + fullName + "، تمت دعوتك",
                "Hello " + fullName + ", you're invited",
                List.of("تمت دعوتك للانضمام إلى فريق " + partnerName + " في بوابة شركاء VORNX بصلاحية " + role + ".",
                        "اضغط الزر لاختيار كلمة المرور وتفعيل حسابك. الرابط صالح لمدة 7 أيام ويُستخدم مرة واحدة."),
                List.of("You've been invited to join the " + partnerName + " team on the VORNX partner portal as " + role + ".",
                        "Click the button to choose your password and activate your account. The link works once and expires in 7 days."),
                url, "تفعيل الحساب", "Activate account",
                "إذا لم تكن تتوقع هذه الدعوة، يمكنك تجاهل هذه الرسالة.",
                "If you weren't expecting this invitation, you can ignore this email.");
    }

    public EmailContent customerWelcome(String firstName, String vornxId) {
        return new EmailContent(
                "أهلًا بك في VORNX · Welcome to VORNX",
                "أهلًا يا " + firstName + "!",
                "Welcome, " + firstName + "!",
                List.of("تم إنشاء حسابك في VORNX بنجاح. معرّفك هو " + vornxId + ".",
                        "من مكان واحد: بطاقتك، محفظتك، اشتراكاتك، تصاريحك وفعالياتك."),
                List.of("Your VORNX account is ready. Your VORNX ID is " + vornxId + ".",
                        "One place for your card, wallet, subscriptions, permits and events."),
                frontend.customerUrl(), "افتح بوابتي", "Open my portal",
                null, null);
    }

    public EmailContent passwordReset(String name, UserType type, String rawToken, int minutes) {
        String url = portalUrl(type) + "/reset-password?token=" + encode(rawToken);
        return new EmailContent(
                "إعادة تعيين كلمة المرور · Reset your password",
                "إعادة تعيين كلمة المرور",
                "Reset your password",
                List.of("مرحبًا " + name + "، وصلنا طلب لإعادة تعيين كلمة المرور لحسابك.",
                        "الرابط صالح لمدة " + minutes + " دقيقة ويُستخدم مرة واحدة."),
                List.of("Hello " + name + ", we received a request to reset your password.",
                        "This link works once and expires in " + minutes + " minutes."),
                url, "تعيين كلمة مرور جديدة", "Set a new password",
                "إذا لم تطلب ذلك، تجاهل هذه الرسالة: كلمة المرور الحالية لن تتغير.",
                "If you didn't ask for this, ignore this email: your current password will not change.");
    }

    /** The 6-digit code of the two-step login. No button: the code is typed in the app that asked for it. */
    public EmailContent loginCode(String name, String code, int minutes) {
        return new EmailContent(
                "رمز الدخول إلى VORNX · Your VORNX login code",
                "رمز الدخول: " + code,
                "Your code: " + code,
                List.of("مرحبًا " + name + "، استخدم هذا الرمز لإكمال تسجيل الدخول.",
                        "الرمز صالح لمدة " + minutes + " دقائق ويُستخدم مرة واحدة."),
                List.of("Hello " + name + ", use this code to finish logging in.",
                        "The code works once and expires in " + minutes + " minutes."),
                null, null, null,
                "لا تشارك هذا الرمز مع أي أحد، ولا حتى موظفي VORNX. إذا لم تحاول الدخول فغيّر كلمة المرور فورًا.",
                "Never share this code with anyone, not even VORNX staff. If you didn't try to log in, change your password now.");
    }

    public EmailContent passwordChanged(String name, UserType type) {
        return new EmailContent(
                "تم تغيير كلمة المرور · Your password was changed",
                "تم تغيير كلمة المرور",
                "Your password was changed",
                List.of("مرحبًا " + name + "، تم تغيير كلمة المرور لحسابك في VORNX للتو.",
                        "لحمايتك، تم تسجيل الخروج من أجهزتك الأخرى."),
                List.of("Hello " + name + ", the password of your VORNX account was just changed.",
                        "To protect you, your other devices were logged out."),
                portalUrl(type), "تسجيل الدخول", "Log in",
                "لم تقم بذلك؟ استخدم «نسيت كلمة المرور» فورًا وتواصل معنا على vornx@outlook.sa",
                "Wasn't you? Use \"Forgot password\" right away and contact us at vornx@outlook.sa");
    }

    public EmailContent invoiceIssued(String partnerName, String number, String period, BigDecimal amount,
                                      LocalDate dueDate) {
        String money = amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
        return new EmailContent(
                "فاتورة جديدة " + number + " · New invoice " + number,
                "فاتورة جديدة",
                "New invoice",
                List.of("صدرت فاتورة جديدة لـ " + partnerName + ".",
                        "رقم الفاتورة: " + number + " · الفترة: " + period,
                        "المبلغ: " + money + " ر.س · تاريخ الاستحقاق: " + dueDate),
                List.of("A new invoice was issued for " + partnerName + ".",
                        "Invoice: " + number + " · Period: " + period,
                        "Amount: " + money + " SAR · Due: " + dueDate),
                frontend.partnerUrl() + "/billing", "عرض الفواتير", "View invoices",
                null, null);
    }

    private String portalUrl(UserType type) {
        return switch (type) {
            case ADMIN -> frontend.adminUrl();
            case PARTNER_USER -> frontend.partnerUrl();
            case CUSTOMER -> frontend.customerUrl();
        };
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
