package br.com.termia.construajogue.ai;

import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.PrefabInstance;
import br.com.termia.construajogue.prefab.PrefabCatalog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Macros do modo LIVRE: `definir <nome>` grava as linhas seguintes até
 * `fim`; `usar <nome> <x> <z> [rot] [tom]` reexecuta o bloco com
 * deslocamento, rotação de 90° e variação de tom. Os limites do mapa
 * valem APÓS a expansão (macro-bomba é interrompida com aviso) e o
 * estado de última parede/peça fica confinado a cada `usar`.
 */
final class AiFreeMacros {

    private static final int MAX_MACROS = 32;
    /** Comandos de documento/marcador não fazem sentido carimbados. */
    private static final List<String> FORBIDDEN = Arrays.asList(
            "nome", "titulo", "ceu", "som", "ambiente", "neblina",
            "objetivo", "inicio", "saida", "usar");

    private final Map<String, List<String>> macros = new HashMap<>();
    private String recordingName;
    private List<String> recordingLines;
    private boolean recordingValid;

    /** Devolve true se a linha pertence ao subsistema de macros. */
    boolean handle(String command, String[] parts, int lineNumber,
                   String line, MapDocument doc, PrefabCatalog catalog,
                   AiFreeMapScript.Result result) {
        if (recordingLines != null) {
            record(command, line, lineNumber, result);
            return true;
        }
        if ("definir".equals(command)) {
            begin(parts, lineNumber, result);
            return true;
        }
        if ("fim".equals(command)) {
            warn(result, "linha " + lineNumber + ": 'fim' sem 'definir'");
            return true;
        }
        if ("usar".equals(command)) {
            expand(parts, lineNumber, doc, catalog, result);
            return true;
        }
        return false;
    }

    /** Roteiro acabou gravando: fecha o bloco aberto com aviso. */
    void endOfScript(AiFreeMapScript.Result result) {
        if (recordingLines == null) return;
        warn(result, "'definir " + recordingName
                + "' sem 'fim'; macro fechado no fim do roteiro");
        close(result);
    }

    private void begin(String[] parts, int lineNumber,
                       AiFreeMapScript.Result result) {
        String name = parts.length > 1 ? parts[1] : "";
        recordingLines = new ArrayList<>();
        recordingName = name;
        recordingValid = true;
        if (!name.matches("[A-Za-z0-9_]{1,24}")) {
            warn(result, "linha " + lineNumber + ": nome de macro "
                    + "inválido (letras, números e _ até 24); bloco "
                    + "descartado");
            recordingValid = false;
        } else if (macros.size() >= MAX_MACROS
                && !macros.containsKey(name)) {
            warn(result, "linha " + lineNumber
                    + ": limite de macros atingido; bloco descartado");
            recordingValid = false;
        } else if (macros.containsKey(name)) {
            warn(result, "linha " + lineNumber + ": macro '" + name
                    + "' redefinido");
        }
    }

    private void record(String command, String line, int lineNumber,
                        AiFreeMapScript.Result result) {
        if ("fim".equals(command)) {
            close(result);
            return;
        }
        if ("definir".equals(command)) {
            warn(result, "linha " + lineNumber + ": 'definir' dentro de "
                    + "macro é ignorado (o bloco '" + recordingName
                    + "' continua)");
            return;
        }
        if (FORBIDDEN.contains(command)) {
            warn(result, "linha " + lineNumber + ": '" + command
                    + "' não pode entrar em macro; linha ignorada");
            return;
        }
        recordingLines.add(line);
    }

    private void close(AiFreeMapScript.Result result) {
        if (recordingValid) {
            if (recordingLines.isEmpty()) {
                warn(result, "macro '" + recordingName + "' vazio");
            }
            macros.put(recordingName, recordingLines);
        }
        recordingLines = null;
        recordingName = null;
    }

    private void expand(String[] parts, int lineNumber, MapDocument doc,
                        PrefabCatalog catalog,
                        AiFreeMapScript.Result result) {
        if (parts.length < 4) {
            throw new IllegalArgumentException(
                    "usar precisa de nome, x e z");
        }
        List<String> body = macros.get(parts[1]);
        if (body == null) {
            warn(result, "linha " + lineNumber + ": macro desconhecido '"
                    + parts[1] + "'");
            return;
        }
        Rewriter rewriter = new Rewriter(parts);
        AiFreeMapScript.Cursor cursor = new AiFreeMapScript.Cursor();
        for (String raw : body) {
            String[] p = raw.split("\\s+");
            String cmd = p[0].toLowerCase(Locale.ROOT);
            try {
                String[] rewritten = rewriter.rewrite(cmd, p);
                String line = rewritten == p ? raw
                        : String.join(" ", rewritten);
                PrefabInstance before = cursor.prefab;
                if (!AiFreeMapScript.execute(cmd, rewritten, line, doc,
                        catalog, result, cursor)) {
                    warn(result, "macro " + parts[1]
                            + ": comando desconhecido '" + cmd + "'");
                }
                if (cursor.prefab != before && rewriter.swapsAxes()) {
                    presetDoorHalves(cursor.prefab);
                }
            } catch (AiFreeMapScript.LimitReached full) {
                warn(result, "linha " + lineNumber + ": macro-bomba — "
                        + "'usar " + parts[1] + "' interrompido ("
                        + full.getMessage() + ")");
                return;
            } catch (RuntimeException bad) {
                warn(result, "macro " + parts[1] + ": linha ignorada ("
                        + cmd + "): " + bad.getMessage());
            }
        }
    }

    /**
     * Porta girada 90/270°: o collider é AABB de mundo (halfX/halfZ
     * ignoram yaw), então os padrões nascem já trocados. `prop` dentro
     * do macro sobrescreve depois, também com o nome trocado.
     */
    private static void presetDoorHalves(PrefabInstance door) {
        boolean gate = "door.gate".equals(door.prefabId);
        if (!gate && !"door.auto".equals(door.prefabId)) return;
        if (door.properties.get("halfX") == null) {
            door.properties.put("halfX", gate ? 0.18f : 0.08f);
        }
        if (door.properties.get("halfZ") == null) {
            door.properties.put("halfZ", gate ? 2.0f : 1.10f);
        }
    }

    private static void warn(AiFreeMapScript.Result result,
                             String message) {
        AiFreeMapScript.warn(result.warnings, message);
    }

    /** Aplica deslocamento, rotação e tom aos argumentos de uma linha. */
    private static final class Rewriter {
        private final float anchorX;
        private final float anchorZ;
        private final int rot;
        private final float toneFactor;
        private final float[] toneColor;
        private boolean lastWallAlongX = true;
        private boolean lastWallDiagonal;

        Rewriter(String[] parts) {
            anchorX = AiFreeMapScript.number(parts[2]);
            anchorZ = AiFreeMapScript.number(parts[3]);
            int turn = 0;
            int index = 4;
            if (parts.length > index && ("0".equals(parts[index])
                    || "90".equals(parts[index])
                    || "180".equals(parts[index])
                    || "270".equals(parts[index]))) {
                turn = Integer.parseInt(parts[index]);
                index++;
            }
            rot = turn;
            float factor = 0f;
            float[] color = null;
            if (parts.length > index) {
                String tone = parts[index].toLowerCase(Locale.ROOT);
                if ("claro".equals(tone)) {
                    factor = 1.3f;
                } else if ("escuro".equals(tone)) {
                    factor = 0.7f;
                } else if (parts.length >= index + 3) {
                    color = new float[]{
                            AiFreeMapScript.number(parts[index]),
                            AiFreeMapScript.number(parts[index + 1]),
                            AiFreeMapScript.number(parts[index + 2])};
                } else {
                    throw new IllegalArgumentException("rot deve ser "
                            + "0|90|180|270 e tom claro|escuro|r g b");
                }
            }
            toneFactor = factor;
            toneColor = color;
        }

        boolean swapsAxes() {
            return rot == 90 || rot == 270;
        }

        String[] rewrite(String cmd, String[] p) {
            switch (cmd) {
                case "piso": {
                    String[] out = p.clone();
                    point(out, 1, 2);
                    pair(out, 3, 4);
                    tone(out, 6);
                    return out;
                }
                case "teto": {
                    String[] out = p.clone();
                    point(out, 1, 2);
                    pair(out, 3, 4);
                    tone(out, 7);
                    return out;
                }
                case "parede": {
                    String[] out = p.clone();
                    trackWallAxis(p);
                    point(out, 1, 2);
                    point(out, 3, 4);
                    tone(out, 7);
                    return out;
                }
                case "vao": {
                    if (p.length <= 2 || rot == 0) return p;
                    String[] out = p.clone();
                    out[2] = fmt(AiFreeMapScript.number(out[2])
                            * openingFactor());
                    return out;
                }
                case "bloco": {
                    String[] out = p.clone();
                    point(out, 1, 3);
                    pair(out, 4, 6);
                    tone(out, 8);
                    return out;
                }
                case "peca": {
                    String[] out = p;
                    if (out.length > 5) {
                        out = out.clone();
                        out[5] = fmt(AiFreeMapScript.number(out[5]) + rot);
                    } else if (rot != 0 && out.length == 5) {
                        out = Arrays.copyOf(out, 6);
                        out[5] = Integer.toString(rot);
                    } else {
                        out = out.clone();
                    }
                    point(out, 2, 4);
                    return out;
                }
                case "prop": {
                    if (!swapsAxes() || p.length < 2) return p;
                    String[] out = p.clone();
                    if ("halfX".equals(out[1])) out[1] = "halfZ";
                    else if ("halfZ".equals(out[1])) out[1] = "halfX";
                    return out;
                }
                case "patrulha": {
                    String[] out = p.clone();
                    point(out, 1, 2);
                    return out;
                }
                default:
                    return p;
            }
        }

        /** Eixo ORIGINAL da última parede decide o sinal do vão. */
        private void trackWallAxis(String[] p) {
            if (p.length < 5) return;
            float x1 = AiFreeMapScript.number(p[1]);
            float z1 = AiFreeMapScript.number(p[2]);
            float x2 = AiFreeMapScript.number(p[3]);
            float z2 = AiFreeMapScript.number(p[4]);
            lastWallAlongX = Math.abs(z1 - z2) < 0.01f;
            lastWallDiagonal = !lastWallAlongX
                    && Math.abs(x1 - x2) >= 0.01f;
        }

        /** offset do vão é medido no eixo positivo da parede final. */
        private float openingFactor() {
            if (rot == 0 || lastWallDiagonal) return 1f;
            if (rot == 180) return -1f;
            if (rot == 90) return lastWallAlongX ? 1f : -1f;
            return lastWallAlongX ? -1f : 1f;
        }

        private void point(String[] out, int xi, int zi) {
            if (out.length <= Math.max(xi, zi)) return;
            float x = AiFreeMapScript.number(out[xi]);
            float z = AiFreeMapScript.number(out[zi]);
            float rx;
            float rz;
            switch (rot) {
                case 90: rx = -z; rz = x; break;
                case 180: rx = -x; rz = -z; break;
                case 270: rx = z; rz = -x; break;
                default: rx = x; rz = z; break;
            }
            out[xi] = fmt(rx + anchorX);
            out[zi] = fmt(rz + anchorZ);
        }

        private void pair(String[] out, int a, int b) {
            if (!swapsAxes() || out.length <= Math.max(a, b)) return;
            String keep = out[a];
            out[a] = out[b];
            out[b] = keep;
        }

        private void tone(String[] out, int start) {
            if (out.length <= start + 2) return;
            for (int i = 0; i < 3; i++) {
                if (toneColor != null) {
                    out[start + i] = fmt(toneColor[i]);
                } else if (toneFactor > 0f) {
                    out[start + i] = fmt(Math.min(1f, Math.max(0f,
                            AiFreeMapScript.number(out[start + i])
                                    * toneFactor)));
                }
            }
        }

        private static String fmt(float value) {
            return Float.toString(value);
        }
    }
}
