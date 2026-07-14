package br.com.termia.construajogue.input;

import android.graphics.Canvas;
import android.graphics.Paint;

/** Desenho e áreas de toque dos controles visíveis durante a partida. */
final class ControlOverlay {

    private final float density;
    private final float maxRadius;
    private final float knobRadius;
    private final float fireRadius;
    private final float reloadRadius;
    private final float jumpRadius;
    private final Paint ringPaint;
    private final Paint knobPaint;
    private final Paint ghostPaint;
    private final Paint firePaint;
    private final Paint fireActivePaint;
    private final Paint buttonPaint;
    private final Paint labelPaint;

    private float fireX;
    private float fireY;
    private float reloadX;
    private float reloadY;
    private float jumpX;
    private float jumpY;
    private float ghostX;
    private float ghostY;
    private float interactX;
    private float interactY;
    private float interactRadius;

    ControlOverlay(float density, float maxRadius) {
        this.density = density;
        this.maxRadius = maxRadius;
        knobRadius = 26f * density;
        fireRadius = 46f * density;
        reloadRadius = 27f * density;
        jumpRadius = 32f * density;
        ringPaint = stroke(0x55DDE7EE, 3f);
        knobPaint = fill(0x776EA8FE);
        ghostPaint = stroke(0x26DDE7EE, 2f);
        firePaint = fill(0x44FF5544);
        fireActivePaint = fill(0x88FF5544);
        buttonPaint = fill(0x446EA8FE);
        labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        labelPaint.setColor(0xAADDE7EE);
        labelPaint.setTextSize(13f * density);
        labelPaint.setTextAlign(Paint.Align.CENTER);
    }

    void onSizeChanged(int width, int height) {
        fireX = width - 84f * density;
        fireY = height - 92f * density;
        reloadX = fireX - 26f * density;
        reloadY = fireY - 96f * density;
        jumpX = fireX - 108f * density;
        jumpY = fireY + 18f * density;
        ghostX = 108f * density;
        ghostY = height - 116f * density;
        interactX = width - 84f * density;
        interactY = height / 2f - 60f * density;
        interactRadius = 34f * density;
    }

    boolean fireContains(float x, float y) {
        return inside(x, y, fireX, fireY, fireRadius);
    }

    boolean reloadContains(float x, float y) {
        return inside(x, y, reloadX, reloadY, reloadRadius);
    }

    boolean jumpContains(float x, float y) {
        return inside(x, y, jumpX, jumpY, jumpRadius);
    }

    boolean interactContains(float x, float y) {
        return inside(x, y, interactX, interactY, interactRadius);
    }

    void draw(Canvas canvas, boolean moving, float anchorX, float anchorY,
              float knobX, float knobY, boolean firing,
              boolean interactVisible) {
        if (moving) {
            canvas.drawCircle(anchorX, anchorY, maxRadius, ringPaint);
            canvas.drawCircle(knobX, knobY, knobRadius, knobPaint);
        } else {
            canvas.drawCircle(ghostX, ghostY, maxRadius, ghostPaint);
            canvas.drawCircle(ghostX, ghostY, knobRadius, ghostPaint);
        }
        button(canvas, fireX, fireY, fireRadius, "TIRO",
                firing ? fireActivePaint : firePaint);
        button(canvas, reloadX, reloadY, reloadRadius, "R", buttonPaint);
        button(canvas, jumpX, jumpY, jumpRadius, "PULO", buttonPaint);
        if (interactVisible) {
            button(canvas, interactX, interactY, interactRadius,
                    "ATIVAR", knobPaint);
        }
    }

    private void button(Canvas canvas, float x, float y, float radius,
                        String label, Paint fill) {
        canvas.drawCircle(x, y, radius, fill);
        canvas.drawCircle(x, y, radius, ringPaint);
        canvas.drawText(label, x, y + labelPaint.getTextSize() * 0.35f,
                labelPaint);
    }

    private static boolean inside(float x, float y, float cx, float cy,
                                  float radius) {
        return Math.hypot(x - cx, y - cy) <= radius * 1.2f;
    }

    private Paint stroke(int color, float widthDp) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(widthDp * density);
        paint.setColor(color);
        return paint;
    }

    private static Paint fill(int color) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        return paint;
    }
}
