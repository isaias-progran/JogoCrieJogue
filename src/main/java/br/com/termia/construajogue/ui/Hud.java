package br.com.termia.construajogue.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;

/**
 * HUD como View sobreposta (mira, munição, vida, marcador de acerto,
 * aviso de recarga). A thread GL escreve campos volatile e chama
 * postInvalidate; o desenho acontece na thread de UI só quando algo muda.
 * O passe ortográfico em GL fica para quando houver fonte texturizada.
 */
public final class Hud extends View {

    private final float density;
    private final Paint crosshair;
    private final Paint text;
    private final Paint bigText;
    private final Paint hitMark;
    private final Paint damageOverlay;

    private volatile int ammo;
    private volatile int reserve;
    private volatile int special;
    private volatile int health = 100;
    private volatile boolean reloading;
    private volatile long hitUntil;
    private volatile long damageUntil;
    private volatile String objective;
    private volatile String big;
    private volatile String hint;

    public Hud(Context context) {
        super(context);
        density = getResources().getDisplayMetrics().density;
        crosshair = new Paint(Paint.ANTI_ALIAS_FLAG);
        crosshair.setColor(0xB3FFFFFF);
        crosshair.setStrokeWidth(2f * density);
        text = new Paint(Paint.ANTI_ALIAS_FLAG);
        text.setColor(0xFFDDE7EE);
        text.setTextSize(16f * density);
        bigText = new Paint(Paint.ANTI_ALIAS_FLAG);
        bigText.setColor(0xFFF2A03D);
        bigText.setTextSize(26f * density);
        bigText.setTextAlign(Paint.Align.CENTER);
        bigText.setFakeBoldText(true);
        hitMark = new Paint(Paint.ANTI_ALIAS_FLAG);
        hitMark.setColor(0xE6FF6644);
        hitMark.setStrokeWidth(3f * density);
        damageOverlay = new Paint();
        damageOverlay.setColor(0x2EFF2222);
        setWillNotDraw(false);
    }

    // ---- chamados pela thread GL ----

    public void setAmmo(int mag, int reserveCount) {
        ammo = mag;
        reserve = reserveCount;
        postInvalidate();
    }

    public void setSpecial(int value) {
        special = value;
        postInvalidate();
    }

    public void setHealth(int value) {
        health = value;
        postInvalidate();
    }

    public void setReloading(boolean value) {
        reloading = value;
        postInvalidate();
    }

    public void setObjective(String value) {
        objective = value;
        postInvalidate();
    }

    /** Mensagem central grande (derrota/vitória); null limpa. */
    public void setBig(String value) {
        big = value;
        postInvalidate();
    }

    /** Dica de controles no rodapé; null limpa. */
    public void setHint(String value) {
        hint = value;
        postInvalidate();
    }

    public void showHitMarker() {
        hitUntil = SystemClock.uptimeMillis() + 130;
        postInvalidate();
        postInvalidateDelayed(150);
    }

    /** Flash vermelho ao levar dano de drone. */
    public void showDamage() {
        damageUntil = SystemClock.uptimeMillis() + 220;
        postInvalidate();
        postInvalidateDelayed(240);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        long now = SystemClock.uptimeMillis();

        if (now < damageUntil) {
            canvas.drawRect(0, 0, getWidth(), getHeight(), damageOverlay);
        }

        float arm = 10f * density;
        float gap = 4f * density;
        canvas.drawLine(cx - arm, cy, cx - gap, cy, crosshair);
        canvas.drawLine(cx + gap, cy, cx + arm, cy, crosshair);
        canvas.drawLine(cx, cy - arm, cx, cy - gap, crosshair);
        canvas.drawLine(cx, cy + gap, cx, cy + arm, crosshair);

        if (now < hitUntil) {
            float in = 6f * density;
            float out = 14f * density;
            canvas.drawLine(cx - out, cy - out, cx - in, cy - in, hitMark);
            canvas.drawLine(cx + in, cy - in, cx + out, cy - out, hitMark);
            canvas.drawLine(cx - out, cy + out, cx - in, cy + in, hitMark);
            canvas.drawLine(cx + in, cy + in, cx + out, cy + out, hitMark);
        }

        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(health <= 30 ? 0xFFFF5544 : 0xFFDDE7EE);
        canvas.drawText("VIDA " + health, 20f * density,
                getHeight() - 24f * density, text);
        text.setColor(0xFFDDE7EE);

        text.setTextAlign(Paint.Align.RIGHT);
        canvas.drawText(ammo + " / " + reserve,
                getWidth() - 170f * density,
                getHeight() - 24f * density, text);
        if (special > 0) {
            text.setColor(0xFFFFC84A);
            canvas.drawText("ESPECIAL " + special,
                    getWidth() - 20f * density,
                    getHeight() - 24f * density, text);
            text.setColor(0xFFDDE7EE);
        }

        String objectiveNow = objective;
        if (objectiveNow != null) {
            text.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(objectiveNow, cx, 34f * density, text);
        }

        if (reloading) {
            text.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("RECARREGANDO…", cx, cy + 56f * density, text);
        }

        String hintNow = hint;
        if (hintNow != null) {
            text.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(hintNow, cx, getHeight() - 54f * density, text);
        }

        String bigNow = big;
        if (bigNow != null) {
            float lineY = cy - 40f * density;
            for (String lineText : bigNow.split("\n")) {
                canvas.drawText(lineText, cx, lineY, bigText);
                lineY += 36f * density;
            }
        }
    }
}
