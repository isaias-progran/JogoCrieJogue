package br.com.termia.construajogue.input;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * Desenha a pausa e guarda as opções de câmera. Só é tocado pela thread de
 * UI; a persistência acontece apenas quando o jogador aperta uma opção.
 */
final class PauseMenu {

    private static final String PREFS = "controls";
    private static final String PREF_SENSITIVITY = "sensitivity";
    private static final String PREF_INVERT_Y = "invert_y";
    /** Baixa, média e alta: graus de giro por dp de arrasto. */
    private static final float[] LOOK_DEG_PER_DP = {0.20f, 0.30f, 0.42f};

    private final float density;
    private final float pauseRadius;
    private final SharedPreferences preferences;
    private final Paint ringPaint;
    private final Paint buttonPaint;
    private final Paint shadePaint;
    private final Paint titlePaint;
    private final Paint textPaint;
    private final Paint footerPaint;

    private float pauseX;
    private float pauseY;
    private float menuX;
    private float resumeY;
    private float sensitivityY;
    private float invertYButtonY;
    private float halfWidth;
    private float halfHeight;
    private float lookScale;
    private int sensitivity;
    private boolean invertY;

    PauseMenu(Context context, float density) {
        this.density = density;
        pauseRadius = 24f * density;
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sensitivity = preferences.getInt(PREF_SENSITIVITY, 1);
        if (sensitivity < 0 || sensitivity >= LOOK_DEG_PER_DP.length) {
            sensitivity = 1;
        }
        invertY = preferences.getBoolean(PREF_INVERT_Y, false);
        updateLookScale();
        ringPaint = stroke(0x55DDE7EE, 3f);
        buttonPaint = fill(0xCC183047);
        shadePaint = fill(0xDD071018);
        titlePaint = text(0xFFF2A03D, 28f, true);
        textPaint = text(0xFFE4EDF3, 15f, false);
        footerPaint = text(0xAADDE7EE, 13f, false);
    }

    void onSizeChanged(int width, int height) {
        pauseX = width - 38f * density;
        pauseY = 36f * density;
        menuX = width / 2f;
        resumeY = height / 2f - 34f * density;
        sensitivityY = resumeY + 62f * density;
        invertYButtonY = sensitivityY + 62f * density;
        halfWidth = 150f * density;
        halfHeight = 23f * density;
    }

    boolean pauseButtonContains(float x, float y) {
        return Math.hypot(x - pauseX, y - pauseY) <= pauseRadius * 1.2f;
    }

    /** Trata as três linhas do menu; true pede para continuar o jogo. */
    boolean onTouch(float x, float y) {
        if (insideButton(x, y, resumeY)) {
            return true;
        }
        if (insideButton(x, y, sensitivityY)) {
            sensitivity = (sensitivity + 1) % LOOK_DEG_PER_DP.length;
            updateLookScale();
            preferences.edit().putInt(PREF_SENSITIVITY, sensitivity).apply();
        } else if (insideButton(x, y, invertYButtonY)) {
            invertY = !invertY;
            preferences.edit().putBoolean(PREF_INVERT_Y, invertY).apply();
        }
        return false;
    }

    float yawDelta(float dx) {
        return dx * lookScale;
    }

    float pitchDelta(float dy) {
        return dy * lookScale * (invertY ? 1f : -1f);
    }

    void drawPauseButton(Canvas canvas) {
        canvas.drawCircle(pauseX, pauseY, pauseRadius, buttonPaint);
        canvas.drawCircle(pauseX, pauseY, pauseRadius, ringPaint);
        canvas.drawText("II", pauseX,
                pauseY + footerPaint.getTextSize() * 0.35f, footerPaint);
    }

    void draw(Canvas canvas) {
        canvas.drawRect(0f, 0f, canvas.getWidth(), canvas.getHeight(),
                shadePaint);
        canvas.drawText("PAUSADO", menuX, resumeY - 72f * density,
                titlePaint);
        drawButton(canvas, resumeY, "CONTINUAR");
        String name = sensitivity == 0 ? "BAIXA"
                : sensitivity == 1 ? "MEDIA" : "ALTA";
        drawButton(canvas, sensitivityY, "SENSIBILIDADE: " + name);
        drawButton(canvas, invertYButtonY,
                "EIXO Y: " + (invertY ? "INVERTIDO" : "NORMAL"));
        canvas.drawText("VOLTAR novamente fecha o jogo", menuX,
                invertYButtonY + 52f * density, footerPaint);
    }

    private void updateLookScale() {
        lookScale = (float) (Math.toRadians(LOOK_DEG_PER_DP[sensitivity])
                / density);
    }

    private boolean insideButton(float x, float y, float cy) {
        return x >= menuX - halfWidth && x <= menuX + halfWidth
                && y >= cy - halfHeight && y <= cy + halfHeight;
    }

    private void drawButton(Canvas canvas, float cy, String label) {
        float radius = 10f * density;
        canvas.drawRoundRect(menuX - halfWidth, cy - halfHeight,
                menuX + halfWidth, cy + halfHeight,
                radius, radius, buttonPaint);
        canvas.drawRoundRect(menuX - halfWidth, cy - halfHeight,
                menuX + halfWidth, cy + halfHeight,
                radius, radius, ringPaint);
        canvas.drawText(label, menuX,
                cy + textPaint.getTextSize() * 0.35f, textPaint);
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

    private Paint text(int color, float sizeDp, boolean bold) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        paint.setTextSize(sizeDp * density);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setFakeBoldText(bold);
        return paint;
    }
}
