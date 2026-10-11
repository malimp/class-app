package com.example.classapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;

import java.io.ByteArrayOutputStream;

/** Draws a report as a picture: white page, the school logo very faint in the background, the text on top. */
final class ReportImage {
    private ReportImage() {}

    static final int W = 1080, PAD = 64, MAX_H = 14000;

    /** Returns null when the report is too long for one picture. */
    static Bitmap render(Context c, String text) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(40);
        p.setColor(Color.rgb(25, 35, 60));
        StaticLayout lay = StaticLayout.Builder.obtain(text, 0, text.length(), p, W - 2 * PAD)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL).setTextDirection(TextDirectionHeuristics.RTL)
                .setLineSpacing(6f, 1f).build();
        int h = Math.max(W, lay.getHeight() + 2 * PAD);
        if (h > MAX_H) return null;
        Bitmap bmp = Bitmap.createBitmap(W, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        cv.drawColor(Color.WHITE);
        Bitmap logo = BitmapFactory.decodeResource(c.getResources(), R.drawable.logo);
        if (logo != null) {
            Paint lp = new Paint(Paint.FILTER_BITMAP_FLAG);
            lp.setAlpha(34); // about 13% opacity: soft, text stays easy to read
            float size = W * 0.9f;
            for (int y = 0; y < h; y += W) {
                float cy = y + Math.min(W, h - y) / 2f;
                cv.drawBitmap(logo, null, new RectF((W - size) / 2f, cy - size / 2f, (W + size) / 2f, cy + size / 2f), lp);
            }
        }
        cv.save();
        cv.translate(PAD, PAD);
        lay.draw(cv);
        cv.restore();
        return bmp;
    }

    static byte[] png(Bitmap b) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.PNG, 100, o);
        return o.toByteArray();
    }
}
