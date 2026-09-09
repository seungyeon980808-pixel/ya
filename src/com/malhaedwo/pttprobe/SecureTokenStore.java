package com.malhaedwo.pttprobe;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public final class SecureTokenStore {
    private static final String PREFS = "malhaedwo_google_oauth_secure";
    private static final String KEY_ALIAS = "malhaedwo_google_oauth_aes_v1";
    private static final Object LOCK = new Object();

    private final SharedPreferences preferences;

    public SecureTokenStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void put(String name, String value) throws Exception {
        synchronized (LOCK) {
            if (value == null) {
                preferences.edit().remove(name).apply();
                return;
            }
            SecretKey key = getOrCreateKey();
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] iv = cipher.getIV();
            ByteBuffer packed = ByteBuffer.allocate(4 + iv.length + encrypted.length);
            packed.putInt(iv.length).put(iv).put(encrypted);
            preferences.edit().putString(name, Base64.encodeToString(packed.array(), Base64.NO_WRAP)).commit();
        }
    }

    public String get(String name) {
        synchronized (LOCK) {
            String encoded = preferences.getString(name, null);
            if (encoded == null || encoded.isEmpty()) return null;
            try {
                byte[] packed = Base64.decode(encoded, Base64.NO_WRAP);
                ByteBuffer buffer = ByteBuffer.wrap(packed);
                int ivLength = buffer.getInt();
                if (ivLength < 12 || ivLength > 32 || buffer.remaining() <= ivLength) throw new Exception("invalid encrypted value");
                byte[] iv = new byte[ivLength];
                buffer.get(iv);
                byte[] encrypted = new byte[buffer.remaining()];
                buffer.get(encrypted);
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
                return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
            } catch (Throwable error) {
                preferences.edit().remove(name).apply();
                return null;
            }
        }
    }

    public void remove(String name) {
        preferences.edit().remove(name).apply();
    }

    public void clear() {
        preferences.edit().clear().commit();
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        java.security.Key existing = store.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setUserAuthenticationRequired(false)
                .build());
        return generator.generateKey();
    }
}
