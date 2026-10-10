package com.example.classapp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Statistical summary of one student's reports in a period; similar remarks are grouped and counted together. */
final class Summary {
    private Summary() {}

    /** One report's contribution for the student: its date, whether he/she was absent, and the matched text. */
    static final class Entry {
        final String date, text;
        final boolean absent, late, implied;

        Entry(String date, boolean absent, String text) {
            this(date, absent, false, text, false);
        }

        Entry(String date, boolean absent, boolean late, String text) {
            this(date, absent, late, text, false);
        }

        private Entry(String date, boolean absent, boolean late, String text, boolean implied) {
            this.date = date;
            this.absent = absent;
            this.late = late && !absent;
            this.text = text == null ? "" : text;
            this.implied = implied;
        }

        /** The student's name is not in the report of his/her own class: counted as present, homework done well. */
        static Entry implied(String date) {
            return implied(date, false);
        }

        static Entry implied(String date, boolean late) {
            return new Entry(date, false, late, "", true);
        }
    }

    private static final class Group {
        final String rep, norm;
        final Set<String> tokens;
        final List<String> dates = new ArrayList<>();
        int count, lastEntry = -1;

        Group(String rep, String norm, Set<String> tokens) {
            this.rep = rep;
            this.norm = norm;
            this.tokens = tokens;
        }
    }

    static final String IMPLIED = "حاضر — تکالیفش خوب انجام شده";

    private static final Set<String> STOP = new HashSet<>(Arrays.asList("و", "در", "با", "را", "به", "از", "که", "هم", "این", "آن"));

    static Set<String> tokens(String norm) {
        Set<String> t = new HashSet<>();
        for (String w : norm.split("[^\\p{L}\\p{N}]+")) if (!w.isEmpty() && !STOP.contains(w)) t.add(w);
        return t;
    }

    /** Two remarks are "similar" when their word sets overlap by at least 60% (Jaccard). */
    static boolean similar(Set<String> a, String na, Set<String> b, String nb) {
        if (a.isEmpty() || b.isEmpty()) return na.equals(nb);
        int inter = 0;
        for (String w : a) if (b.contains(w)) inter++;
        int union = a.size() + b.size() - inter;
        return union > 0 && inter * 10 >= union * 6;
    }

    /** Non-empty lines of the matched text, without the heading line that is just the student's name. */
    static List<String> remarks(String text, String key) {
        List<String> out = new ArrayList<>();
        for (String line : text.split("\\r?\\n")) {
            String n = Text.norm(line);
            if (n.isEmpty() || n.equals(key)) continue;
            out.add(line.trim());
        }
        return out;
    }

    static String build(List<Entry> entries, int totalReports, String key) {
        int absent = 0, present = 0, implied = 0, late = 0;
        List<String> impliedDates = new ArrayList<>(), absentDates = new ArrayList<>(), lateDates = new ArrayList<>();
        Set<String> nameTokens = tokens(key); // the student's own name is not part of the remark
        List<Group> gs = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (e.late) {
                late++;
                lateDates.add(e.date);
            }
            if (e.implied) {
                implied++;
                impliedDates.add(e.date);
                continue;
            }
            List<String> lines = remarks(e.text, key);
            if (e.absent) {
                absent++;
                absentDates.add(e.date);
            } else if (!lines.isEmpty() || e.late) present++;
            for (String l : lines) {
                String n = Text.norm(l);
                Set<String> t = tokens(n);
                t.removeAll(nameTokens);
                Group g = null;
                for (Group x : gs) {
                    if (similar(x.tokens, x.norm, t, n)) {
                        g = x;
                        break;
                    }
                }
                if (g == null) {
                    g = new Group(l, n, t);
                    gs.add(g);
                }
                if (g.lastEntry != i) {
                    g.lastEntry = i;
                    g.count++;
                    g.dates.add(e.date);
                }
            }
        }
        Collections.sort(gs, (a, b) -> Integer.compare(b.count, a.count)); // stable: ties keep first-seen order
        StringBuilder s = new StringBuilder("📊 خلاصه این بازه\n");
        s.append("گزارش‌های بازه: ").append(totalReports).append("\n");
        s.append("حاضر (با گزارش): ").append(present).append("\n");
        if (absent > 0) s.append("❌ غایب: ").append(absent).append(" بار (").append(join(absentDates)).append(")\n");
        if (late > 0) s.append("⏰ تأخیر: ").append(late).append(" بار (").append(join(lateDates)).append(")\n");
        if (implied > 0) {
            s.append("✅ نامش در گزارش نیامده (حاضر، تکالیفش خوب انجام شده): ").append(implied).append(" بار")
                    .append(" (").append(join(impliedDates)).append(")\n");
        }
        int unknown = Math.max(0, totalReports - present - absent - implied);
        if (unknown > 0) s.append("نامشخص (گزارش بدون کلاس یا نام مشابه): ").append(unknown).append("\n");
        if (!gs.isEmpty()) {
            s.append("\n🔁 موارد گزارش‌شده (موارد مشابه با هم شمرده شده‌اند):\n");
            for (Group g : gs) {
                s.append("• ").append(g.count).append(" بار — ").append(g.rep);
                if (g.count > 1) s.append(" (").append(join(g.dates)).append(")");
                s.append("\n");
            }
        }
        return s.toString().trim();
    }

    private static String join(List<String> l) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) b.append("، ");
            b.append(l.get(i));
        }
        return b.toString();
    }
}
