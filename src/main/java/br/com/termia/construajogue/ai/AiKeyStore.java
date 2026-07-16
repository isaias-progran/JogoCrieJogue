package br.com.termia.construajogue.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * A chave digitada nunca entra no APK. Por padrão vive só na memória; se o
 * usuário pedir, o Android Keystore protege o valor persistido com AES-GCM.
 */
public final class AiKeyStore {

    private static final String PREFS = "ai_private";
    private static final String VALUE = "encrypted_api_key";
    private static final String IV = "encrypted_api_key_iv";
    private static final String REMEMBER = "remember_api_key";
    private static final String ALIAS =
            "br.com.termia.construajogue.OPENAI_KEY_V1";

    private final Context context;
    private final SharedPreferences prefs;
    private String sessionKey;

    public AiKeyStore(Context context) {
        this.context = context.getApplicationContext();
        prefs = this.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public synchronized boolean hasKey() {
        return sessionKey != null || prefs.contains(VALUE);
    }

    public synchronized boolean remembersKey() {
        return prefs.getBoolean(REMEMBER, false) && prefs.contains(VALUE);
    }

    public synchronized void save(String value, boolean remember)
            throws Exception {
        String key = normalize(value);
        if (!remember) {
            boolean removed = prefs.edit().remove(VALUE).remove(IV)
                    .putBoolean(REMEMBER, false).commit();
            if (!removed) {
                throw new IllegalStateException(
                        "não foi possível apagar a chave persistida");
            }
            sessionKey = key;
            return;
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey());
        cipher.updateAAD(context.getPackageName().getBytes(
                StandardCharsets.UTF_8));
        byte[] encrypted = cipher.doFinal(key.getBytes(StandardCharsets.UTF_8));
        boolean stored = prefs.edit()
                .putString(VALUE, Base64.encodeToString(encrypted,
                        Base64.NO_WRAP))
                .putString(IV, Base64.encodeToString(cipher.getIV(),
                        Base64.NO_WRAP))
                .putBoolean(REMEMBER, true).commit();
        if (!stored) {
            throw new IllegalStateException(
                    "não foi possível salvar no armazenamento privado");
        }
        sessionKey = key;
    }

    public synchronized String get() throws Exception {
        if (sessionKey != null) return sessionKey;
        String encrypted = prefs.getString(VALUE, null);
        String iv = prefs.getString(IV, null);
        if (encrypted == null || iv == null) return null;
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, secretKey(),
                    new GCMParameterSpec(128, Base64.decode(iv,
                            Base64.NO_WRAP)));
            cipher.updateAAD(context.getPackageName().getBytes(
                    StandardCharsets.UTF_8));
            sessionKey = normalize(new String(cipher.doFinal(
                    Base64.decode(encrypted, Base64.NO_WRAP)),
                    StandardCharsets.UTF_8));
            return sessionKey;
        } catch (Exception broken) {
            // Nunca continua usando um segredo cuja integridade falhou.
            clear();
            throw broken;
        }
    }

    public synchronized void clear() {
        sessionKey = null;
        prefs.edit().clear().commit();
        try {
            KeyStore store = KeyStore.getInstance("AndroidKeyStore");
            store.load(null);
            if (store.containsAlias(ALIAS)) store.deleteEntry(ALIAS);
        } catch (Exception ignored) {
            // Preferências já foram apagadas; não há ciphertext utilizável.
        }
    }

    public synchronized void clearSession() {
        sessionKey = null;
    }

    private SecretKey secretKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(ALIAS)) {
            return (SecretKey) store.getKey(ALIAS, null);
        }
        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT
                        | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }

    private static String normalize(String value) {
        return AiOpenAiClient.normalizeApiKey(value);
    }
}
