package com.mtp.mytoolsproject;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

import timber.log.Timber;

/**
 * Utilitários para configurar e validar o PIN de bloqueio do app.
 * O PIN nunca é salvo em texto puro — apenas o hash PBKDF2 (com salt
 * aleatório por instalação) fica gravado nas preferências criptografadas.
 * 
 * Melhorias implementadas:
 * - PBKDF2 com 100.000 iterações para resistência contra força bruta
 * - EncryptedSharedPreferences para armazenamento seguro
 * - Timber para logging estruturado
 * - Tratamento adequado de exceções
 */
public final class SecurityUtils {

    private static final String PREFS_NAME = "SecurityPrefs";
    private static final String CHAVE_HASH = "pin_hash";
    private static final String CHAVE_SALT = "pin_salt";
    private static final String CHAVE_BLOQUEIO_ATIVO = "bloqueio_ativo";
    
    // Configurações PBKDF2
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 100_000;
    private static final int KEY_LENGTH = 256;
    private static final int SALT_LENGTH = 16;

    private static SharedPreferences encryptedPrefs;

    private SecurityUtils() {}

    /**
     * Obtém SharedPreferences criptografados usando AndroidX Security
     */
    private static SharedPreferences getEncryptedPrefs(Context context) {
        if (encryptedPrefs == null) {
            try {
                MasterKey masterKey = new MasterKey.Builder(context)
                        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                        .build();

                encryptedPrefs = EncryptedSharedPreferences.create(
                        context,
                        PREFS_NAME,
                        masterKey,
                        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                );
            } catch (Exception e) {
                Timber.e(e, "Erro ao criar EncryptedSharedPreferences, fallback para prefs normais");
                encryptedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            }
        }
        return encryptedPrefs;
    }

    public static boolean existePinConfigurado(Context context) {
        SharedPreferences prefs = getEncryptedPrefs(context);
        boolean existe = prefs.contains(CHAVE_HASH);
        Timber.d("PIN configurado: %b", existe);
        return existe;
    }

    public static boolean bloqueioAtivo(Context context) {
        SharedPreferences prefs = getEncryptedPrefs(context);
        boolean ativo = prefs.getBoolean(CHAVE_BLOQUEIO_ATIVO, true);
        Timber.v("Bloqueio ativo: %b", ativo);
        return ativo;
    }

    public static void definirBloqueioAtivo(Context context, boolean ativo) {
        try {
            getEncryptedPrefs(context)
                    .edit()
                    .putBoolean(CHAVE_BLOQUEIO_ATIVO, ativo)
                    .apply();
            Timber.i("Bloqueio definido como: %b", ativo);
        } catch (Exception e) {
            Timber.e(e, "Erro ao definir status do bloqueio");
        }
    }

    public static void salvarNovoPin(Context context, String pin) {
        if (pin == null || pin.isEmpty()) {
            Timber.w("Tentativa de salvar PIN vazio ou nulo");
            return;
        }
        
        try {
            byte[] salt = new byte[SALT_LENGTH];
            SecureRandom secureRandom = new SecureRandom();
            secureRandom.nextBytes(salt);
            String saltHex = bytesToHex(salt);
            
            String hash = gerarHashPBKDF2(pin, salt);
            
            getEncryptedPrefs(context)
                    .edit()
                    .putString(CHAVE_HASH, hash)
                    .putString(CHAVE_SALT, saltHex)
                    .apply();
                    
            Timber.i("Novo PIN salvo com sucesso (salt: %s)", saltHex.substring(0, 8) + "...");
        } catch (Exception e) {
            Timber.e(e, "Erro crítico ao salvar PIN");
            throw new SecurityException("Falha ao salvar PIN", e);
        }
    }

    public static boolean validarPin(Context context, String pinDigitado) {
        if (pinDigitado == null || pinDigitado.isEmpty()) {
            Timber.w("Tentativa de validação com PIN vazio ou nulo");
            return false;
        }
        
        try {
            SharedPreferences prefs = getEncryptedPrefs(context);
            String hashSalvo = prefs.getString(CHAVE_HASH, null);
            String saltSalvo = prefs.getString(CHAVE_SALT, null);
            
            if (hashSalvo == null || saltSalvo == null) {
                Timber.w("PIN não configurado no sistema");
                return false;
            }

            String hashDigitado = gerarHashPBKDF2(pinDigitado, saltSalvo);
            boolean valido = hashSalvo.equals(hashDigitado);
            
            Timber.d("Validação de PIN: %b", valido);
            return valido;
        } catch (Exception e) {
            Timber.e(e, "Erro durante validação do PIN");
            return false;
        }
    }

    public static void removerPin(Context context) {
        try {
            getEncryptedPrefs(context)
                    .edit()
                    .remove(CHAVE_HASH)
                    .remove(CHAVE_SALT)
                    .apply();
            Timber.i("PIN removido com sucesso");
        } catch (Exception e) {
            Timber.e(e, "Erro ao remover PIN");
        }
    }

    /**
     * Gera hash usando PBKDF2 com HMAC-SHA256
     * Muito mais seguro que SHA-256 simples contra ataques de força bruta
     */
    private static String gerarHashPBKDF2(String pin, String saltHex) throws Exception {
        byte[] salt = hexToBytes(saltHex);
        
        KeySpec spec = new PBEKeySpec(
                pin.toCharArray(),
                salt,
                ITERATIONS,
                KEY_LENGTH
        );
        
        SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
        byte[] hashBytes = factory.generateSecret(spec).getEncoded();
        
        return bytesToHex(hashBytes);
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }
}
