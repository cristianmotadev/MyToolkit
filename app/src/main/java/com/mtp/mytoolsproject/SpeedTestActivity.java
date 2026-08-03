package com.mtp.mytoolsproject;

import android.os.Bundle;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.SecureRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Teste de velocidade: ping (latência via root, com fallback TCP), download e
 * upload (usando o endpoint público de teste da Cloudflare, sem necessidade
 * de conta/chave de API).
 */
public class SpeedTestActivity extends AppCompatActivity {

    private static final String URL_DOWNLOAD = "https://speed.cloudflare.com/__down?bytes=20000000"; // 20MB
    private static final String URL_UPLOAD = "https://speed.cloudflare.com/__up";
    private static final long DURACAO_MAXIMA_TESTE_MS = 12000; // não deixa o teste travar em conexões muito lentas

    private Button btnIniciar;
    private ProgressBar progressBar;
    private TextView txtPing, txtDownload, txtUpload, txtStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_speed_test);

        btnIniciar = findViewById(R.id.btnIniciarSpeedTest);
        progressBar = findViewById(R.id.progressBarSpeedTest);
        txtPing = findViewById(R.id.txtResultadoPing);
        txtDownload = findViewById(R.id.txtResultadoDownload);
        txtUpload = findViewById(R.id.txtResultadoUpload);
        txtStatus = findViewById(R.id.txtStatusSpeedTest);

        btnIniciar.setOnClickListener(v -> iniciarTeste());
    }

    private void iniciarTeste() {
        btnIniciar.setEnabled(false);
        progressBar.setVisibility(android.view.View.VISIBLE);
        txtPing.setText("Ping: medindo...");
        txtDownload.setText("Download: aguardando...");
        txtUpload.setText("Upload: aguardando...");

        new Thread(() -> {
            long ping = medirPing();
            atualizarTexto(txtPing, ping >= 0 ? "📶 Ping: " + ping + " ms" : "📶 Ping: falhou");

            atualizarStatus("Testando download...");
            double mbpsDownload = medirDownload();
            atualizarTexto(txtDownload, mbpsDownload >= 0
                    ? String.format("⬇️ Download: %.2f Mbps", mbpsDownload) : "⬇️ Download: falhou");

            atualizarStatus("Testando upload...");
            double mbpsUpload = medirUpload();
            atualizarTexto(txtUpload, mbpsUpload >= 0
                    ? String.format("⬆️ Upload: %.2f Mbps", mbpsUpload) : "⬆️ Upload: falhou");

            atualizarStatus("Teste concluído.");
            runOnUiThread(() -> {
                btnIniciar.setEnabled(true);
                progressBar.setVisibility(android.view.View.GONE);
            });
        }).start();
    }

    /** Tenta medir latência real via "ping" do sistema (root); cai para TCP connect se falhar. */
    private long medirPing() {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "ping -c 4 8.8.8.8"});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String linha;
            StringBuilder saida = new StringBuilder();
            while ((linha = reader.readLine()) != null) saida.append(linha).append("\n");
            process.waitFor();

            Matcher m = Pattern.compile("=\\s*[\\d.]+/([\\d.]+)/").matcher(saida.toString());
            if (m.find()) {
                return Math.round(Double.parseDouble(m.group(1)));
            }
        } catch (Exception ignored) {}

        // Fallback: mede o tempo de handshake TCP até um servidor conhecido
        try {
            long inicio = System.currentTimeMillis();
            java.net.Socket socket = new java.net.Socket();
            socket.connect(new java.net.InetSocketAddress("8.8.8.8", 53), 3000);
            socket.close();
            return System.currentTimeMillis() - inicio;
        } catch (Exception e) {
            return -1;
        }
    }

    private double medirDownload() {
        HttpURLConnection conn = null;
        try {
            URL url = new URL(URL_DOWNLOAD);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            conn.connect();

            InputStream is = conn.getInputStream();
            byte[] buffer = new byte[65536];
            long totalBytes = 0;
            long inicio = System.currentTimeMillis();
            int lidos;
            while ((lidos = is.read(buffer)) != -1) {
                totalBytes += lidos;
                if (System.currentTimeMillis() - inicio > DURACAO_MAXIMA_TESTE_MS) break;
            }
            long duracaoMs = System.currentTimeMillis() - inicio;
            is.close();

            if (duracaoMs <= 0 || totalBytes == 0) return -1;
            double segundos = duracaoMs / 1000.0;
            return (totalBytes * 8.0) / segundos / 1_000_000.0; // Mbps
        } catch (Exception e) {
            return -1;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private double medirUpload() {
        HttpURLConnection conn = null;
        try {
            byte[] dadosAleatorios = new byte[5_000_000]; // 5MB
            new SecureRandom().nextBytes(dadosAleatorios);

            URL url = new URL(URL_UPLOAD);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(8000);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            conn.setFixedLengthStreamingMode(dadosAleatorios.length);

            long inicio = System.currentTimeMillis();
            OutputStream os = conn.getOutputStream();
            os.write(dadosAleatorios);
            os.flush();
            os.close();

            int codigo = conn.getResponseCode(); // força aguardar a resposta completa do servidor
            long duracaoMs = System.currentTimeMillis() - inicio;

            if (codigo < 200 || codigo >= 300 || duracaoMs <= 0) return -1;
            double segundos = duracaoMs / 1000.0;
            return (dadosAleatorios.length * 8.0) / segundos / 1_000_000.0; // Mbps
        } catch (Exception e) {
            return -1;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void atualizarTexto(TextView view, String texto) {
        runOnUiThread(() -> view.setText(texto));
    }

    private void atualizarStatus(String texto) {
        runOnUiThread(() -> txtStatus.setText(texto));
    }
}
