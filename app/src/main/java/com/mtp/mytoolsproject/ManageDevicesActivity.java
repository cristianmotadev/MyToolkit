package com.mtp.mytoolsproject;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.util.Iterator;

public class ManageDevicesActivity extends AppCompatActivity {

    private LinearLayout containerSavedDevices;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_devices);

        containerSavedDevices = findViewById(R.id.containerSavedDevices);
        carregarListaDispositivos();
    }

    private void carregarListaDispositivos() {
        containerSavedDevices.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        JSONObject cache = NetworkUtils.carregarCache(this);
        Iterator<String> keys = cache.keys();
        boolean temItens = false;

        while (keys.hasNext()) {
            temItens = true;
            final String mac = keys.next();
            JSONObject obj = cache.optJSONObject(mac);
            if (obj == null) continue;

            String apelido = obj.optString("apelido", "Dispositivo sem apelido");
            String fabricante = obj.optString("fabricante", "Marca Desconhecida");
            String redeWifi = obj.optString("rede_wifi", "Desconhecida");
            // O campo "portas" já era salvo no JSON, mas esta tela nunca chegava
            // a lê-lo nem exibi-lo — por isso parecia que a informação sumia.
            String portas = obj.optString("portas", "Ainda não verificado");

            View card = inflater.inflate(R.layout.item_saved_device, containerSavedDevices, false);
            TextView txtName = card.findViewById(R.id.txtDeviceName);
            TextView txtMac = card.findViewById(R.id.txtDeviceMac);
            TextView txtBrand = card.findViewById(R.id.txtDeviceBrand);
            Button btnEdit = card.findViewById(R.id.btnEditDevice);
            Button btnDelete = card.findViewById(R.id.btnDeleteDevice);

            txtName.setText(apelido);
            txtMac.setText("MAC: " + mac);
            txtBrand.setText("Marca: " + fabricante + "\n📶 Rede: " + redeWifi + "\n🔌 Portas: " + portas);

            btnEdit.setOnClickListener(v -> mostrarDialogoEditar(mac, apelido));
            btnDelete.setOnClickListener(v -> excluirDispositivo(mac));

            if (fabricante.equalsIgnoreCase("NÃO ENCONTRADO")) {
                card.setOnClickListener(v -> new AlertDialog.Builder(ManageDevicesActivity.this)
                        .setTitle("Refazer Busca de Fabricante")
                        .setMessage("Deseja refazer a busca para o MAC " + mac + "?")
                        .setPositiveButton("Sim", (dialog, which) -> {
                            Toast.makeText(this, "Consultando...", Toast.LENGTH_SHORT).show();

                            // Chamada de rede sempre fora da UI thread
                            new Thread(() -> {
                                String novaMarca = NetworkUtils.consultarFabricante(mac);
                                runOnUiThread(() -> {
                                    if (novaMarca != null && !novaMarca.equalsIgnoreCase("NÃO ENCONTRADO")
                                            && !novaMarca.equalsIgnoreCase("Rede Local / Genérico")) {
                                        atualizarFabricanteCache(mac, novaMarca);
                                        Toast.makeText(this, "Encontrado: " + novaMarca, Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(this, "Ainda não encontrado.", Toast.LENGTH_SHORT).show();
                                    }
                                    carregarListaDispositivos();
                                });
                            }).start();
                        })
                        .setNegativeButton("Não", null)
                        .show());
            }

            containerSavedDevices.addView(card);
        }

        if (!temItens) {
            adicionarAvisoVazio(inflater, "Nenhum dispositivo salvo no cache.");
        }
    }

    private void atualizarFabricanteCache(String mac, String novaMarca) {
        JSONObject cache = NetworkUtils.carregarCache(this);
        try {
            if (cache.has(mac)) {
                JSONObject obj = cache.getJSONObject(mac);
                obj.put("fabricante", novaMarca);
                cache.put(mac, obj);
                NetworkUtils.salvarCache(this, cache);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void adicionarAvisoVazio(LayoutInflater inflater, String mensagem) {
        View card = inflater.inflate(R.layout.item_saved_device, containerSavedDevices, false);
        TextView txtName = card.findViewById(R.id.txtDeviceName);
        TextView txtMac = card.findViewById(R.id.txtDeviceMac);
        TextView txtBrand = card.findViewById(R.id.txtDeviceBrand);
        card.findViewById(R.id.btnEditDevice).setVisibility(View.GONE);
        card.findViewById(R.id.btnDeleteDevice).setVisibility(View.GONE);

        txtName.setText("Aviso");
        txtMac.setText(mensagem);
        txtBrand.setText("");
        containerSavedDevices.addView(card);
    }

    private void mostrarDialogoEditar(String mac, String apelidoAtual) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Editar Apelido");

        final EditText input = new EditText(this);
        input.setText(apelidoAtual);
        builder.setView(input);

        builder.setPositiveButton("Salvar", (dialog, which) -> {
            String novoNome = input.getText().toString().trim();
            if (!novoNome.isEmpty()) {
                atualizarApelidoCache(mac, novoNome);
                carregarListaDispositivos();
                Toast.makeText(this, "Atualizado com sucesso!", Toast.LENGTH_SHORT).show();
            }
        });
        builder.setNegativeButton("Cancelar", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    private void atualizarApelidoCache(String mac, String novoApelido) {
        JSONObject cache = NetworkUtils.carregarCache(this);
        try {
            if (cache.has(mac)) {
                JSONObject obj = cache.getJSONObject(mac);
                obj.put("apelido", novoApelido);
                cache.put(mac, obj);
                NetworkUtils.salvarCache(this, cache);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void excluirDispositivo(String mac) {
        new AlertDialog.Builder(this)
                .setTitle("Excluir")
                .setMessage("Deseja remover este dispositivo do cache?")
                .setPositiveButton("Sim", (dialog, which) -> {
                    JSONObject cache = NetworkUtils.carregarCache(this);
                    cache.remove(mac);
                    if (NetworkUtils.salvarCache(this, cache)) {
                        carregarListaDispositivos();
                        Toast.makeText(this, "Removido com sucesso!", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Não", null)
                .show();
    }
}
