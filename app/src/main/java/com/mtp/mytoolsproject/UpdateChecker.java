package com.mtp.mytoolsproject;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Verifica atualizações em dois canais:
 *
 * - OFICIAL: compara a versão instalada com a última Release publicada no
 *   GitHub (tag da branch main). Funciona via API de Releases.
 *
 * - BETA: como a branch develop não tem "versões" formais, rastreia o
 *   ÚLTIMO COMMIT dela. Guarda o SHA do último commit já visto nas
 *   preferências; se um novo commit aparecer, considera que há atualização
 *   beta disponível.
 *
 * IMPORTANTE: só funciona com o repositório PÚBLICO — a API do GitHub não
 * expõe dados de repositórios privados sem autenticação, e este app
 * propositalmente não embute nenhum token (isso seria um risco de segurança,
 * já que o APK pode ser descompilado e o token extraído).
 */
public final class UpdateChecker {

    public enum Canal { OFICIAL, BETA }

    // Repositório configurado: github.com/cristianmotadev/MyToolkit
    private static final String OWNER = "cristianmotadev";
    private static final String REPO = "MyToolkit";
    private static final String URL_RELEASE_LATEST = "https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases/latest";
    private static final String URL_COMMIT_DEVELOP = "https://api.github.com/repos/" + OWNER + "/" + REPO + "/commits/develop";
    private static final String CHAVE_ULTIMO_SHA_BETA = "beta_ultimo_sha_conhecido";

    private UpdateChecker() {}

    public static class ResultadoVerificacao {
        public boolean temAtualizacao;
        public Canal canal;
        public String versaoAtual;      // versão instalada (só relevante no canal Oficial)
        public String versaoDisponivel; // tag (Oficial) ou SHA curto do commit (Beta)
        public String mensagemExtra;    // mensagem do commit, só no canal Beta
        public String urlDaRelease;
        public String mensagemErro;     // null se não houve erro
    }

    /** Chamada bloqueante — sempre execute em uma thread separada da UI. */
    public static ResultadoVerificacao verificar(Context context, Canal canal) {
        return canal == Canal.BETA ? verificarBeta(context) : verificarOficial(context);
    }

    private static ResultadoVerificacao verificarOficial(Context context) {
        ResultadoVerificacao resultado = new ResultadoVerificacao();
        resultado.canal = Canal.OFICIAL;
        resultado.versaoAtual = obterVersaoInstalada(context);

        HttpURLConnection conn = null;
        try {
            conn = abrirConexao(URL_RELEASE_LATEST);
            int codigo = conn.getResponseCode();
            if (codigo == 404) {
                resultado.mensagemErro = "Nenhuma Release encontrada. O repositório pode estar privado, ou ainda não existe nenhuma Release publicada.";
                return resultado;
            }
            if (codigo != 200) {
                resultado.mensagemErro = "GitHub respondeu com código " + codigo + ".";
                return resultado;
            }

            org.json.JSONObject json = lerJson(conn);
            String tagName = json.optString("tag_name", "");
            String versaoDisponivel = tagName.startsWith("v") ? tagName.substring(1) : tagName;

            resultado.versaoDisponivel = versaoDisponivel;
            resultado.urlDaRelease = json.optString("html_url", "https://github.com/" + OWNER + "/" + REPO + "/releases");
            resultado.temAtualizacao = ehVersaoMaisNova(versaoDisponivel, resultado.versaoAtual);

        } catch (Exception e) {
            resultado.mensagemErro = "Erro ao verificar: " + e.getMessage();
        } finally {
            if (conn != null) conn.disconnect();
        }

        return resultado;
    }

    private static ResultadoVerificacao verificarBeta(Context context) {
        ResultadoVerificacao resultado = new ResultadoVerificacao();
        resultado.canal = Canal.BETA;
        resultado.versaoAtual = obterVersaoInstalada(context);

        SharedPreferences prefs = context.getSharedPreferences("NetworkPrefs", Context.MODE_PRIVATE);
        String shaConhecido = prefs.getString(CHAVE_ULTIMO_SHA_BETA, null);

        HttpURLConnection conn = null;
        try {
            conn = abrirConexao(URL_COMMIT_DEVELOP);
            int codigo = conn.getResponseCode();
            if (codigo == 404) {
                resultado.mensagemErro = "Branch \"develop\" não encontrada (ou repositório privado).";
                return resultado;
            }
            if (codigo != 200) {
                resultado.mensagemErro = "GitHub respondeu com código " + codigo + ".";
                return resultado;
            }

            org.json.JSONObject json = lerJson(conn);
            String shaCompleto = json.optString("sha", "");
            String shaCurto = shaCompleto.length() >= 7 ? shaCompleto.substring(0, 7) : shaCompleto;

            org.json.JSONObject commitInfo = json.optJSONObject("commit");
            String mensagemCommit = commitInfo != null ? commitInfo.optString("message", "") : "";
            if (mensagemCommit.contains("\n")) {
                mensagemCommit = mensagemCommit.substring(0, mensagemCommit.indexOf("\n"));
            }

            resultado.versaoDisponivel = shaCurto;
            resultado.mensagemExtra = mensagemCommit;
            resultado.urlDaRelease = "https://github.com/" + OWNER + "/" + REPO + "/commit/" + shaCompleto;

            // Só avisa se já existia um SHA salvo anteriormente E ele mudou —
            // na primeiríssima verificação, apenas registra a base, sem alarme falso.
            resultado.temAtualizacao = shaConhecido != null && !shaConhecido.equals(shaCompleto);

            prefs.edit().putString(CHAVE_ULTIMO_SHA_BETA, shaCompleto).apply();

        } catch (Exception e) {
            resultado.mensagemErro = "Erro ao verificar: " + e.getMessage();
        } finally {
            if (conn != null) conn.disconnect();
        }

        return resultado;
    }

    private static HttpURLConnection abrirConexao(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(6000);
        conn.setReadTimeout(6000);
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        return conn;
    }

    private static org.json.JSONObject lerJson(HttpURLConnection conn) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder resposta = new StringBuilder();
        String linha;
        while ((linha = reader.readLine()) != null) resposta.append(linha);
        reader.close();
        return new org.json.JSONObject(resposta.toString());
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
