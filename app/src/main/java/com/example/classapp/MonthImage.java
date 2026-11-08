package com.example.classapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/** The month page of the calendar drawn as a picture: weekday header, a box for every day with the names of that day's classes, holidays and events below. Saturday is on the right. */
final class MonthImage {
    private MonthImage() {}

    static final int W = 1080, M = 36, G = 6;

    private static final class Cell {
        int d;
        String holiday;
        List<String[]> names = new ArrayList<>(); // {lines joined by \n, kind}
        List<Cal.Item> evs = new ArrayList<>();
    }

    private static Paint paint(float size, int color, boolean bold, Paint.Align al) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(size);
        p.setColor(color);
        p.setFakeBoldText(bold);
        p.setTextAlign(al);
        return p;
    }

    private static String fit(Paint p, String t, float w) {
        if (p.measureText(t) <= w) return t;
        while (t.length() > 1 && p.measureText(t + "…") > w) t = t.substring(0, t.length() - 1);
        return t + "…";
    }

    /** At most 3 lines, broken at spaces; a longer text is cut with … on the last line. */
    static List<String> wrap(Paint p, String t, float w) {
        List<String> out = new ArrayList<>();
        String cur = "";
        for (String word : t.split(" ")) {
            String u = cur.isEmpty() ? word : cur + " " + word;
            if (!cur.isEmpty() && p.measureText(u) > w) {
                out.add(cur);
                cur = word;
            } else {
                cur = u;
            }
        }
        out.add(cur);
        while (out.size() > 3) {
            String last = out.remove(out.size() - 1);
            out.set(out.size() - 1, out.get(out.size() - 1) + " " + last);
        }
        for (int i = 0; i < out.size(); i++) out.set(i, fit(p, out.get(i), w));
        return out;
    }

    /** Returns null when the page would be too tall. */
    static Bitmap render(Context ctx, JSONArray classes, JSONArray events, int jy, int jm, String cf) {
        final int cw = (W - 2 * M - 6 * G) / 7;
        long first = Cal.epoch(jy, jm, 1);
        int off = Cal.wd(first), n = Cal.monthLen(jy, jm);
        Paint nameP = paint(22, Color.rgb(31, 95, 191), false, Paint.Align.CENTER);
        Cell[] cells = new Cell[n];
        List<List<List<String>>> lines = new ArrayList<>();
        for (int d = 1; d <= n; d++) {
            Cell c = new Cell();
            c.d = d;
            Cal.Day dy = Cal.day(classes, events, first + d - 1, cf, false);
            c.holiday = dy.holiday;
            List<List<String>> ln = new ArrayList<>();
            for (Cal.Item it : dy.items) {
                if ("event".equals(it.kind)) {
                    c.evs.add(it);
                    continue;
                }
                String t = it.cls + ("cancelled".equals(it.kind) ? " (لغو)" : "makeup".equals(it.kind) ? " (جبرانی)" : "");
                c.names.add(new String[]{t, it.kind});
                ln.add(wrap(nameP, t, cw - 10));
            }
            cells[d - 1] = c;
            lines.add(ln);
        }
        int rows = (off + n + 6) / 7;
        int[] rh = new int[rows];
        for (int r = 0; r < rows; r++) {
            int mx = 0;
            for (int k = 0; k < 7; k++) {
                int i = r * 7 + k - off;
                if (i < 0 || i >= n) continue;
                int cnt = 0;
                for (List<String> l : lines.get(i)) cnt += l.size();
                if (!cells[i].evs.isEmpty()) cnt++;
                mx = Math.max(mx, cnt);
            }
            rh[r] = Math.max(96, 52 + mx * 26 + 14);
        }
        List<Cell> list = new ArrayList<>();
        for (Cell c : cells) if (c.holiday != null || !c.evs.isEmpty()) list.add(c);
        int head = 150, hd = 44, gridH = 0;
        for (int h : rh) gridH += h + G;
        int legH = 70;
        for (Cell c : list) legH += (c.holiday != null ? 32 : 0) + c.evs.size() * 32 + 16;
        int H = head + hd + gridH + legH + M;
        if (H > 12000) return null;
        Bitmap bmp = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        cv.drawColor(Color.WHITE);
        Bitmap logo = BitmapFactory.decodeResource(ctx.getResources(), R.drawable.logo);
        if (logo != null) {
            Paint lp = new Paint(Paint.FILTER_BITMAP_FLAG);
            lp.setAlpha(26);
            float z = W * 0.8f;
            cv.drawBitmap(logo, null, new RectF((W - z) / 2f, (H - z) / 2f, (W + z) / 2f, (H + z) / 2f), lp);
        }
        int dark = Color.rgb(25, 35, 60), grey = Color.rgb(90, 103, 133), red = Color.rgb(221, 51, 51);
        cv.drawText("تقویم آموزشی — " + Cal.MONTHS[jm - 1] + " " + jy, W / 2f, 76, paint(54, dark, true, Paint.Align.CENTER));
        cv.drawText(cf == null || cf.isEmpty() ? "همه کلاس‌ها" : "کلاس: " + cf, W / 2f, 124, paint(30, grey, false, Paint.Align.CENTER));
        float y = head;
        for (int k = 0; k < 7; k++) {
            float x = W - M - cw - k * (cw + G);
            cv.drawText(Cal.DAYS[k].substring(0, 1), x + cw / 2f, y + 32, paint(28, k == 6 ? red : grey, true, Paint.Align.CENTER));
        }
        y += hd;
        Paint fillP = new Paint(Paint.ANTI_ALIAS_FLAG), strokeP = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokeP.setStyle(Paint.Style.STROKE);
        strokeP.setStrokeWidth(2);
        strokeP.setColor(Color.rgb(201, 210, 230));
        for (int r = 0; r < rows; r++) {
            for (int k = 0; k < 7; k++) {
                int i = r * 7 + k - off;
                if (i < 0 || i >= n) continue;
                Cell c = cells[i];
                float x = W - M - cw - k * (cw + G);
                RectF rc = new RectF(x, y, x + cw, y + rh[r]);
                fillP.setColor(c.holiday != null ? Color.rgb(253, 234, 234) : Color.rgb(242, 245, 251));
                cv.drawRoundRect(rc, 12, 12, fillP);
                cv.drawRoundRect(rc, 12, 12, strokeP);
                cv.drawText(String.valueOf(c.d), x + cw / 2f, y + 38, paint(32, (k == 6 || c.holiday != null) ? red : dark, true, Paint.Align.CENTER));
                float ty = y + 66;
                List<List<String>> ln = lines.get(i);
                for (int q = 0; q < c.names.size(); q++) {
                    boolean canc = "cancelled".equals(c.names.get(q)[1]);
                    Paint p = paint(22, canc ? Color.rgb(153, 153, 153) : Color.rgb(31, 95, 191), false, Paint.Align.CENTER);
                    p.setStrikeThruText(canc);
                    for (String s : ln.get(q)) {
                        cv.drawText(s, x + cw / 2f, ty, p);
                        ty += 26;
                    }
                    ty += 2;
                }
                if (!c.evs.isEmpty()) cv.drawText("📝 " + c.evs.size() + " رویداد", x + cw / 2f, ty, paint(22, Color.rgb(154, 107, 0), false, Paint.Align.CENTER));
            }
            y += rh[r] + G;
        }
        y += 18;
        cv.drawText(list.isEmpty() ? "رویداد یا تعطیلی خاصی ثبت نشده است" : "تعطیلی‌ها و رویدادهای این ماه", W - M, y + 24, paint(30, dark, true, Paint.Align.RIGHT));
        y += 44;
        for (Cell c : list) {
            cv.drawText(c.d + " " + Cal.MONTHS[jm - 1], W - M, y + 24, paint(26, dark, true, Paint.Align.RIGHT));
            y += 32;
            if (c.holiday != null) {
                Paint p = paint(26, red, false, Paint.Align.RIGHT);
                cv.drawText("🔴 " + fit(p, c.holiday, W - 2 * M - 20), W - M - 10, y + 24, p);
                y += 32;
            }
            for (Cal.Item it : c.evs) {
                Paint p = paint(26, Color.rgb(51, 51, 51), false, Paint.Align.RIGHT);
                cv.drawText(fit(p, Cal.line(it), W - 2 * M - 20), W - M - 10, y + 24, p);
                y += 32;
            }
            y += 16;
        }
        return bmp;
    }
}
