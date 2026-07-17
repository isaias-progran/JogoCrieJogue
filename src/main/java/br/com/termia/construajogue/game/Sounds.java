package br.com.termia.construajogue.game;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.SoundPool;

import br.com.termia.construajogue.R;
import br.com.termia.construajogue.runtime.RuntimeLevel;

/** Efeitos curtos e paisagens sonoras procedurais do jogo. */
public final class Sounds {

    private static final int AMBIENT_SECONDS = 7;

    private final SoundPool pool;
    private final int shot;
    private final int reload;
    private final int hit;
    private final int boom;
    private final int zap;
    private final int pickup;
    private final int chime;
    private final int door;
    private final AudioTrack dayAmbient;
    private final AudioTrack nightAmbient;
    private final AudioTrack tunnelAmbient;
    private final AudioTrack industrialAmbient;
    private final AudioTrack hardStepA;
    private final AudioTrack hardStepB;
    private final AudioTrack wetStepA;
    private final AudioTrack wetStepB;
    private int skyMode = RuntimeLevel.SKY_NONE;
    private int soundscapeMode = RuntimeLevel.SOUNDSCAPE_OUTDOOR;
    private boolean paused;

    public Sounds(Context context) {
        pool = new SoundPool.Builder()
                .setMaxStreams(6)
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
        dayAmbient = ambientTrack(0);
        nightAmbient = ambientTrack(1);
        tunnelAmbient = ambientTrack(2);
        industrialAmbient = ambientTrack(3);
        hardStepA = stepTrack(false, false);
        hardStepB = stepTrack(false, true);
        wetStepA = stepTrack(true, false);
        wetStepB = stepTrack(true, true);
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

    /** Mesmo timbre do arsenal, mais baixo/agudo para distinguir o aliado. */
    public void allyShot() {
        pool.play(shot, 0.48f, 0.48f, 1, 0, 1.16f);
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

    /** Passos alternados; em água usa um ruído mais grave e longo. */
    public synchronized void footstep(boolean wet, boolean alternate) {
        if (paused) return;
        AudioTrack track = wet
                ? (alternate ? wetStepB : wetStepA)
                : (alternate ? hardStepB : hardStepA);
        float volume = wet ? 0.24f
                : soundscapeMode == RuntimeLevel.SOUNDSCAPE_TUNNEL
                ? 0.32f : 0.23f;
        restart(track, volume);
    }

    /** Seleciona céu e acústica; túnel/indústria independem do horário. */
    public synchronized void setAmbient(int newSkyMode,
                                        int newSoundscapeMode) {
        if (skyMode == newSkyMode && soundscapeMode == newSoundscapeMode) {
            return;
        }
        pauseAmbient(true);
        skyMode = newSkyMode;
        soundscapeMode = newSoundscapeMode;
        if (!paused) playAmbient();
    }

    /** Compatibilidade com chamadas antigas: paisagem externa. */
    public void setAmbient(int newSkyMode) {
        setAmbient(newSkyMode, RuntimeLevel.SOUNDSCAPE_OUTDOOR);
    }

    private void playAmbient() {
        if (soundscapeMode == RuntimeLevel.SOUNDSCAPE_TUNNEL) {
            tunnelAmbient.play();
        } else if (soundscapeMode == RuntimeLevel.SOUNDSCAPE_INDUSTRIAL) {
            industrialAmbient.play();
        } else if (skyMode == RuntimeLevel.SKY_DAY) {
            dayAmbient.play();
        } else if (skyMode == RuntimeLevel.SKY_DUSK
                || skyMode == RuntimeLevel.SKY_NIGHT) {
            nightAmbient.play();
        }
    }

    public synchronized void pause() {
        paused = true;
        pool.autoPause();
        pauseAmbient(false);
        pauseOneShots();
    }

    public synchronized void resume() {
        paused = false;
        pool.autoResume();
        playAmbient();
    }

    public synchronized void release() {
        pauseAmbient(false);
        dayAmbient.release();
        nightAmbient.release();
        tunnelAmbient.release();
        industrialAmbient.release();
        hardStepA.release();
        hardStepB.release();
        wetStepA.release();
        wetStepB.release();
        pool.release();
    }

    private void pauseAmbient(boolean rewind) {
        AudioTrack[] tracks = {dayAmbient, nightAmbient, tunnelAmbient,
                industrialAmbient};
        for (AudioTrack track : tracks) {
            if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                track.pause();
            }
            if (rewind) track.setPlaybackHeadPosition(0);
        }
    }

    private void pauseOneShots() {
        AudioTrack[] tracks = {hardStepA, hardStepB, wetStepA, wetStepB};
        for (AudioTrack track : tracks) {
            if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                track.pause();
            }
        }
    }

    private static void restart(AudioTrack track, float volume) {
        try {
            if (track.getPlayState() == AudioTrack.PLAYSTATE_PLAYING) {
                track.stop();
            }
            track.setPlaybackHeadPosition(0);
            track.setVolume(volume);
            track.play();
        } catch (IllegalStateException ignored) {
            // Áudio nunca pode interromper a thread do jogo.
        }
    }

    /**
     * Loops PCM determinísticos: pássaros, vento, túnel com gotas e máquinas.
     * Continuam pequenos e não acrescentam arquivos ao APK.
     */
    private static AudioTrack ambientTrack(int kind) {
        final int rate = 22050;
        short[] samples = new short[rate * AMBIENT_SECONDS];
        int random = 0x654321 + kind * 0x10203;
        float smooth = 0f;
        for (int i = 0; i < samples.length; i++) {
            random = random * 1103515245 + 12345;
            float noise = ((random >>> 16) & 0x7FFF) / 16384f - 1f;
            float t = i / (float) rate;
            smooth += (noise - smooth) * (kind == 0 ? 0.018f : 0.006f);
            float value;
            if (kind == 0) {
                value = smooth * 0.10f
                        + chirp(t, 0.85f, 0.34f, 1450f, 2450f)
                        + chirp(t, 3.15f, 0.27f, 1750f, 2850f)
                        + chirp(t, 5.42f, 0.38f, 1250f, 2250f);
            } else if (kind == 1) {
                float gust = 0.55f + 0.25f * (float) Math.sin(t * 1.17f)
                        + 0.15f * (float) Math.sin(t * 2.41f);
                value = smooth * gust * 0.40f;
                value += insect(t, 1.55f) + insect(t, 4.75f);
            } else if (kind == 2) {
                value = 0.055f * (float) Math.sin(t * Math.PI * 2f * 58f)
                        + 0.025f * (float) Math.sin(
                        t * Math.PI * 2f * 87f) + smooth * 0.13f;
                value += drip(t, 0.72f, 1260f)
                        + drip(t, 2.84f, 980f)
                        + drip(t, 5.63f, 1510f);
            } else {
                float motor = 0.55f + 0.35f * (float) Math.sin(t * 0.83f);
                value = motor * (0.055f * (float) Math.sin(
                        t * Math.PI * 2f * 46f)) + smooth * 0.10f;
                value += clank(t, 1.35f) + clank(t, 4.45f);
            }
            samples[i] = (short) (Math.max(-1f, Math.min(1f, value))
                    * 15000f);
        }
        AudioTrack track = staticTrack(rate, samples);
        track.setLoopPoints(0, samples.length, -1);
        track.setVolume(kind == 2 ? 0.25f : kind == 3 ? 0.22f : 0.18f);
        return track;
    }

    private static AudioTrack stepTrack(boolean wet, boolean alternate) {
        final int rate = 22050;
        int length = wet ? rate / 5 : rate / 10;
        short[] samples = new short[length];
        int random = alternate ? 0x4A31C2 : 0x72B419;
        float smooth = 0f;
        for (int i = 0; i < samples.length; i++) {
            random = random * 1103515245 + 12345;
            float noise = ((random >>> 16) & 0x7FFF) / 16384f - 1f;
            float p = i / (float) samples.length;
            float envelope = (float) Math.exp(-p * (wet ? 5.2f : 9f));
            if (wet) {
                smooth += (noise - smooth) * 0.12f;
                samples[i] = (short) ((smooth * 0.75f
                        + noise * 0.25f) * envelope * 12000f);
            } else {
                smooth += (noise - smooth) * 0.30f;
                float thud = (float) Math.sin(i / (float) rate
                        * Math.PI * 2f * (alternate ? 105f : 92f));
                samples[i] = (short) ((smooth * 0.58f + thud * 0.42f)
                        * envelope * 13500f);
            }
        }
        return staticTrack(rate, samples);
    }

    @SuppressWarnings("deprecation")
    private static AudioTrack staticTrack(int rate, short[] samples) {
        AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC,
                rate, AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                samples.length * 2, AudioTrack.MODE_STATIC);
        track.write(samples, 0, samples.length);
        return track;
    }

    private static float chirp(float time, float start, float duration,
                               float fromHz, float toHz) {
        float local = time - start;
        if (local < 0f || local > duration) return 0f;
        float p = local / duration;
        float envelope = (float) Math.sin(Math.PI * p);
        float frequency = fromHz + (toHz - fromHz) * p;
        return 0.20f * envelope * (float) Math.sin(
                Math.PI * 2f * frequency * local);
    }

    private static float insect(float time, float start) {
        float local = time - start;
        if (local < 0f || local > 0.42f) return 0f;
        float gate = ((int) (local * 34f) & 1) == 0 ? 1f : 0f;
        return gate * 0.055f * (float) Math.sin(
                Math.PI * 2f * 3650f * local);
    }

    private static float drip(float time, float start, float frequency) {
        float local = time - start;
        if (local < 0f || local > 0.34f) return 0f;
        float envelope = (float) Math.exp(-local * 14f);
        return 0.18f * envelope * (float) Math.sin(
                Math.PI * 2f * frequency * local);
    }

    private static float clank(float time, float start) {
        float local = time - start;
        if (local < 0f || local > 0.28f) return 0f;
        float envelope = (float) Math.exp(-local * 12f);
        return 0.13f * envelope * ((float) Math.sin(
                Math.PI * 2f * 640f * local)
                + 0.55f * (float) Math.sin(
                Math.PI * 2f * 910f * local));
    }
}
