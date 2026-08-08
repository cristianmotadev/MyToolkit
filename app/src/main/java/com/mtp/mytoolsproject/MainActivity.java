package com.mtp.mytoolsproject;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, 101);
            }
        }

        criarCanalNotificacao();

        CardView cardWifi = findViewById(R.id.cardWifi);
        CardView cardScanner = findViewById(R.id.cardScanner);
        CardView cardWifiScan = findViewById(R.id.cardWifiScan);
        CardView cardNfc = findViewById(R.id.cardNfc);
        CardView cardSubnet = findViewById(R.id.cardSubnet);
        CardView cardPortScanner = findViewById(R.id.cardPortScanner);
        CardView cardSpeedTest = findViewById(R.id.cardSpeedTest);
        CardView cardBluetooth = findViewById(R.id.cardBluetooth);
        CardView cardTraceroute = findViewById(R.id.cardTraceroute);
        CardView cardDns = findViewById(R.id.cardDns);
        CardView cardSystemInfo = findViewById(R.id.cardSystemInfo);
        CardView cardAppAudit = findViewById(R.id.cardAppAudit);
        CardView cardPasswordGenerator = findViewById(R.id.cardPasswordGenerator);
        CardView cardHashCalculator = findViewById(R.id.cardHashCalculator);
        CardView cardFileEncryption = findViewById(R.id.cardFileEncryption);
        CardView cardConfig = findViewById(R.id.cardConfig);

        cardWifi.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, WifiPasswordsActivity.class));
            }
        });

        cardScanner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, NetworkScannerActivity.class));
            }
        });

        cardWifiScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, WifiScannerActivity.class));
            }
        });

        cardNfc.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, NfcToolsActivity.class));
            }
        });

        cardSubnet.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SubnetCalculatorActivity.class));
            }
        });

        cardPortScanner.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, PortScannerActivity.class));
            }
        });

        cardSpeedTest.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SpeedTestActivity.class));
            }
        });

        cardBluetooth.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, BluetoothScannerActivity.class));
            }
        });

        cardTraceroute.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, TracerouteActivity.class));
            }
        });

        cardDns.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, DnsLookupActivity.class));
            }
        });

        cardSystemInfo.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SystemInfoActivity.class));
            }
        });

        cardAppAudit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, AppAuditActivity.class));
            }
        });

        cardPasswordGenerator.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, PasswordGeneratorActivity.class));
            }
        });

        cardHashCalculator.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, HashCalculatorActivity.class));
            }
        });

        cardFileEncryption.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, FileEncryptionActivity.class));
            }
        });

        cardConfig.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SettingsActivity.class));
            }
        });

        verificarAtualizacaoEmSegundoPlano();
    }

    /**
     * Checa em segundo plano se há uma nova Release no GitHub. Só mostra o
     * diálogo se realmente encontrar uma versão mais nova — em caso de erro
     * (sem internet, repositório privado, etc.) fica em silêncio, sem
     * incomodar o usuário toda vez que abre o app.
     */
    private void verificarAtualizacaoEmSegundoPlano() {
        boolean beta = getSharedPreferences("NetworkPrefs", MODE_PRIVATE).getBoolean("canal_atualizacao_beta", false);
        UpdateChecker.Canal canal = beta ? UpdateChecker.Canal.BETA : UpdateChecker.Canal.OFICIAL;

        new Thread(() -> {
            UpdateChecker.ResultadoVerificacao resultado = UpdateChecker.verificar(this, canal);
            if (resultado.temAtualizacao) {
                runOnUiThread(() -> mostrarDialogoAtualizacao(resultado));
            }
        }).start();
    }

    private void mostrarDialogoAtualizacao(UpdateChecker.ResultadoVerificacao resultado) {
        boolean beta = resultado.canal == UpdateChecker.Canal.BETA;
        String titulo = beta ? "🧪 Nova versão Beta disponível" : "🎉 Nova atualização disponível";
        String mensagem = beta
                ? "Novo commit na branch develop: " + resultado.versaoDisponivel + "\n" + resultado.mensagemExtra
                : "Você tem a versão " + resultado.versaoAtual + ". A versão " + resultado.versaoDisponivel + " já está disponível no GitHub.";

        new AlertDialog.Builder(this)
                .setTitle(titulo)
                .setMessage(mensagem)
                .setPositiveButton("Ver no GitHub", (dialog, which) -> {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(resultado.urlDaRelease));
                    startActivity(intent);
                })
                .setNegativeButton("Depois", null)
                .show();
    }

    private void criarCanalNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Canal My Toolkit";
            String description = "Notificações do My Toolkit";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel("my_tools_channel", name, importance);
            channel.setDescription(description);

            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
}