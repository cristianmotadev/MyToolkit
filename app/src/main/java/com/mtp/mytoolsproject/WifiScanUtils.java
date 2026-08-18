package com.mtp.mytoolsproject;

import android.net.wifi.ScanResult;
import android.os.Build;
import java.nio.ByteBuffer;

/**
 * Utilitários para interpretar os dados brutos que o Android retorna
 * de cada rede Wi-Fi encontrada em uma varredura (ScanResult).
 */
public final class WifiScanUtils {

    private WifiScanUtils() {}

    /**
     * Interpreta o campo "capabilities" do ScanResult (ex: "[WPA2-PSK-CCMP][WPS][ESS]")
     * e devolve um rótulo de segurança legível.
     */
    public static String interpretarSeguranca(String capabilities) {
        if (capabilities == null || capabilities.isEmpty()) return "Aberta";

        String cap = capabilities.toUpperCase();
        boolean temWpa3 = cap.contains("WPA3") || cap.contains("SAE");
        boolean temWpa2 = cap.contains("WPA2") || cap.contains("RSN");
        boolean temWpa = cap.contains("WPA-") || (cap.contains("WPA") && !temWpa2 && !temWpa3);
        boolean temWep = cap.contains("WEP");
        boolean temEnterprise = cap.contains("EAP") || cap.contains("802.1X");

        StringBuilder sb = new StringBuilder();
        if (temWpa3) sb.append("WPA3");
        else if (temWpa2) sb.append("WPA2");
        else if (temWpa) sb.append("WPA");
        else if (temWep) sb.append("WEP");
        else sb.append("Aberta");

        if (temEnterprise) sb.append(" Enterprise");

        return sb.toString();
    }

    public static boolean ehRedeAberta(String capabilities) {
        return interpretarSeguranca(capabilities).equals("Aberta");
    }

    /** Detecção passiva: apenas lê se o roteador anuncia WPS no beacon, sem tentar autenticar em nada. */
    public static boolean possuiWps(String capabilities) {
        return capabilities != null && capabilities.toUpperCase().contains("WPS");
    }

    /**
     * Detecção de WPS mais robusta que possuiWps(String): além de checar o campo
     * "capabilities" (que alguns fabricantes simplificam e às vezes omitem o WPS
     * mesmo quando o roteador anuncia), a partir do Android 11 (API 30) também
     * inspeciona os Information Elements BRUTOS do beacon, procurando o elemento
     * vendor-specific do WPS (OUI 00:50:F2, tipo 04) diretamente — sem depender
     * do resumo que o wpa_supplicant do sistema decidiu expor.
     *
     * Continua sendo 100% passivo: só lê o que o roteador já transmite
     * publicamente no beacon, nenhuma tentativa de autenticação é feita.
     *
     * Mesmo assim pode não pegar 100% dos casos: se o roteador só anuncia WPS
     * na resposta a probe request (e não no beacon que o Android cacheou), essa
     * informação não fica disponível para nenhum app dentro do sandbox do Android.
     */
    public static boolean possuiWpsAvancado(ScanResult resultado) {
        if (possuiWps(resultado.capabilities)) return true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                for (ScanResult.InformationElement ie : resultado.getInformationElements()) {
                    if (ie.getId() == 221) { // 221 = Vendor Specific
                        ByteBuffer buffer = ie.getBytes();
                        if (buffer.remaining() >= 4) {
                            byte[] oui = new byte[4];
                            buffer.get(oui);
                            boolean ehOuiWps = (oui[0] & 0xFF) == 0x00 && (oui[1] & 0xFF) == 0x50
                                    && (oui[2] & 0xFF) == 0xF2 && (oui[3] & 0xFF) == 0x04;
                            if (ehOuiWps) return true;
                        }
                    }
                }
            } catch (Exception ignored) {
                // Alguns aparelhos/ROMs podem restringir o acesso aos IEs brutos; nesse
                // caso caímos de volta silenciosamente para o resultado do método raso acima.
            }
        }
        return false;
    }

    public static boolean usaCriptografiaFraca(String capabilities) {
        String seguranca = interpretarSeguranca(capabilities);
        return seguranca.equals("WEP") || seguranca.equals("WPA");
    }

    /** Converte a frequência (MHz) em número de canal Wi-Fi, cobrindo 2.4GHz, 5GHz e 6GHz (Wi-Fi 6E). */
    public static int frequenciaParaCanal(int freqMhz) {
        if (freqMhz == 2484) return 14;
        if (freqMhz >= 2412 && freqMhz <= 2472) return (freqMhz - 2412) / 5 + 1;
        if (freqMhz >= 5170 && freqMhz <= 5825) return (freqMhz - 5000) / 5;
        if (freqMhz >= 5925 && freqMhz <= 7125) return (freqMhz - 5950) / 5 + 1;
        return -1;
    }

    public static String bandaDaFrequencia(int freqMhz) {
        if (freqMhz >= 2400 && freqMhz < 2500) return "2.4 GHz";
        if (freqMhz >= 4900 && freqMhz < 5925) return "5 GHz";
        if (freqMhz >= 5925 && freqMhz < 7125) return "6 GHz";
        return "Desconhecida";
    }

    /** Classifica a força do sinal (RSSI em dBm) em um rótulo qualitativo. */
    public static String qualidadeSinal(int rssiDbm) {
        if (rssiDbm >= -50) return "Excelente";
        if (rssiDbm >= -60) return "Bom";
        if (rssiDbm >= -70) return "Regular";
        if (rssiDbm >= -80) return "Fraco";
        return "Muito fraco";
    }
}