package com.mtp.mytoolsproject;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Verifica se existe uma versão mais nova publicada como Release no GitHub
 * e compara com a versão instalada no aparelho.
 *
 * IMPORTANTE: só funciona com o repositório PÚBLICO — a API do GitHub não
 * expõe releases de repositórios privados sem autenticação, e este app
 * propositalmente não embute nenhum token (isso seria um risco de segurança,
 * já que o APK pode ser descompilado e o token extraído).
 */
public final class UpdateChecker {

    // Repositório configurado: github.com/cristianmotadev/MyToolkit
    private static final String OWNER = "cristianmotadev";
    private static final String REPO = "MyToolkit";
    private static final String URL_API = "https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases/latest";

    private UpdateChecker() {}

    public static class ResultadoVerificacao {
        public boolean temAtualizacao;
        public String versaoAtual;
        public String versaoDisponivel;
        public String urlDaRelease;
        public String mensagemErro; // null se não houve erro
    }

    /** Chamada bloqueante — sempre execute em uma thread separada da UI. */
    public static ResultadoVerificacao verificar(Context context) {
        ResultadoVerificacao resultado = new ResultadoVerificacao();
        resultado.versaoAtual = obterVersaoInstalada(context);

        HttpURLConnection conn = null;
        try {
            URL url = new URL(URL_API);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(6000);
            conn.setRequestProperty("Accept", "application/vnd.github+json");

            int codigo = conn.getResponseCode();
            if (codigo == 404) {
                resultado.mensagemErro = "Nenhuma Release encontrada. O repositório pode estar privado, ou ainda não existe nenhuma Release publicada.";
                return resultado;
            }
            if (codigo != 200) {
                resultado.mensagemErro = "GitHub respondeu com código " + codigo + ".";
                return resultado;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder resposta = new StringBuilder();
            String linha;
            while ((linha = reader.readLine()) != null) resposta.append(linha);
            reader.close();

            org.json.JSONObject json = new org.json.JSONObject(resposta.toString());
            String tagName = json.optString("tag_name", "");
            String versaoDisponivel = tagName.startsWith("v") ? tagName.substring(1) : tagName;
            String urlRelease = json.optString("html_url", "https://github.com/" + OWNER + "/" + REPO + "/releases");

            resultado.versaoDisponivel = versaoDisponivel;
            resultado.urlDaRelease = urlRelease;
            resultado.temAtualizacao = ehVersaoMaisNova(versaoDisponivel, resultado.versaoAtual);

        } catch (Exception e) {
            resultado.mensagemErro = "Erro ao verificar: " + e.getMessage();
        } finally {
            if (conn != null) conn.disconnect();
        }

        return resultado;
    }

    private static String obterVersaoInstalada(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "0.0";
        }
    }

    /** Compara duas versões no formato "1.2.3" numericamente, parte por parte. */
    private static boolean ehVersaoMaisNova(String versaoRemota, String versaoLocal) {
        try {
            String[] partesRemota = versaoRemota.split("\\.");
            String[] partesLocal = versaoLocal.split("\\.");
            int tamanho = Math.max(partesRemota.length, partesLocal.length);

            for (int i = 0; i < tamanho; i++) {
                int remoto = i < partesRemota.length ? Integer.parseInt(partesRemota[i].replaceAll("[^0-9]", "")) : 0;
                int local = i < partesLocal.length ? Integer.parseInt(partesLocal[i].replaceAll("[^0-9]", "")) : 0;
                if (remoto != local) return remoto > local;
            }
            return false;
        } catch (Exception e) {
            return !versaoRemota.equals(versaoLocal);
        }
    }
}
