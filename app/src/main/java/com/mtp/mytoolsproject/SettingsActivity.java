package com.mtp.mytoolsproject;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.json.JSONObject;

public class SettingsActivity extends AppCompatActivity {

    private Switch switchMdns, switchLocationPermission, switchNotificarNovos;
    private EditText editIntervalo, editIntervaloWifiPadrao, editIntervaloNetworkPadrao;
    private Button btnSalvarIntervalo, btnEditJson, btnLimparCache, btnVerificarRoot, btnSalvarIntervalosPadrao;
    private TextView txtStatusRoot, txtStatusNotificacao, txtVersaoApp;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        switchMdns = findViewById(R.id.switchMdns);
        switchLocationPermission = findViewById(R.id.switchLocationPermission);
        switchNotificarNovos = findViewById(R.id.switchNotificarNovos);
        editIntervalo = findViewById(R.id.editIntervalo);
        editIntervaloWifiPadrao = findViewById(R.id.editIntervaloWifiPadrao);
        editIntervaloNetworkPadrao = findViewById(R.id.editIntervaloNetworkPadrao);
        btnSalvarIntervalo = findViewById(R.id.btnSalvarIntervalo);
        btnEditJson = findViewById(R.id.btnEditJson);
        btnLimparCache = findViewById(R.id.btnLimparCache);
        btnVerificarRoot = findViewById(R.id.btnVerificarRoot);
        btnSalvarIntervalosPadrao = findViewById(R.id.btnSalvarIntervalosPadrao);
        txtStatusRoot = findViewById(R.id.txtStatusRoot);
        txtStatusNotificacao = findViewById(R.id.txtStatusNotificacao);
        txtVersaoApp = findViewById(R.id.txtVersaoApp);

        prefs = getSharedPreferences("NetworkPrefs", MODE_PRIVATE);

        // --- mDNS contínuo ---
        boolean mDnsAtivo = prefs.getBoolean("mdns_continuous", false);
        switchMdns.setChecked(mDnsAtivo);

        int intervaloSalvo = prefs.getInt("intervalo_segundos", 30);
        editIntervalo.setText(String.valueOf(intervaloSalvo));

        boolean notificarNovos = prefs.getBoolean("notificar_novos_dispositivos", true);
        switchNotificarNovos.setChecked(notificarNovos);

        // --- Intervalos padrão dos scanners manuais/automáticos ---
        editIntervaloWifiPadrao.setText(String.valueOf(prefs.getInt("wifi_scan_intervalo_padrao", 30)));
        editIntervaloNetworkPadrao.setText(String.valueOf(prefs.getInt("network_scan_intervalo_padrao", 90)));

        atualizarPermissaoLocalizacaoUi();
        atualizarStatusNotificacoes();
        exibirVersaoApp();

        btnSalvarIntervalo.setOnClickListener(v -> {
            try {
                String textoIntervalo = editIntervalo.getText().toString().trim();
                int segundos = textoIntervalo.isEmpty() ? 30 : Integer.parseInt(textoIntervalo);
                if (segundos < 5) {
                    segundos = 5;
                    editIntervalo.setText(String.valueOf(segundos));
                }
                prefs.edit().putInt("intervalo_segundos", segundos).apply();
                Toast.makeText(this, "Intervalo do mDNS atualizado para " + segundos + "s", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Valor inválido!", Toast.LENGTH_SHORT).show();
            }
        });

        btnSalvarIntervalosPadrao.setOnClickListener(v -> {
            try {
                int wifiSeg = Integer.parseInt(editIntervaloWifiPadrao.getText().toString().trim());
                int netSeg = Integer.parseInt(editIntervaloNetworkPadrao.getText().toString().trim());
                prefs.edit()
                        .putInt("wifi_scan_intervalo_padrao", Math.max(wifiSeg, 5))
                        .putInt("network_scan_intervalo_padrao", Math.max(netSeg, 5))
                        .apply();
                Toast.makeText(this, "Intervalos padrão salvos!", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Valores inválidos!", Toast.LENGTH_SHORT).show();
            }
        });

        switchNotificarNovos.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("notificar_novos_dispositivos", isChecked).apply();
        });

        // Ao tentar ligar o switch, dispara o pop-up nativo de permissão do Android
        switchLocationPermission.setOnCheckedChangeListener((buttonView, isChecked) -> {
            boolean temPermissao = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            if (isChecked && !temPermissao) {
                switchLocationPermission.setChecked(false);
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                }, 102);
            }
        });

        switchMdns.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("mdns_continuous", isChecked).apply();

            Intent serviceIntent = new Intent(SettingsActivity.this, NetworkMonitorService.class);
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent);
                } else {
                    startService(serviceIntent);
                }
                Toast.makeText(this, "Monitoramento contínuo ativado", Toast.LENGTH_SHORT).show();
            } else {
                stopService(serviceIntent);
                Toast.makeText(this, "Monitoramento contínuo desativado", Toast.LENGTH_SHORT).show();
            }
        });

        btnEditJson.setOnClickListener(v -> startActivity(new Intent(SettingsActivity.this, ManageDevicesActivity.class)));

        btnLimparCache.setOnClickListener(v -> confirmarLimpezaCache());

        btnVerificarRoot.setOnClickListener(v -> verificarRoot());
    }

    private void confirmarLimpezaCache() {
        new AlertDialog.Builder(this)
                .setTitle("Limpar banco de dados")
                .setMessage("Isso apaga TODOS os dispositivos salvos no cache (mac_cache.json). Esta ação não pode ser desfeita. Deseja continuar?")
                .setPositiveButton("Sim, limpar", (dialog, which) -> {
                    boolean sucesso = NetworkUtils.salvarCache(this, new JSONObject());
                    Toast.makeText(this, sucesso ? "Cache limpo com sucesso." : "Falha ao limpar o cache.", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void verificarRoot() {
        txtStatusRoot.setText("⏳ Verificando...");
        new Thread(() -> {
            boolean temRoot = NetworkUtils.verificarRootDisponivel();
            runOnUiThread(() -> {
                if (temRoot) {
                    txtStatusRoot.setText("✅ Root detectado e concedido — todas as ferramentas devem funcionar normalmente.");
                    txtStatusRoot.setTextColor(0xFF4CAF50);
                } else {
                    txtStatusRoot.setText("❌ Root não detectado (ou não concedido). Ferramentas que dependem de root (Senhas Wi-Fi, Dispositivos na Rede, mDNS) não vão funcionar.");
                    txtStatusRoot.setTextColor(0xFFFF5252);
                }
            });
        }).start();
    }

    private void exibirVersaoApp() {
        try {
            String versao = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            txtVersaoApp.setText("My Tools Project — versão " + versao);
        } catch (Exception e) {
            txtVersaoApp.setText("My Tools Project");
        }
    }

    private void atualizarStatusNotificacoes() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            boolean permitido = ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
            txtStatusNotificacao.setText(permitido ? "✅ Permissão de notificações concedida." : "❌ Notificações bloqueadas pelo sistema — ative nas configurações do Android.");
            txtStatusNotificacao.setTextColor(permitido ? 0xFF4CAF50 : 0xFFFFA726);
        } else {
            txtStatusNotificacao.setText("✅ Notificações liberadas por padrão nesta versão do Android.");
            txtStatusNotificacao.setTextColor(0xFF4CAF50);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        atualizarPermissaoLocalizacaoUi();
        atualizarStatusNotificacoes();
    }

    private void atualizarPermissaoLocalizacaoUi() {
        boolean temPermissao = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        switchLocationPermission.setChecked(temPermissao);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 102) {
            atualizarPermissaoLocalizacaoUi();
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Permissão de localização concedida!", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
