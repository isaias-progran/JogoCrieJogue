package br.com.termia.construajogue.editor;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import br.com.termia.construajogue.compiler.MapValidator;
import br.com.termia.construajogue.compiler.ValidationIssue;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.StructureObject;
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
        addTool(row, "MOVER", PlanEditorView.TOOL_SELECT);
        Button prefabButton = action("PEÇA…", this::choosePrefab);
        prefabButton.setTag(PlanEditorView.TOOL_PREFAB);
        prefabButton.setTextColor(0xFFE0C060);
        toolButtons.add(prefabButton);
        row.addView(prefabButton);
        heightButton = action("ALTURA", this::editHeight);
        heightButton.setTextColor(0xFF9CC9E4);
        row.addView(heightButton);
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
                    + "ALTURA edita a medida real");
        } else if (tool == PlanEditorView.TOOL_WALL) {
            status.setText("arraste para desenhar; começar perto da ponta "
                    + "de outra parede continua dela (círculos amarelos)");
        } else if (tool == PlanEditorView.TOOL_CEILING) {
            status.setText("arraste o retângulo do teto; use ALTURA para "
                    + "definir a elevação");
        } else if (tool == PlanEditorView.TOOL_PREFAB) {
            PrefabDefinition def = plan.activePrefab();
            status.setText(def == null ? "escolha uma peça"
                    : "toque na planta para soltar: " + def.name);
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

    /** Diálogo para digitar a medida real da seleção. */
    private void editHeight() {
        StructureObject s = plan.selectedStructure();
        if (s == null && plan.selectedPrefab() == null) {
            return;
        }
        EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format(Locale.US, "%.2f", s != null
                ? StructureRoles.heightValue(s)
                : plan.selectedPrefab().transform.y));
        input.selectAll();
        new AlertDialog.Builder(activity)
                .setTitle(s != null ? StructureRoles.heightLabel(s)
                        : "Distância do chão (m)")
                .setView(input)
                .setPositiveButton("Aplicar", (dialog, which) -> {
                    try {
                        plan.applySelectedHeight(Float.parseFloat(
                                input.getText().toString()
                                        .replace(',', '.')));
                    } catch (NumberFormatException bad) {
                        Toast.makeText(activity, "Número inválido",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
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
