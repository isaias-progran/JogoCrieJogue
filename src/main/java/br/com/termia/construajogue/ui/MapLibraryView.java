package br.com.termia.construajogue.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import br.com.termia.construajogue.persistence.MapStore;
import br.com.termia.construajogue.persistence.RecordStore;
import br.com.termia.construajogue.persistence.CampaignStore;
import br.com.termia.construajogue.sharing.MapShareCodec;
import br.com.termia.construajogue.sharing.QrCode;
import br.com.termia.construajogue.util.Ids;

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

        /** Propõe uma nova cópia revisada pela IA; nunca sobrescreve. */
        void onImproveAi(String id);

        void onRename(String id, String name);

        void onDelete(String id);

        void onExport(String id);

        void onImportFile();

        void onImportExchange();

        void onPlayUserCampaign(List<String> mapIds);

        void onPlayCampaign();

        /** Chave pessoal e limites da integração opcional. */
        void onConfigureAi();

        /** Pede um plano à IA e salva somente após prévia/validação. */
        void onGenerateAiScenario();

        /** Joga um mapa de exemplo embarcado (somente leitura). */
        void onPlayExample(String assetPath);

        /** Copia o exemplo para os mapas do usuário e abre o editor. */
        void onCopyExample(String assetPath);
    }

    private final Activity activity;
    private final MapStore store;
    private final RecordStore records;
    private final CampaignStore campaigns;
    private final Listener listener;
    private final LinearLayout column;

    public MapLibraryView(Activity activity, MapStore store,
                          RecordStore records, CampaignStore campaigns,
                          Listener listener) {
        super(activity);
        this.activity = activity;
        this.store = store;
        this.records = records;
        this.campaigns = campaigns;
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

        // Versão visível: prova de qual APK está instalado de verdade.
        TextView version = new TextView(activity);
        String installed;
        try {
            installed = "v" + activity.getPackageManager()
                    .getPackageInfo(activity.getPackageName(), 0)
                    .versionName;
        } catch (Exception missing) {
            installed = "versão desconhecida";
        }
        version.setText(installed);
        version.setTextColor(0xFF7F96A5);
        version.setTextSize(12f);
        column.addView(version);

        LinearLayout actions = new LinearLayout(activity);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(0, 20, 0, 20);
        actions.addView(bigButton("+ NOVO MAPA", 0xFF2E5A8A,
                this::promptNewMap));
        actions.addView(bigButton("▶ CAMPANHA", 0xFF2E7A4A,
                listener::onPlayCampaign));
        column.addView(actions);

        LinearLayout aiActions = new LinearLayout(activity);
        aiActions.setOrientation(LinearLayout.HORIZONTAL);
        aiActions.setPadding(0, 0, 0, 20);
        aiActions.addView(bigButton("⚙ CONFIGURAR IA", 0xFF325A70,
                listener::onConfigureAi));
        aiActions.addView(bigButton("✦ GERAR CENÁRIO COM IA", 0xFF356B63,
                listener::onGenerateAiScenario));
        column.addView(aiActions);

        LinearLayout sharing = new LinearLayout(activity);
        sharing.setOrientation(LinearLayout.HORIZONTAL);
        sharing.setPadding(0, 0, 0, 20);
        sharing.addView(bigButton("IMPORTAR", 0xFF5B4778,
                this::showImportMenu));
        sharing.addView(bigButton("▶ MINHA CAMPANHA", 0xFF7A5A2E,
                this::playUserCampaign));
        sharing.addView(bigButton("ORGANIZAR", 0xFF394B5A,
                () -> promptCampaign(false)));
        column.addView(sharing);

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
        name.setMaxLines(2);
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.setOnClickListener(v -> listener.onPlayExample(assetPath));
        LinearLayout.LayoutParams grow = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        row.addView(name, grow);

        row.addView(actionColumn(
                smallButton("JOGAR",
                        () -> listener.onPlayExample(assetPath)),
                smallButton("EDITAR CÓPIA",
                        () -> listener.onCopyExample(assetPath))));
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
        RecordStore.Record record = records.get(entry.id);
        if (record.exists()) {
            name.setText(entry.name + "\n" + stars(record.stars)
                    + "  ·  " + Math.round(record.bestSeconds) + "s"
                    + "  ·  " + Math.round(record.bestAccuracy * 100f)
                    + "% precisão");
        } else {
            name.setText(entry.name);
        }
        name.setTextColor(0xFFDDE7EE);
        name.setTextSize(17f);
        name.setMaxLines(3);
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.setOnClickListener(v -> listener.onBuild(entry.id));
        LinearLayout.LayoutParams grow = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f);
        row.addView(name, grow);

        row.addView(actionColumn(
                smallButton("CONSTRUIR",
                        () -> listener.onBuild(entry.id)),
                smallButton("JOGAR", () -> listener.onPlay(entry.id)),
                smallButton("EXCLUIR", () -> confirmDelete(entry)),
                smallButton("⋮", () -> showRowMenu(entry))));
        return row;
    }

    private static String stars(int count) {
        return count >= 3 ? "★★★" : count == 2 ? "★★☆" : "★☆☆";
    }

    private void showRowMenu(MapStore.Entry entry) {
        new AlertDialog.Builder(activity)
                .setTitle(entry.name)
                .setItems(new String[]{"✦ Melhorar com IA…", "Renomear",
                                "Duplicar", "Exportar / compartilhar",
                                "Excluir"},
                        (dialog, which) -> {
                            if (which == 0) {
                                listener.onImproveAi(entry.id);
                            } else if (which == 1) {
                                promptRename(entry);
                            } else if (which == 2) {
                                listener.onDuplicate(entry.id);
                            } else if (which == 3) {
                                showShareMenu(entry);
                            } else {
                                confirmDelete(entry);
                            }
                        })
                .show();
    }

    private void showShareMenu(MapStore.Entry entry) {
        new AlertDialog.Builder(activity)
                .setTitle("Compartilhar " + entry.name)
                .setItems(new String[]{"Exportar arquivo JSON",
                                "Copiar código de texto",
                                "Compartilhar código…", "Mostrar QR"},
                        (dialog, which) -> {
                            if (which == 0) listener.onExport(entry.id);
                            else if (which == 1) copyCode(entry.id);
                            else if (which == 2) shareCode(entry.id);
                            else showQr(entry.id);
                        })
                .show();
    }

    private String codeFor(String id) throws IOException {
        return MapShareCodec.encode(store.load(id));
    }

    private void copyCode(String id) {
        try {
            String code = codeFor(id);
            ClipboardManager clipboard = (ClipboardManager) activity
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText(
                    "Mapa Construa & Jogue", code));
            toast("Código copiado (" + code.length() + " caracteres)");
        } catch (IOException | RuntimeException failure) {
            toast("Não consegui criar o código: " + failure.getMessage());
        }
    }

    private void shareCode(String id) {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_SUBJECT, "Mapa Construa & Jogue");
            intent.putExtra(Intent.EXTRA_TEXT, codeFor(id));
            activity.startActivity(Intent.createChooser(intent,
                    "Compartilhar mapa"));
        } catch (IOException | RuntimeException failure) {
            toast("Não consegui compartilhar: " + failure.getMessage());
        }
    }

    private void showQr(String id) {
        try {
            String code = codeFor(id);
            QrCode qr = QrCode.encodeText(code);
            int quiet = 4;
            int pixels = Math.min(900, Math.max(360,
                    activity.getResources().getDisplayMetrics().widthPixels
                            - 64));
            int scale = Math.max(2, pixels / (qr.size + quiet * 2));
            int side = (qr.size + quiet * 2) * scale;
            Bitmap bitmap = Bitmap.createBitmap(side, side,
                    Bitmap.Config.ARGB_8888);
            int[] row = new int[side];
            for (int y = 0; y < side; y++) {
                int my = y / scale - quiet;
                for (int x = 0; x < side; x++) {
                    int mx = x / scale - quiet;
                    row[x] = qr.module(mx, my) ? Color.BLACK : Color.WHITE;
                }
                bitmap.setPixels(row, 0, side, 0, y, side, 1);
            }
            ImageView image = new ImageView(activity);
            image.setImageBitmap(bitmap);
            image.setAdjustViewBounds(true);
            image.setPadding(24, 24, 24, 24);
            new AlertDialog.Builder(activity)
                    .setTitle("QR do mapa")
                    .setMessage("Aponte outra câmera. Se o mapa crescer "
                            + "demais, use o arquivo ou código de texto.")
                    .setView(image)
                    .setPositiveButton("Fechar", null)
                    .show();
        } catch (IOException | RuntimeException failure) {
            new AlertDialog.Builder(activity)
                    .setTitle("QR indisponível")
                    .setMessage(failure.getMessage()
                            + "\n\nO código de texto e o arquivo continuam "
                            + "disponíveis.")
                    .setPositiveButton("OK", null)
                    .show();
        }
    }

    private void promptRename(MapStore.Entry entry) {
        EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setText(entry.name);
        input.selectAll();
        new AlertDialog.Builder(activity)
                .setTitle("Renomear mapa")
                .setView(input)
                .setPositiveButton("Salvar", (dialog, which) ->
                        listener.onRename(entry.id,
                                input.getText().toString()))
                .setNegativeButton("Cancelar", null)
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

    private void showImportMenu() {
        new AlertDialog.Builder(activity)
                .setTitle("Importar mapa")
                .setItems(new String[]{"Colar código de texto",
                                "Escolher arquivo…",
                                "Pasta /sdcard/TermIa/troca"},
                        (dialog, which) -> {
                            if (which == 0) promptImportCode();
                            else if (which == 1) listener.onImportFile();
                            else listener.onImportExchange();
                        })
                .show();
    }

    private void promptImportCode() {
        EditText input = new EditText(activity);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setHint("CJ2:… ou JSON");
        input.setMinLines(5);
        ClipboardManager clipboard = (ClipboardManager) activity
                .getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard.hasPrimaryClip()
                && clipboard.getPrimaryClip().getItemCount() > 0) {
            CharSequence clip = clipboard.getPrimaryClip().getItemAt(0)
                    .coerceToText(activity);
            if (clip != null && (clip.toString().trim().startsWith("CJ2:")
                    || clip.toString().trim().startsWith("{"))) {
                input.setText(clip);
            }
        }
        new AlertDialog.Builder(activity)
                .setTitle("Código do mapa")
                .setView(input)
                .setPositiveButton("Importar", (dialog, which) -> {
                    try {
                        importDocument(MapShareCodec.decode(
                                input.getText().toString()));
                    } catch (IOException | RuntimeException failure) {
                        toast("Código inválido: " + failure.getMessage());
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void importDocument(
            br.com.termia.construajogue.map.MapDocument doc)
            throws IOException {
        doc.id = Ids.create();
        doc.name = (doc.name == null || doc.name.trim().isEmpty()
                ? "Mapa importado" : doc.name.trim() + " (importado)");
        store.save(doc);
        toast("Mapa importado");
        refresh();
    }

    private void playUserCampaign() {
        List<String> ids = validCampaignIds();
        if (ids.isEmpty()) {
            promptCampaign(false);
        } else {
            listener.onPlayUserCampaign(ids);
        }
    }

    private List<String> validCampaignIds() {
        List<MapStore.Entry> entries = store.list();
        java.util.Set<String> known = new java.util.HashSet<>();
        for (MapStore.Entry entry : entries) known.add(entry.id);
        List<String> valid = new java.util.ArrayList<>();
        for (String id : campaigns.get()) if (known.contains(id)) valid.add(id);
        if (!valid.equals(campaigns.get())) campaigns.set(valid);
        return valid;
    }

    /** Seleção em ordem de toque; "recomeçar" facilita reordenar tudo. */
    private void promptCampaign(boolean emptyOrder) {
        List<MapStore.Entry> entries = store.list();
        if (entries.isEmpty()) {
            toast("Crie ao menos um mapa antes da campanha");
            return;
        }
        List<String> order = new java.util.ArrayList<>();
        if (!emptyOrder) order.addAll(validCampaignIds());
        // Os atuais aparecem primeiro, já na ordem salva.
        List<MapStore.Entry> display = new java.util.ArrayList<>();
        for (String id : order) {
            for (MapStore.Entry entry : entries) {
                if (entry.id.equals(id)) display.add(entry);
            }
        }
        for (MapStore.Entry entry : entries) {
            if (!order.contains(entry.id)) display.add(entry);
        }
        String[] labels = new String[display.size()];
        boolean[] checked = new boolean[display.size()];
        for (int i = 0; i < display.size(); i++) {
            labels[i] = display.get(i).name;
            checked[i] = order.contains(display.get(i).id);
        }
        new AlertDialog.Builder(activity)
                .setTitle("Minha campanha")
                .setMessage("Marque os mapas na ordem em que serão "
                        + "jogados. O tempo é somado entre eles.")
                .setMultiChoiceItems(labels, checked, (dialog, which, on) -> {
                    String id = display.get(which).id;
                    if (on && !order.contains(id)) order.add(id);
                    else if (!on) order.remove(id);
                })
                .setPositiveButton("Salvar", (dialog, which) -> {
                    campaigns.set(order);
                    toast(order.isEmpty() ? "Campanha vazia"
                            : order.size() + " mapa(s) na campanha");
                })
                .setNeutralButton("Recomeçar ordem", (dialog, which) ->
                        promptCampaign(true))
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void toast(String message) {
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
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
        button.setTextSize(13f);
        button.setBackgroundColor(color);
        button.setPadding(dp(6), dp(8), dp(6), dp(8));
        button.setMaxLines(2);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.rightMargin = dp(6);
        button.setLayoutParams(params);
        return button;
    }

    private Button smallButton(String label, Runnable onClick) {
        Button button = new Button(activity);
        button.setText(label);
        button.setTextSize(12f);
        button.setTextColor(0xFFAFC9E0);
        button.setBackgroundColor(0x33222E3A);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(5), dp(5), dp(5), dp(5));
        button.setOnClickListener(v -> onClick.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(3);
        params.gravity = Gravity.CENTER_VERTICAL;
        button.setLayoutParams(params);
        return button;
    }

    /** Ações empilhadas deixam nome e miniatura legíveis em retrato. */
    private LinearLayout actionColumn(Button... buttons) {
        LinearLayout column = new LinearLayout(activity);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        for (Button button : buttons) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(2);
            column.addView(button, params);
        }
        return column;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
