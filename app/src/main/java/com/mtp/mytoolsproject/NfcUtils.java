package com.mtp.mytoolsproject;

/**
 * Utilitários de conversão entre bytes e hexadecimal, usados pelo módulo NFC
 * (leitura/escrita NDEF e dump/clonagem MIFARE Classic).
 */
public final class NfcUtils {

    private NfcUtils() {}

    public static String bytesParaHex(byte[] bytes) {
        if (bytes == null) return "";
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    public static byte[] hexParaBytes(String hex) {
        if (hex == null || hex.isEmpty()) return new byte[0];
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
