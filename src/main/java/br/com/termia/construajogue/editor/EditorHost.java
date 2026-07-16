package br.com.termia.construajogue.editor;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import br.com.termia.construajogue.compiler.MapValidator;
import br.com.termia.construajogue.compiler.ValidationIssue;
import br.com.termia.construajogue.game.GameResult;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.ObjectiveSpec;
import br.com.termia.construajogue.map.PrefabInstance;
import br.com.termia.construajogue.map.StructureObject;
import br.com.termia.construajogue.map.WallGeometry;
import br.com.termia.construajogue.map.WallOpening;
import br.com.termia.construajogue.persistence.MapJson;
import br.com.termia.construajogue.persistence.MapStore;
import br.com.termia.construajogue.prefab.PrefabCatalog;
import br.com.termia.construajogue.prefab.PrefabDefinition;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Modo Construir: planta + barra superior (voltar, desfazer/refazer,
 * testar) + barra de ferramentas. Salva após breve inatividade, no máximo
 * a cada 10 s de edição contínua, ao sair e ao testar;
 * o teste recebe um snapshot profundo — o documento nunca é alterado
 * pela partida (ARQUITETURA §8).
 */
public final class EditorHost extends FrameLayout
        implements PlanEditorView.Host {

    private static final long AUTO_SAVE_DELAY_MS = 2500L;
    private static final long AUTO_SAVE_MAX_MS = 10000L;

    public interface Listener {
        void onTest(MapDocument snapshot);

        void onClose();
    }

    final Activity activity;
    private final MapStore store;
    final PrefabCatalog catalog;
    private final Listener listener;
    private final UndoHistory history = new UndoHistory();
    final EditorForms forms = new EditorForms(this);
    private final EditorPickers pickers = new EditorPickers(this);
    final PlanEditorView plan;
    final TextView status;
    private final TextView counts;
    private final List<Button> toolButtons = new ArrayList<>();
    private Button undoButton;
    private Button redoButton;
    private Button deleteButton;
    private Button heightButton;
    private Button rotateButton;
    private Button routeButton;
    private Button duplicateButton;
    private Button lockButton;
    private Button materialButton;
    private Button logicButton;
    private Button storyButton;
    private ScrollView sidePanel;
    private FrameLayout previewOverlay;
    private EditorPreviewView previewView;
    private final EditorTutorial tutorial;
    MapDocument doc;
    private boolean dirty;
    private final Runnable autoSaveTask = new Runnable() {
        @Override
        public void run() {
            if (dirty) {
                saveNow();
            }
        }
    };
    private final Runnable maxAutoSaveTask = new Runnable() {
        @Override
        public void run() {
            if (dirty) saveNow();
        }
    };

    public EditorHost(Activity activity, MapStore store,
                      PrefabCatalog catalog, MapDocument doc,
                      Listener listener) {
        super(activity);
        this.activity = activity;
        this.store = store;
        this.catalog = catalog;
        this.doc = doc;
        this.listener = listener;

        plan = new PlanEditorView(activity, this);
        plan.setDocument(doc);
        addView(plan);

        status = new TextView(activity);
        status.setTextColor(0xFFAFC3D0);
        status.setTextSize(13f);
        status.setPadding(24, 8, 24, 8);
        status.setBackgroundColor(0x88141B22);
        float density = activity.getResources()
                .getDisplayMetrics().density;
        LayoutParams statusParams = new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        statusParams.bottomMargin = (int) (24f * density);
        addView(status, statusParams);

        counts = new TextView(activity);
        counts.setTextColor(0xFF7F96A5);
        counts.setTextSize(11f);
        counts.setPadding(24, 3, 24, 3);
        counts.setBackgroundColor(0xCC10151B);
        addView(counts, new LayoutParams(LayoutParams.MATCH_PARENT,
                (int) (24f * density), Gravity.BOTTOM | Gravity.START));

        addView(buildTopBar(), new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT, Gravity.TOP));
        sidePanel = buildSidePanel();
        LayoutParams panelParams = new LayoutParams(
                (int) (210f * density), LayoutParams.MATCH_PARENT,
                Gravity.TOP | Gravity.END);
        panelParams.topMargin = (int) (104f * density);
        panelParams.bottomMargin = (int) (28f * density);
        panelParams.rightMargin = 8;
        addView(sidePanel, panelParams);
        sidePanel.setVisibility(GONE);
        selectTool(PlanEditorView.TOOL_FLOOR);
        refreshCounts();
        refreshButtons();
        tutorial = new EditorTutorial(activity, this, step -> {
            int[] tools = {PlanEditorView.TOOL_FLOOR,
                    PlanEditorView.TOOL_WALL, PlanEditorView.TOOL_SPAWN,
                    PlanEditorView.TOOL_EXIT, PlanEditorView.TOOL_SELECT};
            selectTool(tools[Math.max(0, Math.min(step, tools.length - 1))]);
        });
        postDelayed(tutorial::startIfNeeded, 450L);
    }

    /**
     * Topo em DUAS linhas fixas; cada botão tem peso 1, então a barra
     * sempre cabe na largura da tela, sem rolagem.
     */
    private LinearLayout buildTopBar() {
        LinearLayout bars = new LinearLayout(activity);
        bars.setOrientation(LinearLayout.VERTICAL);
        bars.setBackgroundColor(0xCC141B22);
        bars.setPadding(4, 4, 4, 4);

        LinearLayout row1 = topRow(bars);
        addWeighted(row1, action("←", this::close));
        undoButton = action("↶", this::undo);
        redoButton = action("↷", this::redo);
        addWeighted(row1, undoButton);
        addWeighted(row1, redoButton);
        Button tools = action("☰", this::togglePanel);
        tools.setTextColor(0xFFE0C060);
        addWeighted(row1, tools);
        Button test = action("▶", this::test);
        test.setTextColor(0xFF9CE49C);
        addWeighted(row1, test);

        LinearLayout row2 = topRow(bars);
        Button select = action("SELEC.",
                () -> selectTool(PlanEditorView.TOOL_SELECT));
        select.setTag(PlanEditorView.TOOL_SELECT);
        toolButtons.add(select);
        addWeighted(row2, select);
        Button paintButton = action("PINTAR", pickers::choosePaint);
        paintButton.setTag(PlanEditorView.TOOL_PAINT);
        paintButton.setTextColor(0xFFC98FD9);
        toolButtons.add(paintButton);
        addWeighted(row2, paintButton);
        rotateButton = action("GIRAR", () -> plan.rotateSelected());
        rotateButton.setTextColor(0xFFA0D9C9);
        addWeighted(row2, rotateButton);
        heightButton = action("MEDIDAS", pickers::editMeasures);
        heightButton.setTextColor(0xFF9CC9E4);
        addWeighted(row2, heightButton);
        deleteButton = action("EXCLUIR", () -> plan.deleteSelected());
        deleteButton.setTextColor(0xFFE49C9C);
        addWeighted(row2, deleteButton);
        return bars;
    }

    private LinearLayout topRow(LinearLayout parent) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        return row;
    }

    /** Peso 1: os botões da linha dividem a largura por igual. */
    private void addWeighted(LinearLayout row, Button button) {
        button.setPadding(4, 8, 4, 8);
        button.setSingleLine(true);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.rightMargin = 4;
        row.addView(button, params);
    }

    /** Painel lateral recolhível, como no editor3d: uma linha por item. */
    private ScrollView buildSidePanel() {
        ScrollView scroll = new ScrollView(activity);
        scroll.setBackgroundColor(0xEE141B22);
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(8, 8, 8, 8);
        storyButton = panelItem("Andar ativo: térreo…", () -> {
            hidePanel();
            forms.chooseStory();
        }, null);
        storyButton.setTextColor(0xFFE0C060);
        panel.addView(storyButton);
        addPanelTool(panel, "Piso", PlanEditorView.TOOL_FLOOR);
        addPanelTool(panel, "Parede", PlanEditorView.TOOL_WALL);
        addPanelTool(panel, "Teto", PlanEditorView.TOOL_CEILING);
        addPanelTool(panel, "Bloco", PlanEditorView.TOOL_BLOCK);
        panel.addView(panelItem("Desenho por pontos…", () -> {
            hidePanel();
            pickers.chooseContour();
        }, PlanEditorView.TOOL_POINTS));
        panel.addView(panelItem("Vão…", () -> {
            hidePanel();
            pickers.chooseOpening();
        }, PlanEditorView.TOOL_OPENING));
        panel.addView(panelItem("Peça…", () -> {
            hidePanel();
            pickers.choosePrefab();
        }, PlanEditorView.TOOL_PREFAB));
        addPanelTool(panel, "Início", PlanEditorView.TOOL_SPAWN);
        addPanelTool(panel, "Saída", PlanEditorView.TOOL_EXIT);
        addPanelTool(panel, "Selecionar área / mover cômodo",
                PlanEditorView.TOOL_GROUP);
        routeButton = panelItem("Rota do inimigo", () -> {
            hidePanel();
            startRoute();
        }, null);
        panel.addView(routeButton);
        panel.addView(panelItem("Objetivo…", () -> {
            hidePanel();
            forms.chooseObjective();
        }, null));
        materialButton = panelItem("Material / água / lava…", () -> {
            hidePanel();
            forms.chooseMaterial();
        }, null);
        panel.addView(materialButton);
        logicButton = panelItem("Lógica da peça…", () -> {
            hidePanel();
            configureSelectedLogic();
        }, null);
        panel.addView(logicButton);
        panel.addView(panelItem("Céu…", () -> {
            hidePanel();
            forms.chooseSky();
        }, null));
        duplicateButton = panelItem("Duplicar seleção", () -> {
            hidePanel();
            plan.duplicateSelected();
        }, null);
        panel.addView(duplicateButton);
        lockButton = panelItem("Travar / destravar", () -> {
            hidePanel();
            plan.toggleSelectedLock();
        }, null);
        panel.addView(lockButton);
        panel.addView(panelItem("Enquadrar seleção", () -> {
            hidePanel();
            plan.focusSelection();
        }, null));
        panel.addView(panelItem("Enquadrar mapa", () -> {
            hidePanel();
            plan.focusAll();
        }, null));
        panel.addView(panelItem("Prévia 3D orbital", () -> {
            hidePanel();
            showPreview3d();
        }, null));
        panel.addView(panelItem("Objetos…", () -> {
            hidePanel();
            pickers.showObjectList();
        }, null));
        scroll.addView(panel, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private void addPanelTool(LinearLayout panel, String label, int tool) {
        Button button = panelItem(label, () -> {
            hidePanel();
            selectTool(tool);
        }, tool);
        toolButtons.add(button);
        panel.addView(button);
    }

    private Button panelItem(String label, Runnable onClick, Integer tag) {
        Button button = new Button(activity);
        button.setText(label);
        button.setTextSize(14f);
        button.setTextColor(0xFFDDE7EE);
        button.setAllCaps(false);
        button.setBackgroundColor(0x00000000);
        button.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        button.setPadding(24, 0, 12, 0);
        if (tag != null) {
            button.setTag(tag);
        }
        button.setOnClickListener(v -> onClick.run());
        float density = activity.getResources()
                .getDisplayMetrics().density;
        button.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (int) (40f * density)));
        return button;
    }

    private void togglePanel() {
        sidePanel.setVisibility(sidePanel.getVisibility() == VISIBLE
                ? GONE : VISIBLE);
    }

    private void hidePanel() {
        sidePanel.setVisibility(GONE);
    }

    private Button action(String label, Runnable onClick) {
        Button button = new Button(activity);
        button.setText(label);
        button.setTextSize(13f);
        button.setTextColor(0xFFDDE7EE);
        button.setBackgroundColor(0x33222E3A);
        button.setPadding(28, 8, 28, 8);
        button.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.rightMargin = 8;
        button.setLayoutParams(params);
        return button;
    }

    void selectTool(int tool) {
        plan.setTool(tool);
        for (Button button : toolButtons) {
            boolean active = (int) button.getTag() == tool;
            button.setBackgroundColor(active ? 0xFF2E5A8A : 0x33222E3A);
        }
        if (tool == PlanEditorView.TOOL_SELECT) {
            status.setText("toque seleciona; arraste move; puxe o "
                    + "quadradinho da borda para esticar até alinhar "
                    + "com a parede");
        } else if (tool == PlanEditorView.TOOL_WALL) {
            status.setText("arraste para desenhar; começar perto da ponta "
                    + "de outra parede continua dela (círculos amarelos)");
        } else if (tool == PlanEditorView.TOOL_CEILING) {
            status.setText("arraste o retângulo do teto; use MEDIDAS "
                    + "para definir a elevação");
        } else if (tool == PlanEditorView.TOOL_PREFAB) {
            PrefabDefinition def = plan.activePrefab();
            status.setText(def == null ? "escolha uma peça"
                    : "toque na planta para soltar: " + def.name);
        } else if (tool == PlanEditorView.TOOL_OPENING) {
            status.setText("toque em cima de uma parede para recortar "
                    + "o vão; SELECIONAR arrasta o vão pela parede");
        } else if (tool == PlanEditorView.TOOL_PAINT) {
            status.setText("toque para pintar; parede pinta o LADO "
                    + "tocado; a BOLINHA pinta o piso/teto/bloco dela; "
                    + "balde pinta paredes ligadas");
        } else if (tool == PlanEditorView.TOOL_POINTS) {
            status.setText("toque ponto a ponto (diagonal vale); "
                    + "PRIMEIRO ponto fecha o anel; nas paredes, tocar "
                    + "o ÚLTIMO termina a linha; ↶ remove");
        } else if (tool == PlanEditorView.TOOL_GROUP) {
            status.setText("arraste uma moldura ao redor dos objetos; "
                    + "depois arraste dentro dela para mover o conjunto");
        } else {
            status.setText("arraste com um dedo; dois dedos movem a vista");
        }
    }

    // ---- PlanEditorView.Host ----

    @Override
    public void beforeChange() {
        history.push(MapJson.write(doc));
    }

    @Override
    public void afterChange() {
        boolean firstDirtyChange = !dirty;
        dirty = true;
        plan.documentChanged();
        removeCallbacks(autoSaveTask);
        postDelayed(autoSaveTask, AUTO_SAVE_DELAY_MS);
        if (firstDirtyChange) {
            postDelayed(maxAutoSaveTask, AUTO_SAVE_MAX_MS);
        }
        refreshCounts();
        refreshStoryButton();
        refreshButtons();
    }

    @Override
    public void selectionChanged(String description) {
        if (status != null && (plan.tool() == PlanEditorView.TOOL_SELECT
                || plan.tool() == PlanEditorView.TOOL_GROUP)) {
            status.setText(description == null
                    ? "nada selecionado" : description);
        }
        refreshButtons();
    }

    @Override
    public void storyChanged(float baseY) {
        refreshStoryButton();
        refreshCounts();
        if (status != null && !plan.hasSelection()) {
            status.setText("andar ativo em Y " + meters(baseY)
                    + " — tudo que você criar ficará neste pavimento");
        }
    }

    // ---- ações ----

    private void refreshButtons() {
        if (undoButton == null || deleteButton == null) {
            return; // setDocument dispara isto antes das barras existirem
        }
        boolean canUndo = history.canUndo()
                || (plan.tool() == PlanEditorView.TOOL_POINTS
                && plan.contourPoints() > 0);
        undoButton.setEnabled(canUndo);
        undoButton.setAlpha(canUndo ? 1f : 0.4f);
        redoButton.setEnabled(history.canRedo());
        redoButton.setAlpha(history.canRedo() ? 1f : 0.4f);
        boolean deletable = plan.hasSelection() && !plan.selectedLocked();
        deleteButton.setEnabled(deletable);
        deleteButton.setAlpha(deletable ? 1f : 0.4f);
        boolean measurable = plan.selectedStructure() != null
                || plan.selectedPrefab() != null
                || plan.selectedOpening() != null;
        heightButton.setEnabled(measurable && !plan.selectedLocked());
        heightButton.setAlpha(measurable && !plan.selectedLocked()
                ? 1f : 0.4f);
        boolean rotatable = plan.selectedStructure() != null
                || plan.selectedPrefab() != null;
        rotateButton.setEnabled(rotatable && !plan.selectedLocked());
        rotateButton.setAlpha(rotatable && !plan.selectedLocked()
                ? 1f : 0.4f);
        boolean routeEnabled = plan.selectedIsEnemy() && !plan.selectedLocked();
        routeButton.setEnabled(routeEnabled);
        routeButton.setAlpha(routeEnabled ? 1f : 0.4f);
        if (duplicateButton != null) {
            boolean duplicable = plan.selectedStructure() != null
                    || plan.selectedPrefab() != null
                    || plan.selectedOpening() != null
                    || plan.hasGroupSelection();
            duplicateButton.setEnabled(duplicable);
            duplicateButton.setAlpha(duplicable ? 1f : 0.4f);
        }
        if (lockButton != null) {
            lockButton.setEnabled(plan.hasSelection());
            lockButton.setAlpha(plan.hasSelection() ? 1f : 0.4f);
            lockButton.setText(plan.selectedAllLocked()
                    ? "Destravar seleção" : "Travar seleção");
        }
        if (materialButton != null) {
            boolean enabled = plan.selectedStructure() != null
                    && !plan.selectedLocked();
            materialButton.setEnabled(enabled);
            materialButton.setAlpha(enabled ? 1f : 0.4f);
        }
        if (logicButton != null) {
            PrefabInstance p = plan.selectedPrefab();
            boolean enabled = p != null && (p.prefabId.startsWith("door.")
                    || p.prefabId.startsWith("terminal.")
                    || "npc.human".equals(p.prefabId))
                    && !plan.selectedLocked();
            logicButton.setEnabled(enabled);
            logicButton.setAlpha(enabled ? 1f : 0.4f);
        }
    }

    private void refreshCounts() {
        int enemies = 0;
        int items = doc.markers.size();
        for (br.com.termia.construajogue.map.PrefabInstance p : doc.prefabs) {
            PrefabDefinition def = catalog.find(p.prefabId);
            if (def == null) {
                continue;
            }
            if (def.behavior.startsWith("drone")
                    || PrefabDefinition.BEHAVIOR_MUTANT.equals(def.behavior)
                    || PrefabDefinition.BEHAVIOR_TURRET.equals(def.behavior)
                    || PrefabDefinition.BEHAVIOR_KAMIKAZE.equals(def.behavior)
                    || PrefabDefinition.BEHAVIOR_BOSS.equals(def.behavior)) {
                enemies++;
            }
            if (def.behavior.startsWith("pickup")) {
                items++;
            }
        }
        boolean warning = doc.structures.size() > 80
                || doc.prefabs.size() > 200 || enemies > 24 || items > 64;
        counts.setText((warning ? "⚠  " : "")
                + "Y " + meters(plan.activeBaseY()) + "  ·  estruturas "
                + doc.structures.size() + "/80"
                + "  ·  peças " + doc.prefabs.size() + "/200"
                + "  ·  inimigos " + enemies + "/24"
                + "  ·  itens " + items + "/64");
        counts.setTextColor(warning ? 0xFFFFC060 : 0xFF7F96A5);
    }

    private void refreshStoryButton() {
        if (storyButton == null) return;
        storyButton.setText("Andar ativo: "
                + (Math.abs(plan.activeBaseY()) < 0.01f
                ? "térreo" : "Y " + meters(plan.activeBaseY())) + "…");
    }


    void openStoryAboveSelectedCeiling() {
        Float top = plan.selectedCeilingTop();
        if (top == null) {
            Toast.makeText(activity, "Selecione um teto primeiro; depois "
                    + "abra Andar ativo novamente", Toast.LENGTH_LONG).show();
            return;
        }
        if (Math.abs(top - plan.activeBaseY()) < 0.08f) {
            Toast.makeText(activity, "Você já está sobre essa laje",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        plan.setActiveBaseY(top);
        plan.focusAll();
        selectTool(PlanEditorView.TOOL_WALL);
        status.setText("novo andar em Y " + meters(top)
                + " — a laje já é o piso; desenhe paredes, peças e teto");
    }


    static String meters(float value) {
        return String.format(Locale.US, "%.2f m", value).replace('.', ',');
    }


    /** Abre a prévia no próprio editor; a planta continua intacta atrás. */
    private void showPreview3d() {
        if (previewOverlay != null) return;
        try {
            MapDocument snapshot = MapJson.read(MapJson.write(doc));
            previewView = new EditorPreviewView(activity, snapshot, catalog,
                    message -> activity.runOnUiThread(() -> Toast.makeText(
                            activity, "Falha na prévia 3D: " + message,
                            Toast.LENGTH_LONG).show()));
        } catch (RuntimeException failure) {
            Toast.makeText(activity, "Não consegui montar a prévia: "
                    + failure.getMessage(), Toast.LENGTH_LONG).show();
            previewView = null;
            return;
        }
        previewOverlay = new FrameLayout(activity);
        previewOverlay.setBackgroundColor(Color.BLACK);
        previewOverlay.addView(previewView, new FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        TextView hint = new TextView(activity);
        hint.setText("PRÉVIA 3D  ·  arraste para orbitar  ·  pinça aproxima");
        hint.setTextColor(0xFFDDE7EE);
        hint.setTextSize(13f);
        hint.setPadding(24, 18, 24, 18);
        hint.setBackgroundColor(0xAA10151B);
        previewOverlay.addView(hint, new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START));

        Button close = action("FECHAR 3D", this::closePreview);
        close.setTextColor(0xFFFFFFFF);
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.END);
        closeParams.setMargins(8, 8, 8, 8);
        previewOverlay.addView(close, closeParams);
        addView(previewOverlay, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
    }

    private void closePreview() {
        if (previewOverlay == null) return;
        previewView.onPause();
        removeView(previewOverlay);
        previewView = null;
        previewOverlay = null;
    }

    /** @return true se consumiu o voltar fechando a prévia. */
    public boolean closePreviewIfOpen() {
        if (previewOverlay == null) return false;
        closePreview();
        return true;
    }

    public void pausePreview() {
        if (previewView != null) previewView.onPause();
    }

    public void resumePreview() {
        if (previewView != null) previewView.onResume();
    }

    /** Próximo toque na planta marca a patrulha do inimigo. */
    private void startRoute() {
        if (plan.startRouteMode()) {
            status.setText("toque onde o inimigo deve patrulhar; toque "
                    + "no próprio inimigo para deixá-lo parado");
        }
    }




    private void configureSelectedLogic() {
        PrefabInstance selected = plan.selectedPrefab();
        if (selected == null) return;
        if ("npc.human".equals(selected.prefabId)) {
            LinearLayout form = new LinearLayout(activity);
            form.setOrientation(LinearLayout.VERTICAL);
            form.setPadding(48, 16, 48, 0);
            EditText name = forms.textField(form, "Nome",
                    textProperty(selected, "name", "Morador"), 48, 1);
            EditText role = forms.textField(form, "Papel na história",
                    textProperty(selected, "role", "Morador local"), 80, 1);
            EditText greeting = forms.textField(form, "Primeira fala",
                    textProperty(selected, "greeting",
                            "Olá. Posso explicar o que aconteceu aqui."),
                    240, 3);
            EditText background = forms.textField(form,
                    "Contexto que a IA usará na conversa",
                    textProperty(selected, "background",
                            "Conhece este lugar e ajuda o jogador."), 600, 5);
            ScrollView scroller = new ScrollView(activity);
            scroller.addView(form);
            new AlertDialog.Builder(activity)
                    .setTitle("Pessoa amigável")
                    .setView(scroller)
                    .setPositiveButton("Aplicar", (dialog, which) ->
                            plan.mutateSelected(() -> {
                                selected.properties.put("name",
                                        requiredText(name, "Morador", 48));
                                selected.properties.put("role",
                                        requiredText(role,
                                                "Morador local", 80));
                                selected.properties.put("greeting",
                                        requiredText(greeting,
                                                "Olá. Posso ajudar.", 240));
                                selected.properties.put("background",
                                        requiredText(background,
                                                "Conhece este lugar.", 600));
                            }))
                    .setNegativeButton("Cancelar", null)
                    .show();
            return;
        }
        if (selected.prefabId.startsWith("terminal.")) {
            LinearLayout form = new LinearLayout(activity);
            form.setOrientation(LinearLayout.VERTICAL);
            form.setPadding(48, 16, 48, 0);
            EditText order = forms.field(form,
                    "Ordem da sequência (0 = livre)",
                    selected.floatProperty("order", 0f));
            new AlertDialog.Builder(activity)
                    .setTitle("Terminal")
                    .setView(form)
                    .setPositiveButton("Aplicar", (dialog, which) -> {
                        try {
                            float value = Math.max(0, Math.round(EditorForms.parse(order)));
                            plan.mutateSelected(() -> {
                                if (value == 0f) {
                                    selected.properties.remove("order");
                                } else {
                                    selected.properties.put("order", value);
                                }
                            });
                        } catch (NumberFormatException bad) {
                            Toast.makeText(activity, "Número inválido",
                                    Toast.LENGTH_SHORT).show();
                        }
                    })
                    .setNegativeButton("Cancelar", null)
                    .show();
            return;
        }
        if ("door.auto".equals(selected.prefabId)) {
            Toast.makeText(activity,
                    "Esta porta abre automaticamente por proximidade",
                    Toast.LENGTH_LONG).show();
            return;
        }
        List<PrefabInstance> terminals = new ArrayList<>();
        for (PrefabInstance p : doc.prefabs) {
            if (p.prefabId.startsWith("terminal.")) terminals.add(p);
        }
        if (terminals.isEmpty()) {
            Toast.makeText(activity, "Coloque um terminal primeiro",
                    Toast.LENGTH_LONG).show();
            return;
        }
        String[] labels = new String[terminals.size()];
        for (int i = 0; i < labels.length; i++) {
            PrefabInstance p = terminals.get(i);
            labels[i] = "Terminal " + (i + 1) + "  ("
                    + String.format(Locale.US, "%.1f, %.1f",
                    p.transform.x, p.transform.z) + ")";
        }
        new AlertDialog.Builder(activity)
                .setTitle("Terminal que abre o portão")
                .setItems(labels, (dialog, which) ->
                        plan.mutateSelected(() -> selected.properties.put(
                                "controllerId", terminals.get(which).id)))
                .show();
    }


    /** Presets: nome, ambiente, horizonte/neblina, alcance, skybox. */


    /** Cores da paleta (nome + RGB 0..1). */




    // ---- PlanEditorView.Host: contorno fechado ----

    @Override
    public void polygonClosed(boolean floorRole) {
        if (!floorRole) {
            plan.finishContour(false);
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle("Fechar contorno")
                .setMessage("Criar paredes em volta do contorno? "
                        + "Trechos diagonais também ganham parede.")
                .setPositiveButton("Piso + paredes",
                        (dialog, which) -> plan.finishContour(true))
                .setNegativeButton("Só o piso",
                        (dialog, which) -> plan.finishContour(false))
                .show();
    }





    static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }



    private static String textProperty(PrefabInstance prefab, String key,
                                       String fallback) {
        String value = prefab.stringProperty(key);
        return value == null || value.trim().isEmpty() ? fallback : value;
    }

    private static String requiredText(EditText input, String fallback,
                                       int limit) {
        String value = input.getText().toString().trim();
        if (value.isEmpty()) value = fallback;
        return value.length() > limit ? value.substring(0, limit) : value;
    }


    private void undo() {
        // desenhando contorno, ↶ remove o último ponto marcado
        if (plan.tool() == PlanEditorView.TOOL_POINTS
                && plan.contourPoints() > 0) {
            plan.removeLastContourPoint();
            return;
        }
        restore(history.undo(MapJson.write(doc)));
    }

    private void redo() {
        restore(history.redo(MapJson.write(doc)));
    }

    private void restore(String snapshot) {
        if (snapshot == null) {
            return;
        }
        doc = MapJson.read(snapshot);
        plan.setDocument(doc);
        dirty = true;
        removeCallbacks(autoSaveTask);
        postDelayed(autoSaveTask, AUTO_SAVE_DELAY_MS);
        removeCallbacks(maxAutoSaveTask);
        postDelayed(maxAutoSaveTask, AUTO_SAVE_MAX_MS);
        refreshCounts();
        refreshStoryButton();
        refreshButtons();
    }

    /** Salva imediatamente; usado também pelo ciclo de vida da Activity. */
    public boolean saveNow() {
        removeCallbacks(autoSaveTask);
        removeCallbacks(maxAutoSaveTask);
        if (!dirty) {
            return true;
        }
        try {
            store.save(doc);
            dirty = false;
            return true;
        } catch (IOException failure) {
            postDelayed(maxAutoSaveTask, AUTO_SAVE_MAX_MS);
            Toast.makeText(activity, "Falha ao salvar: "
                    + failure.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    public void close() {
        if (saveNow()) {
            listener.onClose();
        }
    }

    private void test() {
        List<ValidationIssue> issues = MapValidator.validate(doc, catalog);
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (ValidationIssue issue : issues) {
            (issue.isError() ? errors : warnings).add(issue.message);
        }
        if (!errors.isEmpty()) {
            new AlertDialog.Builder(activity)
                    .setTitle("Corrija antes de testar")
                    .setMessage(join(errors))
                    .setPositiveButton("OK", null)
                    .show();
            return;
        }
        if (!warnings.isEmpty()) {
            Toast.makeText(activity, join(warnings),
                    Toast.LENGTH_LONG).show();
        }
        if (!saveNow()) {
            return;
        }
        // snapshot profundo: a partida nunca toca o documento em edição
        listener.onTest(MapJson.read(MapJson.write(doc)));
    }

    private static String join(List<String> lines) {
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append("• ").append(line);
        }
        return out.toString();
    }
}
