package com.mtp.mytoolsproject;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;

/**
 * Tela de dispositivos salvos, agora com:
 * - Busca/filtro por apelido, MAC, IP ou fabricante
 * - Agrupamento por rede Wi-Fi
 * - Swipe (arrastar pra esquerda) para excluir, além do botão tradicional
 */
public class ManageDevicesActivity extends AppCompatActivity {

    private LinearLayout containerSavedDevices;
    private EditText editBusca;
    private String filtroAtual = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_devices);

        containerSavedDevices = findViewById(R.id.containerSavedDevices);
        editBusca = findViewById(R.id.editBuscaDispositivos);

        editBusca.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filtroAtual = s.toString().trim().toLowerCase(Locale.getDefault());
                carregarListaDispositivos();
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        carregarListaDispositivos();
    }

    private void carregarListaDispositivos() {
        containerSavedDevices.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        JSONObject cache = NetworkUtils.carregarCache(this);

        // Agrupa por rede Wi-Fi (ordem alfabética das redes)
        TreeMap<String, List<String>> grupos = new TreeMap<>();
        Iterator<String> keys = cache.keys();
        boolean temItens = false;

        while (keys.hasNext()) {
            String mac = keys.next();
            JSONObject obj = cache.optJSONObject(mac);
            if (obj == null) continue;
            if (!passaNoFiltro(mac, obj)) continue;

            temItens = true;
            String redeWifi = obj.optString("rede_wifi", "Desconhecida");
            grupos.computeIfAbsent(redeWifi, k -> new ArrayList<>()).add(mac);
        }

        if (!temItens) {
            String mensagem = filtroAtual.isEmpty() ? "Nenhum dispositivo salvo no cache." : "Nenhum resultado para \"" + filtroAtual + "\".";
            adicionarAvisoVazio(inflater, mensagem);
            return;
        }

        for (String rede : grupos.keySet()) {
            adicionarCabecalhoGrupo("📶 " + rede);
            for (String mac : grupos.get(rede)) {
                JSONObject obj = cache.optJSONObject(mac);
                if (obj != null) adicionarCardDispositivo(inflater, mac, obj);
            }
        }
    }

    private boolean passaNoFiltro(String mac, JSONObject obj) {
        if (filtroAtual.isEmpty()) return true;
        String apelido = obj.optString("apelido", "").toLowerCase(Locale.getDefault());
        String fabricante = obj.optString("fabricante", "").toLowerCase(Locale.getDefault());
        String ip = obj.optString("ip", "").toLowerCase(Locale.getDefault());
        String macLower = mac.toLowerCase(Locale.getDefault());

        return apelido.contains(filtroAtual) || fabricante.contains(filtroAtual)
                || ip.contains(filtroAtual) || macLower.contains(filtroAtual);
    }

    private void adicionarCabecalhoGrupo(String texto) {
        TextView header = new TextView(this);
        header.setText(texto);
        header.setTextColor(0xFF2196F3);
        header.setTextSize(15f);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setPadding(4, 24, 4, 10);
        containerSavedDevices.addView(header);
    }

    private void adicionarCardDispositivo(LayoutInflater inflater, String mac, JSONObject obj) {
        String apelido = obj.optString("apelido", "Dispositivo sem apelido");
        String fabricante = obj.optString("fabricante", "Marca Desconhecida");
        String redeWifi = obj.optString("rede_wifi", "Desconhecida");
        String portas = obj.optString("portas", "Ainda não verificado");
        String ultimaVez = NetworkUtils.formatarTempoRelativo(obj.optLong("ultimaVez", 0));

        View card = inflater.inflate(R.layout.item_saved_device, containerSavedDevices, false);
        TextView txtName = card.findViewById(R.id.txtDeviceName);
        TextView txtMac = card.findViewById(R.id.txtDeviceMac);
        TextView txtBrand = card.findViewById(R.id.txtDeviceBrand);
        Button btnEdit = card.findViewById(R.id.btnEditDevice);
        Button btnDelete = card.findViewById(R.id.btnDeleteDevice);

        txtName.setText(apelido);
        txtMac.setText("MAC: " + mac);
        txtBrand.setText("Marca: " + fabricante + "\n📶 Rede: " + redeWifi + "\n🔌 Portas: " + portas + "\n🕒 " + ultimaVez);

        btnEdit.setOnClickListener(v -> mostrarDialogoEditar(mac, apelido));
        btnDelete.setOnClickListener(v -> excluirDispositivo(mac));

        if (fabricante.equalsIgnoreCase("NÃO ENCONTRADO")) {
            card.setOnClickListener(v -> new AlertDialog.Builder(ManageDevicesActivity.this)
                    .setTitle("Refazer Busca de Fabricante")
                    .setMessage("Deseja refazer a busca para o MAC " + mac + "?")
                    .setPositiveButton("Sim", (dialog, which) -> {
                        Toast.makeText(this, "Consultando...", Toast.LENGTH_SHORT).show();
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

        configurarSwipeParaExcluir(card, mac, apelido);
        containerSavedDevices.addView(card);
    }

    /**
     * Arrastar o card pra esquerda além de ~35% da largura exclui direto (sem
     * diálogo de confirmação — o próprio gesto já é a confirmação). Arrastar
     * menos que isso volta o card pro lugar. O botão "Excluir" tradicional
     * continua disponível e pede confirmação, para quem preferir esse fluxo.
     */
    private void configurarSwipeParaExcluir(View card, String mac, String apelido) {
        final float[] deltaX = {0f};

        card.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    deltaX[0] = v.getTranslationX() - event.getRawX();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float novaTranslacao = event.getRawX() + deltaX[0];
                    if (novaTranslacao <= 0) {
                        v.setTranslationX(novaTranslacao);
                        float larguraView = Math.max(v.getWidth(), 1);
                        v.setAlpha(1f - Math.min(0.7f, Math.abs(novaTranslacao) / larguraView));
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    float deslocamento = -v.getTranslationX();
                    float largura = v.getWidth();
                    if (largura > 0 && deslocamento > largura * 0.35f) {
                        v.animate().translationX(-largura).alpha(0f).setDuration(200)
                                .withEndAction(() -> excluirDispositivoSemConfirmacao(mac, apelido))
                                .start();
                    } else {
                        v.animate().translationX(0).alpha(1f).setDuration(150).start();
                    }
                    return true;

                default:
                    return false;
            }
        });
    }

    private void excluirDispositivoSemConfirmacao(String mac, String apelido) {
        JSONObject cache = NetworkUtils.carregarCache(this);
        cache.remove(mac);
        if (NetworkUtils.salvarCache(this, cache)) {
            Toast.makeText(this, "Removido: " + apelido, Toast.LENGTH_SHORT).show();
            carregarListaDispositivos();
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
