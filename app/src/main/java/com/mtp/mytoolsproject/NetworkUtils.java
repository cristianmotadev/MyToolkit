package com.mtp.mytoolsproject;

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

/**
 * Utilitários de rede compartilhados entre NetworkScannerActivity,
 * NetworkMonitorService e ManageDevicesActivity.
 *
 * Antes essa lógica (verificação de portas, consulta de fabricante,
 * leitura/escrita do cache) estava duplicada em 3 arquivos diferentes.
 * Agora fica em um único lugar: corrige um bug em um lugar corrige em todos.
 */
public final class NetworkUtils {

    private static final String CACHE_FILE_NAME = "mac_cache.json";

    private NetworkUtils() {
        // classe utilitária, não instanciável
    }

    // ---------------------------------------------------------------
    // Descoberta de sub-rede (resolve o problema do IP fixo 192.168.1.x)
    // ---------------------------------------------------------------

    /**
     * Descobre o prefixo da sub-rede local (ex: "192.168.15.") a partir
     * do IP real do aparelho na interface Wi-Fi/rede ativa.
     * Antes o código tinha "192.168.1." fixo no código, o que só
     * funcionava em roteadores configurados nessa faixa específica.
     * Se não conseguir detectar, cai de volta para "192.168.1." como
     * antes, para não quebrar o comportamento em caso de erro.
     */
    public static String descobrirPrefixoRedeLocal() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp() || iface.isLoopback()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr.isLoopbackAddress() || !(addr instanceof Inet4Address)) continue;

                    String ip = addr.getHostAddress();
                    if (ip == null) continue;

                    boolean pareceRedeLocal = ip.startsWith("192.168.")
                            || ip.startsWith("10.")
                            || ip.startsWith("172.");

                    if (pareceRedeLocal) {
                        int lastDot = ip.lastIndexOf('.');
                        if (lastDot > 0) {
                            return ip.substring(0, lastDot + 1);
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        return "192.168.1."; // fallback: mantém comportamento antigo se não detectar
    }

    // ---------------------------------------------------------------
    // Nome da rede Wi-Fi atual
    // ---------------------------------------------------------------

    public static String obterNomeRedeWifi(Context context) {
        try {
            WifiManager wifiManager = (WifiManager)
                    context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wifiManager != null) {
                WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                if (wifiInfo != null) {
                    String ssid = wifiInfo.getSSID();
                    if (ssid != null && !ssid.equals("<unknown ssid>") && !ssid.isEmpty()) {
                        return ssid.replace("\"", "");
                    }
                }
            }
        } catch (Exception ignored) {}
        return "Rede Desconhecida";
    }

    // ---------------------------------------------------------------
    // Verificação de portas
    // IMPORTANTE: são chamadas de rede bloqueantes — sempre executar
    // fora da thread principal (dentro de um Executor/Thread/Handler).
    // ---------------------------------------------------------------

    public static boolean verificarPortaAberta(String ip, int porta) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, porta), 300);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static String verificarPortasComuns(String ip) {
        StringBuilder portasAbertas = new StringBuilder();
        if (verificarPortaAberta(ip, 80)) portasAbertas.append("80 (HTTP) ");
        if (verificarPortaAberta(ip, 443)) portasAbertas.append("443 (HTTPS) ");
        if (verificarPortaAberta(ip, 554)) portasAbertas.append("554 (RTSP) ");
        return portasAbertas.length() > 0 ? portasAbertas.toString().trim() : "Nenhuma comum";
    }

    // ---------------------------------------------------------------
    // Consulta de fabricante por MAC
    // IMPORTANTE: pode fazer chamada HTTP bloqueante — sempre executar
    // fora da thread principal.
    // ---------------------------------------------------------------

    public static String consultarFabricante(String macAddress) {
        if (macAddress == null || macAddress.length() < 8) {
            return "NÃO ENCONTRADO";
        }
        String macUpper = macAddress.toUpperCase();
        String oui = macUpper.substring(0, 8);

        if (oui.startsWith("48:EE") || oui.startsWith("28:F0") || oui.startsWith("AC:84")) {
            return "TP-Link / Intelbras";
        } else if (oui.startsWith("EC:F4") || oui.startsWith("38:0A") || oui.startsWith("50:EC")) {
            return "Samsung";
        } else if (oui.startsWith("64:03") || oui.startsWith("AC:DE") || oui.startsWith("F8:FF")) {
            return "Apple";
        } else if (oui.startsWith("CC:2D") || oui.startsWith("E0:63") || oui.startsWith("98:FA")) {
            return "Xiaomi / Redmi";
        } else if (macUpper.charAt(1) == '2' || macUpper.charAt(1) == '6'
                || macUpper.charAt(1) == 'A' || macUpper.charAt(1) == 'E') {
            return "Dispositivo Privado / Aleatório";
        }

        try {
            URL url = new URL("https://api.macvendors.com/" + macAddress);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");

            if (conn.getResponseCode() == 200) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String fabricante = reader.readLine();
                reader.close();
                if (fabricante != null && !fabricante.trim().isEmpty()) {
                    return fabricante.trim();
                }
            }
        } catch (Exception ignored) {}

        return "Rede Local / Genérico";
    }

    // ---------------------------------------------------------------
    // Cache JSON (mac_cache.json) — leitura e escrita centralizadas
    // ---------------------------------------------------------------

    public static synchronized JSONObject carregarCache(Context context) {
        try {
            File file = new File(context.getFilesDir(), CACHE_FILE_NAME);
            if (!file.exists()) return new JSONObject();

            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[(int) file.length()];
            fis.read(buffer);
            fis.close();
            String content = new String(buffer, StandardCharsets.UTF_8);
            return content.startsWith("{") ? new JSONObject(content) : new JSONObject();
        } catch (Exception e) {
            e.printStackTrace();
            return new JSONObject();
        }
    }

    public static synchronized boolean salvarCache(Context context, JSONObject cache) {
        try {
            File file = new File(context.getFilesDir(), CACHE_FILE_NAME);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(cache.toString().getBytes(StandardCharsets.UTF_8));
            fos.close();
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // ---------------------------------------------------------------
    // Verificação de root
    // ---------------------------------------------------------------

    /**
     * Verifica de forma explícita se o binário "su" está disponível e concede acesso.
     * Evita que as ferramentas do app falhem silenciosamente (catch genérico) sem
     * o usuário entender o motivo — usado na tela de Configurações.
     */
    public static boolean verificarRootDisponivel() {
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()));
            String saida = reader.readLine();
            int codigoSaida = process.waitFor();
            return codigoSaida == 0 && saida != null && saida.contains("uid=0");
        } catch (Exception e) {
            return false;
        } finally {
            if (process != null) process.destroy();
        }
    }

    // ---------------------------------------------------------------
    // Formatação de tempo relativo (histórico de "última vez visto")
    // ---------------------------------------------------------------

    public static String formatarTempoRelativo(long timestampMillis) {
        if (timestampMillis <= 0) return "Nunca registrado";

        long diffMillis = System.currentTimeMillis() - timestampMillis;
        long segundos = diffMillis / 1000;
        long minutos = segundos / 60;
        long horas = minutos / 60;
        long dias = horas / 24;

        if (segundos < 60) return "Visto agora mesmo";
        if (minutos < 60) return "Visto há " + minutos + (minutos == 1 ? " minuto" : " minutos");
        if (horas < 24) return "Visto há " + horas + (horas == 1 ? " hora" : " horas");
        if (dias < 30) return "Visto há " + dias + (dias == 1 ? " dia" : " dias");

        java.text.SimpleDateFormat formato = new java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault());
        return "Visto em " + formato.format(new java.util.Date(timestampMillis));
    }
}
