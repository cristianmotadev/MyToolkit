package com.mtp.mytoolsproject;

import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

/**
 * Consulta DNS: resolve domínio -> IPs (A/AAAA) e outros registros (MX, TXT,
 * NS, CNAME) via API pública DoH do Google. Se o texto digitado for um IP,
 * faz resolução reversa (IP -> hostname) em vez disso.
 */
public class DnsLookupActivity extends AppCompatActivity {

    private static final String[] TIPOS_REGISTRO = {"A", "AAAA", "MX", "TXT", "NS", "CNAME"};

    private EditText editDominio;
    private Button btnConsultar;
    private TextView txtLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dns_lookup);

        editDominio = findViewById(R.id.editDnsDominio);
        btnConsultar = findViewById(R.id.btnConsultarDns);
        txtLog = findViewById(R.id.txtDnsLog);
        txtLog.setMovementMethod(new ScrollingMovementMethod());

        editDominio.setText("google.com");

        btnConsultar.setOnClickListener(v -> consultar());
    }

    private void consultar() {
        String entrada = editDominio.getText().toString().trim();
        if (entrada.isEmpty()) return;

        txtLog.setText("");
        btnConsultar.setEnabled(false);

        new Thread(() -> {
            if (DnsUtils.pareceIp(entrada)) {
                log("🔄 Fazendo resolução reversa de " + entrada + "...\n");
                String hostname = DnsUtils.consultarReverso(entrada);
                log(hostname != null ? "🏷️ Hostname encontrado: " + hostname
                        : "❌ Nenhum hostname associado a esse IP (ou o servidor não respondeu).");
            } else {
                log("🔎 Consultando registros DNS de " + entrada + "...\n");
                boolean encontrouAlgo = false;
                for (String tipo : TIPOS_REGISTRO) {
                    List<String> registros = DnsUtils.consultarRegistro(entrada, tipo);
                    if (!registros.isEmpty()) {
                        encontrouAlgo = true;
                        log("📄 " + tipo + ":");
                        for (String registro : registros) {
                            log("   • " + registro);
                        }
                        log("");
                    }
                }
                if (!encontrouAlgo) {
                    log("❌ Nenhum registro encontrado para esse domínio.");
                }
            }
            runOnUiThread(() -> btnConsultar.setEnabled(true));
        }).start();
    }

    private void log(String mensagem) {
        runOnUiThread(() -> txtLog.append(mensagem + "\n"));
    }
}
