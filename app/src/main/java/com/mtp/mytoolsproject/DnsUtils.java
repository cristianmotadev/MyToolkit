package com.mtp.mytoolsproject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Consulta registros DNS usando a API pública DNS-over-HTTPS do Google
 * (dns.google/resolve) — não precisa de chave nem autenticação, e permite
 * consultar tipos de registro que a API padrão do Android (InetAddress)
 * não expõe, como MX, TXT e NS.
 */
public final class DnsUtils {

    private DnsUtils() {}

    /** Consulta um tipo de registro específico (A, AAAA, MX, TXT, NS, CNAME...). */
    public static List<String> consultarRegistro(String dominio, String tipo) {
        List<String> resultados = new ArrayList<>();
        HttpURLConnection conn = null;
        try {
            String urlStr = "https://dns.google/resolve?name=" + URLEncoder.encode(dominio, "UTF-8") + "&type=" + tipo;
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() != 200) return resultados;

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder resposta = new StringBuilder();
            String linha;
            while ((linha = reader.readLine()) != null) resposta.append(linha);
            reader.close();

            JSONObject json = new JSONObject(resposta.toString());
            JSONArray answers = json.optJSONArray("Answer");
            if (answers != null) {
                for (int i = 0; i < answers.length(); i++) {
                    String data = answers.getJSONObject(i).optString("data", "");
                    if (!data.isEmpty()) resultados.add(data);
                }
            }
        } catch (Exception ignored) {
        } finally {
            if (conn != null) conn.disconnect();
        }
        return resultados;
    }

    /** Resolução reversa: IP -> hostname, usando a resolução padrão do sistema. */
    public static String consultarReverso(String ip) {
        try {
            java.net.InetAddress endereco = java.net.InetAddress.getByName(ip);
            String hostname = endereco.getCanonicalHostName();
            return hostname.equals(ip) ? null : hostname;
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean pareceIp(String texto) {
        return texto.matches("^\\d{1,3}(\\.\\d{1,3}){3}$");
    }
}
