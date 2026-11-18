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

    /** "انضم كشريك": we received the application. */
    public EmailContent partnerApplicationReceived(String name, String company) {
        return new EmailContent(
                "وصلنا طلب انضمام " + company + " · We received your application",
                "وصلنا طلبك",
                "We received your application",
                List.of("مرحبًا " + name + "، شكرًا لاهتمام " + company + " بالانضمام إلى VORNX.",
                        "يراجع فريقنا الطلب عادةً خلال يومي عمل. عند الموافقة تصلك رسالة فيها رابط لتعيين كلمة المرور والدخول إلى بوابة الشريك."),
                List.of("Hello " + name + ", thank you for " + company + "'s interest in joining VORNX.",
                        "Our team usually reviews applications within two working days. Once approved, you'll get an email with a link to set your password and open the partner portal."),
                null, null, null,
                "لأي استفسار راسلنا على vornx@outlook.sa",
                "Questions? Write to vornx@outlook.sa");
    }

    /** The application was declined, with the reason. */
    public EmailContent partnerApplicationRejected(String name, String company, String reason) {
        return new EmailContent(
                "بخصوص طلب انضمام " + company + " · About your application",
                "بخصوص طلب الانضمام",
                "About your application",
                List.of("مرحبًا " + name + "، شكرًا لاهتمام " + company + " بـ VORNX.",
                        "بعد المراجعة، لا نستطيع قبول الطلب حاليًا. السبب: " + reason,
                        "يسعدنا استقبال طلب جديد متى ما تغيّر ذلك."),
                List.of("Hello " + name + ", thank you for " + company + "'s interest in VORNX.",
                        "After review, we can't accept the application at this time. Reason: " + reason,
                        "You're welcome to apply again once that changes."),
                null, null, null,
                "لأي استفسار راسلنا على vornx@outlook.sa",
                "Questions? Write to vornx@outlook.sa");
    }

    /** Sign-up: confirm the email address with a 6-digit code (typed in the app, so no button). */
    public EmailContent emailVerification(String name, String code, int minutes) {
        return new EmailContent(
                "تأكيد بريدك في VORNX · Confirm your email",
                "رمز التأكيد: " + code,
                "Your code: " + code,
                List.of("أهلًا " + name + "، شكرًا لتسجيلك في VORNX. اكتب هذا الرمز في التطبيق لتأكيد بريدك.",
                        "الرمز صالح لمدة " + minutes + " دقائق ويُستخدم مرة واحدة."),
                List.of("Hello " + name + ", thanks for signing up to VORNX. Type this code in the app to confirm your email.",
                        "The code works once and expires in " + minutes + " minutes."),
                null, null, null,
                "إذا لم تنشئ حسابًا في VORNX، تجاهل هذه الرسالة.",
                "If you didn't create a VORNX account, ignore this email.");
    }

    /** The authenticator app was turned on / off: tell the owner, in case it wasn't them. */
    public EmailContent totpChanged(String name, boolean enabled) {
        return new EmailContent(
                enabled ? "تم تفعيل تطبيق المصادقة · Authenticator app turned on"
                        : "تم إيقاف تطبيق المصادقة · Authenticator app turned off",
                enabled ? "تم تفعيل تطبيق المصادقة" : "تم إيقاف تطبيق المصادقة",
                enabled ? "Authenticator app turned on" : "Authenticator app turned off",
                List.of("مرحبًا " + name + "، " + (enabled
                        ? "من الآن يطلب تسجيل الدخول الرمز من تطبيق المصادقة على جوالك."
                        : "من الآن يصلك رمز الدخول بالبريد بدل تطبيق المصادقة.")),
                List.of("Hello " + name + ", " + (enabled
                        ? "from now on logging in asks for the code from the authenticator app on your phone."
                        : "from now on the login code comes by email instead of the authenticator app.")),
                frontend.customerUrl() + "/account", "حسابي", "My account",
                "إذا لم تقم بذلك، غيّر كلمة المرور فورًا وتواصل معنا.",
                "If this wasn't you, change your password now and contact us.");
    }

    public EmailContent recoveryCodeUsed(String name, long left) {
        return new EmailContent(
                "استُخدم رمز استرداد · A recovery code was used",
                "استُخدم رمز استرداد",
                "A recovery code was used",
                List.of("مرحبًا " + name + "، سُجّل الدخول إلى حسابك برمز استرداد بدل تطبيق المصادقة.",
                        "بقي لديك " + left + " من رموز الاسترداد. إذا غيّرت جوالك، أوقف التطبيق ثم فعّله من جديد على الجوال الجديد."),
                List.of("Hello " + name + ", someone logged in to your account with a recovery code instead of the authenticator app.",
                        "You have " + left + " recovery codes left. If you changed phones, turn the app off and on again on the new phone."),
                frontend.customerUrl() + "/account", "حسابي", "My account",
                "إذا لم تقم بذلك، غيّر كلمة المرور فورًا وتواصل معنا.",
                "If this wasn't you, change your password now and contact us.");
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
