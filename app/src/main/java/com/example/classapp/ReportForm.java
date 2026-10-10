package com.example.classapp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** The daily report as filled in on the tick-box form, and the messenger text generated from it. */
final class ReportForm {
    /** Resolves a student code to the name printed in the report. */
    interface Names {
        String name(String code);
    }

    /** One student's ticked remarks and free remark. */
    static final class Mark {
        final List<String> opts = new ArrayList<>();
        String other = "";

        boolean empty() {
            return opts.isEmpty() && other.trim().isEmpty();
        }
    }

    String book = "", workbook = "", other = "", homework = "", general = "";
    final Set<String> absent = new LinkedHashSet<>();
    final Set<String> late = new LinkedHashSet<>();
    final Map<String, Mark> marks = new LinkedHashMap<>();

    /** A student is either absent, late or neither; absent wins. */
    void normalize() {
        late.removeAll(absent);
        for (String c : absent) marks.remove(c);
    }

    static String oneLine(String s) {
        return s == null ? "" : s.replaceAll("\\s*[\\r\\n]+\\s*", " ").trim();
    }

    static String names(List<String> codes, Names n) {
        StringBuilder b = new StringBuilder();
        for (String c : codes) {
            if (b.length() > 0) b.append(" ، ");
            b.append(n.name(c));
        }
        return b.toString();
    }

    /**
     * The report text. Students with exactly the same remarks share one heading ("مانیا ، الیسا"); every remark is on
     * its own line. The heading «وضعیت امروز بچه‌ها» is always printed.
     */
    String toText(String teacher, String date, String cls, List<String> members, Names n) {
        normalize();
        StringBuilder s = new StringBuilder();
        if (!teacher.trim().isEmpty()) s.append(teacher.trim()).append(":\n");
        s.append("با سلام\n\n").append(date.trim()).append("\n");
        if (!cls.trim().isEmpty()) s.append("کلاس : ").append(cls.trim()).append("\n");
        s.append("\n");
        String bk = oneLine(book), wb = oneLine(workbook), ot = oneLine(other);
        if (!bk.isEmpty() || !wb.isEmpty() || !ot.isEmpty()) {
            s.append("درس امروز\n");
            if (!bk.isEmpty()) s.append("Student's book : ").append(bk).append("\n");
            if (!wb.isEmpty()) s.append("Workbook : ").append(wb).append("\n");
            if (!ot.isEmpty()) s.append(ot).append("\n");
            s.append("\n");
        }
        List<String> abs = new ArrayList<>(), lat = new ArrayList<>();
        for (String c : members) {
            if (absent.contains(c)) abs.add(c);
            else if (late.contains(c)) lat.add(c);
        }
        if (!abs.isEmpty() || !lat.isEmpty()) {
            if (!abs.isEmpty()) s.append("غایب : ").append(names(abs, n)).append("\n");
            if (!lat.isEmpty()) s.append("تأخیر : ").append(names(lat, n)).append("\n");
            s.append("\n");
        }
        String hw = homework.trim();
        if (!hw.isEmpty()) s.append("تکلیف جلسه بعد\n").append(hw).append("\n\n");
        s.append("وضعیت امروز بچه‌ها\n\n");
        // group students with identical remarks, in class order
        Map<String, List<String>> groups = new LinkedHashMap<>();
        Map<String, List<String>> lines = new LinkedHashMap<>();
        for (String c : members) {
            Mark m = marks.get(c);
            if (absent.contains(c) || m == null || m.empty()) continue;
            List<String> l = new ArrayList<>(m.opts);
            String o = oneLine(m.other);
            if (!o.isEmpty()) l.add(o);
            StringBuilder kb = new StringBuilder();
            for (String x : l) kb.append(x).append('\n');
            String key = kb.toString();
            if (!groups.containsKey(key)) {
                groups.put(key, new ArrayList<String>());
                lines.put(key, l);
            }
            groups.get(key).add(c);
        }
        for (Map.Entry<String, List<String>> e : groups.entrySet()) {
            s.append(names(e.getValue(), n)).append("\n");
            for (String l : lines.get(e.getKey())) s.append(l).append("\n");
            s.append("\n");
        }
        String g = general.trim();
        if (!g.isEmpty()) s.append("یادداشت کلی\n").append(g).append("\n");
        return s.toString().replaceAll("\\s+$", "") + "\n";
    }

    JSONObject toJson() throws JSONException {
        normalize();
        JSONObject o = new JSONObject().put("book", book).put("workbook", workbook).put("other", other)
                .put("homework", homework).put("general", general);
        o.put("absent", new JSONArray(new ArrayList<>(absent))).put("late", new JSONArray(new ArrayList<>(late)));
        JSONObject ms = new JSONObject();
        for (Map.Entry<String, Mark> e : marks.entrySet()) {
            if (e.getValue().empty()) continue;
            ms.put(e.getKey(), new JSONObject().put("opts", new JSONArray(e.getValue().opts)).put("other", e.getValue().other));
        }
        return o.put("marks", ms);
    }

    static ReportForm fromJson(JSONObject o) {
        ReportForm f = new ReportForm();
        if (o == null) return f;
        f.book = o.optString("book");
        f.workbook = o.optString("workbook");
        f.other = o.optString("other");
        f.homework = o.optString("homework");
        f.general = o.optString("general");
        JSONArray a = o.optJSONArray("absent");
        for (int i = 0; a != null && i < a.length(); i++) f.absent.add(a.optString(i));
        JSONArray l = o.optJSONArray("late");
        for (int i = 0; l != null && i < l.length(); i++) f.late.add(l.optString(i));
        JSONObject ms = o.optJSONObject("marks");
        if (ms != null) {
            java.util.Iterator<String> it = ms.keys();
            while (it.hasNext()) {
                String code = it.next();
                JSONObject mo = ms.optJSONObject(code);
                if (mo == null) continue;
                Mark m = new Mark();
                JSONArray oa = mo.optJSONArray("opts");
                for (int i = 0; oa != null && i < oa.length(); i++) m.opts.add(oa.optString(i));
                m.other = mo.optString("other");
                f.marks.put(code, m);
            }
        }
        f.normalize();
        return f;
    }
}
