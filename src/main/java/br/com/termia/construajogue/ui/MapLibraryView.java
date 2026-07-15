package br.com.termia.construajogue.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import br.com.termia.construajogue.persistence.MapStore;

import java.io.IOException;
import java.util.List;

/**
 * Modo Biblioteca: lista os mapas do usuário (tocar no nome = construir),
 * botão jogar por mapa, novo mapa com nome, duplicar/excluir com
 * confirmação e o acesso à campanha original.
 */
public final class MapLibraryView extends ScrollView {

    public interface Listener {
        void onBuild(String id);

        void onPlay(String id);

        void onCreate(String name);

        void onDuplicate(String id);

        void onDelete(String id);

        void onPlayCampaign();

        /** Joga um mapa de exemplo embarcado (somente leitura). */
        void onPlayExample(String assetPath);

        /** Copia o exemplo para os mapas do usuário e abre o editor. */
        void onCopyExample(String assetPath);
    }

    private final Activity activity;
    private final MapStore store;
    private final Listener listener;
    private final LinearLayout column;

    public MapLibraryView(Activity activity, MapStore store,
                          Listener listener) {
        super(activity);
        this.activity = activity;
        this.store = store;
        this.listener = listener;
        setBackgroundColor(0xFF10151B);
        column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(48, 32, 48, 48);
        addView(column, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT));
        refresh();
    }

    public void refresh() {
        column.removeAllViews();
        TextView title = new TextView(activity);
        title.setText("CONSTRUA & JOGUE");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22f);
        column.addView(title);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, 20, 0, 20);
        actions.addView(bigButton("+ NOVO MAPA", 0xFF2E5A8A,
                this::promptNewMap));
        actions.addView(bigButton("▶ CAMPANHA", 0xFF2E7A4A,
                listener::onPlayCampaign));
        column.addView(actions);

        List<MapStore.Entry> entries = store.list();
        if (entries.isEmpty()) {
            TextView empty = new TextView(activity);
            empty.setText("Nenhum mapa ainda. Crie o primeiro ou "
                    + "comece por um exemplo abaixo.");
            empty.setTextColor(0xFF8FA3B0);
            empty.setTextSize(15f);
            empty.setPadding(0, 24, 0, 0);
            column.addView(empty);
        }
        for (MapStore.Entry entry : entries) {
            column.addView(row(entry));
        }
        addExamples();
    }

    /** Seção de exemplos embarcados (somente leitura; edita a cópia). */
    private void addExamples() {
        String[] files;
        try {
            files = activity.getAssets().list("maps/exemplos");
        } catch (IOException missing) {
            return;
        }
        if (files == null || files.length == 0) {
            return;
        }
        TextView title = new TextView(activity);
        title.setText("EXEMPLOS");
        title.setTextColor(0xFF8FA3B0);
        title.setTextSize(15f);
        title.setPadding(0, 36, 0, 4);
        column.addView(title);
        for (String file : files) {
            if (file.endsWith(".json")) {
                column.addView(exampleRow("maps/exemplos/" + file));
            }
        }
    }

    private LinearLayout exampleRow(String assetPath) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(0xFF15202B);
        row.setPadding(16, 8, 12, 8);
        LinearLayout.LayoutParams rowParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = 12;
        row.setLayoutParams(rowParams);

        String mapName = "(exemplo)";
        ImageView thumb = new ImageView(activity);
        float density = getResources().getDisplayMetrics().density;
        int tw = (int) (84f * density);
        int th = (int) (56f * density);
        try {
            br.com.termia.construajogue.map.MapDocument doc =
                    readExample(assetPath);
            mapName = doc.name;
            thumb.setImageBitmap(MapThumbnail.render(doc, tw * 2, th * 2));
        } catch (IOException | RuntimeException broken) {
            thumb.setBackgroundColor(0xFF2A333D);
        }
        thumb.setOnClickListener(v -> listener.onPlayExample(assetPath));
        LinearLayout.LayoutParams thumbParams =
                new LinearLayout.LayoutParams(tw, th);
        thumbParams.rightMargin = 20;
        thumbParams.gravity = Gravity.CENTER_VERTICAL;
        row.addView(thumb, thumbParams);

        TextView name = new TextView(activity);
        name.setText(mapName);
        name.setTextColor(0xFFC9D6E0);
        name.setTextSize(16f);
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.setOnClickListener(v -> listener.onPlayExample(assetPath));
        LinearLayout.LayoutParams grow = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        row.addView(name, grow);

        row.addView(smallButton("JOGAR",
                () -> listener.onPlayExample(assetPath)));
        row.addView(smallButton("EDITAR CÓPIA",
                () -> listener.onCopyExample(assetPath)));
        return row;
    }

    private br.com.termia.construajogue.map.MapDocument readExample(
            String assetPath) throws IOException {
        java.io.ByteArrayOutputStream buffer =
                new java.io.ByteArrayOutputStream();
        try (java.io.InputStream input =
                activity.getAssets().open(assetPath)) {
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

    private LinearLayout row(MapStore.Entry entry) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(0xFF19222C);
        row.setPadding(16, 8, 12, 8);
        LinearLayout.LayoutParams rowParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.topMargin = 12;
        row.setLayoutParams(rowParams);

        // miniatura: vista de topo do próprio mapa
        ImageView thumb = new ImageView(activity);
        float density = getResources().getDisplayMetrics().density;
        int tw = (int) (84f * density);
        int th = (int) (56f * density);
        try {
            thumb.setImageBitmap(MapThumbnail.render(
                    store.load(entry.id), tw * 2, th * 2));
        } catch (IOException | RuntimeException broken) {
            thumb.setBackgroundColor(0xFF2A333D);
        }
        thumb.setOnClickListener(v -> listener.onBuild(entry.id));
        LinearLayout.LayoutParams thumbParams =
                new LinearLayout.LayoutParams(tw, th);
        thumbParams.rightMargin = 20;
        thumbParams.gravity = Gravity.CENTER_VERTICAL;
        row.addView(thumb, thumbParams);

        TextView name = new TextView(activity);
        name.setText(entry.name);
        name.setTextColor(0xFFDDE7EE);
        name.setTextSize(17f);
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.setOnClickListener(v -> listener.onBuild(entry.id));
        LinearLayout.LayoutParams grow = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        row.addView(name, grow);

        row.addView(smallButton("CONSTRUIR",
                () -> listener.onBuild(entry.id)));
        row.addView(smallButton("JOGAR", () -> listener.onPlay(entry.id)));
        row.addView(smallButton("⋮", () -> showRowMenu(entry)));
        return row;
    }

    private void showRowMenu(MapStore.Entry entry) {
        new AlertDialog.Builder(activity)
                .setTitle(entry.name)
                .setItems(new String[]{"Duplicar", "Excluir"},
                        (dialog, which) -> {
                            if (which == 0) {
                                listener.onDuplicate(entry.id);
                            } else {
                                confirmDelete(entry);
                            }
                        })
                .show();
    }

    private void confirmDelete(MapStore.Entry entry) {
        new AlertDialog.Builder(activity)
                .setTitle("Excluir mapa")
                .setMessage("Excluir \"" + entry.name
                        + "\"? Não dá para desfazer.")
                .setPositiveButton("Excluir",
                        (dialog, which) -> listener.onDelete(entry.id))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void promptNewMap() {
        EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setHint("nome do mapa");
        new AlertDialog.Builder(activity)
                .setTitle("Novo mapa")
                .setView(input)
                .setPositiveButton("Criar", (dialog, which) -> {
                    String name = input.getText().toString().trim();
                    listener.onCreate(name.isEmpty() ? "Meu mapa" : name);
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private Button bigButton(String label, int color, Runnable onClick) {
        Button button = new Button(activity);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15f);
        button.setBackgroundColor(color);
        button.setPadding(40, 16, 40, 16);
        button.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.rightMargin = 20;
        button.setLayoutParams(params);
        return button;
    }

    private Button smallButton(String label, Runnable onClick) {
        Button button = new Button(activity);
        button.setText(label);
        button.setTextSize(12f);
        button.setTextColor(0xFFAFC9E0);
        button.setBackgroundColor(0x33222E3A);
        button.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = 8;
        params.gravity = Gravity.CENTER_VERTICAL;
        button.setLayoutParams(params);
        return button;
    }
}
