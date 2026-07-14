package br.com.termia.construajogue;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import br.com.termia.construajogue.compiler.LevelCompiler;
import br.com.termia.construajogue.compiler.MapValidator;
import br.com.termia.construajogue.compiler.ValidationIssue;
import br.com.termia.construajogue.editor.EditorHost;
import br.com.termia.construajogue.game.Sounds;
import br.com.termia.construajogue.input.TouchControls;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.persistence.MapStore;
import br.com.termia.construajogue.prefab.PrefabCatalog;
import br.com.termia.construajogue.runtime.AssetLevelProvider;
import br.com.termia.construajogue.runtime.LevelProvider;
import br.com.termia.construajogue.runtime.RuntimeLevel;
import br.com.termia.construajogue.runtime.SingleLevelProvider;
import br.com.termia.construajogue.ui.Hud;
import br.com.termia.construajogue.ui.MapLibraryView;

import java.io.IOException;
import java.util.List;

/**
 * Controla os três modos (ARQUITETURA §7): Biblioteca, Construir e
 * Jogar. Cada modo monta sua própria pilha de views; a partida cria
 * GameView/HUD/controles novos a cada entrada e os descarta na saída.
 */
public final class MainActivity extends Activity
        implements MapLibraryView.Listener, EditorHost.Listener {

    private static final int MODE_LIBRARY = 0;
    private static final int MODE_EDIT = 1;
    private static final int MODE_PLAY = 2;

    private FrameLayout root;
    private Sounds sounds;
    private MapStore store;
    private PrefabCatalog catalog;

    private int mode = MODE_LIBRARY;
    private boolean playCameFromEditor;
    private EditorHost editor;
    private GameView gameView;
    private TouchControls controls;
    private TextView overlay;
    private String glStatus = "";
    private int fps;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        root = new FrameLayout(this);
        sounds = new Sounds(this);
        store = new MapStore(this);
        setContentView(root);
        showLibrary();
    }

    private PrefabCatalog catalog() throws IOException {
        if (catalog == null) {
            catalog = PrefabCatalog.load(
                    getAssets().open("prefabs/catalog.json"));
        }
        return catalog;
    }

    // ---- troca de modos ----

    private void showLibrary() {
        teardownPlay();
        editor = null;
        mode = MODE_LIBRARY;
        root.removeAllViews();
        root.addView(new MapLibraryView(this, store, this));
    }

    private void showEditor(MapDocument doc) {
        teardownPlay();
        mode = MODE_EDIT;
        root.removeAllViews();
        try {
            editor = new EditorHost(this, store, catalog(), doc, this);
        } catch (IOException failure) {
            toast("Catálogo inválido: " + failure.getMessage());
            showLibrary();
            return;
        }
        root.addView(editor);
    }

    /** Volta ao editor mantido vivo durante o teste. */
    private void showEditorAgain() {
        teardownPlay();
        mode = MODE_EDIT;
        root.removeAllViews();
        root.addView(editor);
    }

    private void play(LevelProvider levels, boolean fromEditor) {
        teardownPlay();
        mode = MODE_PLAY;
        playCameFromEditor = fromEditor;
        root.removeAllViews();
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
        }, controls, levels, sounds, hud);
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
        refreshOverlay();
    }

    private void teardownPlay() {
        if (gameView != null) {
            controls.pause();
            sounds.pause();
            gameView.onPause();
            gameView = null;
            controls = null;
            overlay = null;
        }
    }

    // ---- MapLibraryView.Listener ----

    @Override
    public void onBuild(String id) {
        try {
            showEditor(store.load(id));
        } catch (IOException | RuntimeException failure) {
            toast("Não consegui abrir: " + failure.getMessage());
        }
    }

    @Override
    public void onPlay(String id) {
        try {
            RuntimeLevel level = compile(store.load(id));
            if (level != null) {
                play(new SingleLevelProvider(level), false);
            }
        } catch (IOException | RuntimeException failure) {
            toast("Não consegui jogar: " + failure.getMessage());
        }
    }

    @Override
    public void onCreate(String name) {
        try {
            showEditor(store.create(name));
        } catch (IOException failure) {
            toast("Não consegui criar: " + failure.getMessage());
        }
    }

    @Override
    public void onDuplicate(String id) {
        try {
            store.duplicate(id);
        } catch (IOException | RuntimeException failure) {
            toast("Não consegui duplicar: " + failure.getMessage());
        }
        showLibrary();
    }

    @Override
    public void onDelete(String id) {
        store.delete(id);
        showLibrary();
    }

    @Override
    public void onPlayCampaign() {
        play(new AssetLevelProvider(getAssets()), false);
    }

    // ---- EditorHost.Listener ----

    @Override
    public void onTest(MapDocument snapshot) {
        RuntimeLevel level = compile(snapshot);
        if (level != null) {
            play(new SingleLevelProvider(level), true);
        }
    }

    @Override
    public void onClose() {
        showLibrary();
    }

    /** Valida e compila; erros aparecem em diálogo e devolvem null. */
    private RuntimeLevel compile(MapDocument doc) {
        try {
            List<ValidationIssue> issues =
                    MapValidator.validate(doc, catalog());
            if (MapValidator.hasError(issues)) {
                StringBuilder text = new StringBuilder();
                for (ValidationIssue issue : issues) {
                    if (issue.isError()) {
                        text.append("• ").append(issue.message).append('\n');
                    }
                }
                new AlertDialog.Builder(this)
                        .setTitle("Mapa incompleto")
                        .setMessage(text.toString())
                        .setPositiveButton("OK", null)
                        .show();
                return null;
            }
            return LevelCompiler.compile(doc, catalog());
        } catch (IOException | RuntimeException failure) {
            toast("Falha ao compilar: " + failure.getMessage());
            return null;
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    // ---- ciclo de vida ----

    /** Chamado pela thread GL; leva a atualização para a thread de UI. */
    private void postOverlay() {
        TextView view = overlay;
        if (view != null) {
            view.post(this::refreshOverlay);
        }
    }

    private void refreshOverlay() {
        if (overlay != null) {
            overlay.setText(glStatus + "\n"
                    + getString(R.string.fps_format, fps));
        }
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
        if (gameView != null) {
            controls.pause();
            sounds.pause();
            gameView.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        sounds.resume();
        if (gameView != null) {
            gameView.onResume();
        }
    }

    /**
     * Voltar: na partida pausa primeiro, depois sai para editor ou
     * biblioteca; no editor salva e volta à biblioteca; na biblioteca
     * fecha o app.
     */
    @Override
    public void onBackPressed() {
        if (mode == MODE_PLAY) {
            if (controls != null && !controls.paused()) {
                controls.pause();
            } else if (playCameFromEditor && editor != null) {
                showEditorAgain();
            } else {
                showLibrary();
            }
            return;
        }
        if (mode == MODE_EDIT && editor != null) {
            editor.close();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        sounds.release();
        super.onDestroy();
    }
}
