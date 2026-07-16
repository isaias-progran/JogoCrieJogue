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
 * Seletores e medidas do editor: paleta de pintura, cor livre, desenho
 * por pontos, vão, peça, lista de objetos e o formulário MEDIDAS. Muta o
 * documento somente por mutateSelected/beforeChange/afterChange do host.
 */
final class EditorPickers {

    private final EditorHost h;

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


    EditorPickers(EditorHost host) {
        h = host;
    }

    void showObjectList() {
        int count = h.plan.objectCount();
        if (count == 0) {
            Toast.makeText(h.activity, "O mapa ainda está vazio",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[count];
        for (int i = 0; i < count; i++) {
            labels[i] = h.plan.objectLabel(i);
        }
        new AlertDialog.Builder(h.activity)
                .setTitle("Objetos do mapa")
                .setItems(labels, (dialog, which) -> {
                    h.selectTool(PlanEditorView.TOOL_SELECT);
                    h.plan.selectObject(which);
                })
                .setNegativeButton("Fechar", null)
                .show();
    }

    /** Paleta + modo balde (paredes ligadas pintadas de uma vez). */
    void choosePaint() {
        LinearLayout box = new LinearLayout(h.activity);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(40, 16, 40, 8);
        final CheckBox bucket = new CheckBox(h.activity);
        bucket.setText("Balde: pintar todas as paredes ligadas "
                + "(o lado voltado para o toque)");
        bucket.setTextSize(13f);

        GridLayout grid = new GridLayout(h.activity);
        grid.setColumnCount(4);
        final AlertDialog dialog = new AlertDialog.Builder(h.activity)
                .setTitle("Escolha a cor")
                .setView(box)
                .setNegativeButton("Cancelar", null)
                .create();
        for (Object[] entry : PALETTE) {
            final float[] rgb = (float[]) entry[1];
            Button swatch = new Button(h.activity);
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
                h.plan.setActivePaint(rgb, bucket.isChecked());
                h.selectTool(PlanEditorView.TOOL_PAINT);
                dialog.dismiss();
            });
            grid.addView(swatch);
        }
        box.addView(grid);
        Button custom = new Button(h.activity);
        custom.setText("COR PERSONALIZADA…");
        custom.setAllCaps(false);
        custom.setOnClickListener(v -> {
            boolean useBucket = bucket.isChecked();
            dialog.dismiss();
            showCustomColor(useBucket);
        });
        box.addView(custom);
        box.addView(bucket);
        dialog.show();
    }

    /** Seletor RGB sem dependências externas, com prévia em tempo real. */
    void showCustomColor(boolean bucket) {
        LinearLayout form = new LinearLayout(h.activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(48, 20, 48, 8);
        final int[] rgb = {128, 128, 128};
        TextView preview = new TextView(h.activity);
        preview.setText("PRÉVIA");
        preview.setTextColor(Color.WHITE);
        preview.setGravity(Gravity.CENTER);
        preview.setBackgroundColor(Color.rgb(rgb[0], rgb[1], rgb[2]));
        form.addView(preview, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 88));
        String[] names = {"Vermelho", "Verde", "Azul"};
        for (int i = 0; i < 3; i++) {
            final int channel = i;
            TextView label = new TextView(h.activity);
            label.setText(names[i] + ": " + rgb[i]);
            label.setTextColor(0xFFDDE7EE);
            form.addView(label);
            SeekBar slider = new SeekBar(h.activity);
            slider.setMax(255);
            slider.setProgress(rgb[i]);
            slider.setOnSeekBarChangeListener(
                    new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(SeekBar bar, int value,
                                                      boolean fromUser) {
                            rgb[channel] = value;
                            label.setText(names[channel] + ": " + value);
                            preview.setBackgroundColor(Color.rgb(
                                    rgb[0], rgb[1], rgb[2]));
                        }

                        @Override public void onStartTrackingTouch(SeekBar b) {}
                        @Override public void onStopTrackingTouch(SeekBar b) {}
                    });
            form.addView(slider);
        }
        new AlertDialog.Builder(h.activity)
                .setTitle("Cor personalizada")
                .setView(form)
                .setPositiveButton("Usar", (dialog, which) -> {
                    h.plan.setActivePaint(new float[]{rgb[0] / 255f,
                            rgb[1] / 255f, rgb[2] / 255f}, bucket);
                    h.selectTool(PlanEditorView.TOOL_PAINT);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    /** Contorno livre: escolhe o papel e arma a ferramenta de pontos. */
    void chooseContour() {
        final String[] roles = {StructureObject.ROLE_FLOOR,
                StructureObject.ROLE_CEILING, StructureObject.ROLE_WALL};
        new AlertDialog.Builder(h.activity)
                .setTitle("Desenho por pontos")
                .setItems(new String[]{"Piso (contorno livre)",
                        "Teto (contorno livre)",
                        "Paredes (linha de pontos, aceita diagonal)"},
                        (dialog, which) -> {
                    h.plan.startContour(roles[which]);
                    h.selectTool(PlanEditorView.TOOL_POINTS);
                })
                .show();
    }

    /** Tipo de vão para recortar na parede tocada. */
    void chooseOpening() {
        final String[] types = {WallOpening.DOOR, WallOpening.PORTAL,
                WallOpening.WINDOW, "window_bath"};
        String[] labels = {"Porta (1,0 × 2,1 m)",
                "Portal livre (até o teto da parede)",
                "Janela (1,2 × 1,2 m, peitoril 0,9 m)",
                "Janela de banheiro (0,6 × 0,6 m, peitoril 1,5 m)"};
        new AlertDialog.Builder(h.activity)
                .setTitle("Vão na parede")
                .setItems(labels, (dialog, which) -> {
                    h.plan.setActiveOpening(types[which]);
                    h.selectTool(PlanEditorView.TOOL_OPENING);
                })
                .show();
    }

    /** Navegador do catálogo: escolher arma a ferramenta PEÇA. */
    void choosePrefab() {
        final List<PrefabDefinition> defs = h.catalog.all();
        String[] labels = new String[defs.size()];
        for (int i = 0; i < defs.size(); i++) {
            labels[i] = defs.get(i).name;
        }
        new AlertDialog.Builder(h.activity)
                .setTitle("Escolha a peça")
                .setItems(labels, (dialog, which) -> {
                    h.plan.setActivePrefab(defs.get(which));
                    h.selectTool(PlanEditorView.TOOL_PREFAB);
                })
                .show();
    }

    /**
     * Diálogo MEDIDAS: campos numéricos reais conforme a seleção.
     * Parede: comprimento/altura/espessura. Demais estruturas:
     * largura/profundidade + altura (sentido do papel). Vão:
     * largura/altura/peitoril. Peça: distância do chão.
     */
    void editMeasures() {
        final StructureObject s = h.plan.selectedStructure();
        final WallOpening o = h.plan.selectedOpening();
        if (s == null && o == null && h.plan.selectedPrefab() == null) {
            return;
        }
        LinearLayout form = new LinearLayout(h.activity);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(48, 16, 48, 0);
        final List<EditText> fields = new ArrayList<>();
        String title;

        if (o != null) {
            title = "Medidas do vão";
            fields.add(h.forms.field(form, "Largura (m)", o.width));
            fields.add(h.forms.field(form, "Altura (m)", o.height));
            if (WallOpening.WINDOW.equals(o.type)) {
                fields.add(h.forms.field(form, "Peitoril (m)", o.sill));
            }
        } else if (s != null) {
            boolean wall = StructureObject.ROLE_WALL
                    .equals(StructureRoles.roleOf(s));
            title = "Medidas — " + StructureRoles.name(s);
            if (wall) {
                fields.add(h.forms.field(form, "Comprimento (m)",
                        WallGeometry.halfLength(s) * 2f));
                fields.add(h.forms.field(form, "Altura (m)",
                        StructureRoles.heightValue(s)));
                fields.add(h.forms.field(form, "Espessura (m)",
                        WallGeometry.thickness(s)));
            } else {
                fields.add(h.forms.field(form, "Largura (m)", s.half[0] * 2f));
                fields.add(h.forms.field(form, "Profundidade (m)",
                        s.half[2] * 2f));
                fields.add(h.forms.field(form, StructureRoles.heightLabel(s),
                        StructureRoles.heightValue(s)));
            }
        } else {
            title = "Medidas da peça";
            fields.add(h.forms.field(form, "Distância do chão (m)",
                    h.plan.selectedPrefab().transform.y
                            - h.plan.activeBaseY()));
        }

        new AlertDialog.Builder(h.activity)
                .setTitle(title)
                .setView(form)
                .setPositiveButton("Aplicar",
                        (dialog, which) -> applyMeasures(s, o, fields))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    void applyMeasures(StructureObject s, WallOpening o,
                               List<EditText> fields) {
        final float[] v = new float[fields.size()];
        for (int i = 0; i < fields.size(); i++) {
            try {
                v[i] = Float.parseFloat(fields.get(i).getText()
                        .toString().replace(',', '.'));
            } catch (NumberFormatException bad) {
                Toast.makeText(h.activity, "Número inválido",
                        Toast.LENGTH_SHORT).show();
                return;
            }
        }
        if (o != null) {
            h.plan.mutateSelected(() -> {
                o.width = h.clamp(v[0], 0.3f, 20f);
                o.height = h.clamp(v[1], 0.3f, 10f);
                if (v.length > 2) {
                    o.sill = h.clamp(v[2], 0f, 5f);
                }
            });
        } else if (s != null) {
            boolean wall = StructureObject.ROLE_WALL
                    .equals(StructureRoles.roleOf(s));
            h.plan.mutateSelected(() -> {
                if (wall) {
                    WallGeometry.resize(s, h.clamp(v[0], 0.3f, 64f),
                            h.clamp(v[2], 0.05f, 2f));
                    StructureRoles.applyHeight(s, v[1]);
                } else {
                    s.half[0] = h.clamp(v[0], 0.1f, 64f) / 2f;
                    s.half[2] = h.clamp(v[1], 0.1f, 64f) / 2f;
                    StructureRoles.applyHeight(s, v[2]);
                }
            });
        } else {
            h.plan.applySelectedHeight(v[0]);
        }
    }

}
