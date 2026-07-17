package br.com.termia.construajogue;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;

import br.com.termia.construajogue.compiler.LevelCompiler;
import br.com.termia.construajogue.compiler.MapValidator;
import br.com.termia.construajogue.compiler.ValidationIssue;
import br.com.termia.construajogue.ai.AiFeatureController;
import br.com.termia.construajogue.editor.EditorHost;
import br.com.termia.construajogue.game.Sounds;
import br.com.termia.construajogue.game.GameResult;
import br.com.termia.construajogue.input.TouchControls;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.persistence.MapStore;
import br.com.termia.construajogue.persistence.RecordStore;
import br.com.termia.construajogue.persistence.CampaignStore;
import br.com.termia.construajogue.persistence.MapJson;
import br.com.termia.construajogue.prefab.PrefabCatalog;
import br.com.termia.construajogue.runtime.AssetLevelProvider;
import br.com.termia.construajogue.runtime.LevelProvider;
import br.com.termia.construajogue.runtime.RuntimeLevel;
import br.com.termia.construajogue.runtime.RuntimeNpc;
import br.com.termia.construajogue.runtime.SingleLevelProvider;
import br.com.termia.construajogue.runtime.LazyLevelProvider;
import br.com.termia.construajogue.sharing.MapExchange;
import br.com.termia.construajogue.util.Ids;
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
        implements MapLibraryView.Listener, EditorHost.Listener,
        AiFeatureController.Listener {

    private static final int MODE_LIBRARY = 0;
    private static final int MODE_EDIT = 1;
    private static final int MODE_PLAY = 2;
    private static final int REQUEST_IMPORT_MAP = 41;
    private static final int REQUEST_EXPORT_MAP = 42;

    private FrameLayout root;
    private Sounds sounds;
    private MapStore store;
    private PrefabCatalog catalog;
    private RecordStore records;
    private CampaignStore campaigns;
    private AiFeatureController ai;
    private MapDocument pendingExport;
    private boolean smokeTest;

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
        records = new RecordStore(this);
        campaigns = new CampaignStore(this);
        ai = new AiFeatureController(this, store, this);
        setContentView(root);
        showLibrary();
        smokeTest = getIntent().getBooleanExtra("smoke_test", false);
        if (smokeTest) {
            root.postDelayed(() -> play(
                    new AssetLevelProvider(getAssets()), false), 350L);
        }
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
        setRequestedOrientation(android.content.pm.ActivityInfo
                .SCREEN_ORIENTATION_PORTRAIT);
        root.removeAllViews();
        root.addView(new MapLibraryView(this, store, records, campaigns,
                this));
    }

    private void showEditor(MapDocument doc) {
        teardownPlay();
        mode = MODE_EDIT;
        setRequestedOrientation(android.content.pm.ActivityInfo
                .SCREEN_ORIENTATION_PORTRAIT);
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
        setRequestedOrientation(android.content.pm.ActivityInfo
                .SCREEN_ORIENTATION_PORTRAIT);
        root.removeAllViews();
        root.addView(editor);
    }

    private void play(LevelProvider levels, boolean fromEditor) {
        teardownPlay();
        sounds.resume();
        mode = MODE_PLAY;
        playCameFromEditor = fromEditor;
        setRequestedOrientation(android.content.pm.ActivityInfo
                .SCREEN_ORIENTATION_LANDSCAPE);
        root.removeAllViews();
        Hud hud = new Hud(this);
        controls = new TouchControls(this);
        gameView = new GameView(this, new GameRenderer.Listener() {
            @Override
            public void onGlReady(String detail) {
                Log.i("CJ_SMOKE", "GL_READY " + detail);
                glStatus = getString(R.string.gl_ready, detail);
                postOverlay();
            }

            @Override
            public void onGlError(String message) {
                Log.e("CJ_SMOKE", "GL_ERROR " + message);
                glStatus = getString(R.string.gl_error, message);
                postOverlay();
            }

            @Override
            public void onFps(int value) {
                if (smokeTest) Log.i("CJ_SMOKE", "FPS " + value);
                fps = value;
                postOverlay();
            }

            @Override
            public void onGameResult(GameResult result) {
                root.post(() -> records.record(result));
            }

            @Override
            public void onNpcInteraction(RuntimeNpc npc, String mapName) {
                root.post(() -> ai.talk(npc, mapName));
            }

            @Override
            public void onNpcGreeting(RuntimeNpc npc) {
                root.post(() -> ai.greet(npc));
            }

            @Override
            public void onNpcCombatLine(RuntimeNpc npc, String line) {
                root.post(() -> ai.speakCombat(npc, line));
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
    public void onImproveAi(String id) {
        try {
            ai.promptImproveMap(store.load(id));
        } catch (IOException | RuntimeException failure) {
            toast("Não consegui preparar a melhoria: "
                    + failure.getMessage());
        }
    }

    @Override
    public void onRename(String id, String name) {
        try {
            store.rename(id, name);
        } catch (IOException | RuntimeException failure) {
            toast("Não consegui renomear: " + failure.getMessage());
        }
        showLibrary();
    }

    @Override
    public void onDelete(String id) {
        store.delete(id);
        showLibrary();
    }

    @Override
    public void onExport(String id) {
        try {
            MapDocument doc = store.load(id);
            try {
                java.io.File file = MapExchange.export(doc);
                toast("Exportado para " + file.getAbsolutePath());
            } catch (IOException | SecurityException directFailed) {
                // Android com armazenamento restrito: o seletor oficial
                // mantém a exportação disponível sem pedir permissão ampla.
                pendingExport = doc;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE,
                        safeFileName(doc.name) + ".json");
                startActivityForResult(intent, REQUEST_EXPORT_MAP);
            }
        } catch (IOException | RuntimeException failure) {
            toast("Não consegui exportar: " + failure.getMessage());
        }
    }

    @Override
    public void onImportFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, REQUEST_IMPORT_MAP);
    }

    @Override
    public void onImportExchange() {
        try {
            java.io.File[] files = MapExchange.list();
            if (files.length == 0) {
                toast("A pasta " + MapExchange.directory().getAbsolutePath()
                        + " ainda não tem mapas");
                return;
            }
            String[] names = new String[files.length];
            for (int i = 0; i < files.length; i++) {
                names[i] = files[i].getName();
            }
            new AlertDialog.Builder(this)
                    .setTitle("Importar da pasta de troca")
                    .setItems(names, (dialog, which) -> {
                        try {
                            importDocument(MapExchange.read(files[which]));
                        } catch (IOException | RuntimeException failure) {
                            toast("Não consegui importar: "
                                    + failure.getMessage());
                        }
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
        } catch (IOException | SecurityException failure) {
            toast("Sem acesso direto à pasta; use Escolher arquivo…");
        }
    }

    @Override
    public void onPlayUserCampaign(List<String> mapIds) {
        try {
            // Pré-valida um documento por vez, sem conservar RuntimeLevels.
            for (String mapId : mapIds) {
                List<ValidationIssue> issues = MapValidator.validate(
                        store.load(mapId), catalog());
                if (MapValidator.hasError(issues)) {
                    toast("Campanha contém mapa incompleto: "
                            + firstError(issues));
                    return;
                }
            }
            play(new LazyLevelProvider(store::load, catalog(), mapIds), false);
        } catch (IOException | RuntimeException failure) {
            toast("Não consegui abrir a campanha: " + failure.getMessage());
        }
    }

    @Override
    public void onPlayCampaign() {
        play(new AssetLevelProvider(getAssets()), false);
    }

    @Override
    public void onConfigureAi() {
        ai.showSettings();
    }

    @Override
    public void onGenerateAiScenario() {
        ai.promptScenario();
    }

    @Override
    public void onPlayExample(String assetPath) {
        try {
            RuntimeLevel level = compile(readAssetMap(assetPath));
            if (level != null) {
                play(new SingleLevelProvider(level), false);
            }
        } catch (IOException | RuntimeException failure) {
            toast("Não consegui jogar o exemplo: "
                    + failure.getMessage());
        }
    }

    @Override
    public void onCopyExample(String assetPath) {
        try {
            MapDocument copy = readAssetMap(assetPath);
            copy.id = br.com.termia.construajogue.util.Ids.create();
            copy.name = copy.name + " (minha cópia)";
            store.save(copy);
            showEditor(copy);
        } catch (IOException | RuntimeException failure) {
            toast("Não consegui copiar o exemplo: "
                    + failure.getMessage());
        }
    }

    private MapDocument readAssetMap(String assetPath) throws IOException {
        java.io.ByteArrayOutputStream buffer =
                new java.io.ByteArrayOutputStream();
        try (java.io.InputStream input = getAssets().open(assetPath)) {
            byte[] chunk = new byte[4096];
            int read;
            while ((read = input.read(chunk)) > 0) {
                buffer.write(chunk, 0, read);
            }
        }
        return br.com.termia.construajogue.persistence.MapJson.read(
                new String(buffer.toByteArray(),
                        java.nio.charset.StandardCharsets.UTF_8));
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
    public void onImproveWithAi(MapDocument snapshot) {
        ai.promptImproveMap(snapshot);
    }

    @Override
    public void onClose() {
        showLibrary();
    }

    /** Valida e compila; erros aparecem em diálogo e devolvem null. */
    private RuntimeLevel compile(MapDocument doc) {
        try {
            RuntimeLevel level = null;
            try {
                level = LevelCompiler.compile(doc, catalog());
            } catch (RuntimeException broken) {
                // O validador abaixo explica o problema com mensagens claras.
            }
            List<ValidationIssue> issues =
                    MapValidator.validate(doc, catalog(), level);
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
            return level != null ? level
                    : LevelCompiler.compile(doc, catalog());
        } catch (IOException | RuntimeException failure) {
            toast("Falha ao compilar: " + failure.getMessage());
            return null;
        }
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private static String firstError(List<ValidationIssue> issues) {
        for (ValidationIssue issue : issues) {
            if (issue.isError()) return issue.message;
        }
        return "erro desconhecido";
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (ai != null && ai.onActivityResult(requestCode, resultCode, data)) {
            return;
        }
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            if (requestCode == REQUEST_EXPORT_MAP) pendingExport = null;
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQUEST_IMPORT_MAP) {
            try {
                importDocument(MapJson.read(readUri(uri)));
            } catch (IOException | RuntimeException failure) {
                toast("Arquivo de mapa inválido: " + failure.getMessage());
            }
        } else if (requestCode == REQUEST_EXPORT_MAP && pendingExport != null) {
            try (java.io.OutputStream output =
                         getContentResolver().openOutputStream(uri, "wt")) {
                if (output == null) throw new IOException("destino fechado");
                output.write(MapJson.write(pendingExport).getBytes(
                        java.nio.charset.StandardCharsets.UTF_8));
                output.flush();
                toast("Mapa exportado");
            } catch (IOException | RuntimeException failure) {
                toast("Não consegui exportar: " + failure.getMessage());
            } finally {
                pendingExport = null;
            }
        }
    }

    private String readUri(Uri uri) throws IOException {
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (java.io.InputStream input =
                     getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("arquivo fechado");
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) continue;
                if (bytes.size() + read > 2 * 1024 * 1024) {
                    throw new IOException("arquivo grande demais");
                }
                bytes.write(buffer, 0, read);
            }
        }
        return new String(bytes.toByteArray(),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    private void importDocument(MapDocument doc) throws IOException {
        doc.id = Ids.create();
        String name = doc.name == null ? "" : doc.name.trim();
        doc.name = name.isEmpty() ? "Mapa importado"
                : name + " (importado)";
        store.save(doc);
        toast("Mapa importado");
        showLibrary();
    }

    private static String safeFileName(String name) {
        String safe = name == null ? "mapa"
                : name.replaceAll("[^A-Za-z0-9._-]+", "-");
        return safe.isEmpty() ? "mapa" : safe;
    }

    // ---- AiFeatureController.Listener ----

    @Override
    public PrefabCatalog aiCatalog() throws IOException {
        return catalog();
    }

    @Override
    public void onAiScenarioSaved(List<MapDocument> documents) {
        if (documents == null || documents.isEmpty()) return;
        if (documents.size() == 1) {
            showEditor(documents.get(0));
            return;
        }
        List<String> ids = new java.util.ArrayList<>();
        for (MapDocument document : documents) ids.add(document.id);
        campaigns.set(ids);
        try {
            play(new LazyLevelProvider(store::load, catalog(), ids), false);
            toast(documents.size() + " setores salvos em Minha campanha");
        } catch (IOException | RuntimeException failure) {
            toast("Setores salvos, mas não consegui jogar: "
                    + failure.getMessage());
            showLibrary();
        }
    }

    @Override
    public boolean aiGameActive() {
        return mode == MODE_PLAY && gameView != null && controls != null;
    }

    @Override
    public void pauseForAiDialog() {
        if (controls != null) controls.pause();
    }

    @Override
    public void resumeAfterAiDialog() {
        if (controls != null && mode == MODE_PLAY) controls.resume();
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
        if (mode == MODE_EDIT && editor != null) {
            editor.saveNow();
            editor.pausePreview();
        }
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
        if (gameView != null) {
            sounds.resume();
            gameView.onResume();
        } else if (mode == MODE_EDIT && editor != null) {
            editor.resumePreview();
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
            if (editor.closePreviewIfOpen()) {
                return;
            }
            editor.close();
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (ai != null) ai.shutdown();
        sounds.release();
        super.onDestroy();
    }
}
