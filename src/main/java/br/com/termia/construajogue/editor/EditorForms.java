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
 * Formulários do editor: andar ativo, objetivo, material e céu, mais os
 * campos genéricos de diálogo. Lê e muta o documento SEMPRE pelos
 * ganchos beforeChange/afterChange do EditorHost.
 */
final class EditorForms {

    private final EditorHost h;

    private static final Object[][] SKIES = {
            {"Dia (sol e céu azul)", 0.60f,
                    new float[]{0.55f, 0.70f, 0.86f}, 50f, "day"},
            {"Entardecer (sol baixo)", 0.42f,
                    new float[]{0.46f, 0.30f, 0.26f}, 38f, "dusk"},
            {"Noite (lua e estrelas)", 0.20f,
                    new float[]{0.02f, 0.03f, 0.08f}, 26f, "night"},
            {"Instalação (padrão do jogo)", 0.35f,
                    new float[]{0.04f, 0.05f, 0.07f}, 30f, "none"},
    };


    EditorForms(EditorHost host) {
        h = host;
    }

    /** Escolhe um pavimento existente ou abre um novo sobre a laje. */
    void chooseStory() {
        final List<Float> levels = h.plan.storyBases();
        String[] labels = new String[levels.size() + 2];
        for (int i = 0; i < levels.size(); i++) {
            float y = levels.get(i);
            String name = Math.abs(y) < 0.01f
                    ? "Térreo · Y 0,00 m" : "Pavimento · Y " + h.meters(y);
            labels[i] = Math.abs(y - h.plan.activeBaseY()) < 0.08f
                    ? "✓  " + name : name;
        }
        labels[levels.size()] = "Novo andar sobre o teto selecionado";
        labels[levels.size() + 1] = "Elevação personalizada…";
        new AlertDialog.Builder(h.activity)
                .setTitle("Andares · topo do teto = piso")
                .setItems(labels, (dialog, which) -> {
                    if (which < levels.size()) {
                        h.plan.setActiveBaseY(levels.get(which));
                        h.plan.focusAll();
                    } else if (which == levels.size()) {
                        h.openStoryAboveSelectedCeiling();
                    } else {
                        showCustomStoryHeight();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    void showCustomStoryHeight() {
        EditText input = new EditText(h.activity);
        input.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL
                | InputType.TYPE_NUMBER_FLAG_SIGNED);
        input.setText(String.format(Locale.US, "%.2f", h.plan.activeBaseY()));
        new AlertDialog.Builder(h.activity)
                .setTitle("Elevação do andar (Y)")
                .setMessage("Use a altura do topo da laje. Faixa: -20 a 100 m.")
                .setView(input)
                .setPositiveButton("Abrir", (dialog, which) -> {
                    try {
                        float value = Float.parseFloat(input.getText()
                                .toString().replace(',', '.'));
                        h.plan.setActiveBaseY(h.clamp(value, -20f, 100f));
                        h.plan.focusAll();
                    } catch (NumberFormatException bad) {
                        Toast.makeText(h.activity, "Número inválido",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    void chooseObjective() {
        String[] labels = {"Chegar à saída", "Eliminar todos os inimigos",
                "Coletar N fichas", "Sobreviver por X segundos"};
        String[] types = {ObjectiveSpec.REACH_EXIT,
                ObjectiveSpec.ELIMINATE_ALL, ObjectiveSpec.COLLECT,
                ObjectiveSpec.SURVIVE};
        new AlertDialog.Builder(h.activity)
                .setTitle("Objetivo do mapa")
                .setItems(labels, (dialog, which) ->
                        showObjectiveForm(types[which], labels[which]))
                .show();
    }

    void showObjectiveForm(String type, String label) {
        ObjectiveSpec current = h.doc.objective == null
                ? new ObjectiveSpec() : h.doc.objective;
        LinearLayout form = new LinearLayout(h.activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(48, 16, 48, 0);
        EditText target = field(form, "Quantidade de fichas",
                current.target > 0 ? current.target : 3);
        setFieldVisible(target, ObjectiveSpec.COLLECT.equals(type));
        EditText duration = field(form, "Duração para sobreviver (s)",
                current.durationSeconds > 0f
                        ? current.durationSeconds : 30f);
        setFieldVisible(duration, ObjectiveSpec.SURVIVE.equals(type));
        EditText limit = field(form, "Tempo-limite (s; 0 = sem limite)",
                current.timeLimitSeconds);
        boolean survive = ObjectiveSpec.SURVIVE.equals(type);
        EditText two = field(form, "Meta para 2 estrelas (s; 0 = desligada)",
                current.twoStarSeconds);
        setFieldVisible(two, !survive);
        EditText three = field(form,
                "Meta para 3 estrelas (s; 0 = desligada)",
                current.threeStarSeconds);
        setFieldVisible(three, !survive);
        if (survive) {
            TextView note = new TextView(h.activity);
            note.setText("Estrelas: terminar com vida "
                    + GameResult.SURVIVE_TWO_STAR_HEALTH + "+ vale 2, "
                    + GameResult.SURVIVE_THREE_STAR_HEALTH + "+ vale 3");
            note.setTextSize(13f);
            note.setTextColor(0xFF8FA3B0);
            form.addView(note);
        }
        new AlertDialog.Builder(h.activity)
                .setTitle(label)
                .setView(form)
                .setPositiveButton("Aplicar", (dialog, which) -> {
                    try {
                        ObjectiveSpec next = new ObjectiveSpec();
                        next.type = type;
                        next.target = ObjectiveSpec.COLLECT.equals(type)
                                ? Math.max(0, Math.round(parse(target))) : 0;
                        next.durationSeconds = ObjectiveSpec.SURVIVE.equals(type)
                                ? Math.max(0f, parse(duration)) : 0f;
                        next.timeLimitSeconds = Math.max(0f, parse(limit));
                        next.twoStarSeconds = survive
                                ? 0f : Math.max(0f, parse(two));
                        next.threeStarSeconds = survive
                                ? 0f : Math.max(0f, parse(three));
                        h.beforeChange();
                        h.doc.objective = next;
                        h.afterChange();
                        h.status.setText("Objetivo: " + label);
                    } catch (NumberFormatException bad) {
                        Toast.makeText(h.activity, "Número inválido",
                                Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    void chooseMaterial() {
        final String[] ids = {"plain", "brick", "wood", "checker",
                "metal", "asphalt", "water", "lava"};
        String[] labels = {"Liso", "Tijolo", "Madeira", "Xadrez",
                "Metal", "Asfalto", "Água (lentidão)", "Lava (dano)"};
        new AlertDialog.Builder(h.activity)
                .setTitle("Material da estrutura")
                .setItems(labels, (dialog, which) ->
                        h.plan.mutateSelected(() -> {
                            StructureObject s = h.plan.selectedStructure();
                            if (s == null) return;
                            s.material = ids[which];
                            if ("water".equals(ids[which])) {
                                s.color = new float[]{0.08f, 0.36f, 0.66f};
                            } else if ("lava".equals(ids[which])) {
                                s.color = new float[]{0.95f, 0.22f, 0.04f};
                            } else if ("asphalt".equals(ids[which])) {
                                s.color = new float[]{0.16f, 0.17f, 0.19f};
                            }
                        }))
                .show();
    }

    static float parse(EditText field) {
        return Float.parseFloat(field.getText().toString()
                .replace(',', '.'));
    }

    /**
     * Céu do mapa: ajusta luz ambiente, cor do céu (o horizonte é a
     * própria neblina) e alcance da neblina. Vale para o mapa inteiro
     * e entra no desfazer.
     */
    void chooseSky() {
        String[] labels = new String[SKIES.length];
        for (int i = 0; i < SKIES.length; i++) {
            labels[i] = (String) SKIES[i][0];
        }
        new AlertDialog.Builder(h.activity)
                .setTitle("Céu e iluminação")
                .setItems(labels, (dialog, which) -> {
                    h.beforeChange();
                    h.doc.ambient = (Float) SKIES[which][1];
                    h.doc.fogColor = ((float[]) SKIES[which][2]).clone();
                    h.doc.fogFar = (Float) SKIES[which][3];
                    h.doc.sky = (String) SKIES[which][4];
                    h.afterChange();
                    Toast.makeText(h.activity, "Céu: " + labels[which]
                            + " — teste para ver", Toast.LENGTH_SHORT)
                            .show();
                })
                .show();
    }

    EditText field(LinearLayout form, String label, float value) {
        TextView caption = new TextView(h.activity);
        caption.setText(label);
        caption.setTextSize(13f);
        caption.setTextColor(0xFF8FA3B0);
        form.addView(caption);
        EditText input = new EditText(h.activity);
        input.setInputType(InputType.TYPE_CLASS_NUMBER
                | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setText(String.format(Locale.US, "%.2f", value));
        input.setTag(caption);
        form.addView(input);
        return input;
    }

    EditText textField(LinearLayout form, String label, String value,
                               int limit, int lines) {
        TextView caption = new TextView(h.activity);
        caption.setText(label);
        caption.setTextSize(13f);
        caption.setTextColor(0xFF8FA3B0);
        form.addView(caption);
        EditText input = new EditText(h.activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | (lines > 1 ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        input.setSingleLine(lines == 1);
        if (lines > 1) input.setMinLines(lines);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(limit)});
        input.setText(value);
        form.addView(input);
        return input;
    }

    static void setFieldVisible(EditText input, boolean visible) {
        int value = visible ? android.view.View.VISIBLE
                : android.view.View.GONE;
        input.setVisibility(value);
        Object caption = input.getTag();
        if (caption instanceof TextView) {
            ((TextView) caption).setVisibility(value);
        }
    }

}
