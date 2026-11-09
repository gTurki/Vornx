package com.example.vornx.email;

import java.util.List;

/**
 * WHAT an email says, in both languages. HOW it looks is the template (templates/email/base.html).
 * buttonUrl / note are optional (null = not shown).
 */
public record EmailContent(
        String subject,
        String titleAr,
        String titleEn,
        List<String> linesAr,
        List<String> linesEn,
        String buttonUrl,
        String buttonAr,
        String buttonEn,
        String noteAr,
        String noteEn
) {
    /** The plain-text version, sent alongside the HTML (some email apps show only text; spam filters like both). */
    public String toPlainText() {
        StringBuilder sb = new StringBuilder();
        sb.append(titleAr).append("\n\n");
        linesAr.forEach(l -> sb.append(l).append("\n"));
        if (buttonUrl != null) {
            sb.append("\n").append(buttonAr).append(": ").append(buttonUrl).append("\n");
        }
        if (noteAr != null) {
            sb.append("\n").append(noteAr).append("\n");
        }
        sb.append("\n----------------------------------------\n\n");
        sb.append(titleEn).append("\n\n");
        linesEn.forEach(l -> sb.append(l).append("\n"));
        if (buttonUrl != null) {
            sb.append("\n").append(buttonEn).append(": ").append(buttonUrl).append("\n");
        }
        if (noteEn != null) {
            sb.append("\n").append(noteEn).append("\n");
        }
        sb.append("\n— VORNX");
        return sb.toString();
    }
}
