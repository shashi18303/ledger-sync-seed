package in.simplifymoney.ledgersync.parse;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public final class Dates {
    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DD_MM_YY = DateTimeFormatter.ofPattern("dd-MM-yy");
    private static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("dd-MM-yyyy");

    private Dates() {}

    /**
     * Combines Indian banking date (dd-MM-yy) and time (HH:mm) strings into an IST ZonedDateTime.
     */
    public static ZonedDateTime parseIst(String datePart, String timePart, ZonedDateTime fallback) {
        try {
            LocalDate date;
            if (datePart.length() == 8) {
                date = LocalDate.parse(datePart, DD_MM_YY);
            } else {
                date = LocalDate.parse(datePart, DD_MM_YYYY);
            }
            LocalTime time = LocalTime.parse(timePart.trim());
            return ZonedDateTime.of(date, time, IST);
        } catch (Exception e) {
            return fallback != null ? fallback.withZoneSameInstant(IST) : ZonedDateTime.now(IST);
        }
    }
}
