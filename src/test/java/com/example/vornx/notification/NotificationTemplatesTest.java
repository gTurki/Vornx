package com.example.vornx.notification;

import com.example.vornx.notification.enums.NotificationType;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class NotificationTemplatesTest {

    @Test
    void fillsPlaceholdersInBothLanguages() {
        NotificationType.Rendered r = NotificationType.TRANSFER_RECEIVED.render(Map.of("from", "Fahad A.", "amount", "150.00"));
        assertEquals("Money received", r.titleEn());
        assertEquals("Fahad A. sent you 150.00 SAR.", r.bodyEn());
        assertEquals("أرسل لك Fahad A. مبلغ 150.00 ر.س.", r.bodyAr());
    }

    /** Catches typos like {evnet} in Arabic but {event} in English: both languages must use the same placeholders. */
    @Test
    void everyTypeUsesTheSamePlaceholdersInEnglishAndArabic() {
        Pattern p = Pattern.compile("\\{(\\w+)}");
        for (NotificationType type : NotificationType.values()) {
            Map<String, String> probe = new java.util.HashMap<>();
            NotificationType.Rendered raw = type.render(Map.of());
            for (String text : new String[]{raw.titleEn(), raw.bodyEn()}) {
                Matcher m = p.matcher(text);
                while (m.find()) {
                    probe.put(m.group(1), "X");
                }
            }
            NotificationType.Rendered filled = type.render(probe);
            for (String text : Arrays.asList(filled.titleEn(), filled.titleAr(), filled.bodyEn(), filled.bodyAr())) {
                assertFalse(p.matcher(text).find(), type + " has a placeholder missing in one language: " + text);
            }
        }
    }
}
