package com.mtp.mytoolsproject;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Varredura manual/pontual de dispositivos na rede local (mostra os cards em
 * tempo real nesta tela). O modo automático aqui é o MESMO monitoramento em
 * segundo plano usado pelas Configurações ("Escaneamento contínuo mDNS") —
 * ligar aqui ou lá controla o mesmo NetworkMonitorService, que continua
 * rodando mesmo depois de sair desta tela. Os resultados encontrados em
 * segundo plano ficam disponíveis em "Dispositivos Salvos".
 */
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
    private ProgressBar progressBar;
    private TextView txtProgresso;
    private SharedPreferences prefs;

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
        progressBar = findViewById(R.id.progressBarNetworkScan);
        txtProgresso = findViewById(R.id.txtProgressoNetworkScan);
        prefs = getSharedPreferences("NetworkPrefs", MODE_PRIVATE);

        int intervaloSalvo = prefs.getInt("intervalo_segundos", prefs.getInt("network_scan_intervalo_padrao", 90));
        editIntervalo.setText(String.valueOf(intervaloSalvo));

        atualizarAvisoIntervalo();

        btnScan.setOnClickListener(v -> scanNetwork());

        switchAutoScan.setOnCheckedChangeListener((CompoundButton buttonView, boolean isChecked) -> {
            editIntervalo.setEnabled(!isChecked);
            if (isChecked) {
                ativarVarreduraEmSegundoPlano();
            } else {
                desativarVarreduraEmSegundoPlano();
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

    private void ativarVarreduraEmSegundoPlano() {
        int intervalo = lerIntervaloConfigurado();
        prefs.edit()
                .putInt("intervalo_segundos", intervalo)
                .putBoolean("mdns_continuous", true)
                .apply();

        Intent serviceIntent = new Intent(this, NetworkMonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Toast.makeText(this, "Varredura automática ativada (a cada " + intervalo + "s) — continua rodando mesmo se você sair da tela. Novos dispositivos aparecem em \"Dispositivos Salvos\".", Toast.LENGTH_LONG).show();
    }

    private void desativarVarreduraEmSegundoPlano() {
        prefs.edit().putBoolean("mdns_continuous", false).apply();
        stopService(new Intent(this, NetworkMonitorService.class));
        Toast.makeText(this, "Varredura automática desativada.", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reflete o estado real do serviço — pode ter sido ligado nesta tela,
        // nas Configurações, ou numa sessão anterior.
        boolean autoAtivo = prefs.getBoolean("mdns_continuous", false);
        switchAutoScan.setChecked(autoAtivo);
        editIntervalo.setEnabled(!autoAtivo);
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

        progressBar.setVisibility(View.VISIBLE);
        progressBar.setIndeterminate(true);
        txtProgresso.setText("Enviando pings e lendo tabela ARP...");

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

                // Primeiro lê e filtra todas as entradas válidas, pra saber o total antes de processar
                List<String[]> entradasValidas = new ArrayList<>();
                while ((line = br.readLine()) != null) {
                    if (firstLine) {
                        firstLine = false;
                        continue;
                    }
                    String[] tokens = line.split("\\s+");
                    if (tokens.length >= 4) {
                        String ip = tokens[0];
                        String mac = tokens[3];
                        if (mac == null || mac.equals("00:00:00:00:00:00") || mac.contains("00:00:00")) continue;
                        entradasValidas.add(new String[]{ip, mac.toUpperCase()});
                    }
                }
                br.close();
                process.waitFor();

                int total = entradasValidas.size();
                runOnUiThread(() -> {
                    progressBar.setIndeterminate(false);
                    progressBar.setMax(Math.max(total, 1));
                    progressBar.setProgress(0);
                    txtProgresso.setText("0/" + total + " dispositivos processados");
                });

                JSONObject cache = NetworkUtils.carregarCache(this);
                boolean houveMudanca = false;
                String nomeRedeAtual = NetworkUtils.obterNomeRedeWifi(this);

                for (int idx = 0; idx < entradasValidas.size(); idx++) {
                    String ip = entradasValidas.get(idx)[0];
                    String macKey = entradasValidas.get(idx)[1];
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
                        obj.put("ultimaVez", System.currentTimeMillis());
                    } else {
                        obj = new JSONObject();
                        obj.put("apelido", apelidoPadrao);
                        obj.put("fabricante", fabricante);
                        obj.put("ip", ip);
                        obj.put("rede_wifi", nomeRedeAtual);
                        obj.put("portas", statusPortas);
                        obj.put("ultimaVez", System.currentTimeMillis());
                        cache.put(macKey, obj);
                    }
                    houveMudanca = true;

                    final String fabricanteFinal = fabricante;
                    final String redeFinal = nomeRedeAtual;
                    final String portasFinal = statusPortas;
                    final int concluidos = idx + 1;

                    runOnUiThread(() -> {
                        progressBar.setProgress(concluidos);
                        txtProgresso.setText(concluidos + "/" + total + " dispositivos processados");
                        adicionarCardRetornavel(
                                inflater,
                                containerNetworkCards,
                                apelidoPadrao,
                                "📍 IP: " + ip + "\n🔗 MAC: " + macKey + "\n🏷️ Marca: " + fabricanteFinal
                                        + "\n📶 Rede: " + redeFinal + "\n🔌 Portas: " + portasFinal
                        );
                    });

                    try {
                        Thread.sleep(2500);
                    } catch (InterruptedException ignored) {}
                }

                if (houveMudanca) {
                    NetworkUtils.salvarCache(this, cache);
                }

                final boolean encontrouAlgo = houveMudanca;
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    if (containerNetworkCards.getChildCount() == 0) {
                        adicionarCard(inflater, containerNetworkCards, "⚠️ Aviso", "Nenhum dispositivo encontrado na rede.");
                        txtProgresso.setText("Nenhum dispositivo encontrado.");
                    } else if (encontrouAlgo) {
                        txtProgresso.setText("Varredura concluída: " + total + " dispositivo(s) processado(s).");
                        Toast.makeText(NetworkScannerActivity.this, "Varredura e dados salvos no JSON com sucesso!", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    adicionarCard(inflater, containerNetworkCards, "⚠️ Erro", String.valueOf(e.getMessage()));
                });
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
