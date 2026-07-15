package br.com.termia.construajogue.editor;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import br.com.termia.construajogue.compiler.MapValidator;
import br.com.termia.construajogue.compiler.ValidationIssue;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.StructureObject;
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
 * testar) + barra de ferramentas. Salva automático ao sair e ao testar;
 * o teste recebe um snapshot profundo — o documento nunca é alterado
 * pela partida (ARQUITETURA §8).
 */
public final class EditorHost extends FrameLayout
        implements PlanEditorView.Host {

    public interface Listener {
        void onTest(MapDocument snapshot);

        void onClose();
    }

    private final Activity activity;
    private final MapStore store;
    private final PrefabCatalog catalog;
    private final Listener listener;
    private final UndoHistory history = new UndoHistory();
    private final PlanEditorView plan;
    private final TextView status;
    private final List<Button> toolButtons = new ArrayList<>();
    private Button undoButton;
    private Button redoButton;
    private Button deleteButton;
    private Button heightButton;
    private Button rotateButton;
    private Button routeButton;
    private MapDocument doc;

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
        LayoutParams statusParams = new LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        statusParams.bottomMargin = 130;
        addView(status, statusParams);

        addView(buildTopBar(), new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT, Gravity.TOP));
        addView(buildToolBar(), new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));
        selectTool(PlanEditorView.TOOL_FLOOR);
        refreshButtons();
    }

    private LinearLayout buildTopBar() {
        LinearLayout bar = new LinearLayout(activity);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(0xCC141B22);
        bar.setPadding(12, 6, 12, 6);

        bar.addView(action("← Mapas", this::close));
        TextView title = new TextView(activity);
        title.setText(doc.name);
        title.setTextColor(Color.WHITE);
        title.setTextSize(15f);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams grow = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        grow.gravity = Gravity.CENTER_VERTICAL;
        bar.addView(title, grow);
        undoButton = action("↶", this::undo);
        redoButton = action("↷", this::redo);
        bar.addView(undoButton);
        bar.addView(redoButton);
        Button test = action("▶ TESTAR", this::test);
        test.setTextColor(0xFF9CE49C);
        bar.addView(test);
        return bar;
    }

    private HorizontalScrollView buildToolBar() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(8, 4, 8, 10);
        addTool(row, "PISO", PlanEditorView.TOOL_FLOOR);
        addTool(row, "PAREDE", PlanEditorView.TOOL_WALL);
        addTool(row, "TETO", PlanEditorView.TOOL_CEILING);
        addTool(row, "BLOCO", PlanEditorView.TOOL_BLOCK);
        addTool(row, "INÍCIO", PlanEditorView.TOOL_SPAWN);
        addTool(row, "SAÍDA", PlanEditorView.TOOL_EXIT);
        addTool(row, "SELECIONAR", PlanEditorView.TOOL_SELECT);
        Button prefabButton = action("PEÇA…", this::choosePrefab);
        prefabButton.setTag(PlanEditorView.TOOL_PREFAB);
        prefabButton.setTextColor(0xFFE0C060);
        toolButtons.add(prefabButton);
        row.addView(prefabButton);
        Button openingButton = action("VÃO…", this::chooseOpening);
        openingButton.setTag(PlanEditorView.TOOL_OPENING);
        openingButton.setTextColor(0xFFC9A06C);
        toolButtons.add(openingButton);
        row.addView(openingButton);
        Button paintButton = action("PINTAR…", this::choosePaint);
        paintButton.setTag(PlanEditorView.TOOL_PAINT);
        paintButton.setTextColor(0xFFC98FD9);
        toolButtons.add(paintButton);
        row.addView(paintButton);
        Button skyButton = action("CÉU…", this::chooseSky);
        skyButton.setTextColor(0xFF8FC9F2);
        row.addView(skyButton);
        heightButton = action("MEDIDAS", this::editMeasures);
        heightButton.setTextColor(0xFF9CC9E4);
        row.addView(heightButton);
        rotateButton = action("GIRAR", () -> plan.rotateSelected());
        rotateButton.setTextColor(0xFFA0D9C9);
        row.addView(rotateButton);
        routeButton = action("ROTA", this::startRoute);
        routeButton.setTextColor(0xFFE0A0A0);
        row.addView(routeButton);
        deleteButton = action("EXCLUIR", () -> plan.deleteSelected());
        deleteButton.setTextColor(0xFFE49C9C);
        row.addView(deleteButton);

        HorizontalScrollView scroll = new HorizontalScrollView(activity);
        scroll.setBackgroundColor(0xCC141B22);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(row);
        return scroll;
    }

    private void addTool(LinearLayout row, String label, int tool) {
        Button button = action(label, () -> selectTool(tool));
        button.setTag(tool);
        toolButtons.add(button);
        row.addView(button);
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

    private void selectTool(int tool) {
        plan.setTool(tool);
        for (Button button : toolButtons) {
            boolean active = (int) button.getTag() == tool;
            button.setBackgroundColor(active ? 0xFF2E5A8A : 0x33222E3A);
        }
        if (tool == PlanEditorView.TOOL_SELECT) {
            status.setText("toque para selecionar; arraste para mover; "
                    + "MEDIDAS digita as dimensões reais");
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
            status.setText("toque para pintar; na parede pinta o LADO "
                    + "tocado (o meio pinta os dois); balde pinta as "
                    + "paredes ligadas");
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
        refreshButtons();
    }

    @Override
    public void selectionChanged(String description) {
        if (plan.tool() == PlanEditorView.TOOL_SELECT) {
            status.setText(description == null
                    ? "nada selecionado" : description);
        }
        refreshButtons();
    }

    // ---- ações ----

    private void refreshButtons() {
        if (undoButton == null || deleteButton == null) {
            return; // setDocument dispara isto antes das barras existirem
        }
        undoButton.setEnabled(history.canUndo());
        undoButton.setAlpha(history.canUndo() ? 1f : 0.4f);
        redoButton.setEnabled(history.canRedo());
        redoButton.setAlpha(history.canRedo() ? 1f : 0.4f);
        deleteButton.setEnabled(plan.hasSelection());
        deleteButton.setAlpha(plan.hasSelection() ? 1f : 0.4f);
        heightButton.setEnabled(plan.hasSelection());
        heightButton.setAlpha(plan.hasSelection() ? 1f : 0.4f);
        boolean rotatable = plan.selectedStructure() != null
                || plan.selectedPrefab() != null;
        rotateButton.setEnabled(rotatable);
        rotateButton.setAlpha(rotatable ? 1f : 0.4f);
        routeButton.setEnabled(plan.selectedIsEnemy());
        routeButton.setAlpha(plan.selectedIsEnemy() ? 1f : 0.4f);
    }

    /** Próximo toque na planta marca a patrulha do inimigo. */
    private void startRoute() {
        if (plan.startRouteMode()) {
            status.setText("toque onde o inimigo deve patrulhar; toque "
                    + "no próprio inimigo para deixá-lo parado");
        }
    }

    /** Presets de céu: nome, ambiente, cor do céu/neblina, alcance. */
    private static final Object[][] SKIES = {
            {"Dia (céu azul claro)", 0.60f,
                    new float[]{0.55f, 0.70f, 0.86f}, 50f},
            {"Entardecer (laranja)", 0.42f,
                    new float[]{0.46f, 0.30f, 0.26f}, 38f},
            {"Noite (escuro)", 0.20f,
                    new float[]{0.02f, 0.03f, 0.08f}, 26f},
            {"Instalação (padrão do jogo)", 0.35f,
                    new float[]{0.04f, 0.05f, 0.07f}, 30f},
    };

    /**
     * Céu do mapa: ajusta luz ambiente, cor do céu (o horizonte é a
     * própria neblina) e alcance da neblina. Vale para o mapa inteiro
     * e entra no desfazer.
     */
    private void chooseSky() {
        String[] labels = new String[SKIES.length];
        for (int i = 0; i < SKIES.length; i++) {
            labels[i] = (String) SKIES[i][0];
        }
        new AlertDialog.Builder(activity)
                .setTitle("Céu e iluminação")
                .setItems(labels, (dialog, which) -> {
                    beforeChange();
                    doc.ambient = (Float) SKIES[which][1];
                    doc.fogColor = ((float[]) SKIES[which][2]).clone();
                    doc.fogFar = (Float) SKIES[which][3];
                    afterChange();
                    Toast.makeText(activity, "Céu: " + labels[which]
                            + " — teste para ver", Toast.LENGTH_SHORT)
                            .show();
                })
                .show();
    }

    /** Cores da paleta (nome + RGB 0..1). */
    private static final Object[][] PALETTE = {
            {"Branco", new float[]{0.92f, 0.92f, 0.95f}},
            {"Cinza claro", new float[]{0.65f, 0.66f, 0.70f}},
            {"Cinza", new float[]{0.45f, 0.47f, 0.52f}},
            {"Grafite", new float[]{0.24f, 0.26f, 0.30f}},
            {"Tijolo", new float[]{0.62f, 0.32f, 0.22f}},
            {"Madeira", new float[]{0.55f, 0.40f, 0.25f}},
            {"Areia", new float[]{0.80f, 0.72f, 0.55f}},
            {"Verde", new float[]{0.30f, 0.55f, 0.35f}},
            {"Azul", new float[]{0.30f, 0.45f, 0.65f}},
            {"Ciano", new float[]{0.35f, 0.65f, 0.70f}},
            {"Roxo", new float[]{0.50f, 0.35f, 0.65f}},
            {"Amarelo", new float[]{0.85f, 0.75f, 0.30f}},
            {"Vermelho", new float[]{0.70f, 0.25f, 0.25f}},
    };

    /** Paleta + modo balde (paredes ligadas pintadas de uma vez). */
    private void choosePaint() {
        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(40, 16, 40, 8);
        final CheckBox bucket = new CheckBox(activity);
        bucket.setText("Balde: pintar todas as paredes ligadas "
                + "(o lado voltado para o toque)");
        bucket.setTextSize(13f);

        GridLayout grid = new GridLayout(activity);
        grid.setColumnCount(4);
        final AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("Escolha a cor")
                .setView(box)
                .setNegativeButton("Cancelar", null)
                .create();
        for (Object[] entry : PALETTE) {
            final float[] rgb = (float[]) entry[1];
            Button swatch = new Button(activity);
            swatch.setText((String) entry[0]);
            swatch.setTextSize(11f);
            float glow = rgb[0] + rgb[1] + rgb[2];
            swatch.setTextColor(glow > 1.6f ? 0xFF10151B : 0xFFEEEEEE);
            swatch.setBackgroundColor(Color.rgb((int) (rgb[0] * 255f),
                    (int) (rgb[1] * 255f), (int) (rgb[2] * 255f)));
            GridLayout.LayoutParams params = new GridLayout.LayoutParams();
            params.width = 200;
            params.setMargins(8, 8, 8, 8);
            swatch.setLayoutParams(params);
            swatch.setOnClickListener(v -> {
                plan.setActivePaint(rgb, bucket.isChecked());
                selectTool(PlanEditorView.TOOL_PAINT);
                dialog.dismiss();
            });
            grid.addView(swatch);
        }
        box.addView(grid);
        box.addView(bucket);
        dialog.show();
    }

    /** Tipo de vão para recortar na parede tocada. */
    private void chooseOpening() {
        final String[] types = {WallOpening.DOOR, WallOpening.PORTAL,
                WallOpening.WINDOW};
        String[] labels = {"Porta (1,0 × 2,1 m)",
                "Portal livre (até o teto da parede)",
                "Janela (1,2 × 1,2 m, peitoril 0,9 m)"};
        new AlertDialog.Builder(activity)
                .setTitle("Vão na parede")
                .setItems(labels, (dialog, which) -> {
                    plan.setActiveOpening(types[which]);
                    selectTool(PlanEditorView.TOOL_OPENING);
                })
                .show();
    }

    /** Navegador do catálogo: escolher arma a ferramenta PEÇA. */
    private void choosePrefab() {
        final List<PrefabDefinition> defs = catalog.all();
        String[] labels = new String[defs.size()];
        for (int i = 0; i < defs.size(); i++) {
            labels[i] = defs.get(i).name;
        }
        new AlertDialog.Builder(activity)
                .setTitle("Escolha a peça")
                .setItems(labels, (dialog, which) -> {
                    plan.setActivePrefab(defs.get(which));
                    selectTool(PlanEditorView.TOOL_PREFAB);
                })
                .show();
    }

    /**
     * Diálogo MEDIDAS: campos numéricos reais conforme a seleção.
     * Parede: comprimento/altura/espessura. Demais estruturas:
     * largura/profundidade + altura (sentido do papel). Vão:
     * largura/altura/peitoril. Peça: distância do chão.
     */
    private void editMeasures() {
        final StructureObject s = plan.selectedStructure();
        final WallOpening o = plan.selectedOpening();
        if (s == null && o == null && plan.selectedPrefab() == null) {
            return;
        }
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(48, 16, 48, 0);
        final List<EditText> fields = new ArrayList<>();
        String title;

        if (o != null) {
            title = "Medidas do vão";
            fields.add(field(form, "Largura (m)", o.width));
            fields.add(field(form, "Altura (m)", o.height));
            if (WallOpening.WINDOW.equals(o.type)) {
                fields.add(field(form, "Peitoril (m)", o.sill));
            }
        } else if (s != null) {
            boolean wall = StructureObject.ROLE_WALL
                    .equals(StructureRoles.roleOf(s));
            title = "Medidas — " + StructureRoles.name(s);
            if (wall) {
                fields.add(field(form, "Comprimento (m)",
                        Math.max(s.half[0], s.half[2]) * 2f));
                fields.add(field(form, "Altura (m)",
                        StructureRoles.heightValue(s)));
                fields.add(field(form, "Espessura (m)",
                        Math.min(s.half[0], s.half[2]) * 2f));
            } else {
                fields.add(field(form, "Largura (m)", s.half[0] * 2f));
                fields.add(field(form, "Profundidade (m)",
                        s.half[2] * 2f));
                fields.add(field(form, StructureRoles.heightLabel(s),
                        StructureRoles.heightValue(s)));
            }
        } else {
            title = "Medidas da peça";
            fields.add(field(form, "Distância do chão (m)",
                    plan.selectedPrefab().transform.y));
        }

        new AlertDialog.Builder(activity)
                .setTitle(title)
                .setView(form)
                .setPositiveButton("Aplicar",
                        (dialog, which) -> applyMeasures(s, o, fields))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void applyMeasures(StructureObject s, WallOpening o,
                               List<EditText> fields) {
        final float[] v = new float[fields.size()];
        for (int i = 0; i < fields.size(); i++) {
            try {
                v[i] = Float.parseFloat(fields.get(i).getText()
                        .toString().replace(',', '.'));
            } catch (NumberFormatException bad) {
                Toast.makeText(activity, "Número inválido",
                        Toast.LENGTH_SHORT).show();
                return;
            }
        }
        if (o != null) {
            plan.mutateSelected(() -> {
                o.width = clamp(v[0], 0.3f, 20f);
                o.height = clamp(v[1], 0.3f, 10f);
                if (v.length > 2) {
                    o.sill = clamp(v[2], 0f, 5f);
                }
            });
        } else if (s != null) {
            boolean wall = StructureObject.ROLE_WALL
                    .equals(StructureRoles.roleOf(s));
            plan.mutateSelected(() -> {
                if (wall) {
                    boolean alongX = s.half[0] >= s.half[2];
                    s.half[alongX ? 0 : 2] = clamp(v[0], 0.3f, 64f) / 2f;
                    StructureRoles.applyHeight(s, v[1]);
                    s.half[alongX ? 2 : 0] = clamp(v[2], 0.05f, 2f) / 2f;
                } else {
                    s.half[0] = clamp(v[0], 0.1f, 64f) / 2f;
                    s.half[2] = clamp(v[1], 0.1f, 64f) / 2f;
                    StructureRoles.applyHeight(s, v[2]);
                }
            });
        } else {
            plan.applySelectedHeight(v[0]);
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private EditText field(LinearLayout form, String label, float value) {
        TextView caption = new TextView(activity);
        caption.setText(label);
        caption.setTextSize(13f);
        caption.setTextColor(0xFF8FA3B0);
        form.addView(caption);
        EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format(Locale.US, "%.2f", value));
        form.addView(input);
        return input;
    }

    private void undo() {
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
        refreshButtons();
    }

    private boolean save() {
        try {
            store.save(doc);
            return true;
        } catch (IOException failure) {
            Toast.makeText(activity, "Falha ao salvar: "
                    + failure.getMessage(), Toast.LENGTH_LONG).show();
            return false;
        }
    }

    public void close() {
        save();
        listener.onClose();
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
        if (!save()) {
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
