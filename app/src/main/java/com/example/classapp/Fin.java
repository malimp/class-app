package com.example.classapp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Finance logic (amounts in Toman). Pure logic: no Android classes.
 * Data lives in Store.fin and is NEVER part of the normal backup: it has its own backup file («class-fin»).
 *
 * fin = {
 *   fees:     { normClass: {cls, mode: term|session, fee, ts} }        term fee per class, or fee per session (private classes)
 *   accounts: { studentCode: {name, fee (optional personal fee), discount, ts} }
 *   payments: [ {id, code, name, date, amount, method, note, ts} ]
 *   installs: [ {id, code, name, date(due), amount, ts} ]
 *   rates:    { normTeacher: {teacher, mode: session|month, amount, ts} }
 *   salaries: [ {id, teacher, date, amount, note, ts} ]               paid to teachers
 *   ledger:   [ {id, date, kind: income|expense, title, amount, note, ts} ]
 * }
 * Deleted records stay as {id, deleted:true, ts} so a merge never brings them back.
 */
final class Fin {
    private Fin() {}

    static final String[] METHODS = {"cash", "card", "transfer", "other"};
    static final String[] KEYS_ARR = {"payments", "installs", "salaries", "ledger"};
    static final String[] KEYS_MAP = {"fees", "accounts", "rates"};
    private static final Random RND = new Random();
    private static int counter = 0;

    // ---------- structure ----------

    static JSONObject fix(JSONObject f) {
        if (f == null) f = new JSONObject();
        try {
            for (String k : KEYS_ARR) if (!(f.opt(k) instanceof JSONArray)) f.put(k, new JSONArray());
            for (String k : KEYS_MAP) if (!(f.opt(k) instanceof JSONObject)) f.put(k, new JSONObject());
        } catch (JSONException ignored) {
            // keys are valid
        }
        return f;
    }

    static String newId(String prefix) {
        return prefix + System.currentTimeMillis() + "_" + (counter++) + "_" + (1000 + RND.nextInt(9000));
    }

    private static void put(JSONObject o, String k, Object v) {
        try {
            o.put(k, v);
        } catch (JSONException ignored) {
            // keys are valid
        }
    }

    static List<JSONObject> live(JSONObject fin, String key) {
        List<JSONObject> out = new ArrayList<>();
        JSONArray a = fin.optJSONArray(key);
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && !o.optBoolean("deleted", false)) out.add(o);
        }
        return out;
    }

    /** Adds or replaces (same id) a record of an array; stamps the edit time. Returns the stored record. */
    static JSONObject upsert(JSONObject fin, String key, JSONObject rec, String idPrefix) {
        if (rec.optString("id").isEmpty()) put(rec, "id", newId(idPrefix));
        put(rec, "ts", System.currentTimeMillis());
        JSONArray a = fin.optJSONArray(key);
        for (int i = 0; i < a.length(); i++) {
            if (rec.optString("id").equals(a.optJSONObject(i).optString("id"))) {
                try {
                    a.put(i, rec);
                } catch (JSONException ignored) {
                    // index valid
                }
                return rec;
            }
        }
        a.put(rec);
        return rec;
    }

    static void delete(JSONObject fin, String key, String id) {
        JSONArray a = fin.optJSONArray(key);
        for (int i = 0; a != null && i < a.length(); i++) {
            if (id.equals(a.optJSONObject(i).optString("id"))) {
                JSONObject t = new JSONObject();
                put(t, "id", id);
                put(t, "deleted", true);
                put(t, "ts", System.currentTimeMillis());
                try {
                    a.put(i, t);
                } catch (JSONException ignored) {
                    // index valid
                }
                return;
            }
        }
    }

    static JSONObject find(JSONObject fin, String key, String id) {
        JSONArray a = fin.optJSONArray(key);
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && id.equals(o.optString("id")) && !o.optBoolean("deleted", false)) return o;
        }
        return null;
    }

    // ---------- money ----------

    /** Parses an amount typed by the user (any digit script, with , ٬ ، separators). Returns -1 when empty or invalid. */
    static long money(String s) {
        String d = Text.digits(s == null ? "" : s);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < d.length(); i++) {
            char c = d.charAt(i);
            if (c >= '0' && c <= '9') b.append(c);
            else if (c == ',' || c == '٬' || c == '،' || c == ' ' || c == ' ' || c == '‌' || c == '٫') continue;
            else return -1;
        }
        if (b.length() == 0 || b.length() > 13) return -1;
        return Long.parseLong(b.toString());
    }

    /** 1250000 -> "1,250,000". */
    static String fmt(long v) {
        boolean neg = v < 0;
        String s = Long.toString(Math.abs(v));
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) b.append(',');
            b.append(s.charAt(i));
        }
        return (neg ? "-" : "") + b;
    }

    static String toman(long v) {
        return fmt(v) + " تومان";
    }

    static String methodLabel(String m) {
        switch (m == null ? "" : m) {
            case "cash": return "نقدی";
            case "card": return "کارت";
            case "transfer": return "واریز";
            default: return "سایر";
        }
    }

    // ---------- students ----------

    static String nameKey(JSONObject s) {
        return Text.norm(s.optString("first")) + "|" + Text.norm(s.optString("last"));
    }

    static String name(JSONObject s) {
        return (s.optString("first") + " " + s.optString("last")).trim();
    }

    /** The student a record points to: by code, else by the saved full name. Null when neither exists any more. */
    static JSONObject student(Store st, JSONObject rec) {
        JSONObject s = st.student(rec.optString("code"));
        if (s != null) return s;
        String n = Text.norm(rec.optString("name"));
        if (n.isEmpty()) return null;
        for (int i = 0; i < st.students.length(); i++) {
            JSONObject o = st.students.optJSONObject(i);
            if (Text.norm(name(o)).equals(n)) return o;
        }
        return null;
    }

    /** Records of this student, matched through the same resolution rule. */
    static List<JSONObject> recsOf(Store st, String key, String code) {
        List<JSONObject> out = new ArrayList<>();
        for (JSONObject r : live(st.fin, key)) {
            JSONObject s = student(st, r);
            String c = s != null ? s.optString("code") : r.optString("code");
            if (c.equals(code)) out.add(r);
        }
        return out;
    }

    static JSONObject classOf(Store st, String code) {
        int ci = st.classIndexOf(code);
        return ci < 0 ? null : st.classes.optJSONObject(ci);
    }

    // ---------- fees and accounts ----------

    static JSONObject feeRule(JSONObject fin, String cls) {
        return fin.optJSONObject("fees").optJSONObject(Text.norm(cls));
    }

    static void setFee(JSONObject fin, String cls, String mode, long fee) {
        JSONObject o = new JSONObject();
        put(o, "cls", cls);
        put(o, "mode", "session".equals(mode) ? "session" : "term");
        put(o, "fee", fee);
        put(o, "ts", System.currentTimeMillis());
        put(fin.optJSONObject("fees"), Text.norm(cls), o);
    }

    static JSONObject account(JSONObject fin, String code) {
        return fin.optJSONObject("accounts").optJSONObject(code);
    }

    /** personalFee < 0 means "use the class fee". */
    static void setAccount(JSONObject fin, String code, String name, long personalFee, long discount) {
        JSONObject o = new JSONObject();
        put(o, "name", name);
        if (personalFee >= 0) put(o, "fee", personalFee);
        put(o, "discount", Math.max(0, discount));
        put(o, "ts", System.currentTimeMillis());
        put(fin.optJSONObject("accounts"), code, o);
    }

    /** Sessions a class has held until today (not past its end date). Without a start date: the number of reported days. */
    static int heldSessions(Store st, JSONObject cls, long today) {
        long s = Cal.parse(cls.optString("start"));
        long en = Cal.parse(cls.optString("end"));
        long to = en != JalaliDate.INVALID && en < today ? en : today;
        if (s != JalaliDate.INVALID) return Cal.sessions(cls, st.events, s, to).size();
        Set<String> days = new HashSet<>();
        String cn = Text.norm(cls.optString("name"));
        for (int i = 0; i < st.reports.length(); i++) {
            JSONObject r = st.reports.optJSONObject(i);
            if (r != null && Text.norm(r.optString("class")).equals(cn)) days.add(Text.norm(r.optString("date")));
        }
        return days.size();
    }

    static final class Acc {
        JSONObject student;
        String code, cls = "", mode = "term";
        long unit, sessions, base, discount, charge, paid, balance;
    }

    static Acc acc(Store st, JSONObject student) {
        Acc a = new Acc();
        a.student = student;
        a.code = student.optString("code");
        JSONObject c = classOf(st, a.code);
        JSONObject rule = null;
        if (c != null) {
            a.cls = c.optString("name");
            rule = feeRule(st.fin, a.cls);
        }
        if (rule != null) {
            a.mode = rule.optString("mode", "term");
            a.unit = Math.max(0, rule.optLong("fee", 0));
        }
        JSONObject ac = account(st.fin, a.code);
        if (ac != null) {
            if (ac.has("fee") && ac.optLong("fee", -1) >= 0) a.unit = ac.optLong("fee");
            a.discount = Math.max(0, ac.optLong("discount", 0));
        }
        if ("session".equals(a.mode) && c != null) {
            a.sessions = heldSessions(st, c, JalaliDate.today());
            a.base = a.unit * a.sessions;
        } else {
            a.base = a.unit;
        }
        a.charge = Math.max(0, a.base - a.discount);
        for (JSONObject p : recsOf(st, "payments", a.code)) a.paid += Math.max(0, p.optLong("amount", 0));
        a.balance = a.charge - a.paid;
        return a;
    }

    // ---------- installments ----------

    static final class Inst {
        JSONObject rec;
        long due, amount, covered;
        String status; // paid | partial | overdue | upcoming
    }

    /** Installments of one student in due order. Payments cover them first-in-first-out. */
    static List<Inst> installments(Store st, String code, long paid, long today) {
        List<Inst> out = new ArrayList<>();
        for (JSONObject r : recsOf(st, "installs", code)) {
            Inst i = new Inst();
            i.rec = r;
            i.due = Cal.parse(r.optString("date"));
            i.amount = Math.max(0, r.optLong("amount", 0));
            out.add(i);
        }
        Collections.sort(out, new Comparator<Inst>() {
            @Override
            public int compare(Inst a, Inst b) {
                return a.due < b.due ? -1 : (a.due > b.due ? 1 : a.rec.optString("id").compareTo(b.rec.optString("id")));
            }
        });
        long left = paid;
        for (Inst i : out) {
            i.covered = Math.min(left, i.amount);
            left -= i.covered;
            if (i.covered >= i.amount) i.status = "paid";
            else if (i.due != JalaliDate.INVALID && i.due < today) i.status = "overdue";
            else i.status = i.covered > 0 ? "partial" : "upcoming";
        }
        return out;
    }

    static String statusLabel(String s) {
        switch (s) {
            case "paid": return "پرداخت شده";
            case "partial": return "بخشی پرداخت شده";
            case "overdue": return "معوق";
            default: return "در انتظار";
        }
    }

    /** Unpaid installments falling due on day e: one line each, for the calendar. */
    static List<String> dueLines(Store st, long e) {
        List<String> out = new ArrayList<>();
        long today = JalaliDate.today();
        Set<String> seen = new HashSet<>();
        for (JSONObject r : live(st.fin, "installs")) {
            if (Cal.parse(r.optString("date")) != e) continue;
            JSONObject s = student(st, r);
            String code = s != null ? s.optString("code") : r.optString("code");
            if (!seen.add(code)) continue;
            long paid = 0;
            for (JSONObject p : recsOf(st, "payments", code)) paid += Math.max(0, p.optLong("amount", 0));
            for (Inst i : installments(st, code, paid, today)) {
                if (i.due == e && !"paid".equals(i.status)) {
                    String nm = s != null ? name(s) : r.optString("name");
                    out.add("💰 قسط " + nm + " — " + toman(i.amount - i.covered) + (i.status.equals("overdue") ? " (معوق)" : ""));
                }
            }
        }
        return out;
    }

    // ---------- students overview ----------

    static List<Acc> allAccounts(Store st, String clsFilter) {
        List<Acc> out = new ArrayList<>();
        for (int i = 0; i < st.students.length(); i++) {
            JSONObject s = st.students.optJSONObject(i);
            Acc a = acc(st, s);
            if (!clsFilter.isEmpty() && !Text.norm(a.cls).equals(Text.norm(clsFilter))) continue;
            if (a.charge == 0 && a.paid == 0) continue;
            out.add(a);
        }
        return out;
    }

    static List<Acc> debtors(Store st, String clsFilter) {
        List<Acc> out = new ArrayList<>();
        for (Acc a : allAccounts(st, clsFilter)) if (a.balance > 0) out.add(a);
        Collections.sort(out, new Comparator<Acc>() {
            @Override
            public int compare(Acc a, Acc b) {
                return a.balance > b.balance ? -1 : (a.balance < b.balance ? 1 : 0);
            }
        });
        return out;
    }

    // ---------- teachers ----------

    static void setRate(JSONObject fin, String teacher, String mode, long amount) {
        JSONObject o = new JSONObject();
        put(o, "teacher", teacher);
        put(o, "mode", "month".equals(mode) ? "month" : "session");
        put(o, "amount", amount);
        put(o, "ts", System.currentTimeMillis());
        put(fin.optJSONObject("rates"), Text.norm(teacher), o);
    }

    static JSONObject rate(JSONObject fin, String teacher) {
        return fin.optJSONObject("rates").optJSONObject(Text.norm(teacher));
    }

    static List<String> teachers(Store st) {
        List<String> out = st.teachers();
        Set<String> seen = new HashSet<>();
        for (String t : out) seen.add(Text.norm(t));
        for (int i = 0; i < st.reports.length(); i++) {
            String t = st.reports.optJSONObject(i).optString("teacher").trim();
            if (!t.isEmpty() && seen.add(Text.norm(t))) out.add(t);
        }
        return out;
    }

    /** Sessions a teacher taught = distinct (day, class) reports written by this teacher in the period. */
    static int teacherSessions(Store st, String teacher, long from, long to) {
        Set<String> s = new HashSet<>();
        String tn = Text.norm(teacher);
        for (int i = 0; i < st.reports.length(); i++) {
            JSONObject r = st.reports.optJSONObject(i);
            if (r == null || !Text.norm(r.optString("teacher")).equals(tn)) continue;
            long d = Cal.parse(r.optString("date"));
            if (d == JalaliDate.INVALID || d < from || d > to) continue;
            s.add(d + "|" + Text.norm(r.optString("class")));
        }
        return s.size();
    }

    /** Months (Jalali) in which the teacher wrote at least one report. */
    static int teacherMonths(Store st, String teacher, long from, long to) {
        Set<String> s = new HashSet<>();
        String tn = Text.norm(teacher);
        for (int i = 0; i < st.reports.length(); i++) {
            JSONObject r = st.reports.optJSONObject(i);
            if (r == null || !Text.norm(r.optString("teacher")).equals(tn)) continue;
            long d = Cal.parse(r.optString("date"));
            if (d == JalaliDate.INVALID || d < from || d > to) continue;
            int[] j = Cal.jal(d);
            s.add(j[0] + "/" + j[1]);
        }
        return s.size();
    }

    static long teacherAccrued(Store st, String teacher, long from, long to) {
        JSONObject r = rate(st.fin, teacher);
        if (r == null) return 0;
        long amt = Math.max(0, r.optLong("amount", 0));
        return "month".equals(r.optString("mode")) ? amt * teacherMonths(st, teacher, from, to) : amt * teacherSessions(st, teacher, from, to);
    }

    static long teacherPaid(Store st, String teacher, long from, long to) {
        long sum = 0;
        String tn = Text.norm(teacher);
        for (JSONObject r : live(st.fin, "salaries")) {
            long d = Cal.parse(r.optString("date"));
            if (!Text.norm(r.optString("teacher")).equals(tn)) continue;
            if (d != JalaliDate.INVALID && (d < from || d > to)) continue;
            sum += Math.max(0, r.optLong("amount", 0));
        }
        return sum;
    }

    // ---------- month summary ----------

    static final long ALL_FROM = 0, ALL_TO = 1000000;

    /** {tuition income, other income, expenses, teacher salaries paid, net}. */
    static long[] month(Store st, int jy, int jm) {
        long from = Cal.epoch(jy, jm, 1), to = Cal.epoch(jy, jm, Cal.monthLen(jy, jm));
        long tuition = 0, other = 0, exp = 0, sal = 0;
        for (JSONObject p : live(st.fin, "payments")) {
            long d = Cal.parse(p.optString("date"));
            if (d >= from && d <= to) tuition += Math.max(0, p.optLong("amount", 0));
        }
        for (JSONObject l : live(st.fin, "ledger")) {
            long d = Cal.parse(l.optString("date"));
            if (d < from || d > to) continue;
            if ("income".equals(l.optString("kind"))) other += Math.max(0, l.optLong("amount", 0));
            else exp += Math.max(0, l.optLong("amount", 0));
        }
        for (JSONObject sa : live(st.fin, "salaries")) {
            long d = Cal.parse(sa.optString("date"));
            if (d >= from && d <= to) sal += Math.max(0, sa.optLong("amount", 0));
        }
        return new long[]{tuition, other, exp, sal, tuition + other - exp - sal};
    }

    // ---------- texts (school name first line, no hidden teacher marker) ----------

    private static String head(String title) {
        return Reports.SCHOOL + "\n" + title + "\n";
    }

    static String studentText(Store st, JSONObject student) {
        Acc a = acc(st, student);
        long today = JalaliDate.today();
        StringBuilder b = new StringBuilder(head("صورت‌حساب مالی"));
        b.append("زبان‌آموز: ").append(name(student)).append("\n");
        if (!a.cls.isEmpty()) b.append("کلاس: ").append(a.cls).append("\n");
        if ("session".equals(a.mode)) b.append("شهریه هر جلسه: ").append(toman(a.unit)).append(" × ").append(a.sessions).append(" جلسه\n");
        b.append("شهریه: ").append(toman(a.base)).append("\n");
        if (a.discount > 0) b.append("تخفیف: ").append(toman(a.discount)).append("\n");
        b.append("مبلغ قابل پرداخت: ").append(toman(a.charge)).append("\n");
        b.append("پرداخت شده: ").append(toman(a.paid)).append("\n");
        b.append(a.balance >= 0 ? "مانده بدهی: " : "بستانکار: ").append(toman(Math.abs(a.balance))).append("\n");
        List<JSONObject> pays = recsOf(st, "payments", a.code);
        sortByDate(pays);
        if (!pays.isEmpty()) {
            b.append("\nپرداخت‌ها:\n");
            for (JSONObject p : pays) {
                b.append("• ").append(p.optString("date")).append(" — ").append(toman(p.optLong("amount"))).append(" — ").append(methodLabel(p.optString("method")));
                if (!p.optString("note").trim().isEmpty()) b.append(" (").append(p.optString("note").trim()).append(")");
                b.append("\n");
            }
        }
        List<Inst> ins = installments(st, a.code, a.paid, today);
        if (!ins.isEmpty()) {
            b.append("\nاقساط:\n");
            for (Inst i : ins) {
                b.append("• ").append(i.rec.optString("date")).append(" — ").append(toman(i.amount)).append(" — ").append(statusLabel(i.status)).append("\n");
            }
        }
        return b.toString().trim();
    }

    static String debtorsText(Store st, String clsFilter) {
        StringBuilder b = new StringBuilder(head("لیست بدهکاران" + (clsFilter.isEmpty() ? "" : " — کلاس " + clsFilter)));
        List<Acc> l = debtors(st, clsFilter);
        long sum = 0;
        for (Acc a : l) {
            sum += a.balance;
            b.append("• ").append(name(a.student));
            if (!a.cls.isEmpty() && clsFilter.isEmpty()) b.append(" (").append(a.cls).append(")");
            b.append(" — ").append(toman(a.balance)).append("\n");
        }
        if (l.isEmpty()) b.append("بدهکاری وجود ندارد.\n");
        else b.append("\nمجموع بدهی: ").append(toman(sum)).append("\n");
        return b.toString().trim();
    }

    static String classText(Store st, String cls) {
        StringBuilder b = new StringBuilder(head("وضعیت مالی کلاس " + cls));
        long ch = 0, pd = 0;
        for (Acc a : allAccounts(st, cls)) {
            ch += a.charge;
            pd += a.paid;
            b.append("• ").append(name(a.student)).append(" — شهریه ").append(fmt(a.charge)).append(" — پرداخت ").append(fmt(a.paid))
                    .append(a.balance >= 0 ? " — مانده " : " — بستانکار ").append(fmt(Math.abs(a.balance))).append("\n");
        }
        b.append("\nجمع شهریه: ").append(toman(ch)).append("\nجمع پرداخت: ").append(toman(pd)).append("\nجمع مانده: ").append(toman(ch - pd)).append("\n");
        return b.toString().trim();
    }

    static String monthText(Store st, int jy, int jm) {
        long[] m = month(st, jy, jm);
        StringBuilder b = new StringBuilder(head("گزارش مالی " + Cal.MONTHS[jm - 1] + " " + jy));
        b.append("درآمد شهریه: ").append(toman(m[0])).append("\n");
        b.append("درآمد دیگر: ").append(toman(m[1])).append("\n");
        b.append("هزینه‌ها: ").append(toman(m[2])).append("\n");
        b.append("حقوق معلم‌ها: ").append(toman(m[3])).append("\n");
        b.append(m[4] >= 0 ? "سود خالص: " : "زیان: ").append(toman(Math.abs(m[4]))).append("\n");
        long from = Cal.epoch(jy, jm, 1), to = Cal.epoch(jy, jm, Cal.monthLen(jy, jm));
        List<JSONObject> led = new ArrayList<>();
        for (JSONObject l : live(st.fin, "ledger")) {
            long d = Cal.parse(l.optString("date"));
            if (d >= from && d <= to) led.add(l);
        }
        sortByDate(led);
        if (!led.isEmpty()) {
            b.append("\nدرآمد و هزینه‌های ثبت‌شده:\n");
            for (JSONObject l : led) {
                b.append("• ").append(l.optString("date")).append(" — ").append("income".equals(l.optString("kind")) ? "درآمد" : "هزینه")
                        .append(": ").append(l.optString("title")).append(" — ").append(toman(l.optLong("amount"))).append("\n");
            }
        }
        return b.toString().trim();
    }

    static String teachersText(Store st, long from, long to, String label) {
        StringBuilder b = new StringBuilder(head("حقوق معلم‌ها" + (label.isEmpty() ? "" : " — " + label)));
        for (String t : teachers(st)) {
            JSONObject r = rate(st.fin, t);
            int n = teacherSessions(st, t, from, to);
            long acc = teacherAccrued(st, t, from, to), paid = teacherPaid(st, t, from, to);
            if (r == null && n == 0 && paid == 0) continue;
            b.append("• ").append(t).append(" — ").append(n).append(" جلسه");
            if (r != null) b.append(" — ").append("month".equals(r.optString("mode")) ? "ماهانه " : "هر جلسه ").append(fmt(r.optLong("amount")));
            b.append("\n   محاسبه‌شده: ").append(toman(acc)).append(" — پرداخت‌شده: ").append(toman(paid)).append(" — مانده: ").append(toman(acc - paid)).append("\n");
        }
        return b.toString().trim();
    }

    static String receiptText(Store st, JSONObject pay) {
        JSONObject s = student(st, pay);
        StringBuilder b = new StringBuilder(head("رسید پرداخت"));
        b.append("شماره رسید: ").append(receiptNo(pay)).append("\n");
        b.append("تاریخ: ").append(pay.optString("date")).append("\n");
        b.append("نام زبان‌آموز: ").append(s != null ? name(s) : pay.optString("name")).append("\n");
        if (s != null) {
            String c = st.classNameOf(s.optString("code"));
            if (!c.isEmpty()) b.append("کلاس: ").append(c).append("\n");
        }
        b.append("مبلغ پرداختی: ").append(toman(pay.optLong("amount"))).append("\n");
        b.append("روش پرداخت: ").append(methodLabel(pay.optString("method"))).append("\n");
        if (!pay.optString("note").trim().isEmpty()) b.append("توضیح: ").append(pay.optString("note").trim()).append("\n");
        if (s != null) {
            Acc a = acc(st, s);
            b.append(a.balance >= 0 ? "مانده بدهی: " : "بستانکار: ").append(toman(Math.abs(a.balance))).append("\n");
        }
        return b.toString().trim();
    }

    /** Receipt number: the last 7 digits of the creation time inside the id. */
    static String receiptNo(JSONObject pay) {
        String id = pay.optString("id");
        StringBuilder d = new StringBuilder();
        for (int i = 1; i < id.length() && id.charAt(i) != '_'; i++) if (Character.isDigit(id.charAt(i))) d.append(id.charAt(i));
        String x = d.toString();
        return x.length() > 7 ? x.substring(x.length() - 7) : x;
    }

    /** A friendly reminder message for one debtor (no school header line besides the first). */
    static String reminderText(Store st, JSONObject student) {
        Acc a = acc(st, student);
        StringBuilder b = new StringBuilder(Reports.SCHOOL + "\n");
        b.append("سلام ").append(name(student)).append(" عزیز\n");
        b.append("یادآوری شهریه");
        if (!a.cls.isEmpty()) b.append(" کلاس ").append(a.cls);
        b.append("\nمانده شهریه: ").append(toman(Math.max(0, a.balance))).append("\n");
        List<Inst> ins = installments(st, a.code, a.paid, JalaliDate.today());
        for (Inst i : ins) {
            if ("overdue".equals(i.status)) b.append("قسط معوق ").append(i.rec.optString("date")).append(": ").append(toman(i.amount - i.covered)).append("\n");
        }
        for (Inst i : ins) {
            if ("upcoming".equals(i.status) || "partial".equals(i.status)) {
                b.append("قسط بعدی ").append(i.rec.optString("date")).append(": ").append(toman(i.amount - i.covered)).append("\n");
                break;
            }
        }
        b.append("لطفاً در اولین فرصت اقدام فرمایید. با تشکر");
        return b.toString();
    }

    static void sortByDate(List<JSONObject> l) {
        Collections.sort(l, new Comparator<JSONObject>() {
            @Override
            public int compare(JSONObject a, JSONObject b) {
                long x = Cal.parse(a.optString("date")), y = Cal.parse(b.optString("date"));
                return x < y ? -1 : (x > y ? 1 : a.optString("id").compareTo(b.optString("id")));
            }
        });
    }

    // ---------- separate finance backup ----------

    static String export(Store st) throws JSONException {
        JSONArray stu = new JSONArray();
        for (int i = 0; i < st.students.length(); i++) {
            JSONObject s = st.students.optJSONObject(i);
            stu.put(new JSONObject().put("code", s.optString("code")).put("first", s.optString("first")).put("last", s.optString("last")));
        }
        return new JSONObject().put("app", "class-fin").put("version", 1).put("exportedAt", System.currentTimeMillis())
                .put("students", stu).put("fin", st.fin).toString(2);
    }

    static String validate(JSONObject r) {
        if (!"class-fin".equals(r.optString("app")) || !(r.opt("fin") instanceof JSONObject)) {
            return "این فایل، پشتیبان مالی برنامه نیست";
        }
        return null;
    }

    /** incoming student code -> local code (by same code + same full name, else by full name); unknown codes stay as they are. */
    private static Map<String, String> codeMap(Store st, JSONObject in) {
        Map<String, String> m = new HashMap<>();
        JSONArray a = in.optJSONArray("students");
        for (int i = 0; a != null && i < a.length(); i++) {
            JSONObject s = a.optJSONObject(i);
            if (s == null) continue;
            String code = s.optString("code");
            JSONObject local = st.student(code);
            if (local != null && nameKey(local).equals(nameKey(s))) {
                m.put(code, code);
                continue;
            }
            for (int k = 0; k < st.students.length(); k++) {
                JSONObject l = st.students.optJSONObject(k);
                if (nameKey(l).equals(nameKey(s))) {
                    m.put(code, l.optString("code"));
                    break;
                }
            }
        }
        return m;
    }

    static final class Result {
        int added, updated;

        String summary() {
            return "مورد جدید: " + added + "\nمورد به‌روز شده (نسخه جدیدتر): " + updated;
        }
    }

    private static JSONObject remapped(JSONObject r, Map<String, String> map) throws JSONException {
        JSONObject c = new JSONObject(r.toString());
        String code = c.optString("code");
        if (!code.isEmpty() && map.containsKey(code)) c.put("code", map.get(code));
        return c;
    }

    /** Union by id (newer ts wins); nothing is deleted. */
    static Result merge(Store st, JSONObject in) throws JSONException {
        Result res = new Result();
        JSONObject f = fix(in.optJSONObject("fin"));
        Map<String, String> map = codeMap(st, in);
        for (String k : KEYS_ARR) {
            JSONArray src = f.optJSONArray(k);
            JSONArray dst = st.fin.optJSONArray(k);
            for (int i = 0; src != null && i < src.length(); i++) {
                JSONObject r = src.optJSONObject(i);
                if (r == null || r.optString("id").isEmpty()) continue;
                JSONObject c = remapped(r, map);
                int at = -1;
                for (int j = 0; j < dst.length() && at < 0; j++) if (c.optString("id").equals(dst.optJSONObject(j).optString("id"))) at = j;
                if (at < 0) {
                    dst.put(c);
                    res.added++;
                } else if (c.optLong("ts", 0) > dst.optJSONObject(at).optLong("ts", 0)) {
                    dst.put(at, c);
                    res.updated++;
                }
            }
        }
        for (String k : KEYS_MAP) {
            JSONObject src = f.optJSONObject(k);
            JSONObject dst = st.fin.optJSONObject(k);
            Iterator<String> it = src.keys();
            List<String> keys = new ArrayList<>();
            while (it.hasNext()) keys.add(it.next());
            for (String key : keys) {
                JSONObject v = src.optJSONObject(key);
                if (v == null) continue;
                String nk = "accounts".equals(k) && map.containsKey(key) ? map.get(key) : key;
                JSONObject old = dst.optJSONObject(nk);
                if (old == null) {
                    dst.put(nk, new JSONObject(v.toString()));
                    res.added++;
                } else if (v.optLong("ts", 0) > old.optLong("ts", 0)) {
                    dst.put(nk, new JSONObject(v.toString()));
                    res.updated++;
                }
            }
        }
        st.saveFin();
        return res;
    }

    /** Replaces the finance data with the backup (student codes translated to this phone's students). */
    static void replace(Store st, JSONObject in) throws JSONException {
        JSONObject f = fix(new JSONObject(in.optJSONObject("fin").toString()));
        Map<String, String> map = codeMap(st, in);
        for (String k : KEYS_ARR) {
            JSONArray a = f.optJSONArray(k), out = new JSONArray();
            for (int i = 0; i < a.length(); i++) if (a.optJSONObject(i) != null) out.put(remapped(a.optJSONObject(i), map));
            f.put(k, out);
        }
        JSONObject acc = f.optJSONObject("accounts"), nacc = new JSONObject();
        Iterator<String> it = acc.keys();
        List<String> keys = new ArrayList<>();
        while (it.hasNext()) keys.add(it.next());
        for (String key : keys) nacc.put(map.containsKey(key) ? map.get(key) : key, acc.optJSONObject(key));
        f.put("accounts", nacc);
        st.fin = f;
        st.saveFin();
    }
}
