package com.mtp.mytoolsproject;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;

public class NetworkScannerActivity extends AppCompatActivity {

    private LinearLayout containerNetworkCards;
    private Button btnScan;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_network_scanner);

        containerNetworkCards = findViewById(R.id.containerNetworkCards);
        btnScan = findViewById(R.id.btnScan);

        btnScan.setOnClickListener(v -> scanNetwork());
    }

    private void scanNetwork() {
        containerNetworkCards.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        // Descobre a sub-rede real do aparelho em vez de usar 192.168.1.x fixo
        String prefixoRede = NetworkUtils.descobrirPrefixoRedeLocal();
        Toast.makeText(this, "Varrendo " + prefixoRede + "0/24 e salvando no JSON...", Toast.LENGTH_SHORT).show();

        // Toda a varredura roda fora da thread principal (ping, ARP, HTTP, I/O de arquivo)
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
