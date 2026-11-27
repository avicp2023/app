package com.avicp2023.budgetmanager;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Stores a salted, iterated PIN hash. The PIN itself is never persisted. */
public final class PinSecurity {
    private static final String PREFS = "budget_security";
    private static final String KEY_SALT = "pin_salt";
    private static final String KEY_HASH = "pin_hash";
    private static final int ITERATIONS = 120_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_LENGTH_BYTES = 16;

    private final SharedPreferences preferences;
    private final SecureRandom random = new SecureRandom();

    public PinSecurity(Context context) {
        preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean isEnabled() {
        return preferences.contains(KEY_SALT) && preferences.contains(KEY_HASH);
    }

    public void savePin(String pin) {
        if (pin == null || !pin.matches("[0-9]{4,8}")) {
            throw new IllegalArgumentException("קוד PIN חייב להכיל 4 עד 8 ספרות");
        }
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        random.nextBytes(salt);
        byte[] hash = derive(pin, salt);
        preferences.edit()
                .putString(KEY_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(KEY_HASH, Base64.encodeToString(hash, Base64.NO_WRAP))
                .apply();
        Arrays.fill(hash, (byte) 0);
    }

    public boolean verifyPin(String pin) {
        if (pin == null || !pin.matches("[0-9]{4,8}") || !isEnabled()) return false;
        try {
            byte[] salt = Base64.decode(preferences.getString(KEY_SALT, ""), Base64.NO_WRAP);
            byte[] expected = Base64.decode(preferences.getString(KEY_HASH, ""), Base64.NO_WRAP);
            byte[] actual = derive(pin, salt);
            boolean matches = MessageDigest.isEqual(expected, actual);
            Arrays.fill(actual, (byte) 0);
            return matches;
        } catch (Exception e) {
            return false;
        }
    }

    public void clearPin() {
        preferences.edit().remove(KEY_SALT).remove(KEY_HASH).apply();
    }

    private byte[] derive(String pin, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("לא ניתן לאבטח את קוד ה-PIN במכשיר זה", e);
        } finally {
            spec.clearPassword();
        }
    }
}
