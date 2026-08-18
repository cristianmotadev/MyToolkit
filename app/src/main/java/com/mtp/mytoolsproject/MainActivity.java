package com.mtp.mytoolsproject;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private boolean hasRoot = false;
    private CardView cardWifi, cardScanner, cardWifiScan, cardNfc, cardSubnet, cardPortScanner,
            cardSpeedTest, cardBluetooth, cardTraceroute, cardDns, cardSystemInfo,
            cardAppAudit, cardPasswordGenerator, cardHashCalculator, cardFileEncryption, cardConfig;

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

        // Inicializa todos os cards
        cardWifi = findViewById(R.id.cardWifi);
        cardScanner = findViewById(R.id.cardScanner);
        cardWifiScan = findViewById(R.id.cardWifiScan);
        cardNfc = findViewById(R.id.cardNfc);
        cardSubnet = findViewById(R.id.cardSubnet);
        cardPortScanner = findViewById(R.id.cardPortScanner);
        cardSpeedTest = findViewById(R.id.cardSpeedTest);
        cardBluetooth = findViewById(R.id.cardBluetooth);
        cardTraceroute = findViewById(R.id.cardTraceroute);
        cardDns = findViewById(R.id.cardDns);
        cardSystemInfo = findViewById(R.id.cardSystemInfo);
        cardAppAudit = findViewById(R.id.cardAppAudit);
        cardPasswordGenerator = findViewById(R.id.cardPasswordGenerator);
        cardHashCalculator = findViewById(R.id.cardHashCalculator);
        cardFileEncryption = findViewById(R.id.cardFileEncryption);
        cardConfig = findViewById(R.id.cardConfig);

        // Verifica root e atualiza UI
        verificarRootEAjustarUI();

        configurarCliquesDosCards();

        verificarAtualizacaoEmSegundoPlano();
    }

    /**
     * Verifica disponibilidade de root e ajusta a interface:
     * - Exibe popup informativo sobre ferramentas disponíveis/indisponíveis
     * - Marca cards em vermelho para ferramentas que requerem root
     */
    private void verificarRootEAjustarUI() {
        new Thread(() -> {
            hasRoot = NetworkUtils.verificarRootDisponivel();
            runOnUiThread(() -> {
                if (!hasRoot) {
                    mostrarPopupSemRoot();
                    destacarCardsSemRoot();
                }
            });
        }).start();
    }

    /**
     * Exibe popup informativo listando ferramentas que funcionam e não funcionam sem root.
     */
    private void mostrarPopupSemRoot() {
        String mensagem = "⚠️ Root não detectado!\n\n" +
                "🔧 Ferramentas que FUNCIONAM sem root:\n" +
                "• Radar Wi-Fi (redes próximas)\n" +
                "• Radar Bluetooth\n" +
                "• Scanner de Portas\n" +
                "• Traceroute\n" +
                "• Consulta DNS\n" +
                "• Teste de Velocidade\n" +
                "• Calculadora de Sub-rede\n" +
                "• Wake-on-LAN\n" +
                "• Módulo NFC\n" +
                "• Gerador de Senhas\n" +
                "• Calculadora de Hash\n" +
                "• Criptografar Arquivo\n" +
                "• Auditoria de Apps\n" +
                "• Informações do Sistema\n" +
                "• Gerenciamento de dispositivos\n" +
                "• Exportar/Importar banco de dados\n\n" +
                "❌ Ferramentas que NÃO funcionam sem root:\n" +
                "• Senhas Wi-Fi Salvas\n" +
                "• Monitoramento mDNS em segundo plano\n" +
                "• Radar de Dispositivos na Rede Local";

        new AlertDialog.Builder(this)
                .setTitle("⚠️ Permissões Limitadas")
                .setMessage(mensagem)
                .setPositiveButton("Entendido", null)
                .setNeutralButton("Ver nas Configurações", (dialog, which) ->
                        startActivity(new Intent(this, SettingsActivity.class)))
                .show();
    }

    /**
     * Destaca em vermelho os cards das ferramentas que não funcionam sem root.
     */
    private void destacarCardsSemRoot() {
        // Cards que NÃO funcionam sem root (vermelho)
        int corVermelha = Color.parseColor("#FF5252");
        int corBordaVermelha = Color.parseColor("#D32F2F");

        // Senhas Wi-Fi Salvas
        cardWifi.setCardBackgroundColor(corVermelha);
        cardWifi.setStrokeColor(corBordaVermelha);

        // Radar de Dispositivos na Rede Local
        cardScanner.setCardBackgroundColor(corVermelha);
        cardScanner.setStrokeColor(corBordaVermelha);

        // Configurações (contém switch do mDNS que requer root)
        // O card em si ainda é útil para outras configurações, mas destacamos que tem funcionalidade limitada
        cardConfig.setStrokeColor(corBordaVermelha);
    }

    /**
     * Configura os listeners de clique para cada card.
     */
    private void configurarCliquesDosCards() {
        cardWifi.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, WifiPasswordsActivity.class)));

        cardScanner.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, NetworkScannerActivity.class)));

        cardWifiScan.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, WifiScannerActivity.class)));

        cardNfc.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, NfcToolsActivity.class)));

        cardSubnet.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, SubnetCalculatorActivity.class)));

        cardPortScanner.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, PortScannerActivity.class)));

        cardSpeedTest.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, SpeedTestActivity.class)));

        cardBluetooth.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, BluetoothScannerActivity.class)));

        cardTraceroute.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, TracerouteActivity.class)));

        cardDns.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, DnsLookupActivity.class)));

        cardSystemInfo.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, SystemInfoActivity.class)));

        cardAppAudit.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, AppAuditActivity.class)));

        cardPasswordGenerator.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, PasswordGeneratorActivity.class)));

        cardHashCalculator.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, HashCalculatorActivity.class)));

        cardFileEncryption.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, FileEncryptionActivity.class)));

        cardConfig.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, SettingsActivity.class)));
    }

    /**
     * Checa em segundo plano se há atualização, respeitando o canal (Oficial/Beta)
     * e o tipo (Releases/Commits) escolhidos nas Configurações. Só mostra o
     * diálogo se realmente encontrar algo novo — em caso de erro (sem internet,
     * repositório privado, etc.) fica em silêncio, sem incomodar o usuário toda
     * vez que abre o app.
     */
    private void verificarAtualizacaoEmSegundoPlano() {
        SharedPreferences prefsAtualizacao = getSharedPreferences("NetworkPrefs", MODE_PRIVATE);
        boolean beta = prefsAtualizacao.getBoolean("canal_atualizacao_beta", false);
        boolean commit = prefsAtualizacao.getBoolean("tipo_atualizacao_commit", false);
        UpdateChecker.Canal canal = beta ? UpdateChecker.Canal.BETA : UpdateChecker.Canal.OFICIAL;
        UpdateChecker.Tipo tipo = commit ? UpdateChecker.Tipo.COMMIT : UpdateChecker.Tipo.RELEASE;

        new Thread(() -> {
            UpdateChecker.ResultadoVerificacao resultado = UpdateChecker.verificar(this, canal, tipo);
            if (resultado.temAtualizacao) {
                runOnUiThread(() -> mostrarDialogoAtualizacao(resultado));
            }
        }).start();
    }

    /** Monta o changelog de forma organizada (título, autor, data, descrição) e oferece baixar direto quando há .apk. */
    private void mostrarDialogoAtualizacao(UpdateChecker.ResultadoVerificacao resultado) {
        StringBuilder mensagem = new StringBuilder();
        mensagem.append("📌 ").append(resultado.titulo).append("\n");
        mensagem.append("👤 ").append(resultado.autor).append("  •  🗓️ ").append(resultado.dataFormatada).append("\n\n");

        if (resultado.changelog != null && !resultado.changelog.trim().isEmpty()) {
            mensagem.append(resultado.changelog.trim());
        } else {
            mensagem.append("(sem descrição adicional)");
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(resultado.tituloDialogo())
                .setMessage(mensagem.toString())
                .setNegativeButton("Depois", null)
                .setNeutralButton("Ver no GitHub", (dialog, which) ->
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(resultado.urlPagina))));

        if (resultado.urlApk != null) {
            builder.setPositiveButton("⬇️ Baixar e Instalar", (dialog, which) ->
                    UpdateDownloader.baixarEInstalar(this, resultado.urlApk, resultado.nomeArquivoApk));
        }

        builder.show();
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