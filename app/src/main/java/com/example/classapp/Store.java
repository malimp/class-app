package com.example.classapp;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** All app data (students, classes, reports, manual notes), persisted as JSON in SharedPreferences. */
final class Store {
    static final int VERSION = 7;
    static final int MAX_OPTIONS = 8;
    static final String[] DEFAULT_TEACHERS = {"Asal", "Asha"};
    static final String[] DEFAULT_OPTIONS = {
            "روخوانی تکرار گرفتند", "معنی تکرار گرفتند", "روخوانی با ارفاق پذیرفته شد",
            "معنی با ارفاق پذیرفته شد", "تمرینات انجام نداده بودند", "تمرینات ناقص انجام شده بود"};

    private final SharedPreferences sp;
    JSONArray students, classes, reports, events;
    JSONObject notes, settings;
    /** Finance data: separate from the normal backup (never merged into / exported with it). */
    JSONObject fin;

    Store(Context c) {
        sp = c.getSharedPreferences("class_data", 0);
        students = clean(arr("students"));
        classes = clean(arr("classes"));
        reports = clean(arr("reports"));
        notes = obj("notes");
        settings = obj("settings");
        events = clean(arr("events"));
        fin = Fin.fix(obj("fin"));
        normalize();
        migrateOldNotes();
    }

    // ---------- persistence ----------

    void save() {
        sp.edit().putString("students", students.toString()).putString("classes", classes.toString())
                .putString("reports", reports.toString()).putString("notes", notes.toString()).putString("settings", settings.toString())
                .putString("events", events.toString()).putString("fin", fin.toString()).apply();
    }

    private JSONArray arr(String k) {
        try {
            return new JSONArray(sp.getString(k, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private JSONObject obj(String k) {
        try {
            return new JSONObject(sp.getString(k, "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** Keeps only JSON objects (drops corrupted entries). */
    private static JSONArray clean(JSONArray a) {
        JSONArray out = new JSONArray();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) out.put(o);
        }
        return out;
    }

    /**
     * Integrity: every class has a member list; members must be existing students; a student belongs to at most one
     * class (the first class wins) and appears only once.
     */
    private void normalize() {
        Set<String> known = new HashSet<>();
        for (int i = 0; i < students.length(); i++) known.add(students.optJSONObject(i).optString("code"));
        Set<String> used = new HashSet<>();
        boolean changed = false;
        for (int i = 0; i < classes.length(); i++) {
            JSONObject c = classes.optJSONObject(i);
            JSONArray old = c.optJSONArray("students");
            JSONArray fresh = new JSONArray();
            if (old != null) {
                for (int j = 0; j < old.length(); j++) {
                    String code = old.optString(j);
                    if (known.contains(code) && used.add(code)) fresh.put(code);
                }
            }
            if (old == null || old.length() != fresh.length()) {
                try {
                    c.put("students", fresh);
                    changed = true;
                } catch (JSONException ignored) {
                    // cannot happen for a valid key
                }
            }
        }
        if (changed) save();
    }

    /** Older versions kept manual notes as separate preference keys; move them into the notes object (also backed up). */
    private void migrateOldNotes() {
        SharedPreferences.Editor ed = null;
        for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
            String k = e.getKey();
            if ((k.startsWith("overallNote") || k.startsWith("studentNote")) && e.getValue() instanceof String) {
                try {
                    if (!notes.has(k)) notes.put(k, e.getValue());
                } catch (JSONException ignored) {
                    // skip
                }
                if (ed == null) ed = sp.edit();
                ed.remove(k);
            }
        }
        if (ed != null) {
            ed.apply();
            save();
        }
    }

    // ---------- settings (teachers, remark options) ----------

    private static List<String> strings(JSONArray a) {
        List<String> out = new ArrayList<>();
        if (a == null) return out;
        for (int i = 0; i < a.length(); i++) {
            String v = a.optString(i, "").trim();
            if (!v.isEmpty() && !out.contains(v)) out.add(v);
        }
        return out;
    }

    List<String> teachers() {
        if (!settings.has("teachers")) return new ArrayList<>(Arrays.asList(DEFAULT_TEACHERS));
        return strings(settings.optJSONArray("teachers"));
    }

    List<String> options() {
        if (!settings.has("options")) return new ArrayList<>(Arrays.asList(DEFAULT_OPTIONS));
        List<String> l = strings(settings.optJSONArray("options"));
        return l.size() > MAX_OPTIONS ? new ArrayList<>(l.subList(0, MAX_OPTIONS)) : l;
    }

    String lastTeacher() {
        return settings.optString("lastTeacher", "");
    }

    private void putList(String key, List<String> v) {
        try {
            JSONArray a = new JSONArray();
            for (String s : v) a.put(s);
            settings.put(key, a);
        } catch (JSONException ignored) {
            // skip
        }
    }

    void setTeachers(List<String> v) {
        putList("teachers", strings(new JSONArray(v)));
        save();
    }

    void setOptions(List<String> v) {
        List<String> l = strings(new JSONArray(v));
        if (l.size() > MAX_OPTIONS) l = new ArrayList<>(l.subList(0, MAX_OPTIONS));
        putList("options", l);
        save();
    }

    void setLastTeacher(String t) {
        try {
            settings.put("lastTeacher", t == null ? "" : t);
        } catch (JSONException ignored) {
            // skip
        }
        save();
    }

    // ---------- manager mode (device-local: never part of any backup) ----------

    boolean manager() {
        return sp.getBoolean("manager", false);
    }

    void setManager(boolean on) {
        sp.edit().putBoolean("manager", on).apply();
    }

    // ---------- manager password (4 digits; stored only as a salted hash on this device, never in any backup) ----------

    private static String sha(String s) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte x : h) b.append(String.format(java.util.Locale.US, "%02x", x & 0xff));
            return b.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            return s;
        }
    }

    /** Four digits in any digit script, else null. */
    static String pin(String raw) {
        String d = Text.digits(raw == null ? "" : raw.trim());
        return d.matches("\\d{4}") ? d : null;
    }

    boolean hasManagerPassword() {
        return !sp.getString("mgr_hash", "").isEmpty();
    }

    boolean checkManagerPassword(String raw) {
        String p = pin(raw);
        return p != null && sha(sp.getString("mgr_salt", "") + p).equals(sp.getString("mgr_hash", ""));
    }

    /** raw must be a valid 4-digit pin (see pin()); hint = the reminder message the manager wrote for himself. */
    void setManagerPassword(String raw, String hint) {
        String p = pin(raw);
        if (p == null) return;
        String salt = Long.toHexString(System.nanoTime()) + Long.toHexString(System.currentTimeMillis());
        sp.edit().putString("mgr_salt", salt).putString("mgr_hash", sha(salt + p)).putString("mgr_hint", hint == null ? "" : hint.trim()).apply();
    }

    String managerHint() {
        return sp.getString("mgr_hint", "");
    }

    void setManagerHint(String hint) {
        sp.edit().putString("mgr_hint", hint == null ? "" : hint.trim()).apply();
    }

    // ---------- calendar events ----------

    /** Adds or replaces (same id) an event; stamps it with the edit time. */
    void putEvent(JSONObject ev) {
        try {
            if (ev.optString("id").isEmpty()) ev.put("id", "E" + System.currentTimeMillis() + "_" + events.length());
            ev.put("ts", System.currentTimeMillis());
            String id = ev.optString("id");
            for (int i = 0; i < events.length(); i++) {
                if (id.equals(events.optJSONObject(i).optString("id"))) {
                    events.put(i, ev);
                    save();
                    return;
                }
            }
            events.put(ev);
            save();
        } catch (JSONException ignored) {
            // skip
        }
    }

    /** Deleting leaves a dated tombstone so that a merge from another phone does not bring the event back. */
    void removeEvent(String id) {
        for (int i = 0; i < events.length(); i++) {
            if (id.equals(events.optJSONObject(i).optString("id"))) {
                try {
                    events.put(i, new JSONObject().put("id", id).put("type", "deleted").put("ts", System.currentTimeMillis()));
                } catch (JSONException ignored) {
                    // skip
                }
                save();
                return;
            }
        }
    }

    /** Saves (or, when the text is empty, removes) the lesson plan of one class session. */
    void setLesson(String cls, String date, String text) {
        String id = Cal.lessonId(cls, date);
        if (text.trim().isEmpty()) {
            removeEvent(id);
            return;
        }
        try {
            putEvent(new JSONObject().put("id", id).put("type", Cal.LESSON).put("date", date).put("cls", cls).put("note", text.trim()));
        } catch (JSONException ignored) {
            // skip
        }
    }

    // ---------- finance ----------

    void saveFin() {
        sp.edit().putString("fin", fin.toString()).apply();
    }

    // ---------- notes ----------

    String note(String key) {
        return notes.optString(key, "");
    }

    void setNote(String key, String value) {
        try {
            if (value.trim().isEmpty()) notes.remove(key);
            else notes.put(key, value);
        } catch (JSONException ignored) {
            // skip
        }
        save();
    }

    // ---------- students / classes ----------

    JSONObject student(String code) {
        for (int i = 0; i < students.length(); i++) {
            JSONObject o = students.optJSONObject(i);
            if (o.optString("code").equals(code)) return o;
        }
        return null;
    }

    boolean hasFullName(String first, String last, int exceptIdx) {
        String f = Text.norm(first), l = Text.norm(last);
        for (int i = 0; i < students.length(); i++) {
            if (i == exceptIdx) continue;
            JSONObject o = students.optJSONObject(i);
            if (Text.norm(o.optString("first")).equals(f) && Text.norm(o.optString("last")).equals(l)) return true;
        }
        return false;
    }

    /** Index of the class that contains this student, or -1. */
    int classIndexOf(String code) {
        for (int i = 0; i < classes.length(); i++) {
            JSONArray m = classes.optJSONObject(i).optJSONArray("students");
            if (m == null) continue;
            for (int j = 0; j < m.length(); j++) if (code.equals(m.optString(j))) return i;
        }
        return -1;
    }

    String classNameOf(String code) {
        int i = classIndexOf(code);
        return i < 0 ? "" : classes.optJSONObject(i).optString("name");
    }

    /** A unique code like "S1760000000000"; never collides with existing codes in the list. */
    String newCode(String prefix, JSONArray list) {
        long t = System.currentTimeMillis();
        int n = 0;
        String c;
        do {
            c = prefix + (t + n++);
        } while (codeExists(list, c));
        return c;
    }

    boolean codeExists(JSONArray list, String code) {
        if (code == null || code.isEmpty()) return false;
        String k = Text.norm(code);
        for (int i = 0; i < list.length(); i++) {
            if (Text.norm(list.optJSONObject(i).optString("code")).equals(k)) return true;
        }
        return false;
    }

    /** Adds a student to a class unless the student already belongs to a class. */
    boolean addToClass(int ci, String code) {
        if (classIndexOf(code) >= 0) return false;
        JSONObject c = classes.optJSONObject(ci);
        if (c == null) return false;
        JSONArray m = c.optJSONArray("students");
        if (m == null) m = new JSONArray();
        m.put(code);
        try {
            c.put("students", m);
        } catch (JSONException e) {
            return false;
        }
        return true;
    }

    void removeFromClass(int ci, String code) {
        JSONObject c = classes.optJSONObject(ci);
        if (c == null) return;
        JSONArray m = c.optJSONArray("students");
        if (m == null) return;
        JSONArray fresh = new JSONArray();
        for (int i = 0; i < m.length(); i++) if (!code.equals(m.optString(i))) fresh.put(m.optString(i));
        try {
            c.put("students", fresh);
        } catch (JSONException ignored) {
            // skip
        }
    }

    /** Deletes a student and removes them from their class. */
    void removeStudent(int idx) {
        JSONObject s = students.optJSONObject(idx);
        if (s == null) return;
        String code = s.optString("code");
        for (int i = 0; i < classes.length(); i++) removeFromClass(i, code);
        students.remove(idx);
    }

    // ---------- backup ----------

    String exportJson() throws JSONException {
        return new JSONObject().put("app", "class").put("version", VERSION)
                .put("exportedAt", System.currentTimeMillis())
                .put("students", students).put("classes", classes).put("reports", reports)
                .put("notes", notes).put("settings", settings).put("events", events).toString(2);
    }

    /** Returns an error message, or null when the backup structure is valid. Older backups (without notes) are valid. */
    static String validate(JSONObject r) {
        if (!(r.opt("students") instanceof JSONArray) || !(r.opt("classes") instanceof JSONArray)
                || !(r.opt("reports") instanceof JSONArray)) {
            return "فایل پشتیبان معتبر نیست";
        }
        return null;
    }

    void replaceWith(JSONObject r) {
        students = clean(r.optJSONArray("students"));
        classes = clean(r.optJSONArray("classes"));
        reports = clean(r.optJSONArray("reports"));
        JSONObject n = r.optJSONObject("notes");
        notes = n != null ? n : new JSONObject();
        JSONObject sj = r.optJSONObject("settings"); // older backups have none: keep the current settings
        if (sj != null) settings = sj;
        if (r.optJSONArray("events") != null) events = clean(r.optJSONArray("events")); // older backups have none: keep the current events
        normalize();
        save();
    }
}
