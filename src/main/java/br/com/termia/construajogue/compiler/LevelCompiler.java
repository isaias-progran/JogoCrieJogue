package br.com.termia.construajogue.compiler;

import br.com.termia.construajogue.engine.Boxes;
import br.com.termia.construajogue.geometry.Triangulator;
import br.com.termia.construajogue.map.LogicMarker;
import br.com.termia.construajogue.map.MapDocument;
import br.com.termia.construajogue.map.PrefabInstance;
import br.com.termia.construajogue.map.StructureObject;
import br.com.termia.construajogue.map.WallOpening;
import br.com.termia.construajogue.prefab.PrefabCatalog;
import br.com.termia.construajogue.prefab.PrefabDefinition;
import br.com.termia.construajogue.prefab.PrefabMeshFactory;
import br.com.termia.construajogue.runtime.LegacyLevelLoader;
import br.com.termia.construajogue.runtime.RuntimeLevel;

import java.util.ArrayList;
import java.util.List;

/**
 * Compila um MapDocument VALIDADO em RuntimeLevel, uma única vez, antes
 * do loop do jogo (nunca no quadro). A ordem das listas do documento é
 * preservada — é isso que permite reproduzir bit a bit a arena legada.
 */
public final class LevelCompiler {

    private LevelCompiler() {
    }

    public static RuntimeLevel compile(MapDocument doc,
                                       PrefabCatalog catalog) {
        // visuais e colisão andam separados: estruturas entram nos dois;
        // móveis/objetos têm malha detalhada e collider simplificado
        List<float[]> visuals = new ArrayList<>();
        List<float[]> colors = new ArrayList<>();
        List<float[]> colors2 = new ArrayList<>();
        List<float[]> colors3 = new ArrayList<>();
        List<float[]> solids = new ArrayList<>();
        List<float[]> polyMeshes = new ArrayList<>();
        int polyFloats = 0;
        for (StructureObject s : doc.structures) {
            if (StructureObject.KIND_POLY.equals(s.kind)) {
                float[] mesh = polyTriangles(s);
                polyMeshes.add(mesh);
                polyFloats += mesh.length;
                polyColliders(s, solids);
                continue;
            }
            if (!StructureObject.KIND_BLOCK.equals(s.kind)) {
                throw new IllegalArgumentException(
                        "estrutura não suportada: " + s.kind);
            }
            float[] bounds = new float[6];
            LegacyLevelLoader.toBounds(s.transform.x, s.transform.y,
                    s.transform.z, s.half[0], s.half[1], s.half[2], bounds);
            float[] stubs = s.color2 == null && s.color3 == null ? null
                    : wallStubPlanes(s, doc.structures);
            if (s.openings.isEmpty()) {
                addPainted(visuals, colors, colors2, colors3, bounds, s,
                        stubs);
                solids.add(bounds);
            } else {
                for (float[] piece : cutOpenings(s, bounds)) {
                    addPainted(visuals, colors, colors2, colors3, piece,
                            s, stubs);
                    solids.add(piece);
                }
            }
        }

        List<float[]> drones = new ArrayList<>();
        List<float[]> waves = new ArrayList<>();
        List<float[]> mutants = new ArrayList<>();
        List<float[]> items = new ArrayList<>();
        float[] terminal = null;
        PrefabInstance door = null;
        for (PrefabInstance p : doc.prefabs) {
            PrefabDefinition def = catalog.find(p.prefabId);
            if (def == null) {
                throw new IllegalArgumentException(
                        "prefab desconhecido: " + p.prefabId);
            }
            float x = p.transform.x;
            float y = p.transform.y;
            float z = p.transform.z;
            switch (def.behavior) {
                case PrefabDefinition.BEHAVIOR_DRONE:
                    drones.add(patrol(p));
                    break;
                case PrefabDefinition.BEHAVIOR_DRONE_DORMANT:
                    waves.add(patrol(p));
                    break;
                case PrefabDefinition.BEHAVIOR_MUTANT:
                    mutants.add(patrol(p));
                    break;
                case PrefabDefinition.BEHAVIOR_PICKUP_HEALTH:
                    items.add(new float[]{RuntimeLevel.ITEM_HEALTH, x, y, z});
                    break;
                case PrefabDefinition.BEHAVIOR_PICKUP_AMMO:
                    items.add(new float[]{RuntimeLevel.ITEM_AMMO, x, y, z});
                    break;
                case PrefabDefinition.BEHAVIOR_TERMINAL:
                    terminal = new float[]{x, y, z};
                    break;
                case PrefabDefinition.BEHAVIOR_DOOR:
                    door = p;
                    break;
                case PrefabDefinition.BEHAVIOR_STATIC: {
                    float[][] parts = PrefabMeshFactory.parts(p.prefabId);
                    float[][] boxes =
                            PrefabMeshFactory.colliders(p.prefabId);
                    if (parts == null || boxes == null) {
                        throw new IllegalArgumentException(
                                "peça estática sem malha: " + p.prefabId);
                    }
                    int quarter = quarterTurns(p.transform.yaw);
                    for (float[] part : parts) {
                        float[] r = rotateBox(part, quarter);
                        float[] bounds = new float[6];
                        LegacyLevelLoader.toBounds(x + r[0], y + r[1],
                                z + r[2], r[3], r[4], r[5], bounds);
                        visuals.add(bounds);
                        colors.add(new float[]{part[6], part[7], part[8]});
                        colors2.add(null);
                        colors3.add(null);
                    }
                    for (float[] c : boxes) {
                        float[] r = rotateBox(c, quarter);
                        float[] bounds = new float[6];
                        LegacyLevelLoader.toBounds(x + r[0], y + r[1],
                                z + r[2], r[3], r[4], r[5], bounds);
                        solids.add(bounds);
                    }
                    break;
                }
                default:
                    throw new IllegalArgumentException("comportamento '"
                            + def.behavior + "' sem suporte no compilador");
            }
        }

        float[] spawn = {0f, 0f, 0f, 0f};
        float[] exit = null;
        for (LogicMarker m : doc.markers) {
            if (LogicMarker.PLAYER_SPAWN.equals(m.type)) {
                spawn = new float[]{m.x, m.y, m.z, m.yaw};
            } else if (LogicMarker.EXIT.equals(m.type)) {
                exit = new float[]{m.x, m.z, m.radius};
            }
        }

        int colliderCount = solids.size() + (door == null ? 0 : 1);
        float[][] colliders = new float[colliderCount][6];
        for (int i = 0; i < solids.size(); i++) {
            colliders[i] = solids.get(i);
        }
        float[] vertexData = new float[visuals.size()
                * Boxes.FLOATS_PER_BOX + polyFloats];
        int cursor = 0;
        for (int i = 0; i < visuals.size(); i++) {
            float[] b = visuals.get(i);
            float[] color = colors.get(i);
            float[] pos = colors2.get(i);
            float[] neg = colors3.get(i);
            if (pos == null && neg == null) {
                cursor = Boxes.emitBounds(vertexData, cursor, b,
                        color[0], color[1], color[2]);
            } else {
                boolean thinX = (b[3] - b[0]) < (b[5] - b[2]);
                cursor = Boxes.emitBoundsPainted(vertexData, cursor, b,
                        thinX, color, pos, neg);
            }
        }

        for (float[] mesh : polyMeshes) {
            System.arraycopy(mesh, 0, vertexData, cursor, mesh.length);
            cursor += mesh.length;
        }

        int doorIndex = -1;
        float[] doorVertexData = null;
        float[] doorOriginal = null;
        if (door != null) {
            doorIndex = solids.size();
            LegacyLevelLoader.toBounds(door.transform.x, door.transform.y,
                    door.transform.z, door.floatProperty("halfX", 0f),
                    door.floatProperty("halfY", 0f),
                    door.floatProperty("halfZ", 0f), colliders[doorIndex]);
            doorOriginal = colliders[doorIndex].clone();
            doorVertexData = new float[Boxes.FLOATS_PER_BOX];
            Boxes.emitBounds(doorVertexData, 0, doorOriginal,
                    LegacyLevelLoader.DOOR_COLOR[0],
                    LegacyLevelLoader.DOOR_COLOR[1],
                    LegacyLevelLoader.DOOR_COLOR[2]);
        }

        RuntimeLevel level = new RuntimeLevel(colliders, vertexData,
                doorVertexData, doorIndex, doorOriginal, terminal, exit,
                items.toArray(new float[0][]), spawn,
                drones.toArray(new float[0][]),
                waves.toArray(new float[0][]),
                mutants.toArray(new float[0][]),
                doc.ambient, doc.fogColor.clone(), doc.fogFar);
        level.setSkyMode(skyModeOf(doc.sky));
        return level;
    }

    public static int skyModeOf(String sky) {
        switch (sky == null ? "" : sky) {
            case "day": return RuntimeLevel.SKY_DAY;
            case "dusk": return RuntimeLevel.SKY_DUSK;
            case "night": return RuntimeLevel.SKY_NIGHT;
            default: return RuntimeLevel.SKY_NONE;
        }
    }

    /**
     * Recorta os vãos de uma parede em caixas: trechos cheios entre os
     * vãos, verga acima de cada vão e peitoril abaixo (janela). O vão em
     * si fica sem caixa — passagem e visão ficam livres de verdade.
     */
    public static List<float[]> cutOpenings(StructureObject s, float[] b) {
        boolean alongX = (b[3] - b[0]) >= (b[5] - b[2]);
        int lo = alongX ? 0 : 2;
        int hi = alongX ? 3 : 5;
        float base = b[1];
        float top = b[4];

        List<WallOpening> sorted = new ArrayList<>(s.openings);
        sorted.sort((a, c) -> Float.compare(a.offset, c.offset));
        float center = (b[lo] + b[hi]) / 2f;

        List<float[]> pieces = new ArrayList<>();
        float cursor = b[lo];
        for (WallOpening o : sorted) {
            float cutLo = Math.max(b[lo], center + o.offset - o.width / 2f);
            float cutHi = Math.min(b[hi], center + o.offset + o.width / 2f);
            if (cutHi <= cutLo) {
                continue;
            }
            if (cutLo > cursor) {
                pieces.add(piece(b, lo, hi, cursor, cutLo, base, top));
            }
            float sillTop = base + o.sill;
            float openTop = Math.min(top, sillTop + o.height);
            if (o.sill > 0f) {
                pieces.add(piece(b, lo, hi, cutLo, cutHi, base, sillTop));
            }
            if (openTop < top) {
                pieces.add(piece(b, lo, hi, cutLo, cutHi, openTop, top));
            }
            cursor = Math.max(cursor, cutHi);
        }
        if (cursor < b[hi]) {
            pieces.add(piece(b, lo, hi, cursor, b[hi], base, top));
        }
        return pieces;
    }

    private static float[] piece(float[] b, int lo, int hi,
                                 float from, float to,
                                 float bottom, float topY) {
        float[] out = b.clone();
        out[lo] = from;
        out[hi] = to;
        out[1] = bottom;
        out[4] = topY;
        return out;
    }

    /**
     * Planos de canto de uma parede pintada por lado: onde outra parede
     * perpendicular encosta numa ponta, a pintura do lado deve PARAR na
     * face interna dela — senão a cor interna vaza pela ponta e vira
     * uma faixa vista de fora. Devolve {fimDoCantoBaixo, inícioDoCanto
     * Alto} ao longo do eixo comprido (NaN = ponta sem canto).
     */
    public static float[] wallStubPlanes(StructureObject s,
                                         List<StructureObject> all) {
        boolean thinX = s.half[0] < s.half[2];
        float alongC = thinX ? s.transform.z : s.transform.x;
        float alongH = thinX ? s.half[2] : s.half[0];
        float crossC = thinX ? s.transform.x : s.transform.z;
        float crossH = thinX ? s.half[0] : s.half[2];
        float loEnd = Float.NaN;
        float hiStart = Float.NaN;
        for (StructureObject t : all) {
            if (t == s || t.half == null
                    || !StructureObject.KIND_BLOCK.equals(t.kind)) {
                continue;
            }
            // no eixo comprido de s, a outra parede é fina; no fino, longa
            float tAlongC = thinX ? t.transform.z : t.transform.x;
            float tAlongH = thinX ? t.half[2] : t.half[0];
            float tCrossC = thinX ? t.transform.x : t.transform.z;
            float tCrossH = thinX ? t.half[0] : t.half[2];
            if (tAlongH >= alongH || tCrossH <= tAlongH) {
                continue; // não é perpendicular fina cruzando s
            }
            if (Math.abs(tCrossC - crossC) > tCrossH + crossH - 0.01f) {
                continue; // não alcança a faixa da parede
            }
            float sY0 = s.transform.y - s.half[1];
            float sY1 = s.transform.y + s.half[1];
            float tY0 = t.transform.y - t.half[1];
            float tY1 = t.transform.y + t.half[1];
            if (tY1 <= sY0 + 0.01f || tY0 >= sY1 - 0.01f) {
                continue; // alturas não se cruzam
            }
            float tLo = tAlongC - tAlongH;
            float tHi = tAlongC + tAlongH;
            float sLo = alongC - alongH;
            float sHi = alongC + alongH;
            if (tHi <= sLo || tLo >= sHi) {
                continue;
            }
            // só vale perto de uma ponta (canto), não junção em T no meio
            if (tAlongC >= alongC && tHi >= sHi - 0.6f) {
                hiStart = Float.isNaN(hiStart) ? tLo
                        : Math.min(hiStart, tLo);
            } else if (tAlongC < alongC && tLo <= sLo + 0.6f) {
                loEnd = Float.isNaN(loEnd) ? tHi : Math.max(loEnd, tHi);
            }
        }
        return new float[]{loEnd, hiStart};
    }

    /** Ponta pintada recua 1 cm no canto: evita briga de plano (listra). */
    private static final float CORNER_INSET = 0.01f;

    /**
     * Emite a caixa da parede pintada por face, cortando a pintura nos
     * planos de canto: o trecho de canto sai só na cor base e a ponta
     * recua 1 cm para dentro da outra parede (a tampa ficava no MESMO
     * plano da face dela — dava listra).
     */
    private static void addPainted(List<float[]> visuals,
                                   List<float[]> colors,
                                   List<float[]> colors2,
                                   List<float[]> colors3, float[] b,
                                   StructureObject s, float[] stubs) {
        if (s.color2 == null && s.color3 == null) {
            visuals.add(b);
            colors.add(s.color);
            colors2.add(null);
            colors3.add(null);
            return;
        }
        boolean thinX = s.half[0] < s.half[2];
        int lo = thinX ? 2 : 0;
        int hi = lo + 3;
        float bodyLo = b[lo];
        float bodyHi = b[hi];
        if (stubs != null && !Float.isNaN(stubs[0]) && stubs[0] > bodyLo) {
            float end = Math.min(stubs[0], bodyHi);
            visuals.add(slice(b, lo, hi, bodyLo + CORNER_INSET, end));
            colors.add(s.color);
            colors2.add(null);
            colors3.add(null);
            bodyLo = end;
        }
        if (stubs != null && !Float.isNaN(stubs[1]) && stubs[1] < bodyHi) {
            float start = Math.max(stubs[1], bodyLo);
            visuals.add(slice(b, lo, hi, start, bodyHi - CORNER_INSET));
            colors.add(s.color);
            colors2.add(null);
            colors3.add(null);
            bodyHi = start;
        }
        if (bodyHi > bodyLo) {
            visuals.add(slice(b, lo, hi, bodyLo, bodyHi));
            colors.add(s.color);
            colors2.add(s.color2);
            colors3.add(s.color3);
        }
    }

    private static float[] slice(float[] b, int lo, int hi,
                                 float from, float to) {
        float[] out = b.clone();
        out[lo] = from;
        out[hi] = to;
        return out;
    }

    /**
     * Malha da laje poligonal: tampo e fundo triangulados (corte de
     * orelhas) + lados verticais. Winding igual ao das caixas (CCW
     * visto de fora) para conviver com o back-face culling.
     */
    static float[] polyTriangles(StructureObject s) {
        float[] ccw = Triangulator.ccw(s.polygon);
        int[] tris = Triangulator.earClip(ccw);
        int n = ccw.length / 2;
        float y0 = s.transform.y - s.half[1];
        float y1 = s.transform.y + s.half[1];
        float r = s.color[0];
        float g = s.color[1];
        float b = s.color[2];
        float[] out = new float[(tris.length * 2 + n * 6) * 9];
        int o = 0;
        // tampo (+Y): ordem invertida do CCW matemático p/ o culling
        for (int t = 0; t < tris.length; t += 3) {
            o = vertex(out, o, ccw, tris[t], y1, 0f, 1f, 0f, r, g, b);
            o = vertex(out, o, ccw, tris[t + 2], y1, 0f, 1f, 0f, r, g, b);
            o = vertex(out, o, ccw, tris[t + 1], y1, 0f, 1f, 0f, r, g, b);
        }
        // fundo (-Y)
        for (int t = 0; t < tris.length; t += 3) {
            o = vertex(out, o, ccw, tris[t], y0, 0f, -1f, 0f, r, g, b);
            o = vertex(out, o, ccw, tris[t + 1], y0, 0f, -1f, 0f, r, g, b);
            o = vertex(out, o, ccw, tris[t + 2], y0, 0f, -1f, 0f, r, g, b);
        }
        // lados: percorre o contorno em ordem horária (normais p/ fora)
        for (int i = 0; i < n; i++) {
            int a = (n - 1 - i);
            int c = (n - i) % n;
            float ax = ccw[a * 2];
            float az = ccw[a * 2 + 1];
            float bx = ccw[c * 2];
            float bz = ccw[c * 2 + 1];
            float dx = bx - ax;
            float dz = bz - az;
            float len = (float) Math.hypot(dx, dz);
            if (len < 1e-5f) {
                len = 1f;
            }
            float nx = -dz / len;
            float nz = dx / len;
            float[][] quad = {{ax, y0, az}, {bx, y0, bz},
                    {bx, y1, bz}, {ax, y1, az}};
            int[] order = {0, 1, 2, 0, 2, 3};
            for (int q : order) {
                out[o++] = quad[q][0];
                out[o++] = quad[q][1];
                out[o++] = quad[q][2];
                out[o++] = nx;
                out[o++] = 0f;
                out[o++] = nz;
                out[o++] = r;
                out[o++] = g;
                out[o++] = b;
            }
        }
        float[] trimmed = new float[o];
        System.arraycopy(out, 0, trimmed, 0, o);
        return trimmed;
    }

    private static int vertex(float[] out, int o, float[] p, int i,
                              float y, float nx, float ny, float nz,
                              float r, float g, float b) {
        out[o++] = p[i * 2];
        out[o++] = y;
        out[o++] = p[i * 2 + 1];
        out[o++] = nx;
        out[o++] = ny;
        out[o++] = nz;
        out[o++] = r;
        out[o++] = g;
        out[o++] = b;
        return o;
    }

    /**
     * Colisão da laje poligonal: faixas horizontais de 0,5 m
     * rasterizadas dentro do contorno (andável; borda diagonal fica
     * serrilhada só na física, invisível).
     */
    static void polyColliders(StructureObject s, List<float[]> solids) {
        float[] p = s.polygon;
        int n = p.length / 2;
        float minZ = Float.MAX_VALUE;
        float maxZ = -Float.MAX_VALUE;
        for (int i = 0; i < n; i++) {
            minZ = Math.min(minZ, p[i * 2 + 1]);
            maxZ = Math.max(maxZ, p[i * 2 + 1]);
        }
        float y0 = s.transform.y - s.half[1];
        float y1 = s.transform.y + s.half[1];
        float step = 0.5f;
        List<Float> hits = new ArrayList<>();
        for (float z = minZ; z < maxZ; z += step) {
            float zHi = Math.min(z + step, maxZ);
            float mid = (z + zHi) / 2f;
            hits.clear();
            for (int i = 0; i < n; i++) {
                int j = (i + 1) % n;
                float z1 = p[i * 2 + 1];
                float z2 = p[j * 2 + 1];
                if ((z1 <= mid) == (z2 <= mid)) {
                    continue;
                }
                hits.add(p[i * 2] + (mid - z1)
                        * (p[j * 2] - p[i * 2]) / (z2 - z1));
            }
            hits.sort(Float::compare);
            for (int i = 0; i + 1 < hits.size(); i += 2) {
                float xa = hits.get(i);
                float xb = hits.get(i + 1);
                if (xb - xa > 0.05f) {
                    solids.add(new float[]{xa, y0, z, xb, y1, zHi});
                }
            }
        }
    }

    /** Yaw arredondado para múltiplos de 90° (AABBs só giram assim). */
    public static int quarterTurns(float yaw) {
        int quarter = Math.round(yaw / 90f) % 4;
        return quarter < 0 ? quarter + 4 : quarter;
    }

    /** Gira o centro e troca as meias-dimensões conforme o quarto. */
    private static float[] rotateBox(float[] box, int quarter) {
        if (quarter == 0) {
            return box;
        }
        float cx = box[0];
        float cz = box[2];
        float hx = box[3];
        float hz = box[5];
        float rx;
        float rz;
        switch (quarter) {
            case 1: rx = -cz; rz = cx; break;
            case 2: rx = -cx; rz = -cz; break;
            default: rx = cz; rz = -cx; break;
        }
        boolean swap = (quarter & 1) == 1;
        return new float[]{rx, box[1], rz,
                swap ? hz : hx, box[4], swap ? hx : hz};
    }

    /** {x, y, z, x2, z2}: segundo ponto de patrulha (padrão: parado). */
    private static float[] patrol(PrefabInstance p) {
        return new float[]{p.transform.x, p.transform.y, p.transform.z,
                p.floatProperty("patrolX", p.transform.x),
                p.floatProperty("patrolZ", p.transform.z)};
    }
}
