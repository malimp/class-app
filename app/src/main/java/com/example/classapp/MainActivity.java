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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    static final int PICK_STUDENTS = 10, PICK_CLASSES = 11, PICK_REPORT = 12, PICK_BACKUP = 13, PICK_SAVE_BACKUP = 14, PICK_EXPORT = 15;
    static final int MAX_FILE = 20 * 1024 * 1024;

    interface Submit { boolean go(); }

    static final class Period {
        final ArrayList<Integer> idx = new ArrayList<>();
        int fallback, undated;
    }

    static final class Hit {
        final boolean absent, late;
        final String text; // as shown (heading lines included)
        final String body; // remark lines only (used by the statistical summary)

        Hit(boolean absent, boolean late, String text, String body) {
            this.absent = absent;
            this.late = late && !absent;
            this.text = text;
            this.body = body;
        }
    }

    static final class StudentOut {
        String summary = "", details = "";
        boolean found;
    }

    Store st;
    String fClass = "", fTeacher = ""; // filters of the manager's overall report
    String pendingExport = "";
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
        TextView v = text("مدیریت ساده آموزشگاه زبان\nنسخه 1.8 • Android", 16);
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
        btn("📝 ساخت گزارش با فرم (تیک‌دار)", x -> reportForm(-1, "", JalaliDate.todayJalali(), "", new ReportForm()));
        btn("📋 ورود گزارش از متن (چسباندن از پیام‌رسان)", x -> pasteDialog());
        btn("📥 ورود فایل متنی (.txt)", x -> pick(PICK_REPORT));
        btn("✍️ نوشتن گزارش متنی (دستی)", x -> reportDialog(-1, new JSONObject()));
        backBtn();
        text("گزارش‌ها: " + st.reports.length() + "\nتاریخ هر گزارش (شمسی یا میلادی) مبنای گزارش هفتگی و ماهانه است؛ متن اصلی تغییر نمی‌کند.", 14);
        final ArrayList<Integer> order = new ArrayList<>();
        for (int i = 0; i < st.reports.length(); i++) order.add(i);
        Collections.sort(order, (a, b) -> Long.compare(reportDay(st.reports.optJSONObject(b)), reportDay(st.reports.optJSONObject(a))));
        for (final int i : order) {
            JSONObject o = st.reports.optJSONObject(i);
            String cls = o.optString("class"), t = o.optString("teacher");
            row(o.optString("date") + (cls.isEmpty() ? "" : " — " + cls) + (t.isEmpty() ? "" : " — " + t), x -> showReport(i));
        }
    }

    void showReport(final int i) {
        final JSONObject o = st.reports.optJSONObject(i);
        if (o == null) return;
        base(o.optString("date") + (o.optString("class").isEmpty() ? "" : " — " + o.optString("class")), this::reportsPage);
        btn("✏️ ویرایش", x -> editReport(i));
        exportButtons("report-" + o.optString("date").replace('/', '-'), () -> o.optString("text"));
        btn("🗑️ حذف گزارش", x -> confirm("حذف گزارش", "این گزارش حذف شود؟", "حذف", () -> {
            st.reports.remove(i);
            st.save();
            reportsPage();
        }));
        backBtn();
        if (!o.optString("teacher").isEmpty()) text("معلم: " + o.optString("teacher"), 14);
        text(o.optString("text"), 16).setTextIsSelectable(true);
    }

    void editReport(int i) {
        JSONObject o = st.reports.optJSONObject(i);
        if (o == null) return;
        JSONObject f = o.optJSONObject("form");
        if (f != null) reportForm(i, o.optString("teacher"), o.optString("date"), o.optString("class"), ReportForm.fromJson(f));
        else reportDialog(i, o);
    }

    /** Stores (or replaces) a report; returns the object that was written. */
    JSONObject commitReport(int idx, JSONObject draft, String date, String cls, String teacher, String text, JSONObject form) throws JSONException {
        JSONObject o = idx >= 0 ? st.reports.optJSONObject(idx) : draft;
        o.put("date", date).put("class", cls).put("teacher", teacher).put("text", text);
        if (form != null) o.put("form", form);
        if (o.optString("id").isEmpty()) o.put("id", UUID.randomUUID().toString());
        if (o.optLong("createdAt", 0) == 0) o.put("createdAt", System.currentTimeMillis());
        if (idx < 0) st.reports.put(o);
        st.save();
        return o;
    }

    /** idx >= 0 edits st.reports[idx]; idx < 0 creates a new report from the draft (saved only on Save). */
    void reportDialog(final int idx, final JSONObject draft) {
        final JSONObject o = idx >= 0 ? st.reports.optJSONObject(idx) : draft;
        if (o == null) return;
        String d0 = o.optString("date");
        final EditText date = edit("تاریخ (مثلاً 1405/07/13)", d0.isEmpty() ? JalaliDate.todayJalali() : d0, false);
        final EditText teacher = edit("نام معلم", o.optString("teacher"), false);
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
        l.addView(teacher);
        l.addView(cls);
        l.addView(body);
        formDialog("گزارش روزانه", l, () -> {
            final String text = body.getText().toString();
            if (text.trim().isEmpty()) {
                toast("متن گزارش خالی است");
                return false;
            }
            String ds0 = date.getText().toString().trim();
            final String ds = ds0.isEmpty() ? JalaliDate.todayJalali() : ds0;
            int p = cls.getSelectedItemPosition();
            final String cn = p <= 0 ? "" : opts.get(p);
            final String tn = teacher.getText().toString().trim();
            final Runnable done = () -> {
                if (JalaliDate.parseEpochDay(ds) == JalaliDate.INVALID) {
                    toast("تاریخ قابل تشخیص نبود؛ در گزارش‌های دوره‌ای بر اساس زمان ثبت حساب می‌شود");
                }
                reportsPage();
            };
            final int dup = idx < 0 ? Reports.findDuplicate(st.reports, ds, cn, tn, -1) : -1;
            try {
                if (dup >= 0) {
                    confirm("گزارش تکراری", "برای همین تاریخ، کلاس و معلم قبلاً گزارشی ثبت شده است. جایگزین شود؟", "جایگزین", () -> {
                        try {
                            commitReport(dup, null, ds, cn, tn, text, null);
                        } catch (JSONException e) {
                            toast("خطا در ذخیره");
                            return;
                        }
                        done.run();
                    });
                } else {
                    commitReport(idx, o, ds, cn, tn, text, null);
                    done.run();
                }
            } catch (JSONException e) {
                toast("خطا در ذخیره");
                return false;
            }
            return true;
        });
    }

    /** Paste a report received in a messenger. */
    void pasteDialog() {
        final EditText e = edit("متن گزارش را اینجا بچسبانید", "", true);
        e.setMinLines(10);
        LinearLayout l = form();
        l.addView(e);
        new AlertDialog.Builder(this).setTitle("ورود گزارش از متن").setView(scroll(l))
                .setPositiveButton("ادامه", (d, w) -> {
                    String t = e.getText().toString();
                    if (t.trim().isEmpty()) toast("متنی وارد نشده است");
                    else importReportText(t);
                }).setNegativeButton("لغو", null).show();
    }

    /** Reads teacher, date and class from the text, then lets the manager check them before saving. */
    void importReportText(String txt) {
        try {
            JSONObject draft = new JSONObject();
            String d = JalaliDate.findDate(txt);
            String cls = Reports.matchClass(st, Reports.parseClass(txt));
            if (cls.isEmpty()) cls = Reports.inferClass(st, txt);
            draft.put("date", d != null ? d : JalaliDate.todayJalali()).put("class", cls)
                    .put("teacher", Reports.parseTeacher(txt)).put("text", txt);
            reportDialog(-1, draft);
        } catch (JSONException e) {
            toast("خطا در خواندن گزارش");
        }
    }

    // ---------- tick-box report form ----------

    /** Name printed in reports: the first name, or the full name when another student shares the first name. */
    String displayName(String code) {
        for (int i = 0; i < st.students.length(); i++) {
            JSONObject o = st.students.optJSONObject(i);
            if (!o.optString("code").equals(code)) continue;
            String f = o.optString("first").trim();
            return clashCount(i, Text.norm(f)) == 0 ? f : f + " " + o.optString("last").trim();
        }
        return code;
    }

    List<String> membersOf(String cls) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < st.classes.length(); i++) {
            JSONObject c = st.classes.optJSONObject(i);
            if (!c.optString("name").equals(cls)) continue;
            JSONArray m = c.optJSONArray("students");
            for (int j = 0; m != null && j < m.length(); j++) if (st.student(m.optString(j)) != null) out.add(m.optString(j));
            break;
        }
        return out;
    }

    /** The widgets of one open form; read back with collect(). */
    final class FormUi {
        Spinner teacher, cls;
        EditText date, book, wb, other, hw, general;
        final List<String> members = new ArrayList<>(), options = new ArrayList<>(), teachers = new ArrayList<>(), classNames = new ArrayList<>();
        final Map<String, CheckBox> absent = new LinkedHashMap<>(), late = new LinkedHashMap<>();
        final Map<String, List<CheckBox>> opts = new LinkedHashMap<>();
        final Map<String, EditText> others = new LinkedHashMap<>();

        String teacherName() {
            int p = teacher.getSelectedItemPosition();
            return p <= 0 || p >= teachers.size() ? "" : teachers.get(p);
        }

        String className() {
            int p = cls.getSelectedItemPosition();
            return p <= 0 || p >= classNames.size() ? "" : classNames.get(p);
        }

        ReportForm collect() {
            ReportForm f = new ReportForm();
            f.book = book.getText().toString();
            f.workbook = wb.getText().toString();
            f.other = other.getText().toString();
            f.homework = hw.getText().toString();
            f.general = general.getText().toString();
            for (String c : members) {
                if (absent.get(c).isChecked()) f.absent.add(c);
                else if (late.get(c).isChecked()) f.late.add(c);
                ReportForm.Mark m = new ReportForm.Mark();
                List<CheckBox> cbs = opts.get(c);
                for (int i = 0; i < cbs.size(); i++) if (cbs.get(i).isChecked()) m.opts.add(options.get(i));
                m.other = others.get(c).getText().toString();
                if (!m.empty()) f.marks.put(c, m);
            }
            return f;
        }
    }

    void reportForm(final int idx, String teacher0, String date0, String cls0, final ReportForm f0) {
        final FormUi u = new FormUi();
        final boolean editing = idx >= 0;
        base(editing ? "ویرایش گزارش" : "گزارش روزانه جدید", editing ? () -> showReport(idx) : this::reportsPage);
        if (st.classes.length() == 0) {
            text("ابتدا از بخش «کلاس‌ها» یک کلاس بسازید و زبان‌آموزان را به آن اضافه کنید.", 15);
            backBtn();
            return;
        }
        // header: teacher, date, class
        u.teachers.add("(بدون نام)");
        u.teachers.addAll(st.teachers());
        String tsel = !teacher0.isEmpty() ? teacher0 : st.lastTeacher();
        if (!tsel.isEmpty() && !u.teachers.contains(tsel)) u.teachers.add(tsel);
        text("معلم", 14);
        u.teacher = new Spinner(this);
        ArrayAdapter<String> ta = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, u.teachers);
        ta.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        u.teacher.setAdapter(ta);
        u.teacher.setSelection(Math.max(0, u.teachers.indexOf(tsel)));
        root.addView(u.teacher);
        u.date = edit("تاریخ (مثلاً 1405/07/13)", date0.isEmpty() ? JalaliDate.todayJalali() : date0, false);
        root.addView(u.date);
        u.classNames.add("(انتخاب کلاس)");
        for (int i = 0; i < st.classes.length(); i++) u.classNames.add(st.classes.optJSONObject(i).optString("name"));
        if (!cls0.isEmpty() && !u.classNames.contains(cls0)) u.classNames.add(cls0);
        text("کلاس", 14);
        u.cls = new Spinner(this);
        ArrayAdapter<String> ca = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, u.classNames);
        ca.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        u.cls.setAdapter(ca);
        final int csel = Math.max(0, u.classNames.indexOf(cls0));
        u.cls.setSelection(csel);
        root.addView(u.cls);
        u.members.addAll(membersOf(cls0));
        // lesson
        text("درس امروز", 18);
        u.book = edit("Student's book (مثلاً صفحه 20، 21)", f0.book, false);
        u.wb = edit("Workbook (مثلاً صفحه 15)", f0.workbook, false);
        u.other = edit("سایر (مثلاً صفحات 21-24 کتاب داستان)", f0.other, false);
        root.addView(u.book);
        root.addView(u.wb);
        root.addView(u.other);
        // options = current list + saved remarks that are no longer in the list
        u.options.addAll(st.options());
        for (ReportForm.Mark m : f0.marks.values()) for (String o : m.opts) if (!u.options.contains(o)) u.options.add(o);
        text("حضور و غیاب و وضعیت بچه‌ها", 18);
        if (cls0.isEmpty()) text("کلاس را انتخاب کنید تا اسامی زبان‌آموزان بیاید.", 14);
        else if (u.members.isEmpty()) text("این کلاس عضو ندارد.", 14);
        for (final String code : u.members) addStudentCard(u, code, f0);
        // homework + general
        text("تکلیف جلسه بعد", 18);
        u.hw = edit("تکلیف جلسه بعد", f0.homework, true);
        root.addView(u.hw);
        text("یادداشت کلی", 18);
        u.general = edit("یادداشت کلی (اختیاری)", f0.general, true);
        root.addView(u.general);
        u.cls.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position == csel) return;
                ReportForm f = u.collect();
                reportForm(idx, u.teacherName(), u.date.getText().toString(), u.className(), f);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        btn("💾 ذخیره گزارش", x -> saveForm(idx, u, false));
        btn("📨 ذخیره و ارسال در پیام‌رسان", x -> saveForm(idx, u, true));
        btn("👁 پیش‌نمایش متن", x -> {
            String cn = u.className();
            ReportForm f = u.collect();
            info("پیش‌نمایش", f.toText(u.teacherName(), u.date.getText().toString(), cn, u.members, this::displayName));
        });
        backBtn();
    }

    void addStudentCard(final FormUi u, final String code, ReportForm f0) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.rgb(240, 242, 248));
        card.setPadding(dp(10), dp(6), dp(10), dp(6));
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cp.setMargins(0, dp(4), 0, dp(4));
        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        TextView nm = new TextView(this);
        nm.setText(displayName(code));
        nm.setTextSize(17);
        head.addView(nm, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        final CheckBox ab = new CheckBox(this), lt = new CheckBox(this);
        ab.setText("غایب");
        lt.setText("تأخیر");
        ab.setChecked(f0.absent.contains(code));
        lt.setChecked(f0.late.contains(code) && !f0.absent.contains(code));
        head.addView(ab);
        head.addView(lt);
        card.addView(head);
        final TextView toggle = new TextView(this);
        toggle.setTextSize(15);
        toggle.setTextColor(Color.rgb(30, 80, 160));
        toggle.setPadding(dp(4), dp(8), dp(4), dp(8));
        card.addView(toggle);
        final LinearLayout det = new LinearLayout(this);
        det.setOrientation(LinearLayout.VERTICAL);
        final List<CheckBox> cbs = new ArrayList<>();
        ReportForm.Mark mk = f0.marks.get(code);
        for (String o : u.options) {
            CheckBox c = new CheckBox(this);
            c.setText(o);
            c.setChecked(mk != null && mk.opts.contains(o));
            cbs.add(c);
            det.addView(c);
        }
        final EditText oth = edit("توضیح دیگر", mk == null ? "" : mk.other, false);
        det.addView(oth);
        card.addView(det);
        det.setVisibility(mk != null && !mk.empty() && !ab.isChecked() ? View.VISIBLE : View.GONE);
        u.absent.put(code, ab);
        u.late.put(code, lt);
        u.opts.put(code, cbs);
        u.others.put(code, oth);
        final Runnable refresh = () -> {
            int n = 0;
            for (CheckBox c : cbs) if (c.isChecked()) n++;
            if (!oth.getText().toString().trim().isEmpty()) n++;
            boolean open = det.getVisibility() == View.VISIBLE;
            toggle.setText("وضعیت امروز" + (n > 0 ? " (" + n + " مورد)" : "") + (open ? " ▴" : " ▾"));
            toggle.setVisibility(ab.isChecked() ? View.GONE : View.VISIBLE);
            if (ab.isChecked()) det.setVisibility(View.GONE);
        };
        toggle.setOnClickListener(v -> {
            det.setVisibility(det.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
            refresh.run();
        });
        ab.setOnCheckedChangeListener((b, c) -> {
            if (c) lt.setChecked(false);
            refresh.run();
        });
        lt.setOnCheckedChangeListener((b, c) -> {
            if (c) ab.setChecked(false);
            refresh.run();
        });
        for (CheckBox c : cbs) c.setOnCheckedChangeListener((b, ch) -> refresh.run());
        refresh.run();
        root.addView(card, cp);
    }

    void saveForm(final int idx, final FormUi u, final boolean send) {
        final String cn = u.className();
        if (cn.isEmpty()) {
            toast("کلاس را انتخاب کنید");
            return;
        }
        String ds0 = u.date.getText().toString().trim();
        final String ds = ds0.isEmpty() ? JalaliDate.todayJalali() : ds0;
        final String tn = u.teacherName();
        final ReportForm f = u.collect();
        final String text = f.toText(tn, ds, cn, u.members, this::displayName);
        final Runnable done = () -> {
            if (!tn.isEmpty()) st.setLastTeacher(tn);
            if (send) {
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType("text/plain");
                i.putExtra(Intent.EXTRA_TEXT, text);
                startActivity(Intent.createChooser(i, "ارسال گزارش"));
            }
            reportsPage();
        };
        try {
            final JSONObject fj = f.toJson();
            final int dup = idx < 0 ? Reports.findDuplicate(st.reports, ds, cn, tn, -1) : -1;
            if (dup >= 0) {
                confirm("گزارش تکراری", "برای همین تاریخ، کلاس و معلم قبلاً گزارشی ثبت شده است. جایگزین شود؟", "جایگزین", () -> {
                    try {
                        commitReport(dup, null, ds, cn, tn, text, fj);
                    } catch (JSONException e) {
                        toast("خطا در ذخیره");
                        return;
                    }
                    done.run();
                });
                return;
            }
            commitReport(idx, new JSONObject(), ds, cn, tn, text, fj);
        } catch (JSONException e) {
            toast("خطا در ذخیره");
            return;
        }
        done.run();
    }

    // =====================================================================
    // Weekly / monthly reports
    // =====================================================================

    void periodReport(final int days) {
        base(days == 7 ? "گزارش هفتگی" : "گزارش ماهانه", this::home);
        btn("📌 گزارش کلی کلاس", x -> {
            fClass = "";
            fTeacher = "";
            overallPeriod(days);
        });
        btn("👤 گزارش هر زبان‌آموز", x -> studentPeriod(days));
        btn("🔎 بررسی نام‌های مشابه", x -> ambiguousNames());
        exportButtons((days == 7 ? "weekly" : "monthly") + "-all-students", () -> allStudentsText(days));
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

    String periodTitle(int days) {
        return days == 7 ? "گزارش هفتگی" : "گزارش ماهانه";
    }

    /** Reports of the period that match the class and teacher filters ("" = all). */
    List<Integer> filtered(Period p, String cls, String teacher) {
        List<Integer> out = new ArrayList<>();
        for (int i : p.idx) {
            JSONObject r = st.reports.optJSONObject(i);
            if (!cls.isEmpty() && !Text.norm(r.optString("class")).equals(Text.norm(cls))) continue;
            if (!teacher.isEmpty() && !Text.norm(r.optString("teacher")).equals(Text.norm(teacher))) continue;
            out.add(i);
        }
        return out;
    }

    String filterLine(String cls, String teacher) {
        return "کلاس: " + (cls.isEmpty() ? "همه" : cls) + " • معلم: " + (teacher.isEmpty() ? "همه" : teacher);
    }

    /** Absences and lateness of every member in the reports of the class (all classes when cls is empty). */
    String attendanceText(Period p, String cls, String teacher) {
        List<Integer> rs = filtered(p, cls, teacher);
        Set<String> heads = heads();
        StringBuilder s = new StringBuilder();
        for (int ci = 0; ci < st.classes.length(); ci++) {
            String cn = st.classes.optJSONObject(ci).optString("name");
            if (!cls.isEmpty() && !Text.norm(cn).equals(Text.norm(cls))) continue;
            List<String> members = membersOf(cn);
            if (members.isEmpty()) continue;
            List<Integer> mine = new ArrayList<>();
            for (int i : rs) if (Text.norm(st.reports.optJSONObject(i).optString("class")).equals(Text.norm(cn))) mine.add(i);
            s.append("📋 حضور و غیاب — کلاس ").append(cn).append(" (").append(mine.size()).append(" گزارش)\n");
            if (mine.isEmpty()) {
                s.append("گزارشی برای این کلاس در بازه نیست.\n\n");
                continue;
            }
            for (String code : members) {
                int si = -1;
                for (int k = 0; k < st.students.length(); k++) if (st.students.optJSONObject(k).optString("code").equals(code)) si = k;
                if (si < 0) continue;
                String key = keyOf(si);
                Pattern pat = token(key);
                List<String> ab = new ArrayList<>(), lt = new ArrayList<>();
                if (!key.isEmpty()) {
                    for (int i : mine) {
                        JSONObject r = st.reports.optJSONObject(i);
                        Hit h = hit(r.optString("text"), key, pat, heads);
                        if (h.absent) ab.add(r.optString("date"));
                        else if (h.late) lt.add(r.optString("date"));
                    }
                }
                JSONObject so = st.students.optJSONObject(si);
                s.append("• ").append(so.optString("first")).append(" ").append(so.optString("last")).append(" — ");
                if (ab.isEmpty() && lt.isEmpty()) {
                    s.append("بدون غیبت و تأخیر\n");
                } else {
                    if (!ab.isEmpty()) s.append("غایب: ").append(ab.size()).append(" بار (").append(joinList(ab)).append(")");
                    if (!ab.isEmpty() && !lt.isEmpty()) s.append(" • ");
                    if (!lt.isEmpty()) s.append("تأخیر: ").append(lt.size()).append(" بار (").append(joinList(lt)).append(")");
                    s.append("\n");
                }
            }
            s.append("\n");
        }
        return s.toString();
    }

    String joinList(List<String> l) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) b.append("، ");
            b.append(l.get(i));
        }
        return b.toString();
    }

    String overallBody(int days, Period p, String cls, String teacher) {
        List<Integer> rs = filtered(p, cls, teacher);
        StringBuilder s = new StringBuilder();
        s.append(attendanceText(p, cls, teacher));
        if (rs.isEmpty()) return s.append("در این بازه (با این فیلتر) گزارش روزانه‌ای وجود ندارد.\n").toString();
        for (int i : rs) {
            JSONObject r = st.reports.optJSONObject(i);
            s.append("• ").append(r.optString("date")).append(" | ").append(r.optString("class"));
            if (!r.optString("teacher").isEmpty()) s.append(" | ").append(r.optString("teacher"));
            s.append("\n").append(r.optString("text")).append("\n\n");
        }
        return s.toString();
    }

    String overallText(int days) {
        Period p = period(days);
        StringBuilder s = new StringBuilder(periodTitle(days) + " — گزارش کلی کلاس\n");
        s.append(periodHeader(p, days)).append("\n").append(filterLine(fClass, fTeacher)).append("\n");
        String note = st.note("overallNote" + days);
        if (!note.trim().isEmpty()) s.append("\nتوضیحات: ").append(note.trim()).append("\n");
        s.append("\n").append(overallBody(days, p, fClass, fTeacher));
        return s.toString();
    }

    List<String> teachersInUse() {
        List<String> t = new ArrayList<>(st.teachers());
        for (int i = 0; i < st.reports.length(); i++) {
            String v = st.reports.optJSONObject(i).optString("teacher").trim();
            if (!v.isEmpty() && !t.contains(v)) t.add(v);
        }
        return t;
    }

    void filterButton(final int days, final boolean forClass) {
        btn(forClass ? "🏫 کلاس: " + (fClass.isEmpty() ? "همه" : fClass) : "👩‍🏫 معلم: " + (fTeacher.isEmpty() ? "همه" : fTeacher), x -> {
            final List<String> items = new ArrayList<>();
            items.add("همه");
            if (forClass) {
                for (int i = 0; i < st.classes.length(); i++) items.add(st.classes.optJSONObject(i).optString("name"));
            } else {
                items.addAll(teachersInUse());
            }
            new AlertDialog.Builder(this).setTitle(forClass ? "انتخاب کلاس" : "انتخاب معلم")
                    .setItems(items.toArray(new String[0]), (d, w) -> {
                        if (forClass) fClass = w == 0 ? "" : items.get(w);
                        else fTeacher = w == 0 ? "" : items.get(w);
                        overallPeriod(days);
                    }).show();
        });
    }

    void overallPeriod(final int days) {
        base("گزارش کلی", () -> periodReport(days));
        Period p = period(days);
        text(periodHeader(p, days), 14);
        filterButton(days, true);
        filterButton(days, false);
        noteEditor("overallNote" + days);
        exportButtons("overall-" + (days == 7 ? "weekly" : "monthly"), () -> overallText(days));
        backBtn();
        text(overallBody(days, p, fClass, fTeacher), 15).setTextIsSelectable(true);
    }

    interface TextSource { String get(); }

    /** Two buttons: save as a .txt file, or share the text (Telegram, WhatsApp, Eitaa, ...). */
    void exportButtons(final String name, final TextSource src) {
        btn("📤 ذخیره به‌صورت فایل (TXT)", x -> exportFile(name + "-" + new SimpleDateFormat("yyyyMMdd", Locale.US).format(new Date()) + ".txt", src.get()));
        btn("📨 ارسال در پیام‌رسان", x -> {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_TEXT, src.get());
            startActivity(Intent.createChooser(i, "ارسال گزارش"));
        });
    }

    void exportFile(String fileName, String content) {
        pendingExport = content;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TITLE, fileName);
        startActivityForResult(i, PICK_EXPORT);
    }

    /** UTF-8 with BOM so Persian text opens correctly in Notepad and similar apps. */
    void writeExport(Uri u) throws Exception {
        try (OutputStream out = getContentResolver().openOutputStream(u, "wt")) {
            if (out == null) throw new IOException("امکان نوشتن در فایل نیست");
            out.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            out.write(pendingExport.getBytes(StandardCharsets.UTF_8));
        }
        toast("فایل گزارش ذخیره شد");
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

    /** Number of other students whose first name equals (or starts with) this student's first name. */
    int clashCount(int si, String nf) {
        int clash = 0;
        for (int k = 0; k < st.students.length(); k++) {
            if (k == si) continue;
            String o = Text.norm(st.students.optJSONObject(k).optString("first"));
            if (o.equals(nf) || o.startsWith(nf + " ")) clash++;
        }
        return clash;
    }

    /** Matching key: the first name when unique, otherwise the full name. */
    String keyOf(int si) {
        JSONObject s = st.students.optJSONObject(si);
        String nf = Text.norm(s.optString("first"));
        return clashCount(si, nf) == 0 ? nf : Text.norm(s.optString("first") + " " + s.optString("last"));
    }

    /** True when the report mentions (or marks absent) at least one student of the class; guards against "empty" reports. */
    boolean mentionsAnyMember(String txt, String cls, Set<String> heads) {
        for (int k = 0; k < st.students.length(); k++) {
            if (!cls.equals(st.classNameOf(st.students.optJSONObject(k).optString("code")))) continue;
            String key = keyOf(k);
            if (key.isEmpty()) continue;
            Hit h = hit(txt, key, token(key), heads);
            if (h.absent || h.late || !h.text.trim().isEmpty()) return true;
        }
        return false;
    }

    boolean firstNameAppears(String txt, String nf) {
        Pattern p = token(nf);
        for (String line : txt.split("\\r?\\n")) if (p.matcher(Text.norm(line)).find()) return true;
        return false;
    }

    /**
     * A report of the student's own class that does not name him/her (and does name classmates) means: present and
     * homework done well. Reports of other classes are ignored; reports without a class are "unknown".
     */
    /** Everything the student's report contains (used by both the screen and the exports). */
    StudentOut computeStudent(int si, int days, Period p, Set<String> heads) {
        StudentOut o = new StudentOut();
        JSONObject s = st.students.optJSONObject(si);
        if (s == null) return o;
        String nf = Text.norm(s.optString("first"));
        int clash = clashCount(si, nf);
        String key = keyOf(si);
        Pattern pat = token(key);
        String cls = st.classNameOf(s.optString("code"));
        String ncls = Text.norm(cls);
        StringBuilder out = new StringBuilder();
        int total = 0;
        ArrayList<Summary.Entry> entries = new ArrayList<>();
        if (!key.isEmpty()) {
            for (int i : p.idx) {
                JSONObject r = st.reports.optJSONObject(i);
                String rtext = r.optString("text"), rcls = r.optString("class"), nrc = Text.norm(rcls);
                if (!ncls.isEmpty() && !nrc.isEmpty() && !nrc.equals(ncls)) continue; // another class's report
                total++;
                String date = r.optString("date");
                String hdr = "[ " + date + " | " + rcls + " ]\n";
                Hit h = hit(rtext, key, pat, heads);
                String lateNote = h.late ? "با تأخیر آمد\n" : "";
                if (h.absent || !h.text.isEmpty()) {
                    entries.add(new Summary.Entry(date, h.absent, h.late, h.body));
                    o.found = true;
                    out.append(hdr).append(h.absent ? "غایب بود\n" : "").append(lateNote).append(h.text).append("\n");
                } else if (!ncls.isEmpty() && nrc.equals(ncls) && !(clash > 0 && firstNameAppears(rtext, nf))
                        && (Reports.hasStatusHeader(rtext) || mentionsAnyMember(rtext, cls, heads))) {
                    entries.add(Summary.Entry.implied(date, h.late));
                    o.found = true;
                    out.append(hdr).append(lateNote).append(Summary.IMPLIED).append("\n\n");
                } else if (h.late) {
                    entries.add(new Summary.Entry(date, false, true, ""));
                    o.found = true;
                    out.append(hdr).append(lateNote).append("\n");
                }
            }
        }
        if (!o.found) out.append("موردی برای این زبان‌آموز در گزارش‌های این بازه پیدا نشد.");
        else o.summary = Summary.build(entries, total, key);
        o.details = out.toString();
        return o;
    }

    String studentText(int si, int days, Period p, Set<String> heads) {
        JSONObject s = st.students.optJSONObject(si);
        StudentOut o = computeStudent(si, days, p, heads);
        StringBuilder t = new StringBuilder(periodTitle(days) + " — " + s.optString("first") + " " + s.optString("last") + "\n");
        String cls = st.classNameOf(s.optString("code"));
        if (!cls.isEmpty()) t.append("کلاس: ").append(cls).append("\n");
        t.append(periodHeader(p, days)).append("\n");
        String note = st.note("studentNote" + days + "_" + s.optString("code"));
        if (!note.trim().isEmpty()) t.append("\nتوضیحات: ").append(note.trim()).append("\n");
        t.append("\n");
        if (!o.summary.isEmpty()) t.append(o.summary).append("\n\n");
        return t.append(o.details).toString();
    }

    void studentReport(int si, final int days) {
        final JSONObject s = st.students.optJSONObject(si);
        if (s == null) return;
        String first = s.optString("first"), last = s.optString("last");
        int clash = clashCount(si, Text.norm(first));
        final String cls = st.classNameOf(s.optString("code"));
        final Set<String> heads = heads();
        final int fsi = si;

        base("گزارش " + first + " " + last, () -> periodReport(days));
        final Period p = period(days);
        StringBuilder head = new StringBuilder(periodHeader(p, days));
        if (clash > 0) {
            head.append("\n⚠️ نام کوچک مشترک یا مشابه است؛ فقط مواردی نمایش داده می‌شود که نام و نام خانوادگی کامل در آن‌ها آمده باشد. ")
                    .append("از «بررسی نام‌های مشابه» برای جزئیات استفاده کنید.");
        }
        if (cls.isEmpty()) head.append("\nℹ️ این زبان‌آموز در هیچ کلاسی نیست؛ «حاضر بدون ذکر نام» فقط برای اعضای کلاس محاسبه می‌شود.");
        text(head.toString(), 14);
        noteEditor("studentNote" + days + "_" + s.optString("code"));
        exportButtons((days == 7 ? "weekly-" : "monthly-") + (first + "-" + last).replace(' ', '_'),
                () -> studentText(fsi, days, p, heads));
        backBtn();
        StudentOut o = computeStudent(si, days, p, heads);
        if (!o.summary.isEmpty()) text(o.summary, 15).setTextIsSelectable(true);
        text(o.details, 16).setTextIsSelectable(true);
    }

    /** One file with the report of every student (class by class order of the student list). */
    String allStudentsText(int days) {
        Period p = period(days);
        Set<String> heads = heads();
        StringBuilder t = new StringBuilder(periodTitle(days) + " — همه زبان‌آموزان\n").append(periodHeader(p, days)).append("\n");
        for (int i = 0; i < st.students.length(); i++) {
            t.append("\n==============================\n").append(studentText(i, days, p, heads));
        }
        return t.toString();
    }

    Pattern token(String n) {
        return Reports.token(n);
    }

    /** Attendance line such as "غایب : آوین ، زهرا" (names separated by ، , ; or "و"). */
    static final Pattern ABSENT_LINE = Pattern.compile("^(غایب|غیبت)\\S*\\s*[:：]");

    /** Only list-style lines ("غایب : آوین ، زهرا") count; a remark like "غایب بود" under a student's name does not. */
    boolean isAbsentLine(String n) {
        return ABSENT_LINE.matcher(n).find();
    }

    /** Lateness line such as "تأخیر : آوین ، زهرا" (after normalization the hamza is gone). */
    static final Pattern LATE_LINE = Pattern.compile("^(تاخیر|تاخیر|دیرکرد|دیر)\\S*\\s*[:：]");

    boolean isLateLine(String n) {
        return LATE_LINE.matcher(n).find();
    }

    boolean absentMatches(String n, String key) {
        int i = n.indexOf(':');
        String list = i >= 0 ? n.substring(i + 1) : n.replaceFirst("^\\S+", "");
        for (String t : list.split("[,،;؛]|\\sو\\s")) {
            t = t.trim();
            if (!t.isEmpty() && (t.equals(key) || t.startsWith(key + " "))) return true;
        }
        return false;
    }

    /**
     * Heading blocks (a line that is exactly the name, followed by lines up to a blank line); else lines containing the
     * name. Attendance lines ("غایب: ...") add "غایب بود" when the student is listed.
     */
    String extract(String txt, String key, Pattern pat) {
        Hit h = hit(txt, key, pat);
        return (h.absent ? "غایب بود\n" : "") + h.text;
    }

    Hit hit(String txt, String key, Pattern pat) {
        return hit(txt, key, pat, new HashSet<String>());
    }

    /** Normalized first names and full names of all students: used to know where another student's section starts. */
    Set<String> heads() {
        Set<String> h = new HashSet<>();
        for (int i = 0; i < st.students.length(); i++) {
            JSONObject o = st.students.optJSONObject(i);
            h.add(Text.norm(o.optString("first")));
            h.add(Text.norm(o.optString("first") + " " + o.optString("last")));
        }
        h.remove("");
        return h;
    }

    /** The names on a heading line: "مانیا ، الیسا" gives two names (separators: ، , ; ؛ or "و"). */
    List<String> headingNames(String n) {
        List<String> out = new ArrayList<>();
        for (String part : n.split("[,،;؛]|\\sو\\s")) {
            String b = Text.bare(part);
            if (!b.isEmpty()) out.add(b);
        }
        return out;
    }

    /** Heading for the student: all names short (<= 3 words) and at least one is the student (key or key + last name). */
    boolean isHeadingFor(String n, String key) {
        if (n.isEmpty() || key.isEmpty()) return false;
        List<String> names = headingNames(n);
        if (names.isEmpty() || names.size() > 8) return false;
        int keyWords = key.split(" ").length;
        boolean mine = false;
        for (String p : names) {
            int words = p.split(" ").length;
            if (words > 3) return false;
            if (p.equals(key) || (p.startsWith(key + " ") && words - keyWords <= 2)) mine = true;
        }
        return mine;
    }

    /** True when every name on the line is a known student (the start of another student's section). */
    boolean isNameLine(String n, Set<String> heads) {
        List<String> names = headingNames(n);
        if (names.isEmpty()) return false;
        for (String p : names) {
            boolean known = heads.contains(p);
            for (String h : heads) {
                if (!known && p.startsWith(h + " ")) known = true;
            }
            if (!known) return false;
        }
        return true;
    }

    /**
     * A heading is a line of names (one or several separated by ،); the remark below applies to every name on it. The
     * section runs until a blank line (one blank line right after the heading is tolerated) or the next student's
     * heading. Without a heading, lines containing the name are used.
     */
    Hit hit(String txt, String key, Pattern pat, Set<String> heads) {
        String[] lines = txt.split("\\r?\\n");
        StringBuilder blocks = new StringBuilder(), body = new StringBuilder();
        boolean on = false, absent = false, late = false, skippedBlank = false;
        int got = 0;
        for (String line : lines) {
            String n = Text.norm(line);
            if (isAbsentLine(n)) {
                if (absentMatches(n, key)) absent = true;
                on = false;
                continue;
            }
            if (isLateLine(n)) {
                if (absentMatches(n, key)) late = true;
                on = false;
                continue;
            }
            if (on) {
                if (n.isEmpty()) {
                    if (got == 0 && !skippedBlank) {
                        skippedBlank = true;
                        continue;
                    }
                    on = false;
                } else if (!isHeadingFor(n, key) && isNameLine(n, heads)) {
                    on = false;
                } else {
                    blocks.append(line).append("\n");
                    body.append(line).append("\n");
                    got++;
                    continue;
                }
            }
            if (!n.isEmpty() && isHeadingFor(n, key)) {
                on = true;
                got = 0;
                skippedBlank = false;
                blocks.append(line).append("\n");
            }
        }
        if (blocks.length() > 0) return new Hit(absent, late, blocks.toString(), body.toString());
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            String n = Text.norm(line);
            if (!isAbsentLine(n) && !isLateLine(n) && pat.matcher(n).find()) out.append(line).append("\n");
        }
        return new Hit(absent, late, out.toString(), out.toString());
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
        btn("👩‍🏫 معلم‌ها", x -> listEditor("معلم‌ها", "نام هر معلم در یک خط", st.teachers(), 0));
        btn("☑️ توضیحات آماده وضعیت بچه‌ها (تا ۸ مورد)", x -> listEditor("توضیحات آماده", "هر توضیح در یک خط (حداکثر ۸ مورد)", st.options(), Store.MAX_OPTIONS));
        btn("💾 پشتیبان‌گیری از داده‌ها", x -> backup());
        btn("♻️ بازیابی / ادغام پشتیبان", x -> pick(PICK_BACKUP));
        btn("ℹ️ درباره برنامه", x -> info("کلاس 1.8", "عامل سبک مدیریت آموزشگاه زبان\nAndroid اول، iOS در مرحله بعد\nداده‌ها روی خود گوشی نگهداری می‌شوند."));
        backBtn();
    }

    /** One item per line; max == 0 means unlimited. Teachers and remark options share this editor. */
    void listEditor(final String title, String hint, List<String> cur, final int max) {
        final EditText e = edit(hint, joinLines(cur), true);
        e.setMinLines(8);
        LinearLayout l = form();
        l.addView(e);
        formDialog(title, l, () -> {
            List<String> out = new ArrayList<>();
            for (String line : e.getText().toString().split("\\r?\\n")) {
                String t = line.trim();
                if (!t.isEmpty() && !out.contains(t)) out.add(t);
            }
            if (max > 0 && out.size() > max) {
                toast("حداکثر " + max + " مورد مجاز است");
                return false;
            }
            if (max > 0) st.setOptions(out);
            else st.setTeachers(out);
            toast("ذخیره شد");
            return true;
        });
    }

    String joinLines(List<String> l) {
        StringBuilder b = new StringBuilder();
        for (String v : l) b.append(v).append("\n");
        return b.toString().trim();
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
        new AlertDialog.Builder(this).setTitle("بازیابی اطلاعات")
                .setMessage("ادغام: اطلاعات فایل به داده‌های فعلی اضافه می‌شود و چیزی حذف یا عوض نمی‌شود (مناسب جمع‌بندی گزارش معلم‌ها).\n\n"
                        + "جایگزینی: اطلاعات فعلی کاملاً با فایل عوض می‌شود.")
                .setPositiveButton("ادغام", (d, w) -> {
                    try {
                        Merge.Result m = Merge.into(st, r);
                        home();
                        info("نتیجه ادغام", m.summary());
                    } catch (JSONException e) {
                        toast("خطا در ادغام");
                    }
                })
                .setNeutralButton("جایگزینی", (d, w) -> confirm("جایگزینی کامل", "اطلاعات فعلی حذف و با فایل عوض می‌شود. ادامه؟", "جایگزین", () -> {
                    st.replaceWith(r);
                    toast("بازیابی با موفقیت انجام شد");
                    home();
                }))
                .setNegativeButton("لغو", null).show();
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
                case PICK_EXPORT: writeExport(u); break;
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
        importReportText(readText(u));
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
