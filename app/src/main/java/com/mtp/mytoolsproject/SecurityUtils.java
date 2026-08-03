package com.mtp.mytoolsproject;

import android.content.Context;
import android.content.SharedPreferences;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * Utilitários para configurar e validar o PIN de bloqueio do app.
 * O PIN nunca é salvo em texto puro — apenas o hash SHA-256 (com salt
 * aleatório por instalação) fica gravado nas preferências.
 */
public final class SecurityUtils {

    private static final String PREFS_NAME = "SecurityPrefs";
    private static final String CHAVE_HASH = "pin_hash";
    private static final String CHAVE_SALT = "pin_salt";
    private static final String CHAVE_BLOQUEIO_ATIVO = "bloqueio_ativo";

    private SecurityUtils() {}

    public static boolean existePinConfigurado(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.contains(CHAVE_HASH);
    }

    public static boolean bloqueioAtivo(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getBoolean(CHAVE_BLOQUEIO_ATIVO, true);
    }

    public static void definirBloqueioAtivo(Context context, boolean ativo) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(CHAVE_BLOQUEIO_ATIVO, ativo).apply();
    }

    public static void salvarNovoPin(Context context, String pin) {
        try {
            byte[] salt = new byte[16];
            new SecureRandom().nextBytes(salt);
            String saltHex = NfcUtils.bytesParaHex(salt);
            String hash = gerarHash(pin, saltHex);

            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString(CHAVE_HASH, hash)
                    .putString(CHAVE_SALT, saltHex)
                    .apply();
        } catch (Exception ignored) {}
    }

    public static boolean validarPin(Context context, String pinDigitado) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String hashSalvo = prefs.getString(CHAVE_HASH, null);
        String saltSalvo = prefs.getString(CHAVE_SALT, null);
        if (hashSalvo == null || saltSalvo == null) return false;

        String hashDigitado = gerarHash(pinDigitado, saltSalvo);
        return hashSalvo.equals(hashDigitado);
    }

    public static void removerPin(Context context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(CHAVE_HASH)
                .remove(CHAVE_SALT)
                .apply();
    }

    private static String gerarHash(String pin, String saltHex) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(NfcUtils.hexParaBytes(saltHex));
            byte[] resultado = digest.digest(pin.getBytes("UTF-8"));
            return NfcUtils.bytesParaHex(resultado);
        } catch (Exception e) {
            return "";
        }
    }
}
