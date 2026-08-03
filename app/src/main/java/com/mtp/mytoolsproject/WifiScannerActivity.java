package com.mtp.mytoolsproject;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
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
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Escaneia redes Wi-Fi disponíveis ao redor (não é necessário estar conectado
 * em nenhuma delas). Suporta varredura manual (um clique) e varredura
 * automática com intervalo configurável pelo usuário — que, ao ser ativada,
 * continua rodando em segundo plano via WifiScanBackgroundService mesmo
 * depois de sair desta tela.
 */
public class WifiScannerActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_LOCATION = 201;

    /** Abaixo disso o Android tende a bloquear novas varreduras (throttling do sistema). */
    private static final int INTERVALO_MINIMO_RECOMENDADO_SEGUNDOS = 20;

    private LinearLayout containerRedes;
    private Button btnEscanear;
    private Switch switchAutoScan;
    private EditText editIntervalo;
    private TextView txtAviso;
    private WifiManager wifiManager;
    private BroadcastReceiver scanReceiver;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wifi_scanner);

        containerRedes = findViewById(R.id.containerRedesWifi);
        btnEscanear = findViewById(R.id.btnEscanearRedes);
        switchAutoScan = findViewById(R.id.switchAutoScanWifi);
        editIntervalo = findViewById(R.id.editIntervaloWifi);
        txtAviso = findViewById(R.id.txtAvisoIntervaloWifi);
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        prefs = getSharedPreferences("NetworkPrefs", MODE_PRIVATE);

        int intervaloSalvo = prefs.getInt("wifi_auto_scan_intervalo_segundos", prefs.getInt("wifi_scan_intervalo_padrao", 30));
        editIntervalo.setText(String.valueOf(intervaloSalvo));

        atualizarAvisoIntervalo();

        btnEscanear.setOnClickListener(v -> iniciarVarredura());

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

        scanReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                // Atualiza sempre que qualquer varredura terminar (manual, automática em
                // segundo plano, ou até do próprio sistema) — assim a tela sempre reflete
                // o resultado mais recente disponível.
                exibirResultados();
            }
        };
    }

    private int lerIntervaloConfigurado() {
        try {
            int valor = Integer.parseInt(editIntervalo.getText().toString().trim());
            return Math.max(valor, 5); // nunca deixa configurar abaixo de 5s (trava de segurança)
        } catch (Exception e) {
            return 30;
        }
    }

    private void atualizarAvisoIntervalo() {
        int intervalo = lerIntervaloConfigurado();
        if (intervalo < INTERVALO_MINIMO_RECOMENDADO_SEGUNDOS) {
            txtAviso.setText("⚠️ Intervalos abaixo de " + INTERVALO_MINIMO_RECOMENDADO_SEGUNDOS
                    + "s tendem a ser bloqueados pelo próprio Android (throttling de varredura, a partir do Android 9). Recomendado: 20s ou mais.");
            txtAviso.setTextColor(0xFFFFA726);
        } else {
            txtAviso.setText("Intervalo dentro da faixa recomendada.");
            txtAviso.setTextColor(0xFF4CAF50);
        }
    }

    private void ativarVarreduraEmSegundoPlano() {
        int intervalo = lerIntervaloConfigurado();
        prefs.edit()
                .putInt("wifi_auto_scan_intervalo_segundos", intervalo)
                .putBoolean("wifi_auto_scan_ativo", true)
                .apply();

        Intent serviceIntent = new Intent(this, WifiScanBackgroundService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        Toast.makeText(this, "Radar Wi-Fi automático ativado (a cada " + intervalo + "s) — continua rodando mesmo se você sair da tela.", Toast.LENGTH_LONG).show();
    }

    private void desativarVarreduraEmSegundoPlano() {
        prefs.edit().putBoolean("wifi_auto_scan_ativo", false).apply();
        stopService(new Intent(this, WifiScanBackgroundService.class));
        Toast.makeText(this, "Radar Wi-Fi automático desativado.", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        registerReceiver(scanReceiver, filter);

        // Reflete o estado real do serviço (pode ter sido ligado numa sessão anterior)
        boolean autoAtivo = prefs.getBoolean("wifi_auto_scan_ativo", false);
        switchAutoScan.setChecked(autoAtivo);
        editIntervalo.setEnabled(!autoAtivo);

        exibirResultados(); // mostra na hora o que já estiver em cache, sem esperar nova varredura
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            unregisterReceiver(scanReceiver);
        } catch (Exception ignored) {}
        // Propositalmente NÃO paramos o serviço de segundo plano aqui — se o modo
        // automático estiver ativo, ele deve continuar rodando mesmo fora da tela.
    }

    private void iniciarVarredura() {
        boolean temPermissao = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!temPermissao) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE_LOCATION);
            Toast.makeText(this, "Conceda a permissão de localização para escanear redes Wi-Fi.", Toast.LENGTH_LONG).show();
            return;
        }

        if (!wifiManager.isWifiEnabled()) {
            Toast.makeText(this, "Ative o Wi-Fi para escanear redes.", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean iniciouComSucesso = wifiManager.startScan();
        if (!iniciouComSucesso) {
            // O Android limita a frequência de varreduras (throttling) desde a versão 9.
            Toast.makeText(this, "Varredura limitada pelo sistema (throttling) — mostrando último resultado.", Toast.LENGTH_LONG).show();
            exibirResultados();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_LOCATION && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            iniciarVarredura();
        }
    }

    private void exibirResultados() {
        containerRedes.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        List<ScanResult> resultados;
        try {
            resultados = wifiManager.getScanResults();
        } catch (SecurityException e) {
            adicionarAviso(inflater, "Permissão de localização necessária para ver os resultados.");
            return;
        }

        if (resultados == null || resultados.isEmpty()) {
            adicionarAviso(inflater, "Nenhuma rede encontrada por perto.");
            return;
        }

        List<ScanResult> ordenados = new ArrayList<>(resultados);
        Collections.sort(ordenados, (a, b) -> Integer.compare(b.level, a.level));

        for (ScanResult resultado : ordenados) {
            adicionarCardRede(inflater, resultado);
        }
    }

    private void adicionarCardRede(LayoutInflater inflater, ScanResult resultado) {
        View card = inflater.inflate(R.layout.item_wifi_network, containerRedes, false);

        TextView txtSsid = card.findViewById(R.id.txtWifiSsid);
        TextView txtSeguranca = card.findViewById(R.id.txtWifiSeguranca);
        TextView txtWps = card.findViewById(R.id.txtWifiWps);
        TextView txtBssid = card.findViewById(R.id.txtWifiBssid);
        TextView txtCanal = card.findViewById(R.id.txtWifiCanal);
        TextView txtSinal = card.findViewById(R.id.txtWifiSinal);

        String ssid = (resultado.SSID == null || resultado.SSID.isEmpty()) ? "(Rede oculta)" : resultado.SSID;
        String seguranca = WifiScanUtils.interpretarSeguranca(resultado.capabilities);
        boolean temWps = WifiScanUtils.possuiWpsAvancado(resultado);
        boolean aberta = WifiScanUtils.ehRedeAberta(resultado.capabilities);
        boolean criptografiaFraca = WifiScanUtils.usaCriptografiaFraca(resultado.capabilities);
        int canal = WifiScanUtils.frequenciaParaCanal(resultado.frequency);
        String banda = WifiScanUtils.bandaDaFrequencia(resultado.frequency);
        String qualidade = WifiScanUtils.qualidadeSinal(resultado.level);

        txtSsid.setText(ssid);

        if (aberta) {
            txtSeguranca.setText("🔓 Aberta — sem senha");
            txtSeguranca.setTextColor(0xFFFF5252);
        } else if (criptografiaFraca) {
            txtSeguranca.setText("⚠️ " + seguranca + " — criptografia ultrapassada");
            txtSeguranca.setTextColor(0xFFFFA726);
        } else {
            txtSeguranca.setText("🔒 " + seguranca);
            txtSeguranca.setTextColor(0xFF4CAF50);
        }

        txtBssid.setText("BSSID: " + resultado.BSSID);
        txtCanal.setText("Canal " + (canal > 0 ? String.valueOf(canal) : "?") + " (" + banda + ")");
        txtSinal.setText("📶 Sinal: " + qualidade + " (" + resultado.level + " dBm)");

        if (temWps) {
            txtWps.setVisibility(View.VISIBLE);
            txtWps.setText("⚠️ WPS ativo — vulnerável a ataques conhecidos (Pixie Dust / força bruta de PIN). Considere desativar o WPS no roteador.");
        } else {
            txtWps.setVisibility(View.GONE);
        }

        containerRedes.addView(card);
    }

    private void adicionarAviso(LayoutInflater inflater, String mensagem) {
        View card = inflater.inflate(R.layout.item_wifi_network, containerRedes, false);
        card.findViewById(R.id.txtWifiSeguranca).setVisibility(View.GONE);
        card.findViewById(R.id.txtWifiWps).setVisibility(View.GONE);
        card.findViewById(R.id.txtWifiBssid).setVisibility(View.GONE);
        card.findViewById(R.id.txtWifiCanal).setVisibility(View.GONE);
        card.findViewById(R.id.txtWifiSinal).setVisibility(View.GONE);

        TextView txtSsid = card.findViewById(R.id.txtWifiSsid);
        txtSsid.setText(mensagem);
        containerRedes.addView(card);
    }
}
