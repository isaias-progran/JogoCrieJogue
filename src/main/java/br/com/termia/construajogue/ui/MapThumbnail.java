package br.com.termia.construajogue.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import br.com.termia.construajogue.editor.StructureRoles;
import br.com.termia.construajogue.map.LogicMarker;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.PrefabInstance;
import br.com.termia.construajogue.map.StructureObject;

/**
 * Miniatura do mapa para a biblioteca: vista de topo enquadrada no
 * conteúdo, com o fundo na cor do céu/neblina do próprio mapa (mapa
 * noturno fica escuro na lista). Pisos embaixo, depois blocos e
 * paredes, teto translúcido, peças como pontos e início/saída.
 */
public final class MapThumbnail {

    private MapThumbnail() {
    }

    public static Bitmap render(MapDocument doc, int width, int height) {
        Bitmap bitmap = Bitmap.createBitmap(width, height,
                Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        float[] fog = doc.fogColor;
        canvas.drawColor(Color.rgb(
                (int) Math.min(255f, 30f + fog[0] * 200f),
                (int) Math.min(255f, 34f + fog[1] * 200f),
                (int) Math.min(255f, 42f + fog[2] * 200f)));

        float minX = Float.MAX_VALUE;
        float maxX = -Float.MAX_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        for (StructureObject s : doc.structures) {
            minX = Math.min(minX, s.transform.x - s.half[0]);
            maxX = Math.max(maxX, s.transform.x + s.half[0]);
            minZ = Math.min(minZ, s.transform.z - s.half[2]);
            maxZ = Math.max(maxZ, s.transform.z + s.half[2]);
        }
        if (minX > maxX) {
            minX = -8f;
            maxX = 8f;
            minZ = -6f;
            maxZ = 6f;
        }
        float margin = 1f;
        float scale = Math.min(width / (maxX - minX + 2f * margin),
                height / (maxZ - minZ + 2f * margin));
        float offX = width / 2f - (minX + maxX) / 2f * scale;
        float offZ = height / 2f - (minZ + maxZ) / 2f * scale;

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        // 0 = pisos, 1 = blocos/paredes, 2 = tetos translúcidos
        for (int pass = 0; pass < 3; pass++) {
            for (StructureObject s : doc.structures) {
                String role = StructureRoles.roleOf(s);
                boolean ceiling =
                        StructureObject.ROLE_CEILING.equals(role);
                int layer = StructureObject.ROLE_FLOOR.equals(role) ? 0
                        : ceiling ? 2 : 1;
                if (layer != pass) {
                    continue;
                }
                fill.setColor(Color.argb(ceiling ? 70 : 255,
                        (int) (s.color[0] * 255f),
                        (int) (s.color[1] * 255f),
                        (int) (s.color[2] * 255f)));
                canvas.drawRect(
                        offX + (s.transform.x - s.half[0]) * scale,
                        offZ + (s.transform.z - s.half[2]) * scale,
                        offX + (s.transform.x + s.half[0]) * scale,
                        offZ + (s.transform.z + s.half[2]) * scale, fill);
            }
        }

        for (PrefabInstance p : doc.prefabs) {
            fill.setColor(dotColor(p.prefabId));
            canvas.drawCircle(offX + p.transform.x * scale,
                    offZ + p.transform.z * scale, 3f, fill);
        }
        for (LogicMarker m : doc.markers) {
            boolean spawn = LogicMarker.PLAYER_SPAWN.equals(m.type);
            fill.setColor(spawn ? 0xFF39B54A : 0xFF2E9BD6);
            canvas.drawCircle(offX + m.x * scale, offZ + m.z * scale,
                    4.5f, fill);
        }
        return bitmap;
    }

    private static int dotColor(String prefabId) {
        if (prefabId.startsWith("enemy.")) {
            return 0xFFE05555;
        }
        if (prefabId.startsWith("pickup.")) {
            return 0xFFD9B23C;
        }
        if (prefabId.startsWith("terminal.")
                || prefabId.startsWith("door.")) {
            return 0xFFE08030;
        }
        return 0xFF8FA9C9;
    }
}
