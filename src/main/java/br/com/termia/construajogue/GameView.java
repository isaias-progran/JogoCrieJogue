package br.com.termia.construajogue;

import android.content.Context;
import android.content.res.AssetManager;
import android.opengl.GLSurfaceView;

import br.com.termia.construajogue.game.Sounds;
import br.com.termia.construajogue.input.TouchControls;
import br.com.termia.construajogue.ui.Hud;

/**
 * Superfície ES 3.0 do jogo com render CONTÍNUO (laço de jogo).
 * preserveEGLContext evita re-subir recursos ao pausar; se o Android
 * descartar o contexto mesmo assim, onSurfaceCreated do GameRenderer
 * reconstrói tudo — os dois caminhos precisam funcionar.
 * O toque é tratado pelo TouchControls (View sobreposta), não aqui.
 */
public final class GameView extends GLSurfaceView {

    private final GameRenderer renderer;

    public GameView(Context context, GameRenderer.Listener listener,
                    TouchControls controls, AssetManager assets,
                    Sounds sounds, Hud hud) {
        super(context);
        setEGLContextClientVersion(3);
        setPreserveEGLContextOnPause(true);
        renderer = new GameRenderer(listener, controls, assets, sounds, hud);
        setRenderer(renderer);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
    }

    public GameRenderer renderer() {
        return renderer;
    }
}
