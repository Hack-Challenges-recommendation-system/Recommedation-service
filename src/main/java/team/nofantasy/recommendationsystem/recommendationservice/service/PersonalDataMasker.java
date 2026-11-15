package team.nofantasy.recommendationsystem.recommendationservice.service;

import java.util.regex.Pattern;

/**
 * Hides contact details that users sometimes type into free-text answers, before the text leaves the system.
 */
final class PersonalDataMasker {
    private static final Pattern EMAIL = Pattern.compile("[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}-]+(?:\\.[\\p{L}\\p{N}-]+)+");
    /** Phone numbers such as +7 (999) 123-45-67 or 89991234567; ordinary amounts like 10 000 000 don't match. */
    private static final Pattern PHONE = Pattern.compile("\\+?\\d{1,3}[\\s(-]*\\d{3}[\\s)-]*\\d{3}[\\s-]*\\d{2}[\\s-]*\\d{2}");

    private PersonalDataMasker() {
    }

    static String mask(String text) {
        if (text == null) {
            return null;
        }

        String withoutEmails = EMAIL.matcher(text).replaceAll("[email]");
        return PHONE.matcher(withoutEmails).replaceAll("[phone]");
    }
}
