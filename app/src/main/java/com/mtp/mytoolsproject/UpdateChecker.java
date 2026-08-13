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
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Verifica atualizações combinando duas escolhas independentes:
 *
 * CANAL:
 * - OFICIAL: branch main / releases não marcadas como pré-lançamento
 * - BETA: branch develop / releases marcadas como "pre-release" no GitHub
 *
 * TIPO:
 * - RELEASE: usa a API de Releases (pode ter um .apk anexado para instalação direta)
 * - COMMIT: usa o último commit da branch (sempre só código-fonte, nunca instalável direto)
 *
 * IMPORTANTE: só funciona com o repositório PÚBLICO — sem token embutido no app
 * (evita expor credenciais num APK que pode ser descompilado).
 */
public final class UpdateChecker {

    public enum Canal { OFICIAL, BETA }
    public enum Tipo { RELEASE, COMMIT }

    private static final String OWNER = "cristianmotadev";
    private static final String REPO = "MyToolkit";
    private static final String URL_RELEASES_LISTA = "https://api.github.com/repos/" + OWNER + "/" + REPO + "/releases?per_page=15";
    
    // Cache em memória com timestamp
    private static class CacheEntry<T> {
        T data;
        long timestamp;
        
        CacheEntry(T data, long timestamp) {
            this.data = data;
            this.timestamp = timestamp;
        }
        
        boolean isExpired(long maxAgeMs) {
            return System.currentTimeMillis() - timestamp > maxAgeMs;
        }
    }
    
    private static final java.util.Map<String, CacheEntry<ResultadoVerificacao>> cacheVerificacoes = new java.util.concurrent.ConcurrentHashMap<>();
    private static final long CACHE_VALIDITY_MS = 5 * 60 * 1000; // 5 minutos

    private UpdateChecker() {}

    public static class ResultadoVerificacao {
        public boolean temAtualizacao;
        public Canal canal;
        public Tipo tipo;
        public String versaoAtual;
        public String versaoDisponivel;
        public String titulo;
        public String changelog;
        public String autor;
        public String dataFormatada;
        public String urlPagina;
        public String urlApk;
        public String nomeArquivoApk;
        public String mensagemErro;

        public String tituloDialogo() {
            if (canal == Canal.BETA && tipo == Tipo.COMMIT) return "🧪 Novo commit na develop";
            if (canal == Canal.BETA) return "🧪 Nova versão Beta disponível";
            return "🎉 Nova atualização disponível";
        }
    }

    public static ResultadoVerificacao verificar(Context context, Canal canal, Tipo tipo) {
        return verificar(context, canal, tipo, false);
    }
    
    /**
     * Verifica atualizações com opção de forçar atualização do cache.
     * @param context Contexto da aplicação
     * @param canal Canal (OFICIAL ou BETA)
     * @param tipo Tipo (RELEASE ou COMMIT)
     * @param forcarAtualizacao true para ignorar cache e buscar nova informação
     * @return Resultado da verificação
     */
    public static ResultadoVerificacao verificar(Context context, Canal canal, Tipo tipo, boolean forcarAtualizacao) {
        // Gera chave única para o cache
        String cacheKey = canal.name() + "_" + tipo.name();
        
        // Verifica cache se não for forçado
        if (!forcarAtualizacao) {
            CacheEntry<ResultadoVerificacao> entry = cacheVerificacoes.get(cacheKey);
            if (entry != null && !entry.isExpired(CACHE_VALIDITY_MS)) {
                timber.log.Timber.v("Retornando resultado em cache para %s", cacheKey);
                return entry.data;
            }
        }
        
        timber.log.Timber.d("Verificando atualizações: canal=%s, tipo=%s (cache=%s)", 
            canal, tipo, forcarAtualizacao ? "forçado" : "normal");
        
        ResultadoVerificacao resultado = tipo == Tipo.RELEASE ? verificarRelease(context, canal) : verificarCommit(context, canal);
        
        // Armazena no cache (mesmo com erro, para evitar chamadas repetidas em caso de falha temporária)
        cacheVerificacoes.put(cacheKey, new CacheEntry<>(resultado, System.currentTimeMillis()));
        
        return resultado;
    }
    
    /**
     * Limpa o cache de verificações de atualização.
     * Use quando o usuário mudar manualmente as configurações de canal/tipo.
     */
    public static void limparCache() {
        cacheVerificacoes.clear();
        timber.log.Timber.d("Cache de atualizações limpo");
    }

    private static ResultadoVerificacao verificarRelease(Context context, Canal canal) {
        ResultadoVerificacao resultado = new ResultadoVerificacao();
        resultado.canal = canal;
        resultado.tipo = Tipo.RELEASE;
        resultado.versaoAtual = obterVersaoInstalada(context);

        HttpURLConnection conn = null;
        try {
            conn = abrirConexao(URL_RELEASES_LISTA);
            int codigo = conn.getResponseCode();
            if (codigo != 200) {
                resultado.mensagemErro = codigo == 404
                        ? "Repositório não encontrado (ou privado)."
                        : "GitHub respondeu com código " + codigo + ".";
                return resultado;
            }

            JSONArray releases = new JSONArray(lerCorpo(conn));
            JSONObject escolhida = null;
            boolean querPrerelease = canal == Canal.BETA;

            for (int i = 0; i < releases.length(); i++) {
                JSONObject r = releases.getJSONObject(i);
                if (r.optBoolean("prerelease", false) == querPrerelease && !r.optBoolean("draft", false)) {
                    escolhida = r;
                    break;
                }
            }

            if (escolhida == null) {
                resultado.mensagemErro = querPrerelease
                        ? "Nenhuma Release Beta (pre-release) publicada ainda."
                        : "Nenhuma Release Oficial publicada ainda.";
                return resultado;
            }

            String tagName = escolhida.optString("tag_name", "");
            String versaoDisponivel = tagName.startsWith("v") ? tagName.substring(1) : tagName;

            resultado.versaoDisponivel = versaoDisponivel;
            resultado.titulo = escolhida.optString("name", tagName).trim();
            if (resultado.titulo.isEmpty()) resultado.titulo = tagName;
            resultado.changelog = escolhida.optString("body", "").trim();
            resultado.autor = escolhida.optJSONObject("author") != null
                    ? escolhida.optJSONObject("author").optString("login", "desconhecido") : "desconhecido";
            resultado.dataFormatada = formatarDataIso(escolhida.optString("published_at", ""));
            resultado.urlPagina = escolhida.optString("html_url", "https://github.com/" + OWNER + "/" + REPO + "/releases");
            resultado.temAtualizacao = ehVersaoMaisNova(versaoDisponivel, resultado.versaoAtual);

            JSONArray assets = escolhida.optJSONArray("assets");
            if (assets != null) {
                for (int i = 0; i < assets.length(); i++) {
                    JSONObject asset = assets.getJSONObject(i);
                    String nome = asset.optString("name", "");
                    if (nome.toLowerCase(Locale.ROOT).endsWith(".apk")) {
                        resultado.urlApk = asset.optString("browser_download_url", null);
                        resultado.nomeArquivoApk = nome;
                        break;
                    }
                }
            }

        } catch (Exception e) {
            resultado.mensagemErro = "Erro ao verificar: " + e.getMessage();
        } finally {
            if (conn != null) conn.disconnect();
        }

        return resultado;
    }

    private static ResultadoVerificacao verificarCommit(Context context, Canal canal) {
        ResultadoVerificacao resultado = new ResultadoVerificacao();
        resultado.canal = canal;
        resultado.tipo = Tipo.COMMIT;
        resultado.versaoAtual = obterVersaoInstalada(context);

        String branch = canal == Canal.BETA ? "develop" : "main";
        String url = "https://api.github.com/repos/" + OWNER + "/" + REPO + "/commits/" + branch;

        HttpURLConnection conn = null;
        try {
            conn = abrirConexao(url);
            int codigo = conn.getResponseCode();
            if (codigo != 200) {
                resultado.mensagemErro = codigo == 404
                        ? "Branch \"" + branch + "\" não encontrada (ou repositório privado)."
                        : "GitHub respondeu com código " + codigo + ".";
                return resultado;
            }

            JSONObject json = new JSONObject(lerCorpo(conn));
            JSONObject commitInfo = json.optJSONObject("commit");
            
            // Extrai metadata do commit
            String shaCompleto = json.optString("sha", "");
            String shaCurto = shaCompleto.length() >= 7 ? shaCompleto.substring(0, 7) : shaCompleto;
            
            String mensagemCompleta = "";
            String autor = "desconhecido";
            String dataIso = "";
            
            if (commitInfo != null) {
                mensagemCompleta = commitInfo.optString("message", "");
                JSONObject autorInfo = commitInfo.optJSONObject("author");
                if (autorInfo != null) {
                    autor = autorInfo.optString("name", "desconhecido");
                    dataIso = autorInfo.optString("date", "");
                }
            }

            // Pega o hash do primeiro parent (commit anterior)
            JSONArray parents = json.optJSONArray("parents");
            String parentSha = null;
            if (parents != null && parents.length() > 0) {
                parentSha = parents.optJSONObject(0).optString("sha", null);
            }

            resultado.versaoDisponivel = shaCurto;
            resultado.titulo = "Commit " + shaCurto + " (" + branch + ")";
            resultado.changelog = mensagemCompleta;
            resultado.autor = autor;
            resultado.dataFormatada = formatarDataIso(dataIso);
            resultado.urlPagina = "https://github.com/" + OWNER + "/" + REPO + "/commit/" + shaCompleto;
            resultado.urlApk = null;

            // Determina se há atualização comparando:
            // 1. Se temos o SHA anterior salvo e é diferente do atual
            // 2. OU se há um parent commit (indicando que este é mais novo que o anterior)
            SharedPreferences prefs = context.getSharedPreferences("NetworkPrefs", Context.MODE_PRIVATE);
            String chavePrefs = "commit_ultimo_sha_" + branch;
            String shaConhecido = prefs.getString(chavePrefs, null);
            
            // Considera atualização se:
            // - É o primeiro check (shaConhecido == null) E há parent commit
            // - OU o SHA mudou desde o último check
            if (shaConhecido == null) {
                // Primeira verificação - assume que precisa atualizar se houver histórico
                resultado.temAtualizacao = parentSha != null;
                timber.log.Timber.d("Primeira verificacao de commit na branch %s. Parent: %s, Tem atualizacao: %b", 
                    branch, parentSha, resultado.temAtualizacao);
            } else {
                resultado.temAtualizacao = !shaConhecido.equals(shaCompleto);
                timber.log.Timber.d("Comparacao de commit: conhecido=%s, atual=%s, temAtualizacao=%b", 
                    shaConhecido, shaCompleto, resultado.temAtualizacao);
            }
            
            // Atualiza o SHA conhecido no cache
            prefs.edit().putString(chavePrefs, shaCompleto).apply();

        } catch (Exception e) {
            timber.log.Timber.e(e, "Erro ao verificar commit na branch");
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

    private static String lerCorpo(HttpURLConnection conn) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder resposta = new StringBuilder();
        String linha;
        while ((linha = reader.readLine()) != null) resposta.append(linha);
        reader.close();
        return resposta.toString();
    }

    private static String formatarDataIso(String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) return "data desconhecida";
        try {
            SimpleDateFormat entrada = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
            entrada.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            Date data = entrada.parse(isoDate);
            SimpleDateFormat saida = new SimpleDateFormat("dd/MM/yyyy 'às' HH:mm", Locale.getDefault());
            return saida.format(data);
        } catch (Exception e) {
            return isoDate;
        }
    }

    private static String obterVersaoInstalada(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            return info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "0.0";
        }
    }

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
