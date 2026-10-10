package com.example.classapp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reading the header of a report text (teacher, class), finding the class by names, and duplicate detection. */
final class Reports {
    private Reports() {}

    private static final Pattern TEACHER_COLON = Pattern.compile("^([^\\s:：][^:：]{0,30}?)\\s*[:：]$");
    private static final Pattern LABELED = Pattern.compile("^\\S+\\s*[:：]\\s*(.+)$");
    private static final Pattern RESERVED = Pattern.compile("^(وضعیت|غایب|غیبت|تاخیر|کلاس|درس|تکلیف|یادداشت|با سلام|سلام|student|workbook)");

    /** Keeps only letters, digits and single spaces (input is already normalized). */
    static String bare(String n) {
        return n.replaceAll("[^\\p{L}\\p{N}\\s]+", " ").replaceAll("\\s+", " ").trim();
    }

    static Pattern token(String n) {
        return Pattern.compile("(^|[^\\p{L}\\p{N}])" + Pattern.quote(n) + "([^\\p{L}\\p{N}]|$)");
    }

    /** "Asal:" or "معلم : Asal" within the first three non-empty lines; "" when absent. */
    static String parseTeacher(String txt) {
        int seen = 0;
        for (String line : txt.split("\\r?\\n")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            if (++seen > 3) break;
            String n = Text.norm(t);
            if (n.startsWith("معلم")) {
                Matcher m = LABELED.matcher(t);
                if (m.find()) return m.group(1).trim();
                continue;
            }
            if (RESERVED.matcher(n).find()) continue;
            Matcher c = TEACHER_COLON.matcher(t);
            if (c.find()) return c.group(1).trim();
        }
        return "";
    }

    /** The text after «کلاس :» on its own line; "" when absent. */
    static String parseClass(String txt) {
        for (String line : txt.split("\\r?\\n")) {
            String t = line.trim();
            if (!Text.norm(t).startsWith("کلاس")) continue;
            Matcher m = LABELED.matcher(t);
            if (m.find()) return m.group(1).trim();
        }
        return "";
    }

    /** Exact class name when the text names an existing class (ignoring letter variants), else the text itself. */
    static String matchClass(Store st, String name) {
        String k = Text.norm(name);
        if (k.isEmpty()) return "";
        for (int i = 0; i < st.classes.length(); i++) {
            String cn = st.classes.optJSONObject(i).optString("name");
            if (Text.norm(cn).equals(k)) return cn;
        }
        return name.trim();
    }

    /**
     * The class whose members are named most often in the text (by first name); "" when nothing is named or two classes
     * tie. Used for reports pasted without a «کلاس» line.
     */
    static String inferClass(Store st, String txt) {
        String n = Text.norm(txt);
        String best = "";
        int bestCount = 0;
        boolean tie = false;
        for (int i = 0; i < st.classes.length(); i++) {
            JSONObject c = st.classes.optJSONObject(i);
            JSONArray m = c.optJSONArray("students");
            int count = 0;
            for (int j = 0; m != null && j < m.length(); j++) {
                JSONObject s = st.student(m.optString(j));
                if (s == null) continue;
                String f = Text.norm(s.optString("first"));
                if (!f.isEmpty() && token(f).matcher(n).find()) count++;
            }
            if (count > bestCount) {
                bestCount = count;
                best = c.optString("name");
                tie = false;
            } else if (count == bestCount && count > 0) {
                tie = true;
            }
        }
        return tie ? "" : best;
    }

    /** True when the text has the daily-report status heading «وضعیت امروز بچه‌ها» (or "وضعیت بچه‌ها"). */
    static boolean hasStatusHeader(String txt) {
        for (String line : txt.split("\\r?\\n")) {
            String b = bare(Text.norm(line));
            if (b.equals("وضعیت امروز بچه ها") || b.equals("وضعیت بچه ها") || b.equals("وضعیت امروز بچه‌ها")) return true;
        }
        return false;
    }

    /** Index of a saved report with the same day, class and teacher (class must be known); -1 when none. */
    static int findDuplicate(JSONArray reports, String date, String cls, String teacher, int except) {
        if (Text.norm(cls).isEmpty()) return -1;
        long d = JalaliDate.parseEpochDay(date);
        for (int i = 0; i < reports.length(); i++) {
            if (i == except) continue;
            JSONObject r = reports.optJSONObject(i);
            if (r == null || !Text.norm(r.optString("class")).equals(Text.norm(cls))) continue;
            if (!Text.norm(r.optString("teacher")).equals(Text.norm(teacher))) continue;
            long rd = JalaliDate.parseEpochDay(r.optString("date"));
            boolean sameDay = d != JalaliDate.INVALID && rd != JalaliDate.INVALID ? d == rd
                    : Text.norm(r.optString("date")).equals(Text.norm(date));
            if (sameDay) return i;
        }
        return -1;
    }
}
