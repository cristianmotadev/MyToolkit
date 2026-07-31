package com.mtp.mytoolsproject;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class NetworkScannerActivity extends AppCompatActivity {

    /**
     * Essa varredura já é bem mais pesada que a de Wi-Fi (ping em 254 IPs + leitura
     * de ARP + consulta de fabricante + checagem de portas por dispositivo encontrado),
     * então o intervalo mínimo recomendado é bem maior.
     */
    private static final int INTERVALO_MINIMO_RECOMENDADO_SEGUNDOS = 90;

    private LinearLayout containerNetworkCards;
    private Button btnScan;
    private Switch switchAutoScan;
    private EditText editIntervalo;
    private TextView txtAviso;
    private Handler autoScanHandler;
    private Runnable autoScanRunnable;

    private volatile boolean varreduraEmAndamento = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_network_scanner);

        containerNetworkCards = findViewById(R.id.containerNetworkCards);
        btnScan = findViewById(R.id.btnScan);
        switchAutoScan = findViewById(R.id.switchAutoScanNetwork);
        editIntervalo = findViewById(R.id.editIntervaloNetwork);
        txtAviso = findViewById(R.id.txtAvisoIntervaloNetwork);
        autoScanHandler = new Handler(Looper.getMainLooper());

        int intervaloPadrao = getSharedPreferences("NetworkPrefs", MODE_PRIVATE).getInt("network_scan_intervalo_padrao", 90);
        editIntervalo.setText(String.valueOf(intervaloPadrao));

        atualizarAvisoIntervalo();

        btnScan.setOnClickListener(v -> scanNetwork());

        switchAutoScan.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            editIntervalo.setEnabled(!isChecked);
            if (isChecked) {
                iniciarModoAutomatico();
            } else {
                pararModoAutomatico();
            }
        });

        editIntervalo.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(android.text.Editable s) {
                atualizarAvisoIntervalo();
            }
        });
    }

    private int lerIntervaloConfigurado() {
        try {
            int valor = Integer.parseInt(editIntervalo.getText().toString().trim());
            return Math.max(valor, 15);
        } catch (Exception e) {
            return 90;
        }
    }

    private void atualizarAvisoIntervalo() {
        int intervalo = lerIntervaloConfigurado();
        if (intervalo < INTERVALO_MINIMO_RECOMENDADO_SEGUNDOS) {
            txtAviso.setText("⚠️ Esta varredura é pesada (ping + ARP + fabricante + portas por dispositivo). Intervalos abaixo de "
                    + INTERVALO_MINIMO_RECOMENDADO_SEGUNDOS + "s podem sobrepor varreduras ou sobrecarregar a API de fabricantes.");
            txtAviso.setTextColor(0xFFFFA726);
        } else {
            txtAviso.setText("Intervalo dentro da faixa recomendada.");
            txtAviso.setTextColor(0xFF4CAF50);
        }
    }

    private void iniciarModoAutomatico() {
        pararModoAutomatico();
        autoScanRunnable = new Runnable() {
            @Override
            public void run() {
                scanNetwork();
                autoScanHandler.postDelayed(this, lerIntervaloConfigurado() * 1000L);
            }
        };
        autoScanHandler.post(autoScanRunnable);
        Toast.makeText(this, "Varredura automática ativada (a cada " + lerIntervaloConfigurado() + "s).", Toast.LENGTH_SHORT).show();
    }

    private void pararModoAutomatico() {
        if (autoScanRunnable != null) {
            autoScanHandler.removeCallbacks(autoScanRunnable);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        pararModoAutomatico();
    }

    private void scanNetwork() {
        if (varreduraEmAndamento) {
            // Evita que o modo automático dispare uma nova varredura em cima de uma que ainda não terminou
            Toast.makeText(this, "Ainda terminando a varredura anterior — aguarde.", Toast.LENGTH_SHORT).show();
            return;
        }
        varreduraEmAndamento = true;

        containerNetworkCards.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        String prefixoRede = NetworkUtils.descobrirPrefixoRedeLocal();
        Toast.makeText(this, "Varrendo " + prefixoRede + "0/24 e salvando no JSON...", Toast.LENGTH_SHORT).show();

        new Thread(() -> {
            try {
                for (int i = 1; i < 255; i++) {
                    final String targetIp = prefixoRede + i;
                    new Thread(() -> {
                        try {
                            Runtime.getRuntime().exec("ping -c 1 -w 1 " + targetIp);
                        } catch (Exception ignored) {}
                    }).start();
                }

                Thread.sleep(1500);

                Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "cat /proc/net/arp"});
                BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                boolean firstLine = true;

                JSONObject cache = NetworkUtils.carregarCache(this);
                boolean houveMudanca = false;
                String nomeRedeAtual = NetworkUtils.obterNomeRedeWifi(this);

                while ((line = br.readLine()) != null) {
                    if (firstLine) {
                        firstLine = false;
                        continue;
                    }
                    String[] tokens = line.split("\\s+");
                    if (tokens.length >= 4) {
                        final String ip = tokens[0];
                        final String mac = tokens[3];

                        if (mac == null || mac.equals("00:00:00:00:00:00") || mac.contains("00:00:00")) {
                            continue;
                        }

                        final String macKey = mac.toUpperCase();
                        final String apelidoPadrao = "Novo Aparelho (" + ip + ")";

                        String fabricante = NetworkUtils.consultarFabricante(macKey);
                        String statusPortas = NetworkUtils.verificarPortasComuns(ip);

                        JSONObject obj;
                        if (cache.has(macKey)) {
                            obj = cache.getJSONObject(macKey);
                            obj.put("fabricante", fabricante);
                            obj.put("rede_wifi", nomeRedeAtual);
                            obj.put("ip", ip);
                            obj.put("portas", statusPortas);
                        } else {
                            obj = new JSONObject();
                            obj.put("apelido", apelidoPadrao);
                            obj.put("fabricante", fabricante);
                            obj.put("ip", ip);
                            obj.put("rede_wifi", nomeRedeAtual);
                            obj.put("portas", statusPortas);
                            cache.put(macKey, obj);
                        }
                        houveMudanca = true;

                        final String fabricanteFinal = fabricante;
                        final String redeFinal = nomeRedeAtual;
                        final String portasFinal = statusPortas;

                        runOnUiThread(() -> adicionarCardRetornavel(
                                inflater,
                                containerNetworkCards,
                                apelidoPadrao,
                                "📍 IP: " + ip + "\n🔗 MAC: " + macKey + "\n🏷️ Marca: " + fabricanteFinal
                                        + "\n📶 Rede: " + redeFinal + "\n🔌 Portas: " + portasFinal
                        ));

                        try {
                            Thread.sleep(2500);
                        } catch (InterruptedException ignored) {}
                    }
                }
                br.close();
                process.waitFor();

                if (houveMudanca) {
                    NetworkUtils.salvarCache(this, cache);
                }

                final boolean encontrouAlgo = houveMudanca;
                runOnUiThread(() -> {
                    if (containerNetworkCards.getChildCount() == 0) {
                        adicionarCard(inflater, containerNetworkCards, "⚠️ Aviso", "Nenhum dispositivo encontrado na rede.");
                    } else if (encontrouAlgo) {
                        Toast.makeText(NetworkScannerActivity.this, "Varredura e dados salvos no JSON com sucesso!", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> adicionarCard(inflater, containerNetworkCards, "⚠️ Erro", String.valueOf(e.getMessage())));
            } finally {
                varreduraEmAndamento = false;
            }
        }).start();
    }

    private void adicionarCard(LayoutInflater inflater, LinearLayout container, String title, String subtitle) {
        View card = inflater.inflate(R.layout.item_saved_device, container, false);
        TextView txtName = card.findViewById(R.id.txtDeviceName);
        TextView txtMac = card.findViewById(R.id.txtDeviceMac);
        TextView txtBrand = card.findViewById(R.id.txtDeviceBrand);

        card.findViewById(R.id.btnEditDevice).setVisibility(View.GONE);
        card.findViewById(R.id.btnDeleteDevice).setVisibility(View.GONE);

        txtName.setText(title);
        txtMac.setText(subtitle);
        txtBrand.setText("");
        container.addView(card);
    }

    private void adicionarCardRetornavel(LayoutInflater inflater, LinearLayout container, String title, String subtitle) {
        View card = inflater.inflate(R.layout.item_saved_device, container, false);
        TextView txtName = card.findViewById(R.id.txtDeviceName);
        TextView txtMac = card.findViewById(R.id.txtDeviceMac);
        TextView txtBrand = card.findViewById(R.id.txtDeviceBrand);

        card.findViewById(R.id.btnEditDevice).setVisibility(View.GONE);
        card.findViewById(R.id.btnDeleteDevice).setVisibility(View.GONE);

        txtName.setText(title);
        txtMac.setText("");
        txtBrand.setText(subtitle);
        container.addView(card);
    }
}
