package com.example.classapp;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextDirectionHeuristics;
import android.text.TextPaint;

import java.io.ByteArrayOutputStream;

/**
 * A one-page A4 landscape "end of course" certificate, written as a real PDF with the platform's
 * {@link PdfDocument} — no library, and Persian text is shaped by the same RTL engine as the report images.
 */
final class Certificate {
    /** A4 landscape at 72 dpi. */
    static final int W = 842, H = 595;
    private static final int M = 46;
    private static final int INK = Color.rgb(29, 37, 64);
    private static final int BRAND = Color.rgb(63, 81, 181);
    private static final int MUTED = Color.rgb(90, 103, 133);

    private Certificate() {}

    static byte[] pdf(Context ctx, String name, String cls, String level, String date) throws Exception {
        PdfDocument doc = new PdfDocument();
        PdfDocument.PageInfo info = new PdfDocument.PageInfo.Builder(W, H, 1).create();
        PdfDocument.Page page = doc.startPage(info);
        Canvas cv = page.getCanvas();
        cv.drawColor(Color.WHITE);

        Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        border.setStyle(Paint.Style.STROKE);
        border.setStrokeWidth(3f);
        border.setColor(BRAND);
        cv.drawRect(M / 2f, M / 2f, W - M / 2f, H - M / 2f, border);

        Bitmap logo = BitmapFactory.decodeResource(ctx.getResources(), R.drawable.logo);
        if (logo != null) {
            Paint lp = new Paint(Paint.FILTER_BITMAP_FLAG);
            lp.setAlpha(16);
            float size = W * 0.55f;
            float cx = W / 2f, cy = H / 2f;
            cv.drawBitmap(logo, null, new RectF(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2), lp);
        }

        double y = 80;
        y = center(cv, "گواهی پایان دوره", y, 40, BRAND, true);
        y += 12;
        y = center(cv, "Moradi English Class", y, 17, MUTED, false);
        y += 38;
        y = center(cv, "بدین وسیله گواهی می‌شود", y, 18, INK, false);
        y += 30;

        y = center(cv, name, y, 34, BRAND, true);
        y += 14;
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setColor(Color.rgb(201, 210, 230));
        line.setStrokeWidth(1.2f);
        cv.drawLine(W / 2f - 150, (float) y, W / 2f + 150, (float) y, line);
        y += 34;

        String body = "دورهٔ " + cls + (level.isEmpty() ? "" : " — سطح " + level) + " را با موفقیت به پایان رسانده است.";
        y = center(cv, body, y, 18, INK, false);
        y += 60;

        center(cv, "تاریخ: " + date, y, 15, MUTED, false);
        center(cv, "امضای مدیر آموزشگاه", y + 44, 15, MUTED, false);

        doc.finishPage(page);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        doc.writeTo(out);
        doc.close();
        return out.toByteArray();
    }

    /** Draws one centred RTL line across the inner width and returns the y below it. */
    private static double center(Canvas cv, String text, double y, int size, int color, boolean bold) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(size);
        p.setColor(color);
        p.setFakeBoldText(bold);
        StaticLayout sl = StaticLayout.Builder.obtain(text, 0, text.length(), p, W - 2 * M)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .setTextDirection(TextDirectionHeuristics.RTL)
                .build();
        cv.save();
        cv.translate(M, (float) y);
        sl.draw(cv);
        cv.restore();
        return y + sl.getHeight();
    }
}
