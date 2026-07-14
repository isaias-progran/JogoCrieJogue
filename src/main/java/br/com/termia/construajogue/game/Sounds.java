package br.com.termia.construajogue.game;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import br.com.termia.construajogue.R;

/**
 * Efeitos rápidos via SoundPool (PLANO.md seção 6). WAVs procedurais em
 * res/raw. play() é seguro de chamar da thread GL; antes do load terminar
 * a chamada só não toca nada.
 */
public final class Sounds {

    private final SoundPool pool;
    private final int shot;
    private final int reload;
    private final int hit;
    private final int boom;
    private final int zap;
    private final int pickup;
    private final int chime;
    private final int door;

    public Sounds(Context context) {
        pool = new SoundPool.Builder()
                .setMaxStreams(5)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        shot = pool.load(context, R.raw.shot, 1);
        reload = pool.load(context, R.raw.reload, 1);
        hit = pool.load(context, R.raw.hit, 1);
        boom = pool.load(context, R.raw.boom, 1);
        zap = pool.load(context, R.raw.zap, 1);
        pickup = pool.load(context, R.raw.pickup, 1);
        chime = pool.load(context, R.raw.chime, 1);
        door = pool.load(context, R.raw.door, 1);
    }

    public void zap() {
        pool.play(zap, 0.6f, 0.6f, 1, 0, 1f);
    }

    public void pickup() {
        pool.play(pickup, 0.8f, 0.8f, 2, 0, 1f);
    }

    public void chime() {
        pool.play(chime, 1f, 1f, 2, 0, 1f);
    }

    public void door() {
        pool.play(door, 1f, 1f, 2, 0, 1f);
    }

    public void shot() {
        pool.play(shot, 0.8f, 0.8f, 1, 0, 1f);
    }

    public void reload() {
        pool.play(reload, 0.9f, 0.9f, 1, 0, 1f);
    }

    public void hit() {
        pool.play(hit, 0.7f, 0.7f, 2, 0, 1f);
    }

    public void boom() {
        pool.play(boom, 1f, 1f, 2, 0, 1f);
    }

    public void pause() {
        pool.autoPause();
    }

    public void resume() {
        pool.autoResume();
    }

    public void release() {
        pool.release();
    }
}
