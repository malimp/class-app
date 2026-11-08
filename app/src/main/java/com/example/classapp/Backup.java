package com.example.classapp;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Rolling automatic snapshots of the whole data set, kept in the app's private storage.
 *
 * <p>One snapshot is taken on the first start of each day and one before any restore or replace, so a bad
 * restore (or a corrupted start) can always be undone without the user having exported anything. Only the
 * newest {@link #KEEP} snapshots are kept. A verbatim copy of the stored preferences is kept as a
 * {@code raw-} file whenever a section cannot be parsed, because that raw text may be the only copy left.
 */
final class Backup {
    static final int KEEP = 7;
    static final int KEEP_RAW = 3;
    /** After this many days without a manual backup the home screen shows a reminder. */
    static final int OVERDUE_DAYS = 7;

    private static final String PREFS = "class_meta";
    private static final String KEY_LAST_EXPORT = "lastExport";

    private Backup() {}

    static File dir(Context c) {
        File d = new File(c.getFilesDir(), "auto-backup");
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }

    /** One snapshot file. */
    static final class Snap {
        final File file;
        final long at;
        final String reason;

        Snap(File f) {
            file = f;
            at = f.lastModified();
            String n = f.getName();
            int a = n.indexOf('-', 5);
            int b = n.lastIndexOf('.');
            reason = (a > 0 && b > a) ? n.substring(a + 1, b) : "";
        }
    }

    static String reasonLabel(String r) {
        if ("daily".equals(r)) return "روزانه";
        if ("before-restore".equals(r)) return "قبل از بازیابی";
        if ("quarantine".equals(r)) return "نسخهٔ خام";
        return r;
    }

    /** Writes one snapshot of the current data; failures are silent because this is a safety net, not an action. */
    static void take(Context c, Store st, String reason) {
        try {
            writeSnap(c, st.exportJson(), reason);
        } catch (Exception ignored) {
            // a snapshot that cannot be written must never break the screen the user is on
        }
    }

    /** At most one automatic snapshot per day; returns true when one was written. */
    static boolean daily(Context c, Store st) {
        if (st.students.length() == 0 && st.classes.length() == 0 && st.reports.length() == 0) return false;
        long today = JalaliDate.fromMillis(System.currentTimeMillis());
        List<Snap> l = list(c);
        if (!l.isEmpty() && JalaliDate.fromMillis(l.get(0).at) == today) return false;
        take(c, st, "daily");
        return true;
    }

    /** Keeps the raw preference text of a section that failed to parse. */
    static void raw(Context c, String json) {
        put(c, "raw-" + System.currentTimeMillis() + ".json", json);
        prune(c, "raw-", KEEP_RAW);
    }

    private static void writeSnap(Context c, String json, String reason) {
        if (!put(c, "snap-" + System.currentTimeMillis() + "-" + reason + ".json", json)) return;
        prune(c, "snap-", KEEP);
        prune(c, "raw-", KEEP_RAW);
    }

    private static boolean put(Context c, String name, String json) {
        File d = dir(c);
        File tmp = new File(d, name + ".tmp");
        File out = new File(d, name);
        try {
            FileOutputStream o = new FileOutputStream(tmp);
            try {
                o.write(json.getBytes(StandardCharsets.UTF_8));
            } finally {
                o.close();
            }
            if (!tmp.renameTo(out)) {
                tmp.delete();
                return false;
            }
            return true;
        } catch (IOException e) {
            tmp.delete();
            return false;
        }
    }

    private static List<File> files(Context c, String prefix) {
        File[] all = dir(c).listFiles();
        List<File> out = new ArrayList<>();
        if (all != null) {
            for (File f : all) {
                String n = f.getName();
                if (f.isFile() && n.startsWith(prefix) && n.endsWith(".json")) out.add(f);
            }
        }
        Collections.sort(out, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return Long.compare(b.lastModified(), a.lastModified());
            }
        });
        return out;
    }

    private static void prune(Context c, String prefix, int keep) {
        List<File> l = files(c, prefix);
        for (int i = keep; i < l.size(); i++) l.get(i).delete();
    }

    /** Newest first. */
    static List<Snap> list(Context c) {
        List<Snap> out = new ArrayList<>();
        for (File f : files(c, "snap-")) out.add(new Snap(f));
        return out;
    }

    static String read(File f) throws IOException {
        FileInputStream in = new FileInputStream(f);
        try {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) b.write(buf, 0, n);
            return new String(b.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            in.close();
        }
    }

    static String label(Snap s) {
        String day = Cal.fmt(JalaliDate.fromMillis(s.at));
        String time = new SimpleDateFormat("HH:mm", Locale.US).format(new Date(s.at));
        return day + " — " + time + " — " + reasonLabel(s.reason);
    }

    static String size(Snap s) {
        return Math.max(1, s.file.length() / 1024) + "KB";
    }

    // ---------- last manual backup (device-local, never part of any backup) ----------

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, 0);
    }

    static long lastExport(Context c) {
        return prefs(c).getLong(KEY_LAST_EXPORT, 0);
    }

    static void setLastExport(Context c, long at) {
        prefs(c).edit().putLong(KEY_LAST_EXPORT, at).apply();
    }

    /** Days since the last manual backup, or -1 when the user has never taken one. */
    static int daysSinceExport(Context c) {
        long t = lastExport(c);
        if (t == 0) return -1;
        return (int) Math.max(0, (System.currentTimeMillis() - t) / 86400000L);
    }
}
