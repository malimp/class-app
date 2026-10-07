package com.example.classapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    static final int PICK_STUDENTS = 10, PICK_CLASSES = 11, PICK_REPORT = 12, PICK_BACKUP = 13, PICK_SAVE_BACKUP = 14;
    static final int MAX_FILE = 20 * 1024 * 1024;

    interface Submit { boolean go(); }

    static final class Period {
        final ArrayList<Integer> idx = new ArrayList<>();
        int fallback, undated;
    }

    Store st;
    LinearLayout root;
    Runnable backAction;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        st = new Store(this);
        home();
    }

    @Override
    public void onBackPressed() {
        if (backAction != null) backAction.run();
        else super.onBackPressed();
    }

    // =====================================================================
    // UI helpers
    // =====================================================================

    int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** Every screen: scrollable, RTL, and padded below/above the system bars (edge-to-edge on Android 15). */
    void base(String t, Runnable back) {
        backAction = back;
        FrameLayout outer = new FrameLayout(this);
        outer.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setClipToPadding(false);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(8), dp(16), dp(24));
        sv.addView(root, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        outer.addView(sv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(outer);
        applyInsets(outer);
        root.addView(title(t));
    }

    void applyInsets(View v) {
        v.setOnApplyWindowInsetsListener((view, in) -> {
            int l, t, r, b;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets i = in.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout() | WindowInsets.Type.ime());
                l = i.left;
                t = i.top;
                r = i.right;
                b = i.bottom;
            } else {
                l = in.getSystemWindowInsetLeft();
                t = in.getSystemWindowInsetTop();
                r = in.getSystemWindowInsetRight();
                b = in.getSystemWindowInsetBottom();
            }
            view.setPadding(l, t, r, b);
            return in;
        });
        v.requestApplyInsets();
    }

    TextView title(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(24);
        t.setTextColor(Color.rgb(30, 50, 90));
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(4), dp(16), dp(4), dp(16));
        return t;
    }

    Button btn(String s, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextSize(16);
        b.setAllCaps(false);
        b.setMinHeight(dp(52));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(4), 0, dp(4));
        root.addView(b, p);
        return b;
    }

    void backBtn() {
        btn("← بازگشت", x -> {
            if (backAction != null) backAction.run();
        });
    }

    TextView text(String s, int size) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setPadding(dp(4), dp(6), dp(4), dp(6));
        root.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return t;
    }

    void row(String s, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(16);
        t.setPadding(dp(12), dp(12), dp(12), dp(12));
        t.setBackgroundColor(Color.rgb(240, 242, 248));
        t.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, dp(3), 0, dp(3));
        root.addView(t, p);
    }

    LinearLayout form() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        l.setPadding(dp(20), dp(8), dp(20), dp(8));
        return l;
    }

    ScrollView scroll(View v) {
        ScrollView sv = new ScrollView(this);
        sv.addView(v);
        return sv;
    }

    EditText edit(String hint, String val, boolean multi) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(val);
        e.setTextSize(16);
        e.setInputType(InputType.TYPE_CLASS_TEXT | (multi ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        if (multi) {
            e.setMinLines(3);
            e.setGravity(Gravity.TOP | Gravity.START);
        }
        return e;
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    void info(String title, String msg) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(msg).setPositiveButton("باشه", null).show();
    }

    void confirm(String title, String msg, String ok, Runnable r) {
        new AlertDialog.Builder(this).setTitle(title).setMessage(msg)
                .setPositiveButton(ok, (d, w) -> r.run()).setNegativeButton("لغو", null).show();
    }

    /** A dialog whose Save button validates first and stays open (keeping the typed data) when invalid. */
    void formDialog(String title, View content, Submit s) {
        final AlertDialog d = new AlertDialog.Builder(this).setTitle(title).setView(scroll(content))
                .setPositiveButton("ذخیره", null).setNegativeButton("لغو", null).create();
        d.show();
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (s.go()) d.dismiss();
        });
    }

    void pick(int req) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, req);
    }

    // =====================================================================
    // Home
    // =====================================================================

    void home() {
        base("کلاس", null);
        TextView v = text("مدیریت ساده آموزشگاه زبان\nنسخه 1.5 • Android", 16);
        v.setGravity(Gravity.CENTER);
        btn("👨‍🎓 زبان‌آموزان", x -> studentsPage());
        btn("🏫 کلاس‌ها", x -> classesPage());
        btn("📝 گزارش روزانه", x -> reportsPage());
        btn("📊 گزارش هفتگی", x -> periodReport(7));
        btn("📅 گزارش ماهانه", x -> periodReport(30));
        btn("⚙️ تنظیمات و پشتیبان‌گیری", x -> settings());
    }

    // =====================================================================
    // Students
    // =====================================================================

    void studentsPage() {
        base("زبان‌آموزان", this::home);
        btn("➕ افزودن دستی", x -> studentDialog(-1));
        btn("📥 ورود گروهی Excel (.xlsx)", x -> pick(PICK_STUDENTS));
        btn("🔎 جستجو", x -> searchStudents());
        backBtn();
        text("ستون‌های Excel: کد | نام | نام خانوادگی | موبایل | کد ملی | تاریخ تولد | توضیحات (ردیف اول عنوان است)", 12);
        text("تعداد: " + st.students.length(), 16);
        for (int i = 0; i < st.students.length(); i++) {
            final int idx = i;
            JSONObject o = st.students.optJSONObject(i);
            String cls = st.classNameOf(o.optString("code"));
            row(o.optString("code") + " - " + o.optString("first") + " " + o.optString("last")
                    + (cls.isEmpty() ? "" : "   [" + cls + "]"), x -> studentActions(idx));
        }
    }

    void studentActions(final int idx) {
        final JSONObject o = st.students.optJSONObject(idx);
        if (o == null) return;
        new AlertDialog.Builder(this).setTitle(o.optString("first") + " " + o.optString("last"))
                .setItems(new String[]{"ویرایش", "حذف"}, (d, w) -> {
                    if (w == 0) studentDialog(idx);
                    else confirm("حذف زبان‌آموز", "آیا مطمئن هستید؟ زبان‌آموز از کلاس خود هم حذف می‌شود.", "حذف", () -> {
                        st.removeStudent(idx);
                        st.save();
                        studentsPage();
                    });
                }).show();
    }

    void studentDialog(final int idx) {
        final JSONObject o = idx >= 0 ? st.students.optJSONObject(idx) : new JSONObject();
        if (o == null) return;
        final String[] keys = {"first", "last", "mobile", "national", "birth", "notes"};
        String[] hints = {"نام", "نام خانوادگی", "شماره موبایل", "کد ملی", "تاریخ تولد", "توضیحات"};
        final EditText[] es = new EditText[keys.length];
        LinearLayout l = form();
        for (int i = 0; i < keys.length; i++) {
            es[i] = edit(hints[i], o.optString(keys[i]), i == 5);
            l.addView(es[i]);
        }
        formDialog(idx >= 0 ? "ویرایش زبان‌آموز" : "افزودن زبان‌آموز", l, () -> {
            final String first = es[0].getText().toString().trim();
            final String last = es[1].getText().toString().trim();
            if (first.isEmpty() || last.isEmpty()) {
                toast("نام و نام خانوادگی الزامی است");
                return false;
            }
            Runnable commit = () -> {
                try {
                    o.put("first", first).put("last", last);
                    for (int k = 2; k < keys.length; k++) o.put(keys[k], es[k].getText().toString().trim());
                    if (o.optString("code").isEmpty()) o.put("code", st.newCode("S", st.students));
                    if (idx < 0) st.students.put(o);
                    st.save();
                    studentsPage();
                } catch (JSONException e) {
                    toast("خطا در ذخیره");
                }
            };
            if (idx < 0 && st.hasFullName(first, last, -1)) {
                confirm("نام تکراری", "زبان‌آموزی با همین نام و نام خانوادگی قبلاً ثبت شده است. باز هم ثبت شود؟", "ثبت", commit);
            } else {
                commit.run();
            }
            return true;
        });
    }

    void searchStudents() {
        final EditText e = edit("نام، نام خانوادگی، کد یا موبایل", "", false);
        LinearLayout l = form();
        l.addView(e);
        new AlertDialog.Builder(this).setTitle("جستجوی زبان‌آموز").setView(l)
                .setPositiveButton("جستجو", (d, w) -> {
                    String q = Text.norm(e.getText().toString());
                    final ArrayList<Integer> hits = new ArrayList<>();
                    for (int i = 0; i < st.students.length(); i++) {
                        JSONObject o = st.students.optJSONObject(i);
                        String hay = Text.norm(o.optString("first") + " " + o.optString("last") + " " + o.optString("code") + " " + o.optString("mobile"));
                        if (hay.contains(q)) hits.add(i);
                    }
                    if (hits.isEmpty()) {
                        info("نتیجه", "موردی پیدا نشد");
                        return;
                    }
                    String[] names = new String[hits.size()];
                    for (int i = 0; i < names.length; i++) {
                        JSONObject o = st.students.optJSONObject(hits.get(i));
                        names[i] = o.optString("first") + " " + o.optString("last") + " — " + o.optString("code");
                    }
                    new AlertDialog.Builder(this).setTitle("نتیجه (" + names.length + ")")
                            .setItems(names, (d2, which) -> studentActions(hits.get(which)))
                            .setNegativeButton("بستن", null).show();
                }).setNegativeButton("لغو", null).show();
    }

    // =====================================================================
    // Classes
    // =====================================================================

    void classesPage() {
        base("کلاس‌ها", this::home);
        btn("➕ افزودن کلاس", x -> classDialog(-1));
        btn("📥 ورود گروهی Excel (.xlsx)", x -> pick(PICK_CLASSES));
        backBtn();
        text("ستون‌های Excel: کد | نام کلاس | سطح | شروع | پایان | روزها | ساعت | توضیحات (ردیف اول عنوان است)", 12);
        text("تعداد کلاس‌ها: " + st.classes.length(), 16);
        for (int i = 0; i < st.classes.length(); i++) {
            final int idx = i;
            JSONObject o = st.classes.optJSONObject(i);
            JSONArray m = o.optJSONArray("students");
            row(o.optString("code") + " - " + o.optString("name") + "  (" + (m == null ? 0 : m.length()) + " نفر)", x -> classPage(idx));
        }
    }

    void classPage(final int ci) {
        final JSONObject c = st.classes.optJSONObject(ci);
        if (c == null) {
            classesPage();
            return;
        }
        base(c.optString("name"), this::classesPage);
        btn("➕ افزودن زبان‌آموز", x -> addMembers(ci));
        btn("✏️ ویرایش کلاس", x -> classDialog(ci));
        btn("🗑️ حذف کلاس", x -> confirm("حذف کلاس", "آیا مطمئن هستید؟ زبان‌آموزان حذف نمی‌شوند و بدون کلاس می‌مانند.", "حذف", () -> {
            st.classes.remove(ci);
            st.save();
            classesPage();
        }));
        backBtn();
        String[] keys = {"level", "start", "end", "days", "time", "notes"};
        String[] labels = {"سطح/دوره", "شروع", "پایان", "روزها", "ساعت", "توضیحات"};
        StringBuilder d = new StringBuilder();
        for (int i = 0; i < keys.length; i++) {
            String v = c.optString(keys[i]);
            if (!v.isEmpty()) d.append(labels[i]).append(": ").append(v).append("\n");
        }
        if (d.length() > 0) text(d.toString().trim(), 15);
        JSONArray m = c.optJSONArray("students");
        text("اعضا: " + (m == null ? 0 : m.length()) + " نفر", 16);
        if (m == null) return;
        for (int i = 0; i < m.length(); i++) {
            JSONObject s = st.student(m.optString(i));
            if (s != null) memberRow(ci, s);
        }
    }

    void memberRow(final int ci, JSONObject s) {
        final String code = s.optString("code");
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(this);
        t.setText(s.optString("first") + " " + s.optString("last"));
        t.setTextSize(16);
        h.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button rm = new Button(this);
        rm.setText("حذف از کلاس");
        rm.setAllCaps(false);
        rm.setTextSize(13);
        rm.setOnClickListener(x -> confirm("حذف از کلاس", "این زبان‌آموز از کلاس خارج شود؟ (از لیست زبان‌آموزان حذف نمی‌شود)", "حذف", () -> {
            st.removeFromClass(ci, code);
            st.save();
            classPage(ci);
        }));
        h.addView(rm, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(h, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** Only students that are not in any class are offered (a student can belong to one class only). */
    void addMembers(final int ci) {
        final ArrayList<JSONObject> free = new ArrayList<>();
        for (int i = 0; i < st.students.length(); i++) {
            JSONObject o = st.students.optJSONObject(i);
            if (st.classIndexOf(o.optString("code")) < 0) free.add(o);
        }
        if (free.isEmpty()) {
            toast("زبان‌آموزِ بدون کلاس وجود ندارد (هر زبان‌آموز فقط در یک کلاس است)");
            return;
        }
        String[] names = new String[free.size()];
        for (int i = 0; i < names.length; i++) {
            JSONObject o = free.get(i);
            names[i] = o.optString("first") + " " + o.optString("last") + " — " + o.optString("code");
        }
        final boolean[] chk = new boolean[names.length];
        new AlertDialog.Builder(this).setTitle("افزودن به کلاس")
                .setMultiChoiceItems(names, chk, (d, w, checked) -> chk[w] = checked)
                .setPositiveButton("افزودن", (d, w) -> {
                    int added = 0;
                    for (int i = 0; i < chk.length; i++) {
                        if (chk[i] && st.addToClass(ci, free.get(i).optString("code"))) added++;
                    }
                    st.save();
                    toast(added + " نفر به کلاس اضافه شد");
                    classPage(ci);
                }).setNegativeButton("لغو", null).show();
    }

    void classDialog(final int idx) {
        final JSONObject o = idx >= 0 ? st.classes.optJSONObject(idx) : new JSONObject();
        if (o == null) return;
        final String[] keys = {"name", "level", "start", "end", "days", "time", "notes"};
        String[] hints = {"نام کلاس", "سطح/دوره", "تاریخ شروع", "تاریخ پایان", "روزهای برگزاری", "ساعت", "توضیحات"};
        final EditText[] es = new EditText[keys.length];
        LinearLayout l = form();
        for (int i = 0; i < keys.length; i++) {
            es[i] = edit(hints[i], o.optString(keys[i]), i == 6);
            l.addView(es[i]);
        }
        formDialog(idx >= 0 ? "ویرایش کلاس" : "افزودن کلاس", l, () -> {
            if (es[0].getText().toString().trim().isEmpty()) {
                toast("نام کلاس الزامی است");
                return false;
            }
            try {
                for (int k = 0; k < keys.length; k++) o.put(keys[k], es[k].getText().toString().trim());
                if (o.optString("code").isEmpty()) o.put("code", st.newCode("C", st.classes));
                if (o.optJSONArray("students") == null) o.put("students", new JSONArray());
                if (idx < 0) st.classes.put(o);
                st.save();
            } catch (JSONException e) {
                toast("خطا در ذخیره");
                return false;
            }
            if (idx >= 0) classPage(idx);
            else classesPage();
            return true;
        });
    }

    // =====================================================================
    // Daily reports
    // =====================================================================

    /** Epoch day of a report: its own date when readable, otherwise the day it was saved; INVALID if neither. */
    long reportDay(JSONObject r) {
        long d = JalaliDate.parseEpochDay(r.optString("date"));
        if (d != JalaliDate.INVALID) return d;
        return createdDay(r);
    }

    long createdDay(JSONObject r) {
        long at = r.optLong("createdAt", 0);
        return at > 0 ? JalaliDate.fromMillis(at) : JalaliDate.INVALID;
    }

    void reportsPage() {
        base("گزارش روزانه", this::home);
        btn("📥 ورود فایل متنی (.txt)", x -> pick(PICK_REPORT));
        btn("✍️ نوشتن گزارش جدید", x -> reportDialog(-1, new JSONObject()));
        backBtn();
        text("گزارش‌ها: " + st.reports.length() + "\nتاریخ هر گزارش (شمسی یا میلادی) مبنای گزارش هفتگی و ماهانه است؛ متن اصلی تغییر نمی‌کند.", 14);
        final ArrayList<Integer> order = new ArrayList<>();
        for (int i = 0; i < st.reports.length(); i++) order.add(i);
        Collections.sort(order, (a, b) -> Long.compare(reportDay(st.reports.optJSONObject(b)), reportDay(st.reports.optJSONObject(a))));
        for (final int i : order) {
            JSONObject o = st.reports.optJSONObject(i);
            String cls = o.optString("class");
            row(o.optString("date") + (cls.isEmpty() ? "" : " — " + cls), x -> showReport(i));
        }
    }

    void showReport(final int i) {
        final JSONObject o = st.reports.optJSONObject(i);
        if (o == null) return;
        new AlertDialog.Builder(this).setTitle(o.optString("date") + " — " + o.optString("class")).setMessage(o.optString("text"))
                .setPositiveButton("ویرایش", (d, w) -> reportDialog(i, o))
                .setNeutralButton("حذف", (d, w) -> confirm("حذف گزارش", "این گزارش حذف شود؟", "حذف", () -> {
                    st.reports.remove(i);
                    st.save();
                    reportsPage();
                }))
                .setNegativeButton("بستن", null).show();
    }

    /** idx >= 0 edits st.reports[idx]; idx < 0 creates a new report from the draft (saved only on Save). */
    void reportDialog(final int idx, final JSONObject draft) {
        final JSONObject o = idx >= 0 ? st.reports.optJSONObject(idx) : draft;
        if (o == null) return;
        String d0 = o.optString("date");
        final EditText date = edit("تاریخ (مثلاً 1405/07/13)", d0.isEmpty() ? JalaliDate.todayJalali() : d0, false);
        final ArrayList<String> opts = new ArrayList<>();
        opts.add("(بدون کلاس)");
        for (int i = 0; i < st.classes.length(); i++) opts.add(st.classes.optJSONObject(i).optString("name"));
        String cur = o.optString("class");
        int sel = 0;
        if (!cur.isEmpty()) {
            sel = opts.indexOf(cur);
            if (sel < 0) {
                opts.add(cur);
                sel = opts.size() - 1;
            }
        }
        final Spinner cls = new Spinner(this);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, opts);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        cls.setAdapter(ad);
        cls.setSelection(sel);
        final EditText body = edit("متن کامل گزارش", o.optString("text"), true);
        body.setMinLines(10);
        LinearLayout l = form();
        l.addView(date);
        l.addView(cls);
        l.addView(body);
        formDialog("گزارش روزانه", l, () -> {
            String text = body.getText().toString();
            if (text.trim().isEmpty()) {
                toast("متن گزارش خالی است");
                return false;
            }
            String ds = date.getText().toString().trim();
            if (ds.isEmpty()) ds = JalaliDate.todayJalali();
            int p = cls.getSelectedItemPosition();
            try {
                o.put("date", ds).put("class", p <= 0 ? "" : opts.get(p)).put("text", text);
                if (o.optLong("createdAt", 0) == 0) o.put("createdAt", System.currentTimeMillis());
                if (idx < 0) st.reports.put(o);
            } catch (JSONException e) {
                toast("خطا در ذخیره");
                return false;
            }
            st.save();
            if (JalaliDate.parseEpochDay(ds) == JalaliDate.INVALID) {
                toast("تاریخ قابل تشخیص نبود؛ در گزارش‌های دوره‌ای بر اساس زمان ثبت حساب می‌شود");
            }
            reportsPage();
            return true;
        });
    }

    // =====================================================================
    // Weekly / monthly reports
    // =====================================================================

    void periodReport(final int days) {
        base(days == 7 ? "گزارش هفتگی" : "گزارش ماهانه", this::home);
        btn("📌 گزارش کلی کلاس", x -> overallPeriod(days));
        btn("👤 گزارش هر زبان‌آموز", x -> studentPeriod(days));
        btn("🔎 بررسی نام‌های مشابه", x -> ambiguousNames());
        backBtn();
    }

    /** Reports of the last `days` days (today included), oldest first, by each report's own date. */
    Period period(int days) {
        Period p = new Period();
        long today = JalaliDate.today();
        for (int i = 0; i < st.reports.length(); i++) {
            JSONObject r = st.reports.optJSONObject(i);
            long d = JalaliDate.parseEpochDay(r.optString("date"));
            if (d == JalaliDate.INVALID) {
                d = createdDay(r);
                if (d == JalaliDate.INVALID) {
                    p.undated++;
                    p.idx.add(i);
                    continue;
                }
                p.fallback++;
            }
            if (d > today - days) p.idx.add(i);
        }
        Collections.sort(p.idx, (a, b) -> Long.compare(reportDay(st.reports.optJSONObject(a)), reportDay(st.reports.optJSONObject(b))));
        return p;
    }

    String periodHeader(Period p, int days) {
        StringBuilder s = new StringBuilder("بازه: " + days + " روز اخیر • تعداد گزارش: " + p.idx.size());
        if (p.fallback > 0) s.append("\n⚠️ ").append(p.fallback).append(" گزارش تاریخ قابل تشخیص ندارند و بر اساس زمان ثبت در برنامه حساب شده‌اند.");
        if (p.undated > 0) s.append("\n⚠️ ").append(p.undated).append(" گزارش هیچ تاریخی ندارند و در همه بازه‌ها نمایش داده می‌شوند.");
        return s.toString();
    }

    void noteEditor(final String key) {
        final EditText e = edit("توضیحات دستی", st.note(key), true);
        root.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        btn("💾 ذخیره توضیحات", x -> {
            st.setNote(key, e.getText().toString());
            toast("توضیحات ذخیره شد");
        });
    }

    void overallPeriod(final int days) {
        base("گزارش کلی", () -> periodReport(days));
        Period p = period(days);
        text(periodHeader(p, days), 14);
        noteEditor("overallNote" + days);
        backBtn();
        if (p.idx.isEmpty()) {
            text("در این بازه گزارش روزانه‌ای وجود ندارد.", 15);
            return;
        }
        StringBuilder s = new StringBuilder();
        for (int i : p.idx) {
            JSONObject r = st.reports.optJSONObject(i);
            s.append("• ").append(r.optString("date")).append(" | ").append(r.optString("class")).append("\n")
                    .append(r.optString("text")).append("\n\n");
        }
        text(s.toString(), 15).setTextIsSelectable(true);
    }

    void studentPeriod(final int days) {
        if (st.students.length() == 0) {
            toast("زبان‌آموزی وجود ندارد");
            return;
        }
        String[] a = new String[st.students.length()];
        for (int i = 0; i < a.length; i++) {
            JSONObject o = st.students.optJSONObject(i);
            a[i] = o.optString("first") + " " + o.optString("last") + " — " + o.optString("code");
        }
        new AlertDialog.Builder(this).setTitle("انتخاب زبان‌آموز").setItems(a, (d, w) -> studentReport(w, days))
                .setNegativeButton("لغو", null).show();
    }

    /**
     * Matching rule: if the first name is unique (and no other first name starts with it), reports are matched by first
     * name. Otherwise a report line is attributed to the student only when it contains the FULL name, so lines are never
     * attributed to the wrong person.
     */
    void studentReport(int si, final int days) {
        final JSONObject s = st.students.optJSONObject(si);
        if (s == null) return;
        String first = s.optString("first"), last = s.optString("last");
        String nf = Text.norm(first), nfull = Text.norm(first + " " + last);
        int clash = 0;
        for (int k = 0; k < st.students.length(); k++) {
            if (k == si) continue;
            String o = Text.norm(st.students.optJSONObject(k).optString("first"));
            if (o.equals(nf) || o.startsWith(nf + " ")) clash++;
        }
        final String key = clash == 0 ? nf : nfull;
        final Pattern pat = token(key);

        base("گزارش " + first + " " + last, () -> periodReport(days));
        Period p = period(days);
        StringBuilder head = new StringBuilder(periodHeader(p, days));
        if (clash > 0) {
            head.append("\n⚠️ نام کوچک مشترک یا مشابه است؛ فقط مواردی نمایش داده می‌شود که نام و نام خانوادگی کامل در آن‌ها آمده باشد. ")
                    .append("از «بررسی نام‌های مشابه» برای جزئیات استفاده کنید.");
        }
        text(head.toString(), 14);
        noteEditor("studentNote" + days + "_" + s.optString("code"));
        backBtn();
        StringBuilder out = new StringBuilder();
        boolean found = false;
        if (!key.isEmpty()) {
            for (int i : p.idx) {
                JSONObject r = st.reports.optJSONObject(i);
                String ex = extract(r.optString("text"), key, pat);
                if (!ex.isEmpty()) {
                    found = true;
                    out.append("[ ").append(r.optString("date")).append(" | ").append(r.optString("class")).append(" ]\n").append(ex).append("\n");
                }
            }
        }
        if (!found) out.append("موردی برای این زبان‌آموز در گزارش‌های این بازه پیدا نشد.");
        text(out.toString(), 16).setTextIsSelectable(true);
    }

    Pattern token(String n) {
        return Pattern.compile("(^|[^\\p{L}\\p{N}])" + Pattern.quote(n) + "([^\\p{L}\\p{N}]|$)");
    }

    /** Heading blocks (a line that is exactly the name, followed by lines up to a blank line); else lines containing the name. */
    String extract(String txt, String key, Pattern pat) {
        String[] lines = txt.split("\\r?\\n");
        StringBuilder blocks = new StringBuilder();
        boolean on = false;
        for (String line : lines) {
            String n = Text.norm(line);
            if (on) {
                if (n.isEmpty()) on = false;
                else {
                    blocks.append(line).append("\n");
                    continue;
                }
            }
            if (n.equals(key)) {
                on = true;
                blocks.append(line).append("\n");
            }
        }
        if (blocks.length() > 0) return blocks.toString();
        StringBuilder out = new StringBuilder();
        for (String line : lines) if (pat.matcher(Text.norm(line)).find()) out.append(line).append("\n");
        return out.toString();
    }

    void ambiguousNames() {
        final Map<String, ArrayList<Integer>> groups = new LinkedHashMap<>();
        for (int i = 0; i < st.students.length(); i++) {
            String n = Text.norm(st.students.optJSONObject(i).optString("first"));
            if (n.isEmpty()) continue;
            ArrayList<Integer> g = groups.get(n);
            if (g == null) {
                g = new ArrayList<>();
                groups.put(n, g);
            }
            g.add(i);
        }
        final ArrayList<String> keys = new ArrayList<>();
        ArrayList<String> labels = new ArrayList<>();
        for (Map.Entry<String, ArrayList<Integer>> e : groups.entrySet()) {
            if (e.getValue().size() > 1) {
                keys.add(e.getKey());
                JSONObject f = st.students.optJSONObject(e.getValue().get(0));
                labels.add(f.optString("first") + " (" + e.getValue().size() + " نفر)");
            }
        }
        if (keys.isEmpty()) {
            info("بررسی نام‌های مشابه", "نام کوچک تکراری پیدا نشد.");
            return;
        }
        new AlertDialog.Builder(this).setTitle("نام‌های مشابه").setItems(labels.toArray(new String[0]), (d, w) -> {
            ArrayList<Integer> ids = groups.get(keys.get(w));
            StringBuilder msg = new StringBuilder("زبان‌آموزان با این نام:\n\n");
            for (int id : ids) {
                JSONObject o = st.students.optJSONObject(id);
                String cls = st.classNameOf(o.optString("code"));
                msg.append("• ").append(o.optString("first")).append(" ").append(o.optString("last")).append(" — ").append(o.optString("code"));
                if (!cls.isEmpty()) msg.append(" (").append(cls).append(")");
                msg.append("\n");
            }
            msg.append("\nگزارش‌ها فقط وقتی به این افراد نسبت داده می‌شوند که نام و نام خانوادگی کامل در متن گزارش آمده باشد.");
            info("نام‌های مشابه", msg.toString());
        }).setNegativeButton("بستن", null).show();
    }

    // =====================================================================
    // Settings / backup
    // =====================================================================

    void settings() {
        base("تنظیمات", this::home);
        btn("💾 پشتیبان‌گیری از داده‌ها", x -> backup());
        btn("♻️ بازیابی داده‌ها", x -> pick(PICK_BACKUP));
        btn("ℹ️ درباره برنامه", x -> info("کلاس 1.5", "عامل سبک مدیریت آموزشگاه زبان\nAndroid اول، iOS در مرحله بعد\nداده‌ها روی خود گوشی نگهداری می‌شوند."));
        backBtn();
    }

    void backup() {
        String name = "class-backup-" + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".json";
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("application/json");
        i.putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(i, PICK_SAVE_BACKUP);
    }

    void writeBackup(Uri u) throws Exception {
        String payload = st.exportJson();
        try (OutputStream out = getContentResolver().openOutputStream(u, "wt")) {
            if (out == null) throw new IOException("امکان نوشتن در فایل نیست");
            out.write(payload.getBytes(StandardCharsets.UTF_8));
        }
        toast("پشتیبان با موفقیت ذخیره شد");
    }

    void restoreBackup(Uri u) throws Exception {
        final JSONObject r;
        try {
            r = new JSONObject(readText(u));
        } catch (JSONException e) {
            toast("فایل پشتیبان معتبر نیست");
            return;
        }
        String err = Store.validate(r);
        if (err != null) {
            toast(err);
            return;
        }
        confirm("بازیابی اطلاعات", "اطلاعات فعلی با نسخه پشتیبان جایگزین می‌شود. ادامه؟", "بازیابی", () -> {
            st.replaceWith(r);
            toast("بازیابی با موفقیت انجام شد");
            home();
        });
    }

    // =====================================================================
    // File import
    // =====================================================================

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        try {
            switch (req) {
                case PICK_BACKUP: restoreBackup(u); break;
                case PICK_SAVE_BACKUP: writeBackup(u); break;
                case PICK_REPORT: importReportTxt(u); break;
                case PICK_STUDENTS: importStudents(u); break;
                case PICK_CLASSES: importClasses(u); break;
                default: break;
            }
        } catch (Exception e) {
            toast("فایل قابل خواندن نیست: " + e.getMessage());
        }
    }

    byte[] readBytes(Uri u) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(u)) {
            if (in == null) throw new IOException("امکان باز کردن فایل نیست");
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                b.write(buf, 0, n);
                if (b.size() > MAX_FILE) throw new IOException("فایل بیش از حد بزرگ است");
            }
            return b.toByteArray();
        }
    }

    String readText(Uri u) throws IOException {
        return Text.decode(readBytes(u));
    }

    List<List<String>> readXlsx(Uri u) throws Exception {
        try (InputStream in = getContentResolver().openInputStream(u)) {
            if (in == null) throw new IOException("امکان باز کردن فایل نیست");
            return XlsxReader.read(in);
        }
    }

    void importReportTxt(Uri u) throws Exception {
        String txt = readText(u);
        JSONObject draft = new JSONObject();
        String d = JalaliDate.findDate(txt);
        draft.put("date", d != null ? d : JalaliDate.todayJalali()).put("class", "").put("text", txt);
        reportDialog(-1, draft);
    }

    String get(List<String> r, int i) {
        return i < r.size() ? r.get(i).trim() : "";
    }

    boolean blank(List<String> r) {
        for (String c : r) if (!c.trim().isEmpty()) return false;
        return true;
    }

    /** Excel stores phone numbers as numbers, dropping the leading zero. */
    String fixMobile(String s) {
        String d = Text.digits(s);
        if (d.matches("\\d+")) return (d.length() == 10 && d.startsWith("9")) ? "0" + d : d;
        return s;
    }

    String fixNational(String s) {
        String d = Text.digits(s);
        if (d.matches("\\d+")) {
            StringBuilder b = new StringBuilder(d);
            if (d.length() >= 8 && d.length() < 10) while (b.length() < 10) b.insert(0, '0');
            return b.toString();
        }
        return s;
    }

    /** A date typed as a real Excel date arrives as a serial number (e.g. 45000); convert it. */
    String fixDate(String s) {
        if (s.matches("\\d{5}")) {
            long v = Long.parseLong(s);
            if (v >= 20000 && v <= 70000) return JalaliDate.serialToDate(v);
        }
        return s;
    }

    /** A time typed as a real Excel time arrives as a fraction of a day (e.g. 0.75 = 18:00). */
    String fixTime(String s) {
        if (s.matches("0\\.\\d+")) {
            int mins = (int) Math.round(Double.parseDouble(s) * 24 * 60);
            return String.format(Locale.US, "%02d:%02d", (mins / 60) % 24, mins % 60);
        }
        return s;
    }

    void importStudents(Uri u) throws Exception {
        List<List<String>> rows = readXlsx(u);
        if (rows.size() < 2) {
            toast("فایل Excel خالی است یا ردیف داده ندارد");
            return;
        }
        int added = 0, dup = 0, bad = 0;
        for (int i = 1; i < rows.size(); i++) {
            List<String> r = rows.get(i);
            if (blank(r)) continue;
            String code = get(r, 0), first = get(r, 1), last = get(r, 2);
            if (first.isEmpty() && last.isEmpty()) {
                bad++;
                continue;
            }
            boolean exists = st.codeExists(st.students, code) || st.hasFullName(first, last, -1);
            if (exists) {
                dup++;
                continue;
            }
            JSONObject o = new JSONObject();
            o.put("code", code.isEmpty() ? st.newCode("S", st.students) : code).put("first", first).put("last", last)
                    .put("mobile", fixMobile(get(r, 3))).put("national", fixNational(get(r, 4)))
                    .put("birth", fixDate(get(r, 5))).put("notes", get(r, 6));
            st.students.put(o);
            added++;
        }
        st.save();
        studentsPage();
        info("ورود Excel", "ثبت شد: " + added + "\nتکراری (نادیده گرفته شد): " + dup + "\nردیف بدون نام: " + bad
                + "\nرکوردهای تکراری حذف یا جایگزین نمی‌شوند.");
    }

    void importClasses(Uri u) throws Exception {
        List<List<String>> rows = readXlsx(u);
        if (rows.size() < 2) {
            toast("فایل Excel خالی است یا ردیف داده ندارد");
            return;
        }
        int added = 0, dup = 0, bad = 0;
        for (int i = 1; i < rows.size(); i++) {
            List<String> r = rows.get(i);
            if (blank(r)) continue;
            String code = get(r, 0), name = get(r, 1);
            if (name.isEmpty()) {
                bad++;
                continue;
            }
            boolean exists = st.codeExists(st.classes, code);
            for (int j = 0; j < st.classes.length() && !exists; j++) {
                if (Text.norm(st.classes.optJSONObject(j).optString("name")).equals(Text.norm(name))) exists = true;
            }
            if (exists) {
                dup++;
                continue;
            }
            JSONObject o = new JSONObject();
            o.put("code", code.isEmpty() ? st.newCode("C", st.classes) : code).put("name", name).put("level", get(r, 2))
                    .put("start", fixDate(get(r, 3))).put("end", fixDate(get(r, 4))).put("days", get(r, 5))
                    .put("time", fixTime(get(r, 6))).put("notes", get(r, 7)).put("students", new JSONArray());
            st.classes.put(o);
            added++;
        }
        st.save();
        classesPage();
        info("ورود کلاس‌ها", "ثبت شد: " + added + "\nتکراری (نادیده گرفته شد): " + dup + "\nردیف بدون نام کلاس: " + bad);
    }
}
