package com.mtp.mytoolsproject;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Scanner de porta customizável: informa um IP e uma faixa de portas, e testa
 * cada uma em paralelo (pool de threads) — diferente do scanner de rede, que
 * só testa 3 portas fixas (80/443/554) em cada dispositivo encontrado.
 */
public class PortScannerActivity extends AppCompatActivity {

    private static final int MAX_PORTAS_POR_VARREDURA = 3000;
    private static final int TAMANHO_POOL_THREADS = 60;

    private EditText editIp, editPortaInicio, editPortaFim;
    private Button btnEscanear;
    private ProgressBar progressBar;
    private TextView txtProgresso, txtAviso;
    private LinearLayout containerResultados;

    private volatile boolean varreduraEmAndamento = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_port_scanner);

        editIp = findViewById(R.id.editPortScannerIp);
        editPortaInicio = findViewById(R.id.editPortaInicio);
        editPortaFim = findViewById(R.id.editPortaFim);
        btnEscanear = findViewById(R.id.btnEscanearPortas);
        progressBar = findViewById(R.id.progressBarPortas);
        txtProgresso = findViewById(R.id.txtProgressoPortas);
        txtAviso = findViewById(R.id.txtAvisoPortas);
        containerResultados = findViewById(R.id.containerResultadosPortas);

        String prefixoRede = NetworkUtils.descobrirPrefixoRedeLocal();
        editIp.setText(prefixoRede + "1");

        btnEscanear.setOnClickListener(v -> iniciarVarredura());
    }

    private void iniciarVarredura() {
        if (varreduraEmAndamento) {
            Toast.makeText(this, "Já existe uma varredura em andamento.", Toast.LENGTH_SHORT).show();
            return;
        }

        String ip = editIp.getText().toString().trim();
        int portaInicio, portaFim;
        try {
            portaInicio = Integer.parseInt(editPortaInicio.getText().toString().trim());
            portaFim = Integer.parseInt(editPortaFim.getText().toString().trim());
        } catch (Exception e) {
            Toast.makeText(this, "Portas inválidas.", Toast.LENGTH_SHORT).show();
            return;
        }

        if (portaInicio < 1 || portaFim > 65535 || portaInicio > portaFim) {
            Toast.makeText(this, "Faixa de portas inválida (1-65535).", Toast.LENGTH_SHORT).show();
            return;
        }

        int totalPortas = portaFim - portaInicio + 1;
        if (totalPortas > MAX_PORTAS_POR_VARREDURA) {
            txtAviso.setText("⚠️ Faixa grande demais (" + totalPortas + " portas). Máximo permitido: "
                    + MAX_PORTAS_POR_VARREDURA + " por varredura, para não sobrecarregar o aparelho e a rede.");
            return;
        }
        txtAviso.setText("");

        containerResultados.removeAllViews();
        varreduraEmAndamento = true;
        btnEscanear.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        progressBar.setMax(totalPortas);
        progressBar.setProgress(0);

        final int inicio = portaInicio, fim = portaFim;
        new Thread(() -> executarVarreduraParalela(ip, inicio, fim)).start();
    }

    private void executarVarreduraParalela(String ip, int portaInicio, int portaFim) {
        int totalPortas = portaFim - portaInicio + 1;
        ExecutorService pool = Executors.newFixedThreadPool(TAMANHO_POOL_THREADS);
        CountDownLatch latch = new CountDownLatch(totalPortas);
        AtomicInteger concluidas = new AtomicInteger(0);
        List<Integer> portasAbertas = new CopyOnWriteArrayList<>();

        for (int porta = portaInicio; porta <= portaFim; porta++) {
            final int portaAtual = porta;
            pool.execute(() -> {
                try {
                    if (NetworkUtils.verificarPortaAberta(ip, portaAtual)) {
                        portasAbertas.add(portaAtual);
                    }
                } finally {
                    int feitas = concluidas.incrementAndGet();
                    runOnUiThread(() -> {
                        progressBar.setProgress(feitas);
                        txtProgresso.setText("Verificando... " + feitas + "/" + totalPortas);
                    });
                    latch.countDown();
                }
            });
        }

        try {
            latch.await();
        } catch (InterruptedException ignored) {}
        pool.shutdown();

        Collections.sort(portasAbertas);

        runOnUiThread(() -> {
            varreduraEmAndamento = false;
            btnEscanear.setEnabled(true);
            progressBar.setVisibility(View.GONE);
            txtProgresso.setText("Varredura concluída — " + portasAbertas.size() + " porta(s) aberta(s) de " + totalPortas + " testadas.");

            if (portasAbertas.isEmpty()) {
                adicionarResultado("Nenhuma porta aberta encontrada nessa faixa.", false);
            } else {
                for (int porta : portasAbertas) {
                    adicionarResultado("🟢 Porta " + porta + " aberta — " + nomeServicoConhecido(porta), true);
                }
            }
        });
    }

    private void adicionarResultado(String texto, boolean aberta) {
        TextView tv = new TextView(this);
        tv.setText(texto);
        tv.setTextColor(aberta ? 0xFF4CAF50 : 0xFFAAAAAA);
        tv.setTextSize(14f);
        tv.setPadding(4, 10, 4, 10);
        containerResultados.addView(tv);
    }

    private String nomeServicoConhecido(int porta) {
        switch (porta) {
            case 21: return "FTP";
            case 22: return "SSH";
            case 23: return "Telnet";
            case 25: return "SMTP";
            case 53: return "DNS";
            case 80: return "HTTP";
            case 110: return "POP3";
            case 143: return "IMAP";
            case 443: return "HTTPS";
            case 554: return "RTSP (câmeras)";
            case 3306: return "MySQL";
            case 3389: return "RDP";
            case 8080: return "HTTP alternativo";
            default: return "serviço não identificado";
        }
    }
}
