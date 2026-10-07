package com.example.classapp;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Jalali (Persian) and Gregorian date helpers. "Epoch day" = days since 1970-01-01. */
final class JalaliDate {
    static final long INVALID = Long.MIN_VALUE;
    private static final Pattern DATE = Pattern.compile("(\\d{4})[/\\-.](\\d{1,2})[/\\-.](\\d{1,2})");

    private JalaliDate() {}

    static int[] toJalali(int gy, int gm, int gd) {
        int[] gdm = {0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334};
        int gy2 = (gm > 2) ? (gy + 1) : gy;
        int days = 355666 + (365 * gy) + ((gy2 + 3) / 4) - ((gy2 + 99) / 100) + ((gy2 + 399) / 400) + gd + gdm[gm - 1];
        int jy = -1595 + (33 * (days / 12053));
        days %= 12053;
        jy += 4 * (days / 1461);
        days %= 1461;
        if (days > 365) {
            jy += (days - 1) / 365;
            days = (days - 1) % 365;
        }
        int jm, jd;
        if (days < 186) {
            jm = 1 + (days / 31);
            jd = 1 + (days % 31);
        } else {
            jm = 7 + ((days - 186) / 30);
            jd = 1 + ((days - 186) % 30);
        }
        return new int[]{jy, jm, jd};
    }

    static int[] toGregorian(int jy, int jm, int jd) {
        jy += 1595;
        int days = -355668 + (365 * jy) + ((jy / 33) * 8) + (((jy % 33) + 3) / 4) + jd
                + ((jm < 7) ? (jm - 1) * 31 : ((jm - 7) * 30) + 186);
        int gy = 400 * (days / 146097);
        days %= 146097;
        if (days > 36524) {
            gy += 100 * (--days / 36524);
            days %= 36524;
            if (days >= 365) days++;
        }
        gy += 4 * (days / 1461);
        days %= 1461;
        if (days > 365) {
            gy += (days - 1) / 365;
            days = (days - 1) % 365;
        }
        int gd = days + 1;
        boolean leap = (gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0);
        int[] sal = {0, 31, leap ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        int gm;
        for (gm = 0; gm < 13 && gd > sal[gm]; gm++) gd -= sal[gm];
        return new int[]{gy, gm, gd};
    }

    static long epochDay(int gy, int gm, int gd) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.clear();
        c.set(gy, gm - 1, gd);
        return c.getTimeInMillis() / 86400000L;
    }

    static long today() {
        Calendar c = Calendar.getInstance();
        return epochDay(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    static long fromMillis(long millis) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(millis);
        return epochDay(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    static String todayJalali() {
        Calendar c = Calendar.getInstance();
        int[] j = toJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
        return fmt(j[0], j[1], j[2]);
    }

    static String fmt(int y, int m, int d) {
        return String.format(Locale.US, "%04d/%02d/%02d", y, m, d);
    }

    /** Finds the first yyyy/mm/dd date (Jalali or Gregorian, any digit script) in text; returns epoch day or INVALID. */
    static long parseEpochDay(String text) {
        int[] g = find(text);
        return g == null ? INVALID : epochDay(g[0], g[1], g[2]);
    }

    /** Returns the first date found in the first few non-empty lines, normalized as yyyy/MM/dd, or null. */
    static String findDate(String text) {
        if (text == null) return null;
        String[] lines = text.split("\\r?\\n");
        int seen = 0;
        for (String line : lines) {
            if (line.trim().isEmpty()) continue;
            Matcher m = DATE.matcher(Text.norm(line));
            if (m.find()) {
                int y = Integer.parseInt(m.group(1)), mo = Integer.parseInt(m.group(2)), d = Integer.parseInt(m.group(3));
                if (valid(y, mo, d)) return fmt(y, mo, d);
            }
            if (++seen >= 3) break;
        }
        return null;
    }

    /** Excel date serial (Gregorian 1900 system) to yyyy/MM/dd. */
    static String serialToDate(long serial) {
        long millis = (serial - 25569L) * 86400000L;
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(millis);
        return fmt(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    private static boolean valid(int y, int m, int d) {
        return m >= 1 && m <= 12 && d >= 1 && d <= 31 && ((y >= 1200 && y <= 1700) || (y >= 1800 && y <= 2300));
    }

    /** Returns {gy, gm, gd} of the first valid date in text, or null. */
    private static int[] find(String text) {
        if (text == null) return null;
        Matcher m = DATE.matcher(Text.norm(text));
        while (m.find()) {
            int y = Integer.parseInt(m.group(1)), mo = Integer.parseInt(m.group(2)), d = Integer.parseInt(m.group(3));
            if (!valid(y, mo, d)) continue;
            return y < 1700 ? toGregorian(y, mo, d) : new int[]{y, mo, d};
        }
        return null;
    }
}
