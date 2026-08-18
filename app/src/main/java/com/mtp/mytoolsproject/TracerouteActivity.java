package com.mtp.mytoolsproject;

import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Traceroute manual: já que o binário "traceroute" nem sempre existe em ROMs
 * Android (mesmo com root), a técnica usada aqui é a clássica — enviar pings
 * com TTL (Time To Live) crescente. Cada roteador no caminho descarta o pacote
 * quando o TTL chega a zero e responde com "Time to live exceeded", revelando
 * o IP daquele salto. Quando o TTL é alto o bastante pra chegar ao destino,
 * a resposta normal do ping aparece e o traceroute termina.
 */
public class TracerouteActivity extends AppCompatActivity {

    private static final int MAX_SALTOS = 30;

    private EditText editDestino;
    private Button btnIniciar;
    private TextView txtLog;
    private volatile boolean cancelado = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_traceroute);

        editDestino = findViewById(R.id.editTracerouteDestino);
        btnIniciar = findViewById(R.id.btnIniciarTraceroute);
        txtLog = findViewById(R.id.txtTracerouteLog);
        txtLog.setMovementMethod(new ScrollingMovementMethod());

        editDestino.setText("8.8.8.8");

        btnIniciar.setOnClickListener(v -> iniciarTraceroute());
    }

    private void iniciarTraceroute() {
        String destino = editDestino.getText().toString().trim();
        if (destino.isEmpty()) return;

        cancelado = false;
        txtLog.setText("");
        btnIniciar.setEnabled(false);
        log("🎯 Rastreando caminho até " + destino + " (máx. " + MAX_SALTOS + " saltos)...\n");

        new Thread(() -> {
            String ipResolvido = null;
            try {
                ipResolvido = java.net.InetAddress.getByName(destino).getHostAddress();
            } catch (Exception ignored) {}
            final String ipFinal = ipResolvido;

            for (int ttl = 1; ttl <= MAX_SALTOS && !cancelado; ttl++) {
                String resultadoSalto = executarPingComTtl(destino, ttl);
                final int saltoAtual = ttl;
                runOnUiThread(() -> log("Salto " + saltoAtual + ": " + resultadoSalto));

                if (ipFinal != null && resultadoSalto.contains(ipFinal) && resultadoSalto.contains("respondeu")) {
                    runOnUiThread(() -> log("\n✅ Destino alcançado em " + saltoAtual + " saltos."));
                    break;
                }
            }

            runOnUiThread(() -> btnIniciar.setEnabled(true));
        }).start();
    }

    /** Envia um ping com TTL específico via root e interpreta a resposta. */
    private String executarPingComTtl(String destino, int ttl) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "ping -c 1 -W 2 -t " + ttl + " " + destino});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder saida = new StringBuilder();
            String linha;
            while ((linha = reader.readLine()) != null) saida.append(linha).append(" ");
            process.waitFor();

            String texto = saida.toString();

            Matcher origemIntermediaria = Pattern.compile("[Ff]rom ([0-9.]+)").matcher(texto);
            Matcher destinoAlcancado = Pattern.compile("bytes from ([0-9.]+).*?time[=<]([0-9.]+)").matcher(texto);

            if (destinoAlcancado.find()) {
                return destinoAlcancado.group(1) + " — respondeu em " + destinoAlcancado.group(2) + " ms";
            } else if (origemIntermediaria.find()) {
                return origemIntermediaria.group(1) + " (roteador intermediário)";
            } else {
                return "* (sem resposta / tempo esgotado)";
            }
        } catch (Exception e) {
            return "❌ erro: " + e.getMessage();
        }
    }

    private void log(String mensagem) {
        txtLog.append(mensagem + "\n");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cancelado = true;
    }
}
