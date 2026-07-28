package com.mtp.mytoolsproject;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WifiPasswordsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Tela exibe senhas em texto puro: bloqueia screenshot e
        // aparição em miniaturas do app switcher / gravação de tela.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.activity_wifi_passwords);

        LinearLayout container = findViewById(R.id.containerWifiCards);
        LayoutInflater inflater = LayoutInflater.from(this);

        // Leitura via root + parsing de XML movidos para fora da UI thread,
        // pois antes rodavam de forma síncrona em onCreate() e podiam travar a tela.
        new Thread(() -> {
            try {
                Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "cat /data/misc/apexdata/com.android.wifi/WifiConfigStore.xml"});
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                StringBuilder fileContent = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    fileContent.append(line).append("\n");
                }
                process.waitFor();

                String xml = fileContent.toString();

                if (xml.isEmpty()) {
                    runOnUiThread(() -> adicionarCard(inflater, container, "❌ Erro", "Não foi possível ler o arquivo. Verifique o acesso Root."));
                    return;
                }

                Pattern networkPattern = Pattern.compile("<Network>(.*?)</Network>", Pattern.DOTALL);
                Matcher networkMatcher = networkPattern.matcher(xml);

                int count = 0;
                while (networkMatcher.find()) {
                    String netBlock = networkMatcher.group(1);

                    String ssid = "Desconhecido";
                    Matcher ssidMatcher = Pattern.compile("<string name=\"SSID\">&quot;(.*?)&quot;</string>").matcher(netBlock);
                    if (ssidMatcher.find()) {
                        String ssidBruto = ssidMatcher.group(1);
                        ssid = android.text.Html.fromHtml(ssidBruto, android.text.Html.FROM_HTML_MODE_LEGACY).toString();
                    }

                    String password = "Sem senha / Aberta";
                    Matcher passMatcher = Pattern.compile("<string name=\"PreSharedKey\">&quot;(.*?)&quot;</string>").matcher(netBlock);
                    if (passMatcher.find()) {
                        password = passMatcher.group(1);
                    }

                    count++;
                    final String ssidFinal = ssid;
                    final String passwordFinal = password;
                    runOnUiThread(() -> adicionarCard(inflater, container, "🌐 " + ssidFinal, "🔑 Senha: " + passwordFinal));
                }

                if (count == 0) {
                    runOnUiThread(() -> adicionarCard(inflater, container, "⚠️ Aviso", "Nenhuma rede salva encontrada no arquivo."));
                }

            } catch (Exception e) {
                runOnUiThread(() -> adicionarCard(inflater, container, "⚠️ Erro Root", String.valueOf(e.getMessage())));
            }
        }).start();
    }

    private void adicionarCard(LayoutInflater inflater, LinearLayout container, String titulo, String subtitulo) {
        View cardView = inflater.inflate(R.layout.card_item, container, false);
        TextView title = cardView.findViewById(R.id.txtCardTitle);
        TextView subtitle = cardView.findViewById(R.id.txtCardSubtitle);

        title.setText(titulo);
        subtitle.setText(subtitulo);
        container.addView(cardView);
    }
}
