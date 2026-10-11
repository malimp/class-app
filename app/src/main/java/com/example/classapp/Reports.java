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

    static final String SCHOOL = "Moradi English Class";

    /** Removes the teacher line («معلم : نام», or the old «نام:» first line) so the teacher is not sent with the report. */
    static String stripTeacher(String text) {
        StringBuilder out = new StringBuilder();
        int seen = 0;
        boolean kept = false;
        for (String line : text.split("\\r?\\n", -1)) {
            String t = line.trim(), n = Text.norm(t);
            boolean drop = false;
            if (!t.isEmpty()) {
                seen++;
                if (n.startsWith("معلم")) drop = LABELED.matcher(t).find();
                else if (seen <= 3 && !RESERVED.matcher(n).find() && TEACHER_COLON.matcher(t).find()) drop = true;
            }
            if (!drop) {
                if (out.length() > 0 || kept) out.append('\n');
                out.append(line);
                kept = true;
            }
        }
        return out.toString();
    }

    // The teacher travels inside the text as invisible characters (not shown in any messenger):
    // U+2063, then the UTF-8 bits of the name (0 = U+200B, 1 = U+2060), then U+2063.
    private static final Pattern HIDDEN = Pattern.compile("\u2063([\u200B\u2060]+)\u2063");

    static String hiddenTeacher(String text) {
        Matcher m = HIDDEN.matcher(text);
        if (!m.find()) return "";
        String bits = m.group(1);
        if (bits.length() % 8 != 0) return "";
        byte[] b = new byte[bits.length() / 8];
        for (int i = 0; i < bits.length(); i++) {
            if (bits.charAt(i) == '\u2060') b[i / 8] |= (byte) (1 << (7 - i % 8));
        }
        return new String(b, java.nio.charset.StandardCharsets.UTF_8).trim();
    }

    static String hide(String name) {
        StringBuilder s = new StringBuilder("\u2063");
        for (byte x : name.trim().getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            for (int i = 7; i >= 0; i--) s.append(((x >> i) & 1) == 1 ? '\u2060' : '\u200B');
        }
        return s.append('\u2063').toString();
    }

    /** What is sent or exported as text: school name first, teacher only as the invisible marker. */
    static String withSchool(String text) {
        return build(text, true);
    }

    /** The same without the invisible marker (pictures, previews). */
    static String plain(String text) {
        return build(text, false);
    }

    private static String build(String text, boolean hideTeacher) {
        String raw = text == null ? "" : text;
        String teacher = parseTeacher(raw);
        String t = HIDDEN.matcher(stripTeacher(raw)).replaceAll("");
        boolean has = false;
        for (String line : t.split("\\r?\\n")) {
            if (line.trim().isEmpty()) continue;
            has = Text.norm(line).equals(Text.norm(SCHOOL));
            break;
        }
        if (!has) t = SCHOOL + "\n" + t;
        if (hideTeacher && !teacher.isEmpty()) {
            String[] lines = t.split("\\n", -1);
            for (int i = 0; i < lines.length; i++) {
                if (!lines[i].trim().isEmpty()) {
                    lines[i] = lines[i] + hide(teacher);
                    break;
                }
            }
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < lines.length; i++) b.append(i > 0 ? "\n" : "").append(lines[i]);
            t = b.toString();
        }
        return t;
    }

    /** One short line for lists: the report without its header lines. */
    static String preview(String text) {
        StringBuilder b = new StringBuilder();
        for (String line : plain(text).split("\\r?\\n")) {
            String t = line.trim(), n = Text.norm(t);
            if (t.isEmpty() || n.equals(Text.norm(SCHOOL)) || n.startsWith("سلام") || n.startsWith("با سلام")
                    || n.startsWith("کلاس") || n.startsWith("وضعیت") || n.startsWith("درس") || n.startsWith("تکلیف")
                    || n.startsWith("student") || n.startsWith("workbook") || JalaliDate.findDate(t) != null) continue;
            if (b.length() > 0) b.append(" ⏎ ");
            b.append(t);
            if (b.length() > 70) break;
        }
        String r = b.toString();
        return r.length() > 70 ? r.substring(0, 70) + "…" : r;
    }

    static Pattern token(String n) {
        return Pattern.compile("(^|[^\\p{L}\\p{N}])" + Pattern.quote(n) + "([^\\p{L}\\p{N}]|$)");
    }

    /** "Asal:" or "معلم : Asal" within the first three non-empty lines; "" when absent. */
    static String parseTeacher(String txt) {
        String h = hiddenTeacher(txt);
        if (!h.isEmpty()) return h;
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
