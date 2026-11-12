package com.swiftdrop.backend.modules.pricing.strategy;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Working-day arithmetic for delivery estimates. Working days are Monday to Friday;
 * public holidays are not modelled yet.
 */
public final class DeliveryCalendar {

    /** All delivery dates are worked out in Sri Lanka time. */
    public static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Colombo");

    /** Estimated deliveries are promised by the end of the courier's working day. */
    public static final LocalTime END_OF_DELIVERY_DAY = LocalTime.of(18, 0);

    private DeliveryCalendar() {
    }

    public static boolean isWorkingDay(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    /** The date {@code workingDays} working days after {@code from} (weekends are skipped). */
    public static LocalDate addWorkingDays(LocalDate from, int workingDays) {
        LocalDate date = from;
        int remaining = workingDays;
        while (remaining > 0) {
            date = date.plusDays(1);
            if (isWorkingDay(date)) remaining--;
        }
        return date;
    }

    public static Instant endOfDeliveryDay(LocalDate date) {
        return date.atTime(END_OF_DELIVERY_DAY).atZone(BUSINESS_ZONE).toInstant();
    }
}
