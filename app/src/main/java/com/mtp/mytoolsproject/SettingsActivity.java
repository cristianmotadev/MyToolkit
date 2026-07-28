package com.mtp.mytoolsproject;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class SettingsActivity extends AppCompatActivity {

    private Switch switchMdns, switchLocationPermission;
    private EditText editIntervalo;
    private Button btnSalvarIntervalo;
    private Button btnEditJson;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        switchMdns = findViewById(R.id.switchMdns);
        switchLocationPermission = findViewById(R.id.switchLocationPermission);
        editIntervalo = findViewById(R.id.editIntervalo);
        btnSalvarIntervalo = findViewById(R.id.btnSalvarIntervalo);
        btnEditJson = findViewById(R.id.btnEditJson);

        prefs = getSharedPreferences("NetworkPrefs", MODE_PRIVATE);

        boolean mDnsAtivo = prefs.getBoolean("mdns_continuous", false);
        switchMdns.setChecked(mDnsAtivo);

        atualizarPermissaoLocalizacaoUi();

        int intervaloSalvo = prefs.getInt("intervalo_segundos", 30);
        editIntervalo.setText(String.valueOf(intervaloSalvo));

        btnSalvarIntervalo.setOnClickListener(v -> {
            try {
                String textoIntervalo = editIntervalo.getText().toString().trim();
                int segundos = textoIntervalo.isEmpty() ? 30 : Integer.parseInt(textoIntervalo);
                if (segundos < 5) {
                    segundos = 5;
                    editIntervalo.setText(String.valueOf(segundos));
                }
                prefs.edit().putInt("intervalo_segundos", segundos).apply();
                Toast.makeText(this, "Intervalo atualizado para " + segundos + "s", Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, "Valor inválido!", Toast.LENGTH_SHORT).show();
            }
        });

        // Ao tentar ligar o switch, dispara o pop-up nativo de permissão do Android
        switchLocationPermission.setOnCheckedChangeListener((buttonView, isChecked) -> {
            boolean temPermissao = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
            if (isChecked && !temPermissao) {
                // Desmarca temporariamente até o usuário aceitar no pop-up
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

        btnEditJson.setOnClickListener(v -> {
            startActivity(new Intent(SettingsActivity.this, ManageDevicesActivity.class));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        atualizarPermissaoLocalizacaoUi();
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