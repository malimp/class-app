package com.example.classapp;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Screens of the educational calendar and of the finance section (both Jalali, amounts in Toman).
 * Everything that changes events or finance data is shown only in manager mode (a device-local switch without password);
 * lesson plans can be written by teachers too.
 */
final class CalUi {
    private final MainActivity a;

    CalUi(MainActivity a) {
        this.a = a;
    }

    private interface Pick {
        void on(String v);
    }

    private static final Pattern DATE = Pattern.compile("^(\\d{4})[/\\-.](\\d{1,2})[/\\-.](\\d{1,2})$");
    private static final int RED = Color.rgb(200, 40, 40);

    // =====================================================================
    // small UI helpers
    // =====================================================================

    private LinearLayout hrow() {
        LinearLayout l = new LinearLayout(a);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, a.dp(2), 0, a.dp(2));
        a.root.addView(l, p);
        return l;
    }

    private Button hbtn(LinearLayout row, String s, View.OnClickListener l) {
        Button b = new Button(a);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextSize(14);
        b.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(a.dp(2), 0, a.dp(2), 0);
        row.addView(b, p);
        return b;
    }

    private Spinner spin(List<String> items, int sel) {
        Spinner s = new Spinner(a);
        ArrayAdapter<String> ad = new ArrayAdapter<>(a, android.R.layout.simple_spinner_item, items);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(ad);
        s.setSelection(Math.max(0, Math.min(sel, items.size() - 1)));
        return s;
    }

    private TextView label(String s) {
        TextView t = new TextView(a);
        t.setText(s);
        t.setTextSize(13);
        t.setTextColor(Color.rgb(100, 108, 130));
        t.setPadding(a.dp(4), a.dp(8), a.dp(4), 0);
        return t;
    }

    private List<String> classNames() {
        List<String> l = new ArrayList<>();
        for (int i = 0; i < a.st.classes.length(); i++) l.add(a.st.classes.optJSONObject(i).optString("name"));
        return l;
    }

    /** A class chooser in the page (first entry = all classes); calls back only when the choice changes. */
    private void classFilter(String cur, final Pick p) {
        final List<String> names = new ArrayList<>();
        names.add("همه کلاس‌ها");
        names.addAll(classNames());
        final int sel = Math.max(0, names.indexOf(cur));
        Spinner s = spin(names, sel);
        final int[] last = {sel};
        s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (pos == last[0]) return;
                last[0] = pos;
                p.on(pos == 0 ? "" : names.get(pos));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        a.root.addView(s, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** Dialog with a message and one button per action (each closes the dialog first). */
    private void actions(String title, String msg, final String[] labels, final Runnable[] acts) {
        final AlertDialog[] d = new AlertDialog[1];
        LinearLayout l = a.form();
        TextView t = new TextView(a);
        t.setText(msg);
        t.setTextSize(15);
        t.setTextIsSelectable(true);
        t.setPadding(a.dp(4), a.dp(4), a.dp(4), a.dp(8));
        l.addView(t);
        for (int i = 0; i < labels.length; i++) {
            final Runnable r = acts[i];
            Button b = new Button(a);
            b.setText(labels[i]);
            b.setAllCaps(false);
            b.setOnClickListener(x -> {
                d[0].dismiss();
                r.run();
            });
            l.addView(b);
        }
        d[0] = new AlertDialog.Builder(a).setTitle(title).setView(a.scroll(l)).setNegativeButton("بستن", null).create();
        d[0].show();
    }

    /** A form dialog with an optional extra (e.g. delete) button on the left. Save stays closed-on-valid only. */
    private void dialog(String title, View content, String neutral, final Runnable neutralAct, final MainActivity.Submit s) {
        AlertDialog.Builder b = new AlertDialog.Builder(a).setTitle(title).setView(a.scroll(content))
                .setPositiveButton("ذخیره", null).setNegativeButton("لغو", null);
        if (neutral != null) b.setNeutralButton(neutral, null);
        final AlertDialog d = b.create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (s.go()) d.dismiss();
        });
        if (neutral != null) {
            d.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                d.dismiss();
                neutralAct.run();
            });
        }
    }

    private void sendText(String text) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, Reports.plain(text));
        a.startActivity(Intent.createChooser(i, "ارسال"));
    }

    /** Valid Jalali date typed by the user -> "yyyy/MM/dd", else null. */
    private static String okDate(String s) {
        Matcher m = DATE.matcher(Text.digits(s == null ? "" : s.trim()));
        if (!m.matches()) return null;
        int y = Integer.parseInt(m.group(1)), mo = Integer.parseInt(m.group(2)), d = Integer.parseInt(m.group(3));
        if (y < 1200 || y > 1700 || mo < 1 || mo > 12 || d < 1 || d > Cal.monthLen(y, mo)) return null;
        return JalaliDate.fmt(y, mo, d);
    }

    private static String val(EditText e) {
        return e.getText().toString().trim();
    }

    private static void put(JSONObject o, String k, Object v) {
        try {
            o.put(k, v);
        } catch (JSONException ignored) {
            // keys are valid
        }
    }

    private boolean mgr() {
        return a.st.manager();
    }

    // =====================================================================
    // Calendar
    // =====================================================================

    private int ky, km;
    private boolean kInit;
    private String kc = "";
    private String sc = ""; // class shown on the sessions page

    private void initMonth() {
        if (!kInit) {
            int[] j = Cal.jal(JalaliDate.today());
            ky = j[0];
            km = j[1];
            kInit = true;
        }
    }

    private void moveMonth(int d) {
        km += d;
        if (km > 12) {
            km = 1;
            ky++;
        }
        if (km < 1) {
            km = 12;
            ky--;
        }
        month();
    }

    void month() {
        initMonth();
        final int jy = ky, jm = km;
        a.base("تقویم آموزشی", a::home);
        LinearLayout nav = hrow();
        hbtn(nav, "▶ قبلی", x -> moveMonth(-1));
        TextView t = new TextView(a);
        t.setText(Cal.MONTHS[jm - 1] + " " + jy);
        t.setTextSize(18);
        t.setGravity(Gravity.CENTER);
        nav.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));
        hbtn(nav, "بعدی ◀", x -> moveMonth(1));
        LinearLayout r2 = hrow();
        hbtn(r2, "امروز", x -> {
            kInit = false;
            month();
        });
        hbtn(r2, "📚 جلسات کلاس‌ها", x -> sessionsPage(kc));
        if (mgr()) hbtn(r2, "➕ رویداد", x -> eventDialog(null, JalaliDate.fmt(jy, jm, 1), kc));
        classFilter(kc, v -> {
            kc = v;
            month();
        });

        final long first = Cal.epoch(jy, jm, 1), today = JalaliDate.today();
        int off = Cal.wd(first), n = Cal.monthLen(jy, jm);
        LinearLayout hdr = hrow();
        for (int i = 0; i < 7; i++) {
            TextView h = new TextView(a);
            h.setText(Cal.DAYS[i].substring(0, 1));
            h.setGravity(Gravity.CENTER);
            h.setTextSize(13);
            h.setTextColor(i == 6 ? RED : Color.rgb(60, 70, 100));
            hdr.addView(h, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
        int cells = off + n;
        for (int w = 0; w * 7 < cells; w++) {
            LinearLayout row = hrow();
            for (int c = 0; c < 7; c++) {
                int idx = w * 7 + c, day = idx - off + 1;
                TextView cell = new TextView(a);
                cell.setGravity(Gravity.CENTER);
                cell.setTextSize(12);
                cell.setMinHeight(a.dp(62));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                lp.setMargins(a.dp(1), a.dp(1), a.dp(1), a.dp(1));
                if (day < 1 || day > n) {
                    row.addView(cell, lp);
                    continue;
                }
                final long e = first + day - 1;
                String hol = Cal.holiday(a.st.events, e);
                int[] cnt = Cal.counts(a.st.classes, a.st.events, e, kc);
                StringBuilder b = new StringBuilder(String.valueOf(day));
                if (cnt[0] > 0) b.append("\n").append(cnt[0]).append(" کلاس");
                if (cnt[1] > 0) b.append("\n📝").append(cnt[1]);
                if (mgr() && !Fin.dueLines(a.st, e).isEmpty()) b.append("\n💰");
                cell.setText(b.toString());
                boolean red = c == 6 || hol != null;
                cell.setTextColor(red ? RED : Color.rgb(30, 40, 70));
                cell.setBackgroundColor(e == today ? Color.rgb(200, 220, 255) : hol != null ? Color.rgb(253, 232, 232) : Color.rgb(240, 242, 248));
                cell.setOnClickListener(x -> dayPage(e));
                row.addView(cell, lp);
            }
        }
        TextView note = a.text("🔴 تعطیل (مناسبت‌های قمری با محاسبهٔ تقریبی هستند و ممکن است یک روز اختلاف داشته باشند؛ مدیر می‌تواند تعطیلی را حذف یا اضافه کند) • «N کلاس» = تعداد کلاس‌های همان روز • 📝 = آزمون/رویداد", 12);
        note.setTextColor(Color.rgb(100, 108, 130));
        a.exportButtons("calendar-" + jy + "-" + (jm < 10 ? "0" : "") + jm, () -> Cal.monthText(a.st.classes, a.st.events, jy, jm, kc));
        a.imageButtons("calendar-" + jy + "-" + (jm < 10 ? "0" : "") + jm, () -> Cal.monthText(a.st.classes, a.st.events, jy, jm, kc));
        boolean any = false;
        for (int d = 1; d <= n; d++) {
            final long e = first + d - 1;
            String h = Cal.holiday(a.st.events, e);
            StringBuilder b = new StringBuilder();
            for (Cal.Item it : Cal.day(a.st.classes, a.st.events, e, kc, false).items) {
                if ("event".equals(it.kind)) b.append(Cal.line(it)).append("\n");
            }
            if (h == null && b.length() == 0) continue;
            if (!any) {
                a.text("رویدادها و تعطیلی‌های این ماه", 16);
                any = true;
            }
            a.row2(d + " " + Cal.MONTHS[jm - 1] + (h != null ? "  🔴 " + h : ""), b.toString().trim(), x -> dayPage(e));
        }
        if (!any) a.text("رویداد یا تعطیلی خاصی در این ماه ثبت نشده است.", 14);
        a.backBtn();
    }

    void dayPage(final long e) {
        final int[] j = Cal.jal(e);
        ky = j[0];
        km = j[1];
        kInit = true;
        final boolean mgr = mgr();
        final Cal.Day dy = Cal.day(a.st.classes, a.st.events, e, kc);
        final String ds = Cal.fmt(e);
        a.base("روز", this::month);
        LinearLayout nav = hrow();
        hbtn(nav, "▶ روز قبل", x -> dayPage(e - 1));
        hbtn(nav, "روز بعد ◀", x -> dayPage(e + 1));
        TextView h = a.text(Cal.dayHeader(e), 18);
        h.setGravity(Gravity.CENTER);
        JSONObject cust = null, hid = null;
        for (JSONObject o : Cal.eventsOn(a.st.events, e)) {
            if (Cal.HOLIDAY.equals(o.optString("type"))) cust = o;
            if (Cal.HIDE_HOLIDAY.equals(o.optString("type"))) hid = o;
        }
        final JSONObject fcust = cust, fhid = hid;
        String bi = Cal.builtinHoliday(e);
        if (dy.holiday != null) {
            a.text("🔴 تعطیل: " + dy.holiday, 17).setTextColor(RED);
            if (mgr && fcust != null) {
                a.btn("حذف این تعطیلی", x -> {
                    a.st.removeEvent(fcust.optString("id"));
                    dayPage(e);
                });
            }
        }
        if (mgr && fhid != null && bi != null) {
            a.text("تعطیلی رسمی «" + bi + "» برای این روز حذف شده است.", 14);
            a.btn("↩️ بازگرداندن تعطیلی رسمی", x -> {
                a.st.removeEvent(fhid.optString("id"));
                dayPage(e);
            });
        }
        classFilter(kc, v -> {
            kc = v;
            dayPage(e);
        });
        if (dy.items.isEmpty()) a.text("جلسه یا رویدادی در این روز نیست.", 14);
        for (final Cal.Item it : dy.items) {
            boolean les = "session".equals(it.kind) || "makeup".equals(it.kind);
            String sub = "";
            if (les) {
                JSONObject l = Cal.lesson(a.st.events, it.cls, ds);
                String nt = l == null ? "" : l.optString("note").trim();
                sub = nt.isEmpty() ? "✏️ برنامهٔ درسی را بنویسید" : "📖 " + nt;
            }
            a.row2(Cal.line(it), sub, x -> itemClick(it, e));
        }
        if (mgr) {
            for (String l : Fin.dueLines(a.st, e)) a.row2(l, "", x -> {
            });
            a.btn("➕ رویداد", x -> eventDialog(null, ds, kc));
            a.btn("⛔ لغو جلسهٔ کلاس", x -> cancelDialog(e));
            a.btn("🔴 تعطیل کردن این روز", x -> holidayDialog(e));
            if (fcust == null && fhid == null && bi != null) {
                a.btn("🚫 حذف تعطیلی رسمی این روز", x -> {
                    JSONObject o = new JSONObject();
                    put(o, "type", Cal.HIDE_HOLIDAY);
                    put(o, "date", ds);
                    a.st.putEvent(o);
                    dayPage(e);
                });
            }
        }
        a.exportButtons("calendar-" + ds.replace('/', '-'), () -> Cal.dayText(a.st.classes, a.st.events, e, kc));
        a.imageButtons("calendar-" + ds.replace('/', '-'), () -> Cal.dayText(a.st.classes, a.st.events, e, kc));
        a.backBtn();
    }

    private void itemClick(final Cal.Item it, final long e) {
        final String ds = Cal.fmt(e);
        final boolean mgr = mgr();
        if ("session".equals(it.kind)) {
            lessonDialog(it.cls, ds, () -> dayPage(e));
            return;
        }
        if ("makeup".equals(it.kind)) {
            if (mgr && it.ev != null) {
                actions("جلسهٔ جبرانی — " + it.cls, "می‌توانید برنامهٔ درسی را بنویسید یا جلسهٔ جبرانی را ویرایش کنید.",
                        new String[]{"📖 برنامهٔ درسی", "ویرایش"},
                        new Runnable[]{() -> lessonDialog(it.cls, ds, () -> dayPage(e)), () -> eventDialog(it.ev, ds, kc)});
            } else {
                lessonDialog(it.cls, ds, () -> dayPage(e));
            }
            return;
        }
        if ("cancelled".equals(it.kind)) {
            if (!mgr) {
                a.info("جلسه برگزار نمی‌شود", Cal.line(it));
                return;
            }
            if (it.ev != null) {
                final boolean hol = Cal.HOLIDAY.equals(it.ev.optString("type"));
                actions("جلسهٔ لغو شده", Cal.line(it), new String[]{hol ? "حذف این تعطیلی (همهٔ کلاس‌ها)" : "↩️ بازگرداندن جلسه"},
                        new Runnable[]{() -> {
                            a.st.removeEvent(it.ev.optString("id"));
                            dayPage(e);
                        }});
            } else {
                actions("جلسهٔ لغو شده", Cal.line(it), new String[]{"🚫 حذف تعطیلی رسمی این روز"}, new Runnable[]{() -> {
                    JSONObject o = new JSONObject();
                    put(o, "type", Cal.HIDE_HOLIDAY);
                    put(o, "date", ds);
                    a.st.putEvent(o);
                    dayPage(e);
                }});
            }
            return;
        }
        if (mgr && it.ev != null) eventDialog(it.ev, ds, kc);
        else a.info(Cal.typeLabel(it.type), Cal.line(it));
    }

    /** Lesson plan of one class session: manager and teachers can write it. */
    private void lessonDialog(final String cls, final String date, final Runnable after) {
        JSONObject l = Cal.lesson(a.st.events, cls, date);
        final EditText note = a.edit("برنامهٔ درسی این جلسه (خالی = پاک شود)", l == null ? "" : l.optString("note"), true);
        note.setMinLines(6);
        LinearLayout f = a.form();
        f.addView(note);
        a.formDialog("برنامهٔ درسی — " + cls + " — " + date, f, () -> {
            a.st.setLesson(cls, date, note.getText().toString());
            after.run();
            return true;
        });
    }

    private void eventDialog(final JSONObject ev, String ds, String cls0) {
        final JSONObject o = ev == null ? new JSONObject() : ev;
        final List<String> types = new ArrayList<>(), typeIds = new ArrayList<>();
        for (String t : Cal.ADD_TYPES) {
            types.add(Cal.typeIcon(t) + " " + Cal.typeLabel(t));
            typeIds.add(t);
        }
        final Spinner type = spin(types, Math.max(0, typeIds.indexOf(o.optString("type", Cal.EXAM))));
        final EditText title = a.edit("عنوان", o.optString("title"), false);
        final List<String> cls = new ArrayList<>();
        cls.add("همهٔ کلاس‌ها");
        cls.addAll(classNames());
        String cur = ev != null ? o.optString("cls") : cls0;
        if (!cur.isEmpty() && !cls.contains(cur)) cls.add(cur);
        final Spinner cs = spin(cls, Math.max(0, cls.indexOf(cur)));
        final EditText time = a.edit("ساعت (اختیاری)", o.optString("time"), false);
        final EditText date = a.edit("تاریخ (مثلاً 1405/07/13)", o.optString("date", ds), false);
        final EditText note = a.edit("توضیح", o.optString("note"), true);
        note.setMinLines(2);
        LinearLayout f = a.form();
        f.addView(label("نوع"));
        f.addView(type);
        f.addView(title);
        f.addView(label("کلاس (برای تعطیلی، همهٔ کلاس‌ها)"));
        f.addView(cs);
        f.addView(time);
        f.addView(date);
        f.addView(note);
        final String evId = ev == null ? null : ev.optString("id");
        dialog(ev == null ? "رویداد جدید" : "ویرایش رویداد", f, ev == null ? null : "حذف", () -> a.confirm("حذف رویداد", "این رویداد حذف شود؟", "حذف", () -> {
            a.st.removeEvent(evId);
            refresh();
        }), () -> {
            String t = typeIds.get(type.getSelectedItemPosition());
            String d = okDate(val(date));
            String c = cs.getSelectedItemPosition() == 0 ? "" : cls.get(cs.getSelectedItemPosition());
            if (d == null) {
                a.toast("تاریخ معتبر نیست (مثلاً 1405/07/13)");
                return false;
            }
            if (Cal.MAKEUP.equals(t) && c.isEmpty()) {
                a.toast("برای جلسهٔ جبرانی یک کلاس انتخاب کنید");
                return false;
            }
            if ((Cal.EXAM.equals(t) || Cal.EVENT.equals(t) || Cal.HOLIDAY.equals(t)) && val(title).isEmpty()) {
                a.toast("عنوان را بنویسید");
                return false;
            }
            JSONObject r;
            try {
                r = new JSONObject(o.toString());
            } catch (JSONException e) {
                r = new JSONObject();
            }
            put(r, "type", t);
            put(r, "title", val(title));
            put(r, "cls", Cal.HOLIDAY.equals(t) ? "" : c);
            put(r, "time", val(time));
            put(r, "date", d);
            put(r, "note", val(note));
            a.st.putEvent(r);
            refresh();
            return true;
        });
    }

    /** Re-draws the current calendar page (day page when a day is open, otherwise the month). */
    private void refresh() {
        month();
    }

    private void cancelDialog(final long e) {
        final List<String> cs = new ArrayList<>();
        cs.add("همهٔ کلاس‌ها");
        for (int i = 0; i < a.st.classes.length(); i++) {
            JSONObject c = a.st.classes.optJSONObject(i);
            if (Cal.classDay(c, e)) cs.add(c.optString("name"));
        }
        final Spinner sp = spin(cs, 0);
        final EditText why = a.edit("دلیل (اختیاری)", "", false);
        LinearLayout f = a.form();
        f.addView(label("کلاس"));
        f.addView(sp);
        f.addView(why);
        a.formDialog("لغو جلسه — " + Cal.fmt(e), f, () -> {
            JSONObject o = new JSONObject();
            put(o, "type", Cal.OFF);
            put(o, "date", Cal.fmt(e));
            put(o, "cls", sp.getSelectedItemPosition() == 0 ? "" : cs.get(sp.getSelectedItemPosition()));
            put(o, "title", val(why));
            a.st.putEvent(o);
            dayPage(e);
            return true;
        });
    }

    private void holidayDialog(final long e) {
        final EditText title = a.edit("عنوان تعطیلی", "تعطیل", false);
        LinearLayout f = a.form();
        f.addView(title);
        a.formDialog("تعطیل کردن " + Cal.fmt(e), f, () -> {
            JSONObject o = new JSONObject();
            put(o, "type", Cal.HOLIDAY);
            put(o, "date", Cal.fmt(e));
            put(o, "title", val(title).isEmpty() ? "تعطیل" : val(title));
            a.st.putEvent(o);
            dayPage(e);
            return true;
        });
    }

    void sessionsPage(String cls) {
        sc = cls == null ? "" : cls;
        a.base("جلسات کلاس‌ها", this::month);
        final List<String> names = new ArrayList<>();
        names.add("— انتخاب کنید —");
        names.addAll(classNames());
        final int sel = Math.max(0, names.indexOf(sc));
        Spinner s = spin(names, sel);
        s.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (pos == sel) return;
                sessionsPage(pos == 0 ? "" : names.get(pos));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        a.root.addView(s, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        JSONObject c = null;
        for (int i = 0; i < a.st.classes.length(); i++) {
            if (a.st.classes.optJSONObject(i).optString("name").equals(sc)) c = a.st.classes.optJSONObject(i);
        }
        if (c == null) {
            a.text(a.st.classes.length() == 0 ? "هنوز کلاسی ثبت نشده است." : "یک کلاس انتخاب کنید.", 14);
            a.backBtn();
            return;
        }
        final JSONObject cl = c;
        long st0 = Cal.parse(c.optString("start")), en = Cal.parse(c.optString("end"));
        if (st0 == JalaliDate.INVALID) st0 = JalaliDate.today();
        if (en == JalaliDate.INVALID) en = st0 + 120;
        List<long[]> list = Cal.sessions(c, a.st.events, st0, en);
        a.text("ساعت: " + (c.optString("time").isEmpty() ? "—" : c.optString("time")) + " • روزها: " + (c.optString("days").isEmpty() ? "—" : c.optString("days")) + " • " + list.size() + " جلسه", 14);
        a.exportButtons("sessions-" + sc.replace(' ', '_'), () -> Cal.classSessionsText(cl, a.st.events));
        a.imageButtons("sessions-" + sc.replace(' ', '_'), () -> Cal.classSessionsText(cl, a.st.events));
        for (final long[] x : list) {
            JSONObject l = Cal.lesson(a.st.events, sc, Cal.fmt(x[0]));
            String nt = l == null ? "" : l.optString("note").trim();
            a.row2("جلسه " + x[1] + " — " + Cal.dayHeader(x[0]), nt.isEmpty() ? "برنامهٔ درسی ثبت نشده" : "📖 " + nt,
                    v -> lessonDialog(sc, Cal.fmt(x[0]), () -> sessionsPage(sc)));
        }
        a.backBtn();
    }

    // =====================================================================
    // Finance (manager mode only)
    // =====================================================================

    private int fy, fm;
    private boolean fInit, allTime = true;
    private String fc = "", fq = "";

    private void initFinMonth() {
        if (!fInit) {
            int[] j = Cal.jal(JalaliDate.today());
            fy = j[0];
            fm = j[1];
            fInit = true;
        }
    }

    private void moveFin(int d, Runnable redraw) {
        initFinMonth();
        fm += d;
        if (fm > 12) {
            fm = 1;
            fy++;
        }
        if (fm < 1) {
            fm = 12;
            fy--;
        }
        redraw.run();
    }

    private boolean needManager() {
        if (mgr()) return false;
        a.toast("بخش مالی فقط در «حالت مدیر» (تنظیمات) در دسترس است");
        a.home();
        return true;
    }

    private String stLabel(Fin.Acc x) {
        return Fin.name(x.student) + (x.cls.isEmpty() ? "" : " • " + x.cls);
    }

    void finHome() {
        if (needManager()) return;
        initFinMonth();
        int[] j = Cal.jal(JalaliDate.today());
        long[] m = Fin.month(a.st, j[0], j[1]);
        long tot = 0;
        List<Fin.Acc> db = Fin.debtors(a.st, "");
        for (Fin.Acc x : db) tot += x.balance;
        a.base("مالی", a::home);
        a.text("📊 " + Cal.MONTHS[j[1] - 1] + " " + j[0] + "\nدرآمد شهریه: " + Fin.toman(m[0]) + "\nدرآمد دیگر: " + Fin.toman(m[1])
                + "\nهزینه‌ها: " + Fin.toman(m[2]) + "\nحقوق معلم‌ها: " + Fin.toman(m[3])
                + "\n" + (m[4] >= 0 ? "سود خالص: " : "زیان: ") + Fin.toman(Math.abs(m[4])), 16);
        a.text("⏳ مجموع بدهی زبان‌آموزان: " + Fin.toman(tot) + "\n" + db.size() + " بدهکار", 16);
        a.btn("💳 ثبت پرداخت", x -> paymentDialog(null, null, this::finHome));
        a.btn("👥 حساب زبان‌آموزان", x -> accountsPage());
        a.btn("⏳ بدهکاران", x -> debtorsPage());
        a.btn("🏷 شهریهٔ کلاس‌ها", x -> feesPage());
        a.btn("👩‍🏫 حقوق معلم‌ها", x -> teachersPage());
        a.btn("🧾 درآمد و هزینه‌ها", x -> ledgerPage());
        a.btn("📈 گزارش ماهانه", x -> monthFinPage());
        a.btn("💾 پشتیبان مالی", x -> finBackupPage());
        TextView n = a.text("مبالغ به تومان است. اطلاعات مالی فقط روی همین گوشی می‌ماند و در پشتیبان معمولی نیست؛ برای آن «پشتیبان مالی» جدا بگیرید.", 12);
        n.setTextColor(Color.rgb(100, 108, 130));
        a.backBtn();
    }

    void debtorsPage() {
        a.base("بدهکاران", this::finHome);
        classFilter(fc, v -> {
            fc = v;
            debtorsPage();
        });
        final String cf = fc;
        a.exportButtons("debtors", () -> Fin.debtorsText(a.st, cf));
        a.imageButtons("debtors", () -> Fin.debtorsText(a.st, cf));
        List<Fin.Acc> l = Fin.debtors(a.st, fc);
        if (l.isEmpty()) a.text("بدهکاری وجود ندارد.", 14);
        for (final Fin.Acc x : l) {
            a.row2(stLabel(x), "بدهی: " + Fin.toman(x.balance) + "  (برای حساب و یادآوری بزنید)", v -> actions(Fin.name(x.student), "بدهی: " + Fin.toman(x.balance),
                    new String[]{"حساب مالی", "📨 ارسال یادآوری"},
                    new Runnable[]{() -> accountPage(x.code), () -> sendText(Fin.reminderText(a.st, x.student))}));
        }
        a.backBtn();
    }

    void accountsPage() {
        a.base("حساب زبان‌آموزان", this::finHome);
        final EditText q = a.edit("🔎 جستجو", fq, false);
        a.root.addView(q, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        a.root.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        q.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int st, int c, int af) {
            }

            @Override
            public void onTextChanged(CharSequence s, int st, int b, int c) {
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
                fq = s.toString();
                fillAccounts(box);
            }
        });
        fillAccounts(box);
        a.backBtn();
    }

    private void fillAccounts(LinearLayout box) {
        box.removeAllViews();
        String q = Text.norm(fq);
        boolean any = false;
        LinearLayout saved = a.root;
        a.root = box;
        for (int i = 0; i < a.st.students.length(); i++) {
            JSONObject s = a.st.students.optJSONObject(i);
            if (!Text.norm(s.optString("first") + " " + s.optString("last")).contains(q)) continue;
            final Fin.Acc x = Fin.acc(a.st, s);
            any = true;
            String bal = x.balance > 0 ? "بدهی " + Fin.fmt(x.balance) : x.balance < 0 ? "بستانکار " + Fin.fmt(-x.balance) : "تسویه";
            a.row2(stLabel(x), bal, v -> accountPage(x.code));
        }
        if (!any) a.text("زبان‌آموزی نیست.", 14);
        a.root = saved;
    }

    void accountPage(final String code) {
        final JSONObject s = a.st.student(code);
        if (s == null) {
            accountsPage();
            return;
        }
        Fin.Acc x = Fin.acc(a.st, s);
        List<Fin.Inst> ins = Fin.installments(a.st, code, x.paid, JalaliDate.today());
        List<JSONObject> pays = Fin.recsOf(a.st, "payments", code);
        Fin.sortByDate(pays);
        a.base("حساب مالی", this::accountsPage);
        StringBuilder b = new StringBuilder(stLabel(x)).append("\n");
        if ("session".equals(x.mode)) b.append("شهریهٔ هر جلسه: ").append(Fin.toman(x.unit)).append(" × ").append(x.sessions).append(" جلسه\n");
        b.append("شهریه: ").append(Fin.toman(x.base)).append("\n");
        if (x.discount > 0) b.append("تخفیف: ").append(Fin.toman(x.discount)).append("\n");
        b.append("مبلغ قابل پرداخت: ").append(Fin.toman(x.charge)).append("\nپرداخت شده: ").append(Fin.toman(x.paid)).append("\n");
        b.append(x.balance >= 0 ? "مانده بدهی: " : "بستانکار: ").append(Fin.toman(Math.abs(x.balance)));
        if (x.cls.isEmpty()) b.append("\nاین زبان‌آموز در هیچ کلاسی نیست؛ شهریهٔ شخصی را تعیین کنید.");
        TextView t = a.text(b.toString(), 16);
        t.setTextColor(x.balance > 0 ? RED : Color.BLACK);
        a.btn("💳 ثبت پرداخت", v -> paymentDialog(code, null, () -> accountPage(code)));
        a.btn("✏️ شهریهٔ شخصی و تخفیف", v -> personalFeeDialog(code));
        a.btn("➕ افزودن قسط", v -> installDialog(code));
        a.btn("📨 ارسال یادآوری", v -> sendText(Fin.reminderText(a.st, s)));
        a.exportButtons("statement-" + code, () -> Fin.studentText(a.st, s));
        a.imageButtons("statement-" + code, () -> Fin.studentText(a.st, s));
        a.text("پرداخت‌ها", 16);
        if (pays.isEmpty()) a.text("پرداختی ثبت نشده است.", 14);
        for (final JSONObject p : pays) {
            a.row2(p.optString("date") + " — " + Fin.toman(p.optLong("amount")), Fin.methodLabel(p.optString("method")) + (p.optString("note").isEmpty() ? "" : " • " + p.optString("note")) + "  🧾",
                    v -> receiptDialog(p.optString("id"), () -> accountPage(code)));
        }
        a.text("اقساط", 16);
        if (ins.isEmpty()) a.text("قسطی تعریف نشده است.", 14);
        for (final Fin.Inst i : ins) {
            a.row2(i.rec.optString("date") + " — " + Fin.toman(i.amount), Fin.statusLabel(i.status), v -> actions("قسط", i.rec.optString("date") + " — " + Fin.toman(i.amount) + "\n" + Fin.statusLabel(i.status),
                    new String[]{"حذف قسط"}, new Runnable[]{() -> {
                        Fin.delete(a.st.fin, "installs", i.rec.optString("id"));
                        a.st.saveFin();
                        accountPage(code);
                    }}));
        }
        a.backBtn();
    }

    private List<String> studentLabels(List<String> codes) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < a.st.students.length(); i++) {
            JSONObject s = a.st.students.optJSONObject(i);
            codes.add(s.optString("code"));
            String c = a.st.classNameOf(s.optString("code"));
            out.add(Fin.name(s) + (c.isEmpty() ? "" : " — " + c));
        }
        return out;
    }

    /** New or edited payment. code: preselected student (may be null). */
    private void paymentDialog(String code, final String id, final Runnable after) {
        if (a.st.students.length() == 0) {
            a.toast("ابتدا زبان‌آموز ثبت کنید");
            return;
        }
        final JSONObject o = id == null ? new JSONObject() : Fin.find(a.st.fin, "payments", id);
        if (o == null) return;
        final List<String> codes = new ArrayList<>();
        List<String> labels = studentLabels(codes);
        String cur = code != null ? code : o.optString("code");
        final Spinner sp = spin(labels, Math.max(0, codes.indexOf(cur)));
        final EditText date = a.edit("تاریخ (مثلاً 1405/07/13)", o.optString("date", JalaliDate.todayJalali()), false);
        final EditText amount = a.edit("مبلغ (تومان)", o.optLong("amount") > 0 ? Fin.fmt(o.optLong("amount")) : "", false);
        amount.setInputType(android.text.InputType.TYPE_CLASS_TEXT);
        final List<String> ml = new ArrayList<>(), mid = new ArrayList<>();
        for (String m : Fin.METHODS) {
            mid.add(m);
            ml.add(Fin.methodLabel(m));
        }
        final Spinner method = spin(ml, Math.max(0, mid.indexOf(o.optString("method", "cash"))));
        final EditText note = a.edit("توضیح (اختیاری)", o.optString("note"), false);
        LinearLayout f = a.form();
        f.addView(label("زبان‌آموز"));
        f.addView(sp);
        f.addView(date);
        f.addView(amount);
        f.addView(label("روش پرداخت"));
        f.addView(method);
        f.addView(note);
        a.formDialog(id == null ? "ثبت پرداخت" : "ویرایش پرداخت", f, () -> {
            String d = okDate(val(date));
            long am = Fin.money(val(amount));
            JSONObject s = a.st.student(codes.get(sp.getSelectedItemPosition()));
            if (s == null) {
                a.toast("زبان‌آموز را انتخاب کنید");
                return false;
            }
            if (d == null) {
                a.toast("تاریخ معتبر نیست");
                return false;
            }
            if (am <= 0) {
                a.toast("مبلغ را درست وارد کنید");
                return false;
            }
            JSONObject r;
            try {
                r = new JSONObject(o.toString());
            } catch (JSONException e) {
                r = new JSONObject();
            }
            put(r, "code", s.optString("code"));
            put(r, "name", Fin.name(s));
            put(r, "date", d);
            put(r, "amount", am);
            put(r, "method", mid.get(method.getSelectedItemPosition()));
            put(r, "note", val(note));
            final JSONObject saved = Fin.upsert(a.st.fin, "payments", r, "P");
            a.st.saveFin();
            after.run();
            if (id == null) receiptDialog(saved.optString("id"), after);
            return true;
        });
    }

    private void receiptDialog(final String id, final Runnable after) {
        final JSONObject p = Fin.find(a.st.fin, "payments", id);
        if (p == null) return;
        final String text = Fin.receiptText(a.st, p);
        actions("رسید پرداخت", text, new String[]{"📨 ارسال", "🖼 ارسال به‌صورت تصویر", "ویرایش", "حذف"}, new Runnable[]{
                () -> sendText(text),
                () -> a.shareImage(text),
                () -> paymentDialog(null, id, after),
                () -> a.confirm("حذف پرداخت", "این پرداخت حذف شود؟", "حذف", () -> {
                    Fin.delete(a.st.fin, "payments", id);
                    a.st.saveFin();
                    after.run();
                })});
    }

    private void personalFeeDialog(final String code) {
        final JSONObject s = a.st.student(code);
        if (s == null) return;
        JSONObject ac = Fin.account(a.st.fin, code);
        Fin.Acc rule = Fin.acc(a.st, s);
        TextView info = new TextView(a);
        info.setText("شهریهٔ کلاس" + (rule.cls.isEmpty() ? "" : " (" + rule.cls + ")") + ": " + Fin.toman(rule.unit) + ("session".equals(rule.mode) ? " برای هر جلسه" : "")
                + ". اگر شهریهٔ شخصی را خالی بگذارید، همین مبلغ حساب می‌شود.");
        final EditText fee = a.edit("شهریهٔ شخصی (تومان؛ برای کلاس جلسه‌ای = هر جلسه)", ac != null && ac.optLong("fee", -1) >= 0 && ac.has("fee") ? Fin.fmt(ac.optLong("fee")) : "", false);
        final EditText disc = a.edit("تخفیف (تومان)", ac != null && ac.optLong("discount") > 0 ? Fin.fmt(ac.optLong("discount")) : "", false);
        LinearLayout f = a.form();
        f.addView(info);
        f.addView(fee);
        f.addView(disc);
        a.formDialog("شهریهٔ شخصی و تخفیف — " + Fin.name(s), f, () -> {
            String fs = val(fee), ds = val(disc);
            long pf = fs.isEmpty() ? -1 : Fin.money(fs), dc = ds.isEmpty() ? 0 : Fin.money(ds);
            if ((!fs.isEmpty() && pf < 0) || dc < 0) {
                a.toast("مبلغ را درست وارد کنید");
                return false;
            }
            Fin.setAccount(a.st.fin, code, Fin.name(s), pf, dc);
            a.st.saveFin();
            accountPage(code);
            return true;
        });
    }

    private void installDialog(final String code) {
        final JSONObject s = a.st.student(code);
        if (s == null) return;
        long left = Math.max(0, Fin.acc(a.st, s).balance);
        TextView info = new TextView(a);
        info.setText("مبلغ کل بین اقساط مساوی تقسیم می‌شود (باقی‌مانده به قسط آخر اضافه می‌شود).");
        final EditText total = a.edit("مبلغ کل اقساط (تومان)", left > 0 ? Fin.fmt(left) : "", false);
        final EditText n = a.edit("تعداد قسط", "1", false);
        final EditText date = a.edit("تاریخ اولین قسط (مثلاً 1405/08/15)", JalaliDate.todayJalali(), false);
        final EditText gap = a.edit("فاصلهٔ اقساط (روز)", "30", false);
        LinearLayout f = a.form();
        f.addView(info);
        f.addView(total);
        f.addView(n);
        f.addView(date);
        f.addView(gap);
        a.formDialog("افزودن قسط — " + Fin.name(s), f, () -> {
            long tot = Fin.money(val(total));
            int cnt, gp;
            try {
                cnt = Integer.parseInt(Text.digits(val(n)));
                gp = Integer.parseInt(Text.digits(val(gap)));
            } catch (NumberFormatException e) {
                a.toast("تعداد و فاصله را درست وارد کنید");
                return false;
            }
            String d = okDate(val(date));
            if (tot <= 0) {
                a.toast("مبلغ را درست وارد کنید");
                return false;
            }
            if (cnt < 1 || cnt > 36) {
                a.toast("تعداد قسط باید بین ۱ تا ۳۶ باشد");
                return false;
            }
            if (d == null) {
                a.toast("تاریخ معتبر نیست");
                return false;
            }
            if (gp < 1 || gp > 366) {
                a.toast("فاصلهٔ اقساط معتبر نیست");
                return false;
            }
            long base = tot / cnt, e0 = Cal.parse(d);
            for (int k = 0; k < cnt; k++) {
                JSONObject r = new JSONObject();
                put(r, "code", code);
                put(r, "name", Fin.name(s));
                put(r, "date", Cal.fmt(e0 + (long) k * gp));
                put(r, "amount", k == cnt - 1 ? tot - base * (cnt - 1) : base);
                Fin.upsert(a.st.fin, "installs", r, "I");
            }
            a.st.saveFin();
            accountPage(code);
            return true;
        });
    }

    void feesPage() {
        a.base("شهریهٔ کلاس‌ها", this::finHome);
        a.text("شهریهٔ هر کلاس برای کل ترم (یا برای کلاس خصوصی، به‌ازای هر جلسه) تعیین می‌شود. هر زبان‌آموز می‌تواند شهریهٔ شخصی یا تخفیف داشته باشد.", 13);
        if (a.st.classes.length() == 0) a.text("هنوز کلاسی ثبت نشده است.", 14);
        for (int i = 0; i < a.st.classes.length(); i++) {
            final String name = a.st.classes.optJSONObject(i).optString("name");
            JSONObject r = Fin.feeRule(a.st.fin, name);
            a.row2(name, r == null ? "تعیین نشده" : ("session".equals(r.optString("mode")) ? "هر جلسه " : "کل ترم ") + Fin.toman(r.optLong("fee")), v -> classFeeDialog(name));
        }
        a.backBtn();
    }

    private void classFeeDialog(final String cls) {
        JSONObject r = Fin.feeRule(a.st.fin, cls);
        final Spinner mode = spin(java.util.Arrays.asList("کل ترم", "هر جلسه (کلاس خصوصی)"), r != null && "session".equals(r.optString("mode")) ? 1 : 0);
        final EditText fee = a.edit("مبلغ (تومان)", r != null && r.optLong("fee") > 0 ? Fin.fmt(r.optLong("fee")) : "", false);
        LinearLayout f = a.form();
        f.addView(label("نوع"));
        f.addView(mode);
        f.addView(fee);
        a.formDialog("شهریهٔ کلاس " + cls, f, () -> {
            String fs = val(fee);
            long am = fs.isEmpty() ? 0 : Fin.money(fs);
            if (am < 0) {
                a.toast("مبلغ را درست وارد کنید");
                return false;
            }
            Fin.setFee(a.st.fin, cls, mode.getSelectedItemPosition() == 1 ? "session" : "term", am);
            a.st.saveFin();
            feesPage();
            return true;
        });
    }

    void teachersPage() {
        initFinMonth();
        a.base("حقوق معلم‌ها", this::finHome);
        final long from = allTime ? Fin.ALL_FROM : Cal.epoch(fy, fm, 1), to = allTime ? Fin.ALL_TO : Cal.epoch(fy, fm, Cal.monthLen(fy, fm));
        final String lab = allTime ? "" : Cal.MONTHS[fm - 1] + " " + fy;
        LinearLayout r1 = hrow();
        hbtn(r1, allTime ? "✔ کل زمان" : "کل زمان", x -> {
            allTime = true;
            teachersPage();
        });
        hbtn(r1, allTime ? "یک ماه" : "✔ یک ماه", x -> {
            allTime = false;
            teachersPage();
        });
        if (!allTime) {
            LinearLayout r2 = hrow();
            hbtn(r2, "▶", x -> moveFin(-1, this::teachersPage));
            TextView t = new TextView(a);
            t.setText(lab);
            t.setGravity(Gravity.CENTER);
            t.setTextSize(16);
            r2.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.5f));
            hbtn(r2, "◀", x -> moveFin(1, this::teachersPage));
        }
        a.exportButtons("teachers", () -> Fin.teachersText(a.st, from, to, lab));
        a.imageButtons("teachers", () -> Fin.teachersText(a.st, from, to, lab));
        for (final String t : Fin.teachers(a.st)) {
            JSONObject r = Fin.rate(a.st.fin, t);
            int n = Fin.teacherSessions(a.st, t, from, to);
            long ac = Fin.teacherAccrued(a.st, t, from, to), pd = Fin.teacherPaid(a.st, t, from, to);
            a.text(t + " — " + n + " جلسه • " + (r == null ? "نرخ تعیین نشده" : ("month".equals(r.optString("mode")) ? "ماهانه " : "هر جلسه ") + Fin.fmt(r.optLong("amount")))
                    + "\nمحاسبه‌شده: " + Fin.toman(ac) + "\nپرداخت‌شده: " + Fin.toman(pd) + "\nمانده: " + Fin.toman(ac - pd), 15);
            LinearLayout row = hrow();
            hbtn(row, "✏️ نرخ", x -> rateDialog(t));
            hbtn(row, "💵 ثبت پرداخت", x -> salaryDialog(t, null));
        }
        a.text("پرداخت‌های ثبت‌شده به معلم‌ها", 16);
        List<JSONObject> sal = Fin.live(a.st.fin, "salaries");
        Fin.sortByDate(sal);
        if (sal.isEmpty()) a.text("پرداختی ثبت نشده است.", 14);
        for (int i = sal.size() - 1; i >= 0; i--) {
            final JSONObject x = sal.get(i);
            a.row2(x.optString("date") + " — " + x.optString("teacher") + " — " + Fin.toman(x.optLong("amount")), x.optString("note"), v -> actions("پرداخت به معلم",
                    x.optString("date") + " — " + x.optString("teacher") + " — " + Fin.toman(x.optLong("amount")), new String[]{"ویرایش", "حذف"},
                    new Runnable[]{() -> salaryDialog(x.optString("teacher"), x.optString("id")), () -> a.confirm("حذف", "حذف شود؟", "حذف", () -> {
                        Fin.delete(a.st.fin, "salaries", x.optString("id"));
                        a.st.saveFin();
                        teachersPage();
                    })}));
        }
        a.backBtn();
    }

    private void rateDialog(final String t) {
        JSONObject r = Fin.rate(a.st.fin, t);
        final Spinner mode = spin(java.util.Arrays.asList("به‌ازای هر جلسه", "مبلغ ثابت ماهانه"), r != null && "month".equals(r.optString("mode")) ? 1 : 0);
        final EditText amount = a.edit("مبلغ (تومان)", r != null && r.optLong("amount") > 0 ? Fin.fmt(r.optLong("amount")) : "", false);
        LinearLayout f = a.form();
        f.addView(label("نوع"));
        f.addView(mode);
        f.addView(amount);
        a.formDialog("نرخ " + t, f, () -> {
            String s = val(amount);
            long am = s.isEmpty() ? 0 : Fin.money(s);
            if (am < 0) {
                a.toast("مبلغ را درست وارد کنید");
                return false;
            }
            Fin.setRate(a.st.fin, t, mode.getSelectedItemPosition() == 1 ? "month" : "session", am);
            a.st.saveFin();
            teachersPage();
            return true;
        });
    }

    private void salaryDialog(final String t, final String id) {
        final JSONObject o = id == null ? new JSONObject() : Fin.find(a.st.fin, "salaries", id);
        if (o == null) return;
        final EditText date = a.edit("تاریخ (مثلاً 1405/07/13)", o.optString("date", JalaliDate.todayJalali()), false);
        final EditText amount = a.edit("مبلغ (تومان)", o.optLong("amount") > 0 ? Fin.fmt(o.optLong("amount")) : "", false);
        final EditText note = a.edit("توضیح (اختیاری)", o.optString("note"), false);
        LinearLayout f = a.form();
        f.addView(date);
        f.addView(amount);
        f.addView(note);
        a.formDialog(id == null ? "پرداخت به " + t : "ویرایش پرداخت به معلم", f, () -> {
            String d = okDate(val(date));
            long am = Fin.money(val(amount));
            if (d == null) {
                a.toast("تاریخ معتبر نیست");
                return false;
            }
            if (am <= 0) {
                a.toast("مبلغ را درست وارد کنید");
                return false;
            }
            JSONObject r;
            try {
                r = new JSONObject(o.toString());
            } catch (JSONException e) {
                r = new JSONObject();
            }
            if (r.optString("teacher").isEmpty()) put(r, "teacher", t);
            put(r, "date", d);
            put(r, "amount", am);
            put(r, "note", val(note));
            Fin.upsert(a.st.fin, "salaries", r, "W");
            a.st.saveFin();
            teachersPage();
            return true;
        });
    }

    void ledgerPage() {
        a.base("درآمد و هزینه‌ها", this::finHome);
        a.btn("➕ ثبت درآمد / هزینه", x -> ledgerDialog(null));
        List<JSONObject> l = Fin.live(a.st.fin, "ledger");
        Fin.sortByDate(l);
        if (l.isEmpty()) a.text("موردی ثبت نشده است (مثلاً اجاره، کتاب، چاپ...).", 14);
        for (int i = l.size() - 1; i >= 0; i--) {
            final JSONObject x = l.get(i);
            a.row2(("income".equals(x.optString("kind")) ? "🟢 " : "🔴 ") + x.optString("title") + " — " + Fin.toman(x.optLong("amount")),
                    x.optString("date") + (x.optString("note").isEmpty() ? "" : " • " + x.optString("note")), v -> ledgerDialog(x.optString("id")));
        }
        a.backBtn();
    }

    private void ledgerDialog(final String id) {
        final JSONObject o = id == null ? new JSONObject() : Fin.find(a.st.fin, "ledger", id);
        if (o == null) return;
        final Spinner kind = spin(java.util.Arrays.asList("هزینه", "درآمد"), "income".equals(o.optString("kind")) ? 1 : 0);
        final EditText date = a.edit("تاریخ (مثلاً 1405/07/13)", o.optString("date", JalaliDate.todayJalali()), false);
        final EditText title = a.edit("عنوان (مثلاً اجاره)", o.optString("title"), false);
        final EditText amount = a.edit("مبلغ (تومان)", o.optLong("amount") > 0 ? Fin.fmt(o.optLong("amount")) : "", false);
        final EditText note = a.edit("توضیح (اختیاری)", o.optString("note"), false);
        LinearLayout f = a.form();
        f.addView(label("نوع"));
        f.addView(kind);
        f.addView(date);
        f.addView(title);
        f.addView(amount);
        f.addView(note);
        dialog(id == null ? "درآمد / هزینه" : "ویرایش", f, id == null ? null : "حذف", () -> a.confirm("حذف", "حذف شود؟", "حذف", () -> {
            Fin.delete(a.st.fin, "ledger", id);
            a.st.saveFin();
            ledgerPage();
        }), () -> {
            String d = okDate(val(date));
            long am = Fin.money(val(amount));
            if (d == null) {
                a.toast("تاریخ معتبر نیست");
                return false;
            }
            if (val(title).isEmpty()) {
                a.toast("عنوان را بنویسید");
                return false;
            }
            if (am <= 0) {
                a.toast("مبلغ را درست وارد کنید");
                return false;
            }
            JSONObject r;
            try {
                r = new JSONObject(o.toString());
            } catch (JSONException e) {
                r = new JSONObject();
            }
            put(r, "kind", kind.getSelectedItemPosition() == 1 ? "income" : "expense");
            put(r, "date", d);
            put(r, "title", val(title));
            put(r, "amount", am);
            put(r, "note", val(note));
            Fin.upsert(a.st.fin, "ledger", r, "L");
            a.st.saveFin();
            ledgerPage();
            return true;
        });
    }

    void monthFinPage() {
        initFinMonth();
        final int jy = fy, jm = fm;
        a.base("گزارش ماهانهٔ مالی", this::finHome);
        LinearLayout nav = hrow();
        hbtn(nav, "▶ قبلی", x -> moveFin(-1, this::monthFinPage));
        TextView t = new TextView(a);
        t.setText(Cal.MONTHS[jm - 1] + " " + jy);
        t.setTextSize(18);
        t.setGravity(Gravity.CENTER);
        nav.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.2f));
        hbtn(nav, "بعدی ◀", x -> moveFin(1, this::monthFinPage));
        final String name = "finance-" + jy + "-" + (jm < 10 ? "0" : "") + jm;
        a.exportButtons(name, () -> Fin.monthText(a.st, jy, jm));
        a.imageButtons(name, () -> Fin.monthText(a.st, jy, jm));
        a.text(Fin.monthText(a.st, jy, jm), 15).setTextIsSelectable(true);
        a.backBtn();
    }

    // ---------- separate finance backup ----------

    void finBackupPage() {
        a.base("پشتیبان مالی", this::finHome);
        a.text("اطلاعات مالی (شهریه، پرداخت، قسط، حقوق، هزینه) در پشتیبان معمولی نیست و برای معلم‌ها فرستاده نمی‌شود. این فایل جدا را فقط خودتان نگه دارید. «ادغام» بدون حذف چیزی اطلاعات فایل را اضافه می‌کند؛ نام زبان‌آموزان باید در هر دو دستگاه یکسان باشد.", 14);
        a.btn("💾 ذخیرهٔ پشتیبان مالی", x -> {
            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/json");
            i.putExtra(Intent.EXTRA_TITLE, "class-fin-backup-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".json");
            a.startActivityForResult(i, MainActivity.PICK_FIN_SAVE);
        });
        a.btn("♻️ بازیابی / ادغام پشتیبان مالی", x -> a.pick(MainActivity.PICK_FIN_RESTORE));
        a.backBtn();
    }

    void writeFinBackup(Uri u) throws Exception {
        String payload = Fin.export(a.st);
        try (OutputStream out = a.getContentResolver().openOutputStream(u, "wt")) {
            if (out == null) throw new IOException("امکان نوشتن در فایل نیست");
            out.write(payload.getBytes(StandardCharsets.UTF_8));
        }
        a.toast("پشتیبان مالی ذخیره شد؛ آن را جای امن نگه دارید");
    }

    void restoreFin(Uri u) throws Exception {
        final JSONObject r;
        try {
            r = new JSONObject(a.readText(u));
        } catch (JSONException e) {
            a.toast("فایل پشتیبان معتبر نیست");
            return;
        }
        String err = Fin.validate(r);
        if (err != null) {
            a.toast(err);
            return;
        }
        new AlertDialog.Builder(a).setTitle("بازیابی اطلاعات مالی")
                .setMessage("ادغام: اطلاعات فایل بدون حذف چیزی به داده‌های فعلی اضافه می‌شود.\n\nجایگزینی: اطلاعات مالی فعلی کاملاً با فایل عوض می‌شود.")
                .setPositiveButton("ادغام", (d, w) -> {
                    try {
                        Fin.Result m = Fin.merge(a.st, r);
                        finHome();
                        a.info("نتیجهٔ ادغام", m.summary());
                    } catch (JSONException e) {
                        a.toast("خطا در ادغام");
                    }
                })
                .setNeutralButton("جایگزینی", (d, w) -> a.confirm("جایگزینی کامل", "اطلاعات مالی فعلی حذف و با فایل عوض می‌شود. ادامه؟", "جایگزین", () -> {
                    try {
                        Fin.replace(a.st, r);
                        a.toast("بازیابی انجام شد");
                    } catch (JSONException e) {
                        a.toast("خطا در بازیابی");
                    }
                    finHome();
                }))
                .setNegativeButton("لغو", null).show();
    }
}
