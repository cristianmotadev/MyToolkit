package com.mtp.mytoolsproject;

import java.util.Arrays;
import java.util.List;

/**
 * Dicionário de chaves padrão de fábrica publicamente conhecidas, usadas por
 * ferramentas de auditoria MIFARE Classic (ex: MIFARE Classic Tool, libnfc/mfoc).
 *
 * Serve apenas para tentar autenticar em setores que ainda usam uma chave
 * padrão/de fábrica. Setores com chave customizada permanecem bloqueados —
 * isso é esperado e correto, não uma falha da ferramenta.
 */
public final class NfcKeyDictionary {

    private NfcKeyDictionary() {}

    public static final List<byte[]> CHAVES_PADRAO = Arrays.asList(
            hex("FFFFFFFFFFFF"), // Chave de fábrica padrão (a mais comum em tags "em branco")
            hex("000000000000"), // Chave zerada
            hex("A0A1A2A3A4A5"), // Chave padrão NFC Forum / MAD
            hex("D3F7D3F7D3F7"), // Chave padrão NDEF (setor MAD2)
            hex("B0B1B2B3B4B5"),
            hex("4D3A99C351DD"),
            hex("1A982C7E459A"),
            hex("AABBCCDDEEFF"),
            hex("714C5C886E97"),
            hex("587EE5F9350F"),
            hex("A22AC0F16E0E"),
            hex("A0478CC39091"),
            hex("533CB6C723F6"),
            hex("8FD0A4F256E9")
    );

    private static byte[] hex(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }
}
