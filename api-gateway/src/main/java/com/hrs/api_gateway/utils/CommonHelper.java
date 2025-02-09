package com.hrs.api_gateway.utils;

import com.hrs.api_gateway.model.BookingDTO;
import com.hrs.api_gateway.model.HotelDTO;
import jakarta.ws.rs.BadRequestException;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class CommonHelper {

    private static final Safelist SAFE_LIST = Safelist.basic();

    public static boolean isValidBookingInput(BookingDTO bookingDTO) {
        return isSafeHtml(bookingDTO.getCheckinDate().toString()) &&
                isSafeHtml(bookingDTO.getCheckoutDate().toString());
    }

    public static boolean isValidHotelInput(HotelDTO hotelDTO) {
        return isSafeHtml(hotelDTO.getName()) &&
                isSafeHtml(hotelDTO.getCity()) &&
                isSafeHtml(hotelDTO.getAddress());
    }

    private static boolean isSafeHtml(String html) {
        if (html == null) return true;
        String cleanedHtml = Jsoup.clean(html, SAFE_LIST);
        return cleanedHtml.equals(html);
    }

    public static LocalDate parseLocalDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        try {
            return LocalDate.parse(dateStr);
        } catch (Exception e) {
            throw new BadRequestException("Invalid date format. Date should be in YYYY-MM-DD format.");
        }
    }

    public static LocalDateTime parseLocalDateTime(String dateTimeString) {
        if (dateTimeString == null || dateTimeString.trim().isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(dateTimeString, DateTimeFormatter.ISO_DATE_TIME); // Assuming ISO date-time format in ES
    }
}