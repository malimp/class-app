package com.example.classapp;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Educational calendar logic (Jalali only): month layout, official holidays, class sessions generated from the class
 * days/start/end, events (exam, registration, ...), lesson plans. Pure logic: no Android classes.
 * Weekday numbers: Saturday = 0 ... Friday = 6.
 */
final class Cal {
    private Cal() {}

    static final String EXAM = "exam", EVENT = "event", REG = "register", TERM_START = "termstart", TERM_END = "termend",
            MAKEUP = "makeup", OFF = "off", HOLIDAY = "holiday", HIDE_HOLIDAY = "hidehol", LESSON = "lesson";

    /** Types the manager can add (internal types are not listed). */
    static final String[] ADD_TYPES = {EXAM, EVENT, REG, TERM_START, TERM_END, MAKEUP, OFF, HOLIDAY};

    static final String[] MONTHS = {"فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور", "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"};
    static final String[] DAYS = {"شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنج‌شنبه", "جمعه"};
    static final int NO_TIME = 99999, MAX_SPAN = 800;

    static String typeLabel(String t) {
        switch (t) {
            case EXAM: return "آزمون";
            case EVENT: return "رویداد";
            case REG: return "ثبت‌نام";
            case TERM_START: return "شروع ترم";
            case TERM_END: return "پایان ترم";
            case MAKEUP: return "جلسه جبرانی";
            case OFF: return "لغو جلسه";
            case HOLIDAY: return "تعطیلی";
            case LESSON: return "برنامه درسی";
            default: return t;
        }
    }

    static String typeIcon(String t) {
        switch (t) {
            case EXAM: return "📝";
            case EVENT: return "🎉";
            case REG: return "🧾";
            case TERM_START: return "🚩";
            case TERM_END: return "🏁";
            case MAKEUP: return "🔁";
            case OFF: return "⛔";
            case HOLIDAY: return "🔴";
            default: return "•";
        }
    }

    // ---------- dates ----------

    static int[] jal(long e) {
        Calendar c = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(e * 86400000L);
        return JalaliDate.toJalali(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH));
    }

    static long epoch(int jy, int jm, int jd) {
        int[] g = JalaliDate.toGregorian(jy, jm, jd);
        return JalaliDate.epochDay(g[0], g[1], g[2]);
    }

    static int monthLen(int jy, int jm) {
        if (jm <= 6) return 31;
        if (jm <= 11) return 30;
        int[] g = JalaliDate.toGregorian(jy, 12, 30);
        int[] back = JalaliDate.toJalali(g[0], g[1], g[2]);
        return back[0] == jy && back[1] == 12 && back[2] == 30 ? 30 : 29;
    }

    /** Saturday = 0 ... Friday = 6. */
    static int wd(long e) {
        return (int) (((e + 5) % 7 + 7) % 7);
    }

    static String fmt(long e) {
        int[] j = jal(e);
        return JalaliDate.fmt(j[0], j[1], j[2]);
    }

    static long parse(String s) {
        return JalaliDate.parseEpochDay(s);
    }

    // ---------- Hijri (tabular approximation; the official Iranian calendar may differ by a day or two) ----------

    static int[] hijri(long e) {
        int jd = (int) (e + 2440588L);
        int l = jd - 1948440 + 10632;
        int n = (l - 1) / 10631;
        l = l - 10631 * n + 354;
        int j = ((10985 - l) / 5316) * ((50 * l) / 17719) + (l / 5670) * ((43 * l) / 15238);
        l = l - ((30 - j) / 15) * ((17719 * j) / 50) - (j / 16) * ((15238 * j) / 43) + 29;
        int m = (24 * l) / 709;
        int d = l - (709 * m) / 24;
        int y = 30 * n + j - 30;
        return new int[]{y, m, d};
    }

    /** Name of an official holiday (built-in list), or null. Lunar holidays are marked approximate. */
    static String builtinHoliday(long e) {
        int[] j = jal(e);
        int k = j[1] * 100 + j[2];
        switch (k) {
            case 101: return "نوروز (۱ فروردین)";
            case 102: return "نوروز (۲ فروردین)";
            case 103: return "نوروز (۳ فروردین)";
            case 104: return "نوروز (۴ فروردین)";
            case 112: return "روز جمهوری اسلامی";
            case 113: return "روز طبیعت (سیزده‌به‌در)";
            case 314: return "رحلت امام خمینی";
            case 315: return "قیام ۱۵ خرداد";
            case 1122: return "پیروزی انقلاب اسلامی";
            case 1229: return "ملی شدن صنعت نفت";
            default: break;
        }
        int[] h = hijri(e);
        int hk = h[1] * 100 + h[2];
        String name = null;
        switch (hk) {
            case 109: name = "تاسوعای حسینی"; break;
            case 110: name = "عاشورای حسینی"; break;
            case 220: name = "اربعین حسینی"; break;
            case 228: name = "رحلت پیامبر و شهادت امام حسن"; break;
            case 317: name = "میلاد پیامبر و امام صادق"; break;
            case 603: name = "شهادت حضرت فاطمه"; break;
            case 713: name = "ولادت امام علی"; break;
            case 727: name = "مبعث پیامبر"; break;
            case 815: name = "ولادت امام زمان"; break;
            case 921: name = "شهادت امام علی"; break;
            case 1001: name = "عید سعید فطر"; break;
            case 1002: name = "تعطیل به مناسبت عید فطر"; break;
            case 1025: name = "شهادت امام صادق"; break;
            case 1210: name = "عید سعید قربان"; break;
            case 1218: name = "عید سعید غدیر"; break;
            default: break;
        }
        if (name == null && h[1] == 2 && h[2] >= 29 && hijri(e + 1)[1] == 3) name = "شهادت امام رضا";
        return name == null ? null : name + " (تقریبی)";
    }

    // ---------- events ----------

    static String lessonId(String cls, String date) {
        return "L|" + Text.norm(cls) + "|" + date;
    }

    static boolean forClass(JSONObject ev, String cls) {
        String c = ev.optString("cls");
        return c.isEmpty() || cls == null || cls.isEmpty() || Text.norm(c).equals(Text.norm(cls));
    }

    static List<JSONObject> eventsOn(JSONArray events, long e) {
        List<JSONObject> out = new ArrayList<>();
        for (int i = 0; events != null && i < events.length(); i++) {
            JSONObject o = events.optJSONObject(i);
            if (o != null && parse(o.optString("date")) == e) out.add(o);
        }
        return out;
    }

    /** The holiday of this day: a manager-defined one wins; a built-in one can be hidden. Null when it is a normal day. */
    static String holiday(JSONArray events, long e) {
        boolean hidden = false;
        for (JSONObject o : eventsOn(events, e)) {
            String t = o.optString("type");
            if (HOLIDAY.equals(t)) {
                String title = o.optString("title").trim();
                return title.isEmpty() ? "تعطیل" : title;
            }
            if (HIDE_HOLIDAY.equals(t)) hidden = true;
        }
        return hidden ? null : builtinHoliday(e);
    }

    static JSONObject lesson(JSONArray events, String cls, String date) {
        String id = lessonId(cls, date);
        for (int i = 0; events != null && i < events.length(); i++) {
            JSONObject o = events.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) return o;
        }
        return null;
    }

    // ---------- class schedule ----------

    /** Weekdays (Saturday = 0) written in the class «days» field, e.g. «شنبه و دوشنبه», «یکشنبه/سه‌شنبه». */
    static boolean[] parseDays(String s) {
        boolean[] r = new boolean[7];
        String n = Text.norm(s).replace(" ", "");
        String[] comp = {"یکشنبه", "دوشنبه", "سهشنبه", "چهارشنبه", "پنجشنبه"};
        for (int i = 0; i < comp.length; i++) {
            if (n.contains(comp[i])) {
                r[i + 1] = true;
                n = n.replace(comp[i], "");
            }
        }
        if (n.contains("شنبه")) r[0] = true;
        if (n.contains("جمعه")) r[6] = true;
        return r;
    }

    private static final Pattern HM = Pattern.compile("(\\d{1,2})[:.٫](\\d{2})");
    private static final Pattern H_ONLY = Pattern.compile("(?<!\\d)(\\d{1,2})(?!\\d)");

    /** Start of the class hour in minutes from midnight (for ordering); NO_TIME when unreadable. */
    static int startMin(String time) {
        String n = Text.norm(time);
        Matcher m = HM.matcher(n);
        if (m.find()) {
            int h = Integer.parseInt(m.group(1)), mi = Integer.parseInt(m.group(2));
            if (h < 24 && mi < 60) return h * 60 + mi;
        }
        Matcher m2 = H_ONLY.matcher(n);
        if (m2.find()) {
            int h = Integer.parseInt(m2.group(1));
            if (h < 24) return h * 60;
        }
        return NO_TIME;
    }

    static boolean classDay(JSONObject c, long e) {
        long s = parse(c.optString("start"));
        long en = parse(c.optString("end"));
        if (s != JalaliDate.INVALID && e < s) return false;
        if (en != JalaliDate.INVALID && e > en) return false;
        return parseDays(c.optString("days"))[wd(e)];
    }

    /** Reason this class does not meet on a regular class day (holiday / cancelled), or null. */
    static String cancelReason(JSONObject c, JSONArray events, long e) {
        String h = holiday(events, e);
        if (h != null) return "تعطیل: " + h;
        for (JSONObject o : eventsOn(events, e)) {
            if (OFF.equals(o.optString("type")) && forClass(o, c.optString("name"))) {
                String t = o.optString("title").trim();
                return "لغو" + (t.isEmpty() ? "" : ": " + t);
            }
        }
        return null;
    }

    /** The manager-made event (custom holiday or cancel) responsible for the cancellation, or null (built-in holiday). */
    static JSONObject cancelEvent(JSONObject c, JSONArray events, long e) {
        for (JSONObject o : eventsOn(events, e)) {
            if (HOLIDAY.equals(o.optString("type"))) return o;
        }
        for (JSONObject o : eventsOn(events, e)) {
            if (OFF.equals(o.optString("type")) && forClass(o, c.optString("name"))) return o;
        }
        return null;
    }

    static boolean makeupOn(JSONObject c, JSONArray events, long e) {
        for (JSONObject o : eventsOn(events, e)) {
            if (MAKEUP.equals(o.optString("type")) && !o.optString("cls").isEmpty()
                    && Text.norm(o.optString("cls")).equals(Text.norm(c.optString("name")))) return true;
        }
        return false;
    }

    /** Does this class actually hold a session on day e (regular and not cancelled, or a makeup)? */
    static boolean held(JSONObject c, JSONArray events, long e) {
        return (classDay(c, e) && cancelReason(c, events, e) == null) || makeupOn(c, events, e);
    }

    /** Session number of day e for this class (1-based, counted from the class start), 0 when unknown. */
    static int number(JSONObject c, JSONArray events, long e) {
        long s = parse(c.optString("start"));
        if (s == JalaliDate.INVALID) return 0;
        if (e - s > MAX_SPAN) s = e - MAX_SPAN; // numbering window cap (very long classes)
        int n = 0;
        for (long d = s; d <= e; d++) if (held(c, events, d)) n++;
        return n;
    }

    // ---------- day view ----------

    static final class Item {
        String kind; // session | cancelled | makeup | event
        String cls = "", time = "", title = "", note = "", type = "", reason = "";
        int num, sort = NO_TIME;
        JSONObject ev;
    }

    static final class Day {
        long day;
        String holiday; // null on normal days
        List<Item> items = new ArrayList<>();
    }

    /** All classes' sessions of one day (each with its own hour, ordered by time) plus events. clsFilter "" = every class. */
    static Day day(JSONArray classes, JSONArray events, long e, String clsFilter) {
        return day(classes, events, e, clsFilter, true);
    }

    static Day day(JSONArray classes, JSONArray events, long e, String clsFilter, boolean withNumbers) {
        Day d = new Day();
        java.util.Set<String> used = new java.util.HashSet<>();
        d.day = e;
        d.holiday = holiday(events, e);
        for (int i = 0; classes != null && i < classes.length(); i++) {
            JSONObject c = classes.optJSONObject(i);
            if (c == null) continue;
            String name = c.optString("name");
            if (!clsFilter.isEmpty() && !Text.norm(name).equals(Text.norm(clsFilter))) continue;
            Item it = null;
            if (classDay(c, e)) {
                String why = cancelReason(c, events, e);
                it = new Item();
                it.kind = why == null ? "session" : "cancelled";
                it.reason = why == null ? "" : why;
                if (why != null) {
                    it.ev = cancelEvent(c, events, e);
                    if (it.ev != null) used.add(it.ev.optString("id"));
                }
            }
            if (makeupOn(c, events, e) && (it == null || "cancelled".equals(it.kind))) {
                it = new Item();
                it.kind = "makeup";
            }
            if (it == null) continue;
            it.cls = name;
            it.time = c.optString("time");
            it.sort = startMin(it.time);
            if (!"cancelled".equals(it.kind) && withNumbers) it.num = number(c, events, e);
            if ("makeup".equals(it.kind)) {
                for (JSONObject o : eventsOn(events, e)) {
                    if (MAKEUP.equals(o.optString("type")) && Text.norm(o.optString("cls")).equals(Text.norm(name))) it.ev = o;
                }
            }
            d.items.add(it);
        }
        Collections.sort(d.items, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                if (a.sort != b.sort) return a.sort < b.sort ? -1 : 1;
                return Text.norm(a.cls).compareTo(Text.norm(b.cls));
            }
        });
        List<Item> evs = new ArrayList<>();
        for (JSONObject o : eventsOn(events, e)) {
            String t = o.optString("type");
            if (LESSON.equals(t) || HIDE_HOLIDAY.equals(t) || HOLIDAY.equals(t) || MAKEUP.equals(t) || "deleted".equals(t)) continue;
            if (used.contains(o.optString("id"))) continue; // already shown as a cancelled session
            if (!clsFilter.isEmpty() && !forClass(o, clsFilter)) continue;
            Item it = new Item();
            it.kind = "event";
            it.type = t;
            it.cls = o.optString("cls");
            it.time = o.optString("time");
            it.title = o.optString("title");
            it.note = o.optString("note");
            it.sort = startMin(it.time);
            it.ev = o;
            evs.add(it);
        }
        Collections.sort(evs, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                return a.sort < b.sort ? -1 : (a.sort > b.sort ? 1 : 0);
            }
        });
        d.items.addAll(evs);
        return d;
    }

    /** One line for a day item, e.g. «16:00 — کلاس A1 — جلسه 7». */
    static String line(Item it) {
        StringBuilder b = new StringBuilder();
        if ("event".equals(it.kind)) {
            b.append(typeIcon(it.type)).append(' ').append(typeLabel(it.type));
            if (!it.title.isEmpty()) b.append(": ").append(it.title);
            if (!it.cls.isEmpty()) b.append(" — کلاس ").append(it.cls);
            else b.append(" — همه کلاس‌ها");
            if (!it.time.isEmpty()) b.append(" — ساعت ").append(it.time);
            if (!it.note.isEmpty()) b.append(" (").append(it.note).append(")");
            return b.toString();
        }
        if (!it.time.isEmpty()) b.append(it.time).append(" — ");
        b.append("کلاس ").append(it.cls);
        if ("cancelled".equals(it.kind)) return b.append(" — برگزار نمی‌شود (").append(it.reason).append(")").toString();
        if ("makeup".equals(it.kind)) b.append(" — جلسه جبرانی");
        if (it.num > 0) b.append(" — جلسه ").append(it.num);
        return b.toString();
    }

    /** Short markers for the month grid: how many classes meet, how many events. */
    static int[] counts(JSONArray classes, JSONArray events, long e, String clsFilter) {
        int sessions = 0, evs = 0;
        for (Item it : day(classes, events, e, clsFilter, false).items) {
            if ("event".equals(it.kind)) evs++;
            else if (!"cancelled".equals(it.kind)) sessions++;
        }
        return new int[]{sessions, evs};
    }

    // ---------- exports ----------

    static String dayHeader(long e) {
        int[] j = jal(e);
        return DAYS[wd(e)] + " " + j[2] + " " + MONTHS[j[1] - 1] + " " + j[0] + " (" + JalaliDate.fmt(j[0], j[1], j[2]) + ")";
    }

    static String monthText(JSONArray classes, JSONArray events, int jy, int jm, String clsFilter) {
        StringBuilder b = new StringBuilder("تقویم آموزشی — " + MONTHS[jm - 1] + " " + jy);
        if (!clsFilter.isEmpty()) b.append(" — کلاس ").append(clsFilter);
        b.append("\n");
        int n = monthLen(jy, jm);
        boolean any = false;
        for (int d = 1; d <= n; d++) {
            long e = epoch(jy, jm, d);
            Day day = day(classes, events, e, clsFilter);
            if (day.holiday == null && day.items.isEmpty()) continue;
            any = true;
            b.append("\n").append(dayHeader(e)).append("\n");
            if (day.holiday != null) b.append("🔴 تعطیل: ").append(day.holiday).append("\n");
            for (Item it : day.items) {
                b.append("• ").append(line(it)).append("\n");
                if (!"event".equals(it.kind) && !"cancelled".equals(it.kind) && it.num > 0) {
                    JSONObject l = lesson(events, it.cls, JalaliDate.fmt(jy, jm, d));
                    if (l != null && !l.optString("note").trim().isEmpty()) b.append("   برنامه درسی: ").append(l.optString("note").trim()).append("\n");
                }
            }
        }
        if (!any) b.append("\nرویدادی ثبت نشده است.\n");
        return b.toString().trim();
    }

    static String dayText(JSONArray classes, JSONArray events, long e, String clsFilter) {
        Day day = day(classes, events, e, clsFilter);
        StringBuilder b = new StringBuilder("تقویم آموزشی — ").append(dayHeader(e)).append("\n");
        if (day.holiday != null) b.append("🔴 تعطیل: ").append(day.holiday).append("\n");
        if (day.items.isEmpty() && day.holiday == null) b.append("جلسه یا رویدادی نیست.\n");
        for (Item it : day.items) {
            b.append("• ").append(line(it)).append("\n");
            if (!"event".equals(it.kind) && !"cancelled".equals(it.kind)) {
                JSONObject l = lesson(events, it.cls, fmt(e));
                if (l != null && !l.optString("note").trim().isEmpty()) b.append("   برنامه درسی: ").append(l.optString("note").trim()).append("\n");
            }
        }
        return b.toString().trim();
    }

    /** Held sessions of one class from..to: [epochDay, number] pairs. */
    static List<long[]> sessions(JSONObject c, JSONArray events, long from, long to) {
        List<long[]> out = new ArrayList<>();
        long s = parse(c.optString("start"));
        if (s == JalaliDate.INVALID) {
            for (long d = from; d <= to && d - from <= MAX_SPAN; d++) if (held(c, events, d)) out.add(new long[]{d, 0});
            return out;
        }
        int n = 0;
        long first = Math.max(s, to - MAX_SPAN);
        for (long d = first; d <= to; d++) {
            if (held(c, events, d)) {
                n++;
                if (d >= from) out.add(new long[]{d, n});
            }
        }
        return out;
    }

    static String classSessionsText(JSONObject c, JSONArray events) {
        long s = parse(c.optString("start")), en = parse(c.optString("end"));
        if (s == JalaliDate.INVALID) s = JalaliDate.today();
        if (en == JalaliDate.INVALID) en = s + 120;
        StringBuilder b = new StringBuilder("برنامه جلسات کلاس " + c.optString("name") + "\n");
        if (!c.optString("time").isEmpty()) b.append("ساعت: ").append(c.optString("time")).append("\n");
        for (long[] x : sessions(c, events, s, en)) {
            b.append("\nجلسه ").append(x[1]).append(" — ").append(dayHeader(x[0])).append("\n");
            JSONObject l = lesson(events, c.optString("name"), fmt(x[0]));
            if (l != null && !l.optString("note").trim().isEmpty()) b.append("   ").append(l.optString("note").trim()).append("\n");
        }
        return b.toString().trim();
    }
}
