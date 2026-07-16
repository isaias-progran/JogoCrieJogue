package br.com.termia.construajogue.editor;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import br.com.termia.construajogue.compiler.LevelCompiler;
import br.com.termia.construajogue.map.LogicMarker;
import br.com.termia.construajogue.map.PrefabInstance;
import br.com.termia.construajogue.map.StructureObject;
import br.com.termia.construajogue.map.WallGeometry;
import br.com.termia.construajogue.map.WallOpening;
import br.com.termia.construajogue.prefab.PrefabMeshFactory;

/**
 * Desenho da planta 2D. Lê o estado do PlanEditorView (mesmo pacote) e
 * pinta o Canvas; nunca muta documento nem seleção — toda a lógica de
 * edição continua na view.
 */
final class PlanRenderer {

    private final PlanEditorView v;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint();
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint measurePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint routePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    PlanRenderer(PlanEditorView view) {
        v = view;
        stroke.setStyle(Paint.Style.STROKE);
        textPaint.setColor(0xFFAFC3D0);
        textPaint.setTextSize(28f);
        measurePaint.setColor(0xFFF2E3A0);
        measurePaint.setTextSize(26f);
        measurePaint.setFakeBoldText(true);
        measurePaint.setShadowLayer(4f, 0f, 0f, 0xFF000000);
        routePaint.setStyle(Paint.Style.STROKE);
        routePaint.setStrokeWidth(3f);
        routePaint.setPathEffect(
                new android.graphics.DashPathEffect(
                        new float[]{14f, 10f}, 0f));
    }

    void draw(Canvas canvas) {
        if (v.doc == null) {
            return;
        }
        drawGrid(canvas);
        for (StructureObject s : v.doc.structures) {
            if (v.visible(s) && !StructureRoles.isCeiling(s)) {
                drawStructure(canvas, s, s == v.selectedStructure
                        || v.group.contains(s), false);
            }
        }
        for (StructureObject s : v.doc.structures) {
            if (v.visible(s)) drawOpenings(canvas, s);
        }
        // tetos por cima de tudo, translúcidos
        for (StructureObject s : v.doc.structures) {
            if (v.visible(s) && StructureRoles.isCeiling(s)) {
                drawStructure(canvas, s, s == v.selectedStructure
                        || v.group.contains(s), true);
            }
        }
        if (v.tool == PlanEditorView.TOOL_WALL) {
            drawWallAnchors(canvas);
        }
        if (v.dragging && v.tool != PlanEditorView.TOOL_SELECT) {
            drawPreview(canvas);
        }
        if (v.tool == PlanEditorView.TOOL_SELECT && v.selectedStructure != null
                && v.selectedStructure.polygon == null
                && !v.selectedStructure.locked) {
            drawEdgeHandles(canvas, v.selectedStructure);
        }
        drawContour(canvas);
        for (PrefabInstance p : v.doc.prefabs) {
            if (v.visible(p)) drawRoute(canvas, p, p == v.selectedPrefab);
        }
        for (PrefabInstance p : v.doc.prefabs) {
            if (v.visible(p)) {
                drawPrefab(canvas, p,
                        p == v.selectedPrefab || v.group.contains(p));
            }
        }
        for (LogicMarker m : v.doc.markers) {
            if (v.visible(m)) {
                drawMarker(canvas, m,
                        m == v.selectedMarker || v.group.contains(m));
            }
        }
        drawGroupSelection(canvas);
        drawMeasureLabels(canvas);
    }

    /** Moldura da área sendo escolhida e do grupo já selecionado. */
    private void drawGroupSelection(Canvas canvas) {
        if (v.tool != PlanEditorView.TOOL_GROUP) return;
        float[] b;
        if (v.dragging && !v.groupMoving && v.group.isEmpty()) {
            b = new float[]{Math.min(v.startX, v.curX), Math.min(v.startZ, v.curZ),
                    Math.max(v.startX, v.curX), Math.max(v.startZ, v.curZ)};
        } else {
            b = v.group.bounds();
        }
        if (b == null) return;
        fill.setColor(0x222FA8E0);
        canvas.drawRect(v.toPxX(b[0]), v.toPxY(b[1]),
                v.toPxX(b[2]), v.toPxY(b[3]), fill);
        stroke.setColor(v.group.anyLocked() ? 0xFFFFB050 : 0xFF4CC8FF);
        stroke.setStrokeWidth(4f);
        stroke.setPathEffect(new android.graphics.DashPathEffect(
                new float[]{14f, 8f}, 0f));
        canvas.drawRect(v.toPxX(b[0]), v.toPxY(b[1]),
                v.toPxX(b[2]), v.toPxY(b[3]), stroke);
        stroke.setPathEffect(null);
    }

    /** Contorno em andamento: linhas, pontos e o primeiro em destaque. */
    private void drawContour(Canvas canvas) {
        if (v.tool != PlanEditorView.TOOL_POINTS || v.contour.isEmpty()) {
            return;
        }
        stroke.setColor(0xFFE0C060);
        stroke.setStrokeWidth(3f);
        for (int i = 0; i + 1 < v.contour.size(); i++) {
            float[] a = v.contour.get(i);
            float[] b = v.contour.get(i + 1);
            canvas.drawLine(v.toPxX(a[0]), v.toPxY(a[1]),
                    v.toPxX(b[0]), v.toPxY(b[1]), stroke);
        }
        for (int i = 0; i < v.contour.size(); i++) {
            float[] p = v.contour.get(i);
            fill.setColor(i == 0 ? 0xFF39B54A : 0xFFE0C060);
            canvas.drawCircle(v.toPxX(p[0]), v.toPxY(p[1]),
                    i == 0 ? 13f : 9f, fill);
        }
        // cota do próximo trecho
        if (!v.contour.isEmpty()) {
            float[] last = v.contour.get(v.contour.size() - 1);
            canvas.drawText(meters((float) Math.hypot(v.curX - last[0],
                            v.curZ - last[1])),
                    v.toPxX((last[0] + v.curX) / 2f) + 10f,
                    v.toPxY((last[1] + v.curZ) / 2f) - 10f, measurePaint);
        }
    }

    /** Alças no meio das arestas: puxe para esticar até alinhar. */
    private void drawEdgeHandles(Canvas canvas, StructureObject s) {
        float cx = v.toPxX(s.transform.x);
        float cy = v.toPxY(s.transform.z);
        float[][] handles = {
                {v.toPxX(s.transform.x - s.half[0]), cy},
                {v.toPxX(s.transform.x + s.half[0]), cy},
                {cx, v.toPxY(s.transform.z - s.half[2])},
                {cx, v.toPxY(s.transform.z + s.half[2])}};
        for (float[] handle : handles) {
            fill.setColor(0xFFFFFFFF);
            canvas.drawRect(handle[0] - 9f, handle[1] - 9f,
                    handle[0] + 9f, handle[1] + 9f, fill);
            stroke.setColor(0xFF2E5A8A);
            stroke.setStrokeWidth(2f);
            canvas.drawRect(handle[0] - 9f, handle[1] - 9f,
                    handle[0] + 9f, handle[1] + 9f, stroke);
        }
    }

    /** Rota de patrulha: linha tracejada até um anel no destino. */
    private void drawRoute(Canvas canvas, PrefabInstance p,
                           boolean selected) {
        if (!p.prefabId.startsWith("enemy.")
                || !p.properties.containsKey("patrolX")) {
            return;
        }
        float tx = p.floatProperty("patrolX", p.transform.x);
        float tz = p.floatProperty("patrolZ", p.transform.z);
        routePaint.setColor(selected ? 0xFFFFFFFF : 0x99E05555);
        canvas.drawLine(v.toPxX(p.transform.x), v.toPxY(p.transform.z),
                v.toPxX(tx), v.toPxY(tz), routePaint);
        canvas.drawCircle(v.toPxX(tx), v.toPxY(tz), 10f, routePaint);
    }

    /** Vãos sobre a parede: porta marrom, portal escuro, janela azul. */
    private void drawOpenings(Canvas canvas, StructureObject s) {
        if (s.openings.isEmpty()) {
            return;
        }
        if (WallGeometry.diagonal(s)) {
            float[] direction = WallGeometry.direction(s);
            float thickness = Math.max(5f,
                    WallGeometry.thickness(s) * v.scale);
            for (WallOpening o : s.openings) {
                float[] center = WallGeometry.pointAt(s, o.offset);
                float hx = direction[0] * o.width * 0.5f;
                float hz = direction[1] * o.width * 0.5f;
                if (o == v.selectedOpening) {
                    stroke.setColor(0xFFFFFFFF);
                    stroke.setStrokeWidth(thickness + 5f);
                    canvas.drawLine(v.toPxX(center[0] - hx),
                            v.toPxY(center[1] - hz),
                            v.toPxX(center[0] + hx),
                            v.toPxY(center[1] + hz), stroke);
                }
                stroke.setColor(WallOpening.WINDOW.equals(o.type)
                        ? 0xFF4A90C2 : WallOpening.DOOR.equals(o.type)
                        ? 0xFF8A6238 : 0xFF10151B);
                stroke.setStrokeWidth(thickness);
                canvas.drawLine(v.toPxX(center[0] - hx),
                        v.toPxY(center[1] - hz),
                        v.toPxX(center[0] + hx),
                        v.toPxY(center[1] + hz), stroke);
            }
            return;
        }
        boolean alongX = s.half[0] >= s.half[2];
        for (WallOpening o : s.openings) {
            float cx = alongX ? s.transform.x + o.offset : s.transform.x;
            float cz = alongX ? s.transform.z : s.transform.z + o.offset;
            float hx = alongX ? o.width / 2f : s.half[0];
            float hz = alongX ? s.half[2] : o.width / 2f;
            fill.setColor(WallOpening.WINDOW.equals(o.type) ? 0xFF4A90C2
                    : WallOpening.DOOR.equals(o.type) ? 0xFF8A6238
                    : 0xFF10151B);
            canvas.drawRect(v.toPxX(cx - hx), v.toPxY(cz - hz),
                    v.toPxX(cx + hx), v.toPxY(cz + hz), fill);
            if (o == v.selectedOpening) {
                stroke.setColor(0xFFFFFFFF);
                stroke.setStrokeWidth(3f);
                canvas.drawRect(v.toPxX(cx - hx), v.toPxY(cz - hz),
                        v.toPxX(cx + hx), v.toPxY(cz + hz), stroke);
            }
        }
    }

    /**
     * Seta de FRENTE do móvel: a frente (porta do armário, torneira,
     * assento) aponta para -Z em yaw 0 e gira junto com a peça — assim
     * dá para ver qual lado deve encostar na parede.
     */
    private void drawFrontArrow(Canvas canvas, PrefabInstance p,
                                int quarter, float hx, float hz) {
        float dx;
        float dz;
        switch (quarter) {
            case 1: dx = 1f; dz = 0f; break;
            case 2: dx = 0f; dz = 1f; break;
            case 3: dx = -1f; dz = 0f; break;
            default: dx = 0f; dz = -1f; break;
        }
        float edge = dx != 0f ? hx : hz;
        float bx = v.toPxX(p.transform.x + dx * edge);
        float by = v.toPxY(p.transform.z + dz * edge);
        float tx = v.toPxX(p.transform.x + dx * (edge + 10f / v.scale));
        float ty = v.toPxY(p.transform.z + dz * (edge + 10f / v.scale));
        stroke.setColor(0xFFF2E3A0);
        stroke.setStrokeWidth(3f);
        canvas.drawLine(bx, by, tx, ty, stroke);
        // cabeça da seta perpendicular à direção
        float pxOff = dz != 0f ? 6f : 0f;
        float pyOff = dx != 0f ? 6f : 0f;
        canvas.drawLine(tx, ty, bx + pxOff + (tx - bx) / 2f,
                by + pyOff + (ty - by) / 2f, stroke);
        canvas.drawLine(tx, ty, bx - pxOff + (tx - bx) / 2f,
                by - pyOff + (ty - by) / 2f, stroke);
    }

    /** Ícone da peça: bolinha colorida com letra (porta vira barra). */
    private void drawPrefab(Canvas canvas, PrefabInstance p,
                            boolean selected) {
        float px = v.toPxX(p.transform.x);
        float py = v.toPxY(p.transform.z);
        String letter;
        int color;
        float[] footprint = PrefabMeshFactory.footprint(p.prefabId);
        if (footprint != null) {
            // móvel/objeto: pegada real no plano, girada com a peça
            int quarter = LevelCompiler.quarterTurns(p.transform.yaw);
            boolean turned = (quarter & 1) == 1;
            float hx = turned ? footprint[1] : footprint[0];
            float hz = turned ? footprint[0] : footprint[1];
            fill.setColor(0x8858728A);
            canvas.drawRect(v.toPxX(p.transform.x - hx),
                    v.toPxY(p.transform.z - hz),
                    v.toPxX(p.transform.x + hx),
                    v.toPxY(p.transform.z + hz), fill);
            stroke.setColor(p.prefabId.startsWith("prop.lamp")
                    ? 0xFFF2E3A0 : 0xFF8FA9C9);
            stroke.setStrokeWidth(2f);
            canvas.drawRect(v.toPxX(p.transform.x - hx),
                    v.toPxY(p.transform.z - hz),
                    v.toPxX(p.transform.x + hx),
                    v.toPxY(p.transform.z + hz), stroke);
            drawFrontArrow(canvas, p, quarter, hx, hz);
            if (selected) {
                stroke.setColor(0xFFFFFFFF);
                stroke.setStrokeWidth(3f);
                canvas.drawCircle(px, py, 18f, stroke);
            }
            return;
        }
        if (p.prefabId.startsWith("npc.human")) {
            letter = "H";
            color = 0xFF4FB5D8;
        } else if (p.prefabId.startsWith("enemy.mutant")) {
            letter = "M";
            color = 0xFFB05CC9;
        } else if (p.prefabId.startsWith("enemy.turret")) {
            letter = "R";
            color = 0xFFB04A42;
        } else if (p.prefabId.startsWith("enemy.kamikaze")) {
            letter = "K";
            color = 0xFFFF7A30;
        } else if (p.prefabId.startsWith("enemy.boss")) {
            letter = "C";
            color = 0xFFC054D8;
        } else if (p.prefabId.startsWith("enemy.drone.wave")) {
            letter = "Z";
            color = 0xFF8A8F9C;
        } else if (p.prefabId.startsWith("enemy.")) {
            letter = "D";
            color = 0xFFE05555;
        } else if (p.prefabId.startsWith("pickup.health")) {
            letter = "+";
            color = 0xFF39B54A;
        } else if (p.prefabId.startsWith("pickup.token")) {
            letter = "F";
            color = 0xFF42BFE8;
        } else if (p.prefabId.startsWith("pickup.special")) {
            letter = "S";
            color = 0xFFFFA030;
        } else if (p.prefabId.startsWith("pickup.")) {
            letter = "A";
            color = 0xFFD9B23C;
        } else if (p.prefabId.startsWith("terminal.")) {
            letter = "T";
            color = 0xFFE08030;
        } else if (p.prefabId.startsWith("door.")) {
            letter = "";
            color = 0xFF7580A0;
            float hx = Math.max(0.5f, p.floatProperty("halfX", 1.5f));
            float hz = Math.max(0.2f, p.floatProperty("halfZ", 0.4f));
            fill.setColor(color);
            canvas.drawRect(v.toPxX(p.transform.x - hx),
                    v.toPxY(p.transform.z - hz),
                    v.toPxX(p.transform.x + hx),
                    v.toPxY(p.transform.z + hz), fill);
        } else {
            letter = "?";
            color = 0xFF888888;
        }
        if (!letter.isEmpty()) {
            fill.setColor(color);
            canvas.drawCircle(px, py, 13f, fill);
            textPaint.setColor(0xFF10151B);
            canvas.drawText(letter, px - 8f, py + 9f, textPaint);
            textPaint.setColor(0xFFAFC3D0);
        }
        if (selected) {
            stroke.setColor(0xFFFFFFFF);
            stroke.setStrokeWidth(3f);
            canvas.drawCircle(px, py, 18f, stroke);
        }
    }

    /** Pontas de parede onde o toque gruda com a ferramenta PAREDE. */
    private void drawWallAnchors(Canvas canvas) {
        stroke.setColor(0xFFE0C060);
        stroke.setStrokeWidth(2.5f);
        for (StructureObject s : v.doc.structures) {
            if (!v.visible(s) || !StructureObject.ROLE_WALL
                    .equals(StructureRoles.roleOf(s))) {
                continue;
            }
            boolean horizontal = s.half[0] >= s.half[2];
            float len = (horizontal ? s.half[0] : s.half[2]) - PlanEditorView.WALL_HALF_T;
            for (int side = -1; side <= 1; side += 2) {
                float ex = horizontal
                        ? s.transform.x + side * len : s.transform.x;
                float ez = horizontal
                        ? s.transform.z : s.transform.z + side * len;
                canvas.drawCircle(v.toPxX(ex), v.toPxY(ez), 9f, stroke);
            }
        }
    }

    private void drawGrid(Canvas canvas) {
        float step = v.scale < 14f ? 4f : 1f;
        gridPaint.setColor(0xFF1D2630);
        float x0 = Math.max(-PlanEditorView.AREA, v.toWorldX(0));
        float x1 = Math.min(PlanEditorView.AREA, v.toWorldX(v.getWidth()));
        float z0 = Math.max(-PlanEditorView.AREA, v.toWorldZ(0));
        float z1 = Math.min(PlanEditorView.AREA, v.toWorldZ(v.getHeight()));
        for (float x = (float) Math.ceil(x0 / step) * step; x <= x1;
                x += step) {
            canvas.drawLine(v.toPxX(x), v.toPxY(z0), v.toPxX(x), v.toPxY(z1),
                    gridPaint);
        }
        for (float z = (float) Math.ceil(z0 / step) * step; z <= z1;
                z += step) {
            canvas.drawLine(v.toPxX(x0), v.toPxY(z), v.toPxX(x1), v.toPxY(z),
                    gridPaint);
        }
        gridPaint.setColor(0xFF2C3A48);
        canvas.drawLine(v.toPxX(0), v.toPxY(z0), v.toPxX(0), v.toPxY(z1), gridPaint);
        canvas.drawLine(v.toPxX(x0), v.toPxY(0), v.toPxX(x1), v.toPxY(0), gridPaint);
        // borda da área útil
        stroke.setColor(0xFF33475A);
        stroke.setStrokeWidth(2f);
        canvas.drawRect(v.toPxX(-PlanEditorView.AREA), v.toPxY(-PlanEditorView.AREA), v.toPxX(PlanEditorView.AREA),
                v.toPxY(PlanEditorView.AREA), stroke);
        textPaint.setColor(0xFFE0C060);
        float density = v.getResources().getDisplayMetrics().density;
        canvas.drawText("ANDAR ATIVO  ·  Y "
                        + String.format("%.2f m", v.activeBaseY)
                        .replace('.', ','),
                20f, 122f * density, textPaint);
        textPaint.setColor(0xFFAFC3D0);
    }

    private void drawStructure(Canvas canvas, StructureObject s,
                               boolean selected, boolean translucent) {
        if (s.polygon != null) {
            android.graphics.Path path = new android.graphics.Path();
            float[] p = s.polygon;
            path.moveTo(v.toPxX(p[0]), v.toPxY(p[1]));
            for (int i = 1; i < p.length / 2; i++) {
                path.lineTo(v.toPxX(p[i * 2]), v.toPxY(p[i * 2 + 1]));
            }
            path.close();
            fill.setColor(Color.argb(translucent ? 70 : 255,
                    (int) (s.color[0] * 255f), (int) (s.color[1] * 255f),
                    (int) (s.color[2] * 255f)));
            canvas.drawPath(path, fill);
            stroke.setColor(selected ? 0xFFFFFFFF
                    : translucent ? 0x998FA9C9 : 0x66000000);
            stroke.setStrokeWidth(selected ? 4f : 1.5f);
            canvas.drawPath(path, stroke);
            if (selected && v.tool == PlanEditorView.TOOL_SELECT) {
                // alças nos vértices: puxe para remodelar
                for (int i = 0; i < p.length / 2; i++) {
                    fill.setColor(0xFFFFFFFF);
                    canvas.drawCircle(v.toPxX(p[i * 2]),
                            v.toPxY(p[i * 2 + 1]), 9f, fill);
                }
            }
            return;
        }
        float l = v.toPxX(s.transform.x - s.half[0]);
        float t = v.toPxY(s.transform.z - s.half[2]);
        float r = v.toPxX(s.transform.x + s.half[0]);
        float b = v.toPxY(s.transform.z + s.half[2]);
        fill.setColor(Color.argb(translucent ? 70 : 255,
                (int) (s.color[0] * 255f), (int) (s.color[1] * 255f),
                (int) (s.color[2] * 255f)));
        canvas.drawRect(l, t, r, b, fill);
        if (s.color2 != null || s.color3 != null) {
            // cada metade do eixo fino mostra a cor da sua face
            boolean thinX = s.half[0] < s.half[2];
            if (s.color2 != null) {
                fill.setColor(Color.argb(translucent ? 70 : 255,
                        (int) (s.color2[0] * 255f),
                        (int) (s.color2[1] * 255f),
                        (int) (s.color2[2] * 255f)));
                if (thinX) {
                    canvas.drawRect(v.toPxX(s.transform.x), t, r, b, fill);
                } else {
                    canvas.drawRect(l, v.toPxY(s.transform.z), r, b, fill);
                }
            }
            if (s.color3 != null) {
                fill.setColor(Color.argb(translucent ? 70 : 255,
                        (int) (s.color3[0] * 255f),
                        (int) (s.color3[1] * 255f),
                        (int) (s.color3[2] * 255f)));
                if (thinX) {
                    canvas.drawRect(l, t, v.toPxX(s.transform.x), b, fill);
                } else {
                    canvas.drawRect(l, t, r, v.toPxY(s.transform.z), fill);
                }
            }
        }
        stroke.setColor(selected ? 0xFFFFFFFF
                : translucent ? 0x998FA9C9 : 0x66000000);
        stroke.setStrokeWidth(selected ? 4f : 1.5f);
        canvas.drawRect(l, t, r, b, stroke);
    }

    private void drawMarker(Canvas canvas, LogicMarker m, boolean selected) {
        boolean spawn = LogicMarker.PLAYER_SPAWN.equals(m.type);
        float px = v.toPxX(m.x);
        float py = v.toPxY(m.z);
        fill.setColor(spawn ? 0xFF39B54A : 0xFF2E9BD6);
        canvas.drawCircle(px, py, 14f, fill);
        if (!spawn && m.radius > 0f) {
            stroke.setColor(0x882E9BD6);
            stroke.setStrokeWidth(2f);
            canvas.drawCircle(px, py, m.radius * v.scale, stroke);
        }
        if (selected) {
            stroke.setColor(0xFFFFFFFF);
            stroke.setStrokeWidth(3f);
            canvas.drawCircle(px, py, 18f, stroke);
        }
        canvas.drawText(spawn ? "INÍCIO" : "SAÍDA", px + 18f, py - 12f,
                textPaint);
    }

    private void drawPreview(Canvas canvas) {
        stroke.setColor(0xFFE0C060);
        stroke.setStrokeWidth(3f);
        if (v.tool == PlanEditorView.TOOL_SPAWN || v.tool == PlanEditorView.TOOL_EXIT
                || v.tool == PlanEditorView.TOOL_PREFAB || v.tool == PlanEditorView.TOOL_OPENING
                || v.tool == PlanEditorView.TOOL_POINTS) {
            canvas.drawCircle(v.toPxX(v.curX), v.toPxY(v.curZ), 14f, stroke);
            return;
        }
        if (v.tool == PlanEditorView.TOOL_WALL) {
            boolean horizontal = Math.abs(v.curX - v.startX)
                    >= Math.abs(v.curZ - v.startZ);
            float ex = horizontal ? v.curX : v.startX;
            float ez = horizontal ? v.startZ : v.curZ;
            canvas.drawLine(v.toPxX(v.startX), v.toPxY(v.startZ), v.toPxX(ex),
                    v.toPxY(ez), stroke);
            float len = horizontal ? Math.abs(v.curX - v.startX)
                    : Math.abs(v.curZ - v.startZ);
            canvas.drawText(meters(len),
                    v.toPxX((v.startX + ex) / 2f) + 12f,
                    v.toPxY((v.startZ + ez) / 2f) - 12f, measurePaint);
            return;
        }
        canvas.drawRect(v.toPxX(Math.min(v.startX, v.curX)),
                v.toPxY(Math.min(v.startZ, v.curZ)),
                v.toPxX(Math.max(v.startX, v.curX)),
                v.toPxY(Math.max(v.startZ, v.curZ)), stroke);
        // cota ao vivo do retângulo (medida interna do cômodo)
        canvas.drawText(meters(Math.abs(v.curX - v.startX)) + " × "
                        + meters(Math.abs(v.curZ - v.startZ)),
                v.toPxX((v.startX + v.curX) / 2f) - 60f,
                v.toPxY((v.startZ + v.curZ) / 2f), measurePaint);
    }

    private static String meters(float value) {
        return String.format("%.2f m", value).replace('.', ',');
    }

    /**
     * Cotas permanentes: comprimento das paredes e L×P das demais
     * estruturas, quando o zoom dá espaço (ou quando selecionadas).
     */
    private void drawMeasureLabels(Canvas canvas) {
        for (StructureObject s : v.doc.structures) {
            if (!v.visible(s)) continue;
            boolean selected = s == v.selectedStructure;
            if (StructureObject.ROLE_WALL
                    .equals(StructureRoles.roleOf(s))) {
                float len = Math.max(s.half[0], s.half[2]) * 2f;
                if (!selected && (v.scale < 16f || len * v.scale < 110f)) {
                    continue;
                }
                boolean alongX = s.half[0] >= s.half[2];
                canvas.drawText(meters(len),
                        v.toPxX(s.transform.x) - 34f,
                        v.toPxY(s.transform.z) + (alongX ? -14f : 0f),
                        measurePaint);
            } else {
                // bolinha de seleção sempre visível + "teto 3,00 × 5,00"
                float[] cp = v.chipPos(s);
                float px = v.toPxX(cp[0]);
                float py = v.toPxY(cp[1]);
                fill.setColor(PlanEditorView.chipColor(s));
                canvas.drawCircle(px, py, 8f, fill);
                stroke.setColor(selected ? 0xFFFFFFFF : 0x66000000);
                stroke.setStrokeWidth(selected ? 3f : 1.5f);
                canvas.drawCircle(px, py, selected ? 12f : 8f, stroke);
                if (selected || (v.scale >= 14f
                        && s.half[0] * 2f * v.scale >= 110f)) {
                    canvas.drawText(StructureRoles.name(s) + "  "
                                    + meters(s.half[0] * 2f) + " × "
                                    + meters(s.half[2] * 2f),
                            px + 14f, py + 9f, measurePaint);
                }
            }
        }
        if (v.selectedOpening != null) {
            StructureObject wall = v.selectedOpeningWall;
            boolean alongX = wall.half[0] >= wall.half[2];
            float ox = alongX ? wall.transform.x + v.selectedOpening.offset
                    : wall.transform.x;
            float oz = alongX ? wall.transform.z
                    : wall.transform.z + v.selectedOpening.offset;
            canvas.drawText(meters(v.selectedOpening.width) + " × "
                            + meters(v.selectedOpening.height),
                    v.toPxX(ox) - 50f, v.toPxY(oz) - 20f, measurePaint);
        }
    }
}
