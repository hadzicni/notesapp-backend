package ch.hadzic.nikola.notesapp.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Utility class for formatting dates.
 */
public class DateFormatUtil {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public static String format(LocalDateTime date) {
        return FORMATTER.format(date);
    }

    /**
     * Parses a date string produced by {@link #format(LocalDateTime)}.
     * Example: 31.12.2025 23:59
     *
     * @param value formatted date string
     * @return parsed {@link LocalDateTime}
     * @throws NullPointerException     if value is null
     * @throws DateTimeParseException   if the value cannot be parsed
     */
    public static LocalDateTime parse(String value) {
        return LocalDateTime.parse(value, FORMATTER);
    }
}
