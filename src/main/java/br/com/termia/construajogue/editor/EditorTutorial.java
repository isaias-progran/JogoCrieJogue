package br.com.termia.construajogue.editor;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Cinco balões do primeiro uso, sem acoplar a sequência ao editor. */
final class EditorTutorial {

    interface Listener {
        void onStep(int step);
    }

    private static final String KEY = "editor_tutorial_v1";
    private static final String[] MESSAGES = {
            "1/5  PISO\nArraste na planta para desenhar o chão do cômodo.",
            "2/5  PAREDE\nArraste de uma ponta à outra. As pontas amarelas "
                    + "ajudam a fechar os cantos.",
            "3/5  INÍCIO\nToque no lugar onde o jogador deve nascer.",
            "4/5  SAÍDA\nToque no destino da fase. Depois você pode escolher "
                    + "outros objetivos no menu ☰.",
            "5/5  TESTAR\nUse o botão ▶ no topo. O mapa é salvo antes de a "
                    + "partida começar."
    };

    private final Activity activity;
    private final FrameLayout host;
    private final Listener listener;
    private final SharedPreferences preferences;
    private LinearLayout bubble;
    private int step;

    EditorTutorial(Activity activity, FrameLayout host, Listener listener) {
        this.activity = activity;
        this.host = host;
        this.listener = listener;
        preferences = activity.getSharedPreferences("onboarding", 0);
    }

    void startIfNeeded() {
        if (preferences.getBoolean(KEY, false) || bubble != null) return;
        step = 0;
        show();
    }

    private void show() {
        if (bubble != null) host.removeView(bubble);
        listener.onStep(step);
        bubble = new LinearLayout(activity);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(28, 20, 28, 16);
        bubble.setBackgroundColor(0xF02A3947);

        TextView text = new TextView(activity);
        text.setText(MESSAGES[step]);
        text.setTextColor(Color.WHITE);
        text.setTextSize(15f);
        bubble.addView(text, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        LinearLayout actions = new LinearLayout(activity);
        actions.setGravity(Gravity.END);
        Button skip = button("PULAR");
        skip.setOnClickListener(v -> finish());
        actions.addView(skip);
        Button next = button(step == MESSAGES.length - 1
                ? "CONCLUIR" : "PRÓXIMO");
        next.setOnClickListener(v -> {
            if (++step >= MESSAGES.length) finish();
            else show();
        });
        actions.addView(next);
        bubble.addView(actions);

        float density = activity.getResources().getDisplayMetrics().density;
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        params.leftMargin = (int) (12f * density);
        params.rightMargin = (int) (12f * density);
        params.bottomMargin = (int) (58f * density);
        host.addView(bubble, params);
    }

    private Button button(String label) {
        Button value = new Button(activity);
        value.setText(label);
        value.setTextSize(12f);
        value.setTextColor(0xFFDDEEFF);
        value.setBackgroundColor(0x332E5A8A);
        return value;
    }

    private void finish() {
        preferences.edit().putBoolean(KEY, true).apply();
        if (bubble != null) host.removeView(bubble);
        bubble = null;
    }
}
