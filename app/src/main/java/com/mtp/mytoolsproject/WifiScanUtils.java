package com.mtp.mytoolsproject;

import android.net.wifi.ScanResult;
import android.os.Build;
import java.nio.ByteBuffer;

import timber.log.Timber;

/**
 * Utilitários para interpretar os dados brutos que o Android retorna
 * de cada rede Wi-Fi encontrada em uma varredura (ScanResult).
 * 
 * Melhorias implementadas:
 * - Logging estruturado com Timber
 * - Validação de entrada mais robusta
 * - Documentação aprimorada
 */
public final class WifiScanUtils {

    private static final String TAG = "WifiScanUtils";

    private WifiScanUtils() {}

    /**
     * Interpreta o campo "capabilities" do ScanResult (ex: "[WPA2-PSK-CCMP][WPS][ESS]")
     * e devolve um rótulo de segurança legível.
     * 
     * @param capabilities string de capacidades da rede Wi-Fi
     * @return rótulo de segurança (WPA3, WPA2, WPA, WEP, Aberta)
     */
    public static String interpretarSeguranca(String capabilities) {
        if (capabilities == null || capabilities.isEmpty()) {
            Timber.v("Capabilities nula ou vazia, classificando como 'Aberta'");
            return "Aberta";
        }

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

        String resultado = sb.toString();
        Timber.d("Segurança interpretada: %s (capabilities: %s)", resultado, capabilities);
        return resultado;
    }

    /**
     * Verifica se a rede é aberta (sem criptografia).
     * 
     * @param capabilities string de capacidades da rede
     * @return true se a rede for aberta
     */
    public static boolean ehRedeAberta(String capabilities) {
        boolean aberta = interpretarSeguranca(capabilities).equals("Aberta");
        Timber.v("Rede aberta: %b", aberta);
        return aberta;
    }

    /** Detecção passiva: apenas lê se o roteador anuncia WPS no beacon, sem tentar autenticar em nada. */
    public static boolean possuiWps(String capabilities) {
        boolean temWps = capabilities != null && capabilities.toUpperCase().contains("WPS");
        Timber.v("WPS detectado: %b", temWps);
        return temWps;
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
        if (resultado == null) {
            Timber.w("ScanResult nulo fornecido para possuiWpsAvancado");
            return false;
        }
        
        if (possuiWps(resultado.capabilities)) {
            Timber.d("WPS detectado via capabilities");
            return true;
        }

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
                            if (ehOuiWps) {
                                Timber.d("WPS detectado via Information Element (OUI 00:50:F2:04)");
                                return true;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Timber.e(e, "Erro ao analisar Information Elements para detecção de WPS");
            }
        } else {
            Timber.v("Android < R, usando apenas detecção básica de WPS");
        }
        
        Timber.v("WPS não detectado");
        return false;
    }

    /**
     * Verifica se a rede usa criptografia fraca (WEP ou WPA original).
     * 
     * @param capabilities string de capacidades da rede
     * @return true se usar criptografia fraca
     */
    public static boolean usaCriptografiaFraca(String capabilities) {
        String seguranca = interpretarSeguranca(capabilities);
        boolean fraca = seguranca.equals("WEP") || seguranca.equals("WPA");
        Timber.d("Criptografia fraca: %b (%s)", fraca, seguranca);
        return fraca;
    }

    /** 
     * Converte a frequência (MHz) em número de canal Wi-Fi, cobrindo 2.4GHz, 5GHz e 6GHz (Wi-Fi 6E).
     * 
     * @param freqMhz frequência em MHz
     * @return número do canal ou -1 se desconhecido
     */
    public static int frequenciaParaCanal(int freqMhz) {
        int canal;
        if (freqMhz == 2484) {
            canal = 14;
        } else if (freqMhz >= 2412 && freqMhz <= 2472) {
            canal = (freqMhz - 2412) / 5 + 1;
        } else if (freqMhz >= 5170 && freqMhz <= 5825) {
            canal = (freqMhz - 5000) / 5;
        } else if (freqMhz >= 5925 && freqMhz <= 7125) {
            canal = (freqMhz - 5950) / 5 + 1;
        } else {
            canal = -1;
            Timber.v("Frequência desconhecida: %d MHz", freqMhz);
        }
        
        Timber.v("Frequência %d MHz -> Canal %d", freqMhz, canal);
        return canal;
    }

    /**
     * Determina a banda de frequência (2.4 GHz, 5 GHz, 6 GHz).
     * 
     * @param freqMhz frequência em MHz
     * @return nome da banda
     */
    public static String bandaDaFrequencia(int freqMhz) {
        String banda;
        if (freqMhz >= 2400 && freqMhz < 2500) {
            banda = "2.4 GHz";
        } else if (freqMhz >= 4900 && freqMhz < 5925) {
            banda = "5 GHz";
        } else if (freqMhz >= 5925 && freqMhz < 7125) {
            banda = "6 GHz";
        } else {
            banda = "Desconhecida";
            Timber.v("Banda desconhecida para frequência: %d MHz", freqMhz);
        }
        
        Timber.v("Frequência %d MHz -> Banda %s", freqMhz, banda);
        return banda;
    }

    /** 
     * Classifica a força do sinal (RSSI em dBm) em um rótulo qualitativo.
     * 
     * @param rssiDbm força do sinal em dBm
     * @return classificação qualitativa (Excelente, Bom, Regular, Fraco, Muito fraco)
     */
    public static String qualidadeSinal(int rssiDbm) {
        String qualidade;
        if (rssiDbm >= -50) {
            qualidade = "Excelente";
        } else if (rssiDbm >= -60) {
            qualidade = "Bom";
        } else if (rssiDbm >= -70) {
            qualidade = "Regular";
        } else if (rssiDbm >= -80) {
            qualidade = "Fraco";
        } else {
            qualidade = "Muito fraco";
        }
        
        Timber.v("RSSI %d dBm -> Qualidade: %s", rssiDbm, qualidade);
        return qualidade;
    }
}