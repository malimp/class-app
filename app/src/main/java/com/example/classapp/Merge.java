package com.example.classapp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Merges a backup (from another phone or the web version) into the current data. Nothing is ever deleted. */
final class Merge {
    private Merge() {}

    static final class Result {
        int students, classes, reports, duplicateReports, members, notes;

        String summary() {
            return "زبان‌آموز جدید: " + students + "\nکلاس جدید: " + classes + "\nعضو جدید در کلاس‌ها: " + members
                    + "\nگزارش جدید: " + reports + "\nگزارش تکراری (نادیده گرفته شد): " + duplicateReports
                    + "\nیادداشت جدید: " + notes;
        }
    }

    private static String fullKey(JSONObject s) {
        return Text.norm(s.optString("first")) + "|" + Text.norm(s.optString("last"));
    }

    /** Identity of a report: its id, else day + class + teacher + text. */
    static String reportKey(JSONObject r) {
        String id = r.optString("id");
        if (!id.isEmpty()) return "id:" + id;
        return "t:" + Text.norm(r.optString("date")) + "|" + Text.norm(r.optString("class")) + "|"
                + Text.norm(r.optString("teacher")) + "|" + Text.norm(r.optString("text"));
    }

    static Result into(Store st, JSONObject in) throws JSONException {
        Result res = new Result();
        // students
        Map<String, String> map = new HashMap<>(); // incoming code -> local code
        JSONArray ins = in.optJSONArray("students");
        for (int i = 0; ins != null && i < ins.length(); i++) {
            JSONObject s = ins.optJSONObject(i);
            if (s == null) continue;
            String code = s.optString("code");
            String found = null;
            JSONObject byCode = st.student(code);
            for (int k = 0; k < st.students.length() && found == null; k++) {
                JSONObject l = st.students.optJSONObject(k);
                if (fullKey(l).equals(fullKey(s))) found = l.optString("code");
            }
            if (found == null && byCode != null && fullKey(byCode).equals(fullKey(s))) found = code;
            if (found == null) {
                JSONObject copy = new JSONObject(s.toString());
                String nc = code;
                if (code.isEmpty() || st.codeExists(st.students, code)) nc = st.newCode("S", st.students);
                copy.put("code", nc);
                st.students.put(copy);
                found = nc;
                res.students++;
            }
            map.put(code, found);
        }
        // classes
        JSONArray inc = in.optJSONArray("classes");
        for (int i = 0; inc != null && i < inc.length(); i++) {
            JSONObject c = inc.optJSONObject(i);
            if (c == null) continue;
            int ci = -1;
            for (int k = 0; k < st.classes.length() && ci < 0; k++) {
                if (Text.norm(st.classes.optJSONObject(k).optString("name")).equals(Text.norm(c.optString("name")))) ci = k;
            }
            if (ci < 0) {
                JSONObject copy = new JSONObject(c.toString());
                copy.put("students", new JSONArray());
                if (copy.optString("code").isEmpty() || st.codeExists(st.classes, copy.optString("code"))) {
                    copy.put("code", st.newCode("C", st.classes));
                }
                st.classes.put(copy);
                ci = st.classes.length() - 1;
                res.classes++;
            }
            JSONArray mem = c.optJSONArray("students");
            for (int j = 0; mem != null && j < mem.length(); j++) {
                String local = map.get(mem.optString(j));
                if (local != null && st.addToClass(ci, local)) res.members++;
            }
        }
        // reports
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < st.reports.length(); i++) keys.add(reportKey(st.reports.optJSONObject(i)));
        JSONArray inr = in.optJSONArray("reports");
        for (int i = 0; inr != null && i < inr.length(); i++) {
            JSONObject r = inr.optJSONObject(i);
            if (r == null) continue;
            if (keys.add(reportKey(r))) {
                st.reports.put(new JSONObject(r.toString()));
                res.reports++;
            } else {
                res.duplicateReports++;
            }
        }
        // notes: only keys that do not exist yet
        JSONObject nn = in.optJSONObject("notes");
        if (nn != null) {
            Iterator<String> it = nn.keys();
            List<String> ks = new ArrayList<>();
            while (it.hasNext()) ks.add(it.next());
            for (String k : ks) {
                // student notes are keyed by student code: translate to the local code
                String key = k;
                int us = k.indexOf('_');
                if (k.startsWith("studentNote") && us > 0) {
                    String local = map.get(k.substring(us + 1));
                    if (local != null) key = k.substring(0, us + 1) + local;
                }
                if (!st.notes.has(key) && !nn.optString(k).trim().isEmpty()) {
                    st.notes.put(key, nn.optString(k));
                    res.notes++;
                }
            }
        }
        // settings: union of teachers; options only when this phone has none of its own
        JSONObject sj = in.optJSONObject("settings");
        if (sj != null) {
            List<String> t = st.teachers();
            JSONArray ta = sj.optJSONArray("teachers");
            for (int i = 0; ta != null && i < ta.length(); i++) {
                String v = ta.optString(i).trim();
                if (!v.isEmpty() && !t.contains(v)) t.add(v);
            }
            if (sj.has("teachers") || st.settings.has("teachers")) st.setTeachers(t);
            if (!st.settings.has("options") && sj.optJSONArray("options") != null) {
                List<String> o = new ArrayList<>();
                JSONArray oa = sj.optJSONArray("options");
                for (int i = 0; i < oa.length(); i++) o.add(oa.optString(i));
                st.setOptions(o);
            }
        }
        st.save();
        return res;
    }
}
