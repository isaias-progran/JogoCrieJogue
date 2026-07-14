package br.com.termia.construajogue;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import br.com.termia.construajogue.game.Sounds;
import br.com.termia.construajogue.input.TouchControls;
import br.com.termia.construajogue.ui.Hud;

/**
 * Pilha de views da partida completa: GameView (GL), HUD, controles/menu de
 * pausa e overlay de diagnóstico. HUD/overlay não capturam os toques.
 */
public final class MainActivity extends Activity {

    private GameView gameView;
    private Sounds sounds;
    private TouchControls controls;
    private TextView overlay;
    private String glStatus = "";
    private int fps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FrameLayout root = new FrameLayout(this);
        sounds = new Sounds(this);
        Hud hud = new Hud(this);
        controls = new TouchControls(this);
        gameView = new GameView(this, new GameRenderer.Listener() {
            @Override
            public void onGlReady(String detail) {
                glStatus = getString(R.string.gl_ready, detail);
                postOverlay();
            }

            @Override
            public void onGlError(String message) {
                glStatus = getString(R.string.gl_error, message);
                postOverlay();
            }

            @Override
            public void onFps(int value) {
                fps = value;
                postOverlay();
            }
        }, controls, getAssets(), sounds, hud);
        root.addView(gameView);
        root.addView(hud);
        // Controles por último: o menu de pausa precisa cobrir o HUD.
        root.addView(controls);
        overlay = new TextView(this);
        overlay.setTextColor(0xFFDDE7EE);
        overlay.setTextSize(13f);
        overlay.setPadding(24, 24, 24, 24);
        glStatus = getString(R.string.gl_starting);
        root.addView(overlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START));
        setContentView(root);
        refreshOverlay();
    }

    /** Chamado pela thread GL; leva a atualização para a thread de UI. */
    private void postOverlay() {
        overlay.post(this::refreshOverlay);
    }

    private void refreshOverlay() {
        overlay.setText(glStatus + "\n"
                + getString(R.string.fps_format, fps));
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemUi();
        } else if (controls != null) {
            controls.pause();
        }
    }

    /** Imersivo pegajoso: barras só voltam com gesto e somem de novo. */
    private void hideSystemUi() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    protected void onPause() {
        controls.pause();
        sounds.pause();
        gameView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        sounds.resume();
        gameView.onResume();
    }

    /** Voltar pausa primeiro; se já estiver pausado, fecha normalmente. */
    @Override
    public void onBackPressed() {
        if (!controls.paused()) {
            controls.pause();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        sounds.release();
        super.onDestroy();
    }
}
