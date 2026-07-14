package br.com.termia.construajogue.input;

import android.content.Context;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;

/**
 * Controles no padrão dos FPS mobile (CoD/PUBG): joystick flutuante na
 * metade esquerda; OLHAR em qualquer área livre da tela (não só a direita);
 * o botão de TIRO também MIRA — segurar e arrastar atira e vira a câmera
 * com o mesmo dedo; botões de PULO e recarga no arco do polegar direito.
 * Dedos rastreados por pointerId — nunca por índice (lição do dicom3d:
 * índices mudam quando outro dedo sobe).
 * Thread: a UI escreve; a thread GL lê moveX/moveZ/firing (volatile) e
 * consome takeLookYaw/takeLookPitch/takeReload/takeJump (synchronized) —
 * padrão do dicom3d.
 */
public final class TouchControls extends View {

    private static final float DEAD_ZONE = 0.12f;

    private final float maxRadius;
    private final ControlOverlay overlay;
    private final PauseMenu pauseMenu;

    private int moveId = -1;
    private int lookId = -1;
    private int fireId = -1;
    private float anchorX;
    private float anchorY;
    private float knobX;
    private float knobY;
    private float lastLookX;
    private float lastLookY;
    private float lastFireX;
    private float lastFireY;
    private volatile float moveX;
    private volatile float moveZ;
    private volatile boolean firing;
    private volatile boolean interactVisible;
    private volatile boolean gameOver;
    private volatile boolean paused;
    private float pendingYaw;
    private float pendingPitch;
    private boolean reloadPending;
    private boolean interactPending;
    private boolean restartPending;
    private boolean jumpPending;

    public TouchControls(Context context) {
        super(context);
        float density = getResources().getDisplayMetrics().density;
        maxRadius = 60f * density;
        overlay = new ControlOverlay(density, maxRadius);
        pauseMenu = new PauseMenu(context, density);
        setWillNotDraw(false);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        super.onSizeChanged(w, h, oldW, oldH);
        overlay.onSizeChanged(w, h);
        pauseMenu.onSizeChanged(w, h);
    }

    // ---- lidos pela thread GL ----

    public float moveX() {
        return moveX;
    }

    public float moveZ() {
        return moveZ;
    }

    public boolean firing() {
        return firing;
    }

    /** Lido pela thread GL para congelar toda a simulação. */
    public boolean paused() {
        return paused;
    }

    public synchronized float takeLookYaw() {
        float value = pendingYaw;
        pendingYaw = 0f;
        return value;
    }

    public synchronized float takeLookPitch() {
        float value = pendingPitch;
        pendingPitch = 0f;
        return value;
    }

    public synchronized boolean takeReload() {
        boolean value = reloadPending;
        reloadPending = false;
        return value;
    }

    public synchronized boolean takeInteract() {
        boolean value = interactPending;
        interactPending = false;
        return value;
    }

    public synchronized boolean takeJump() {
        boolean value = jumpPending;
        jumpPending = false;
        return value;
    }

    public synchronized boolean takeRestart() {
        boolean value = restartPending;
        restartPending = false;
        return value;
    }

    // ---- escritos pela thread GL (postInvalidate é thread-safe) ----

    /** Mostra o botão ATIVAR perto de terminais. */
    public void setInteractVisible(boolean value) {
        if (interactVisible != value) {
            interactVisible = value;
            postInvalidate();
        }
    }

    /** Fim de partida: qualquer toque vira pedido de reinício. */
    public void setGameOver(boolean value) {
        if (gameOver != value) {
            gameOver = value;
            if (value) {
                firing = false;
                moveX = 0f;
                moveZ = 0f;
            }
            postInvalidate();
        }
    }

    /** Pode ser chamado pela Activity ao perder foco. */
    public void pause() {
        setPaused(true);
    }

    private void setPaused(boolean value) {
        if (paused == value) {
            return;
        }
        paused = value;
        if (value) {
            clearGameplayInput();
        }
        invalidate();
    }

    private void clearGameplayInput() {
        moveId = -1;
        lookId = -1;
        fireId = -1;
        moveX = 0f;
        moveZ = 0f;
        firing = false;
        synchronized (this) {
            pendingYaw = 0f;
            pendingPitch = 0f;
            reloadPending = false;
            interactPending = false;
            jumpPending = false;
        }
    }

    // ---- thread de UI ----

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                onFingerDown(event, event.getActionIndex());
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < event.getPointerCount(); i++) {
                    onFingerMove(event.getPointerId(i),
                            event.getX(i), event.getY(i));
                }
                break;
            case MotionEvent.ACTION_POINTER_UP:
                onFingerUp(event.getPointerId(event.getActionIndex()));
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                onFingerUp(moveId);
                onFingerUp(lookId);
                onFingerUp(fireId);
                break;
            default:
                break;
        }
        return true;
    }

    private void onFingerDown(MotionEvent event, int index) {
        float x = event.getX(index);
        float y = event.getY(index);
        int id = event.getPointerId(index);
        if (gameOver) {
            synchronized (this) {
                restartPending = true;
            }
            return;
        }
        if (paused) {
            if (pauseMenu.onTouch(x, y)) {
                setPaused(false);
            }
            invalidate();
            return;
        }
        if (pauseMenu.pauseButtonContains(x, y)) {
            setPaused(true);
            return;
        }
        if (interactVisible && overlay.interactContains(x, y)) {
            synchronized (this) {
                interactPending = true;
            }
            return;
        }
        if (overlay.fireContains(x, y)) {
            if (fireId == -1) {
                fireId = id;
                firing = true;
                lastFireX = x;
                lastFireY = y;
                invalidate();
            }
        } else if (overlay.reloadContains(x, y)) {
            synchronized (this) {
                reloadPending = true;
            }
        } else if (overlay.jumpContains(x, y)) {
            synchronized (this) {
                jumpPending = true;
            }
        } else if (x < getWidth() / 2f && moveId == -1) {
            moveId = id;
            anchorX = x;
            anchorY = y;
            knobX = x;
            knobY = y;
            invalidate();
        } else if (lookId == -1) {
            // qualquer área livre da tela olha (padrão dos FPS mobile)
            lookId = id;
            lastLookX = x;
            lastLookY = y;
        }
    }

    private void onFingerMove(int id, float x, float y) {
        if (id == moveId) {
            float dx = x - anchorX;
            float dy = y - anchorY;
            float length = (float) Math.hypot(dx, dy);
            if (length > maxRadius) {
                dx = dx * maxRadius / length;
                dy = dy * maxRadius / length;
            }
            knobX = anchorX + dx;
            knobY = anchorY + dy;
            float mx = dx / maxRadius;
            float mz = -dy / maxRadius; // arrastar p/ cima = frente
            if (Math.hypot(mx, mz) < DEAD_ZONE) {
                mx = 0f;
                mz = 0f;
            }
            moveX = mx;
            moveZ = mz;
            invalidate();
        } else if (id == lookId) {
            float dx = x - lastLookX;
            float dy = y - lastLookY;
            lastLookX = x;
            lastLookY = y;
            synchronized (this) {
                pendingYaw += pauseMenu.yawDelta(dx);
                pendingPitch += pauseMenu.pitchDelta(dy);
            }
        } else if (id == fireId) {
            // arrastar segurando o TIRO mira com o mesmo dedo (CoD/PUBG)
            float dx = x - lastFireX;
            float dy = y - lastFireY;
            lastFireX = x;
            lastFireY = y;
            synchronized (this) {
                pendingYaw += pauseMenu.yawDelta(dx);
                pendingPitch += pauseMenu.pitchDelta(dy);
            }
        }
    }

    private void onFingerUp(int id) {
        if (id == -1) {
            return;
        }
        if (id == moveId) {
            moveId = -1;
            moveX = 0f;
            moveZ = 0f;
            invalidate();
        } else if (id == lookId) {
            lookId = -1;
        } else if (id == fireId) {
            fireId = -1;
            firing = false;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (gameOver) {
            return; // fim de partida: só a mensagem do HUD na tela
        }
        if (paused) {
            pauseMenu.draw(canvas);
            return;
        }
        overlay.draw(canvas, moveId != -1, anchorX, anchorY, knobX, knobY,
                firing, interactVisible);
        pauseMenu.drawPauseButton(canvas);
    }

}
