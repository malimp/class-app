package com.example.classapp;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** All app data (students, classes, reports, manual notes), persisted as JSON in SharedPreferences. */
final class Store {
    static final int VERSION = 5;

    private final SharedPreferences sp;
    JSONArray students, classes, reports;
    JSONObject notes;

    Store(Context c) {
        sp = c.getSharedPreferences("class_data", 0);
        students = clean(arr("students"));
        classes = clean(arr("classes"));
        reports = clean(arr("reports"));
        notes = obj("notes");
        normalize();
        migrateOldNotes();
    }

    // ---------- persistence ----------

    void save() {
        sp.edit().putString("students", students.toString()).putString("classes", classes.toString())
                .putString("reports", reports.toString()).putString("notes", notes.toString()).apply();
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
                .put("notes", notes).toString(2);
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
        normalize();
        save();
    }
}
