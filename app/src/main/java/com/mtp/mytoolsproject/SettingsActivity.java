package com.mtp.mytoolsproject;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
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
import androidx.core.content.FileProvider;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class SettingsActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_IMPORTAR = 301;

    private Switch switchMdns, switchLocationPermission, switchNotificarNovos, switchBloqueioApp;
    private EditText editIntervalo, editIntervaloWifiPadrao, editIntervaloNetworkPadrao;
    private Button btnSalvarIntervalo, btnEditJson, btnLimparCache, btnVerificarRoot, btnSalvarIntervalosPadrao, btnAlterarPin, btnExportarCache, btnImportarCache;
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
        switchBloqueioApp = findViewById(R.id.switchBloqueioApp);
        btnAlterarPin = findViewById(R.id.btnAlterarPin);
        btnExportarCache = findViewById(R.id.btnExportarCache);
        btnImportarCache = findViewById(R.id.btnImportarCache);
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

        switchBloqueioApp.setChecked(SecurityUtils.bloqueioAtivo(this));
        switchBloqueioApp.setOnCheckedChangeListener((buttonView, isChecked) ->
                SecurityUtils.definirBloqueioAtivo(this, isChecked));

        btnAlterarPin.setOnClickListener(v -> confirmarAlteracaoDePin());

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

        btnExportarCache.setOnClickListener(v -> exportarBancoDeDados());

        btnImportarCache.setOnClickListener(v -> importarBancoDeDados());
    }

    private void exportarBancoDeDados() {
        try {
            JSONObject cache = NetworkUtils.carregarCache(this);
            File exportado = new File(getCacheDir(), "mac_cache_export.json");
            FileOutputStream fos = new FileOutputStream(exportado);
            fos.write(cache.toString(2).getBytes(StandardCharsets.UTF_8));
            fos.close();

            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", exportado);
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Exportar banco de dados"));
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao exportar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void importarBancoDeDados() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, REQUEST_CODE_IMPORTAR);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_IMPORTAR && resultCode == RESULT_OK && data != null && data.getData() != null) {
            new AlertDialog.Builder(this)
                    .setTitle("Importar banco de dados")
                    .setMessage("Isso SUBSTITUI todos os dispositivos salvos atualmente pelo conteúdo do arquivo escolhido. Deseja continuar?")
                    .setPositiveButton("Sim, importar", (dialog, which) -> processarArquivoImportado(data.getData()))
                    .setNegativeButton("Cancelar", null)
                    .show();
        }
    }

    private void processarArquivoImportado(Uri uri) {
        try (InputStream is = getContentResolver().openInputStream(uri)) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int bytesLidos;
            while ((bytesLidos = is.read(chunk)) != -1) {
                buffer.write(chunk, 0, bytesLidos);
            }
            String conteudo = buffer.toString("UTF-8");
            JSONObject novoCache = new JSONObject(conteudo); // valida que é um JSON válido antes de gravar

            if (NetworkUtils.salvarCache(this, novoCache)) {
                Toast.makeText(this, "Banco de dados importado com sucesso!", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Falha ao salvar os dados importados.", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Arquivo inválido ou erro ao importar: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void confirmarAlteracaoDePin() {
        final EditText inputPinAtual = new EditText(this);
        inputPinAtual.setHint("PIN atual");
        inputPinAtual.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);

        new AlertDialog.Builder(this)
                .setTitle("Alterar PIN")
                .setMessage("Digite o PIN atual para continuar.")
                .setView(inputPinAtual)
                .setPositiveButton("Continuar", (dialog, which) -> {
                    String pinDigitado = inputPinAtual.getText().toString().trim();
                    if (SecurityUtils.validarPin(this, pinDigitado)) {
                        SecurityUtils.removerPin(this);
                        startActivity(new Intent(this, LockActivity.class));
                    } else {
                        Toast.makeText(this, "PIN incorreto.", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
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
        // O Radar de Dispositivos agora também liga/desliga esse mesmo serviço,
        // então sincroniza o switch aqui pra refletir o estado real.
        switchMdns.setChecked(prefs.getBoolean("mdns_continuous", false));
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
