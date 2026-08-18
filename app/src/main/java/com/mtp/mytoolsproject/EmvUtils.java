package com.mtp.mytoolsproject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Parser BER-TLV simplificado e helpers de comandos APDU para leitura
 * SOMENTE LEITURA de dados públicos de cartões EMV contactless (crédito/débito).
 *
 * Os dados aqui lidos (número, validade, nome do titular) são expostos pelo
 * próprio padrão EMV sem exigir senha/PIN — é o mesmo tipo de informação que
 * já está impressa/gravada fisicamente no cartão. Não há nenhuma operação de
 * escrita, e cartões EMV têm proteção criptográfica por transação que torna
 * clonagem funcional inviável a partir desses dados.
 */
public final class EmvUtils {

    private EmvUtils() {}

    /** SELECT do PPSE ("2PAY.SYS.DDF01") — primeiro passo padrão de leitura EMV contactless. */
    public static final byte[] APDU_SELECT_PPSE =
            NfcUtils.hexParaBytes("00A404000E325041592E5359532E444446303100");

    /** GET PROCESSING OPTIONS sem PDOL — funciona na maioria dos cartões com PDOL simples/vazio. */
    public static final byte[] APDU_GPO_SEM_PDOL =
            NfcUtils.hexParaBytes("80A8000002830000");

    public static byte[] montarSelectAid(byte[] aid) {
        byte[] cmd = new byte[5 + aid.length + 1];
        cmd[0] = 0x00; cmd[1] = (byte) 0xA4; cmd[2] = 0x04; cmd[3] = 0x00;
        cmd[4] = (byte) aid.length;
        System.arraycopy(aid, 0, cmd, 5, aid.length);
        cmd[cmd.length - 1] = 0x00;
        return cmd;
    }

    public static byte[] montarReadRecord(int record, int sfi) {
        return new byte[]{0x00, (byte) 0xB2, (byte) record, (byte) ((sfi << 3) | 0x04), 0x00};
    }

    /** Uma entrada da AFL (Application File Locator): qual SFI e faixa de registros ler. */
    public static class EntradaAfl {
        public int sfi;
        public int primeiroRegistro;
        public int ultimoRegistro;
    }

    public static List<EntradaAfl> parsearAfl(byte[] aflBytes) {
        List<EntradaAfl> lista = new ArrayList<>();
        if (aflBytes == null) return lista;
        for (int i = 0; i + 4 <= aflBytes.length; i += 4) {
            EntradaAfl e = new EntradaAfl();
            e.sfi = (aflBytes[i] & 0xFF) >> 3;
            e.primeiroRegistro = aflBytes[i + 1] & 0xFF;
            e.ultimoRegistro = aflBytes[i + 2] & 0xFF;
            lista.add(e);
        }
        return lista;
    }

    /** Busca recursivamente (BER-TLV) a primeira ocorrência de uma tag e devolve seu valor. */
    public static byte[] buscarTag(byte[] dados, String tagHex) {
        if (dados == null) return null;
        return buscarTagRecursivo(dados, NfcUtils.hexParaBytes(tagHex));
    }

    private static byte[] buscarTagRecursivo(byte[] dados, byte[] tagAlvo) {
        int i = 0;
        while (i < dados.length) {
            int inicioTag = i;
            int primeiroByte = dados[i] & 0xFF;
            boolean constructed = (primeiroByte & 0x20) != 0;
            i++;
            if ((primeiroByte & 0x1F) == 0x1F) {
                while (i < dados.length && (dados[i] & 0x80) != 0) i++;
                if (i < dados.length) i++;
            }
            if (i >= dados.length) break;

            byte[] tagAtual = Arrays.copyOfRange(dados, inicioTag, i);

            int primeiroLen = dados[i] & 0xFF;
            int length;
            i++;
            if ((primeiroLen & 0x80) == 0) {
                length = primeiroLen;
            } else {
                int numBytes = primeiroLen & 0x7F;
                length = 0;
                for (int b = 0; b < numBytes && i < dados.length; b++) {
                    length = (length << 8) | (dados[i] & 0xFF);
                    i++;
                }
            }
            if (length < 0 || i + length > dados.length) break;

            byte[] valor = Arrays.copyOfRange(dados, i, i + length);

            if (Arrays.equals(tagAtual, tagAlvo)) {
                return valor;
            }
            if (constructed) {
                byte[] achadoDentro = buscarTagRecursivo(valor, tagAlvo);
                if (achadoDentro != null) return achadoDentro;
            }

            i += length;
        }
        return null;
    }

    /** Converte dígitos BCD em string, descartando padding (nibbles > 9, ex: 'F'). */
    public static String bcdParaDigitos(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            int alto = (b >> 4) & 0xF;
            int baixo = b & 0xF;
            if (alto <= 9) sb.append(alto);
            if (baixo <= 9) sb.append(baixo);
        }
        return sb.toString();
    }

    /** Mostra só os 4 primeiros e 4 últimos dígitos do número do cartão, mascarando o meio. */
    public static String mascararPan(String pan) {
        if (pan == null || pan.length() < 8) return pan;
        String inicio = pan.substring(0, 4);
        String fim = pan.substring(pan.length() - 4);
        StringBuilder meio = new StringBuilder();
        for (int i = 0; i < pan.length() - 8; i++) meio.append("*");
        return inicio + " " + meio + " " + fim;
    }
}
