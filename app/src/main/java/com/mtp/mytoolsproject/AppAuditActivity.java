package com.mtp.mytoolsproject;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Lista os apps instalados e quais permissões sensíveis ("perigosas", segundo
 * a classificação do próprio Android) cada um pede — câmera, microfone,
 * localização, contatos, SMS, etc. Ordenado do mais "arriscado" pro menos.
 */
public class AppAuditActivity extends AppCompatActivity {

    private static final String[] PERMISSOES_SENSIVEIS = {
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.READ_CONTACTS",
            "android.permission.READ_SMS",
            "android.permission.SEND_SMS",
            "android.permission.READ_CALL_LOG",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.BODY_SENSORS",
    };

    private static class ItemApp {
        String nome;
        String pacote;
        List<String> permissoesSensiveis;
    }

    private LinearLayout container;
    private EditText editBusca;
    private List<ItemApp> todosOsApps = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_audit);

        container = findViewById(R.id.containerAppAudit);
        editBusca = findViewById(R.id.editBuscaAppAudit);
        TextView txtStatus = findViewById(R.id.txtStatusAppAudit);

        editBusca.addTextChangedListener(new android.text.TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderizarLista(s.toString().trim().toLowerCase(Locale.getDefault()));
            }
            @Override public void afterTextChanged(android.text.Editable s) {}
        });

        txtStatus.setText("Carregando apps instalados...");
        new Thread(() -> {
            carregarApps();
            runOnUiThread(() -> {
                txtStatus.setText(todosOsApps.size() + " apps com permissões sensíveis — ordenados do mais para o menos arriscado.");
                renderizarLista("");
            });
        }).start();
    }

    private void carregarApps() {
        PackageManager pm = getPackageManager();
        List<PackageInfo> pacotes = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS);
        todosOsApps.clear();

        for (PackageInfo pacote : pacotes) {
            if (pacote.requestedPermissions == null) continue;

            List<String> sensiveisConcedidas = new ArrayList<>();
            for (int i = 0; i < pacote.requestedPermissions.length; i++) {
                String permissao = pacote.requestedPermissions[i];
                boolean ehSensivel = false;
                for (String p : PERMISSOES_SENSIVEIS) {
                    if (p.equals(permissao)) { ehSensivel = true; break; }
                }
                if (ehSensivel) {
                    boolean concedida = pacote.requestedPermissionsFlags != null
                            && i < pacote.requestedPermissionsFlags.length
                            && (pacote.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0;
                    if (concedida) sensiveisConcedidas.add(permissao.replace("android.permission.", ""));
                }
            }

            if (sensiveisConcedidas.isEmpty()) continue;

            ItemApp item = new ItemApp();
            ApplicationInfo appInfo = pacote.applicationInfo;
            item.nome = appInfo != null ? String.valueOf(pm.getApplicationLabel(appInfo)) : pacote.packageName;
            item.pacote = pacote.packageName;
            item.permissoesSensiveis = sensiveisConcedidas;
            todosOsApps.add(item);
        }

        Collections.sort(todosOsApps, (a, b) -> Integer.compare(b.permissoesSensiveis.size(), a.permissoesSensiveis.size()));
    }

    private void renderizarLista(String filtro) {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(this);

        for (ItemApp item : todosOsApps) {
            if (!filtro.isEmpty()
                    && !item.nome.toLowerCase(Locale.getDefault()).contains(filtro)
                    && !item.pacote.toLowerCase(Locale.getDefault()).contains(filtro)) {
                continue;
            }

            View card = inflater.inflate(R.layout.item_app_audit, container, false);
            TextView txtNome = card.findViewById(R.id.txtAppNome);
            TextView txtPacote = card.findViewById(R.id.txtAppPacote);
            TextView txtPermissoes = card.findViewById(R.id.txtAppPermissoes);

            int qtd = item.permissoesSensiveis.size();
            txtNome.setText((qtd >= 4 ? "🚨 " : qtd >= 2 ? "⚠️ " : "🔎 ") + item.nome);
            txtPacote.setText(item.pacote);
            txtPermissoes.setText(qtd + " permissão(ões) sensível(eis): " + String.join(", ", item.permissoesSensiveis));
            txtPermissoes.setTextColor(qtd >= 4 ? 0xFFFF5252 : qtd >= 2 ? 0xFFFFA726 : 0xFFAAAAAA);

            container.addView(card);
        }
    }
}
