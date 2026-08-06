package com.mtp.mytoolsproject;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lê as senhas Wi-Fi salvas no sistema (via root) e mantém um backup próprio
 * dentro do armazenamento do app. Isso resolve um problema real: quando o
 * usuário manda "esquecer" uma rede no Android, o sistema apaga a senha do
 * arquivo de configuração — e como esta tela sempre lia direto desse arquivo,
 * a senha "sumia" também do app. Agora, toda vez que a tela é aberta, ela
 * funde o que está ao vivo no sistema com o que já tinha sido salvo antes,
 * então uma rede esquecida no Android continua aparecendo aqui (marcada como
 * removida do sistema) — desde que o app já tivesse lido essa rede alguma vez
 * ANTES dela ser esquecida.
 */
public class WifiPasswordsActivity extends AppCompatActivity {

    private static final String ARQUIVO_BACKUP = "wifi_passwords_backup.json";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Tela exibe senhas em texto puro: bloqueia screenshot e
        // aparição em miniaturas do app switcher / gravação de tela.
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.activity_wifi_passwords);

        LinearLayout container = findViewById(R.id.containerWifiCards);
        LayoutInflater inflater = LayoutInflater.from(this);

        new Thread(() -> {
            try {
                Map<String, String> redesAoVivo = lerRedesAoVivo();

                JSONObject backup = carregarBackup();
                Set<String> chavesAoVivo = new HashSet<>(redesAoVivo.keySet());

                long agora = System.currentTimeMillis();
                for (Map.Entry<String, String> entry : redesAoVivo.entrySet()) {
                    JSONObject obj = new JSONObject();
                    obj.put("senha", entry.getValue());
                    obj.put("ultimaVez", agora);
                    backup.put(entry.getKey(), obj);
                }
                salvarBackup(backup);

                if (backup.length() == 0) {
                    runOnUiThread(() -> adicionarCard(inflater, container, "⚠️ Aviso", "Nenhuma rede salva encontrada.", null, null, false));
                    return;
                }

                Iterator<String> keys = backup.keys();
                while (keys.hasNext()) {
                    String ssid = keys.next();
                    JSONObject obj = backup.getJSONObject(ssid);
                    String senha = obj.optString("senha", "Sem senha / Aberta");
                    boolean aindaNoSistema = chavesAoVivo.contains(ssid);
                    String tituloExibido = aindaNoSistema ? ("🌐 " + ssid) : ("🗄️ " + ssid + " (removida do sistema, salva no app)");
                    boolean temSenhaReal = !senha.equals("Sem senha / Aberta");

                    runOnUiThread(() -> adicionarCard(inflater, container, tituloExibido, "🔑 Senha: " + senha, ssid, senha, temSenhaReal));
                }

            } catch (Exception e) {
                runOnUiThread(() -> adicionarCard(inflater, container, "⚠️ Erro Root", String.valueOf(e.getMessage()), null, null, false));
            }
        }).start();
    }

    private Map<String, String> lerRedesAoVivo() throws Exception {
        Map<String, String> resultado = new HashMap<>();

        Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "cat /data/misc/apexdata/com.android.wifi/WifiConfigStore.xml"});
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        StringBuilder fileContent = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            fileContent.append(line).append("\n");
        }
        process.waitFor();

        String xml = fileContent.toString();
        if (xml.isEmpty()) return resultado;

        Pattern networkPattern = Pattern.compile("<Network>(.*?)</Network>", Pattern.DOTALL);
        Matcher networkMatcher = networkPattern.matcher(xml);

        while (networkMatcher.find()) {
            String netBlock = networkMatcher.group(1);

            String ssid = "Desconhecido";
            Matcher ssidMatcher = Pattern.compile("<string name=\"SSID\">&quot;(.*?)&quot;</string>").matcher(netBlock);
            if (ssidMatcher.find()) {
                ssid = android.text.Html.fromHtml(ssidMatcher.group(1), android.text.Html.FROM_HTML_MODE_LEGACY).toString();
            }

            String password = "Sem senha / Aberta";
            Matcher passMatcher = Pattern.compile("<string name=\"PreSharedKey\">&quot;(.*?)&quot;</string>").matcher(netBlock);
            if (passMatcher.find()) {
                password = passMatcher.group(1);
            }

            resultado.put(ssid, password);
        }
        return resultado;
    }

    private JSONObject carregarBackup() {
        try {
            File file = new File(getFilesDir(), ARQUIVO_BACKUP);
            if (!file.exists()) return new JSONObject();
            FileInputStream fis = new FileInputStream(file);
            byte[] buffer = new byte[(int) file.length()];
            fis.read(buffer);
            fis.close();
            String content = new String(buffer, StandardCharsets.UTF_8);
            return content.startsWith("{") ? new JSONObject(content) : new JSONObject();
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private void salvarBackup(JSONObject backup) {
        try {
            File file = new File(getFilesDir(), ARQUIVO_BACKUP);
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(backup.toString().getBytes(StandardCharsets.UTF_8));
            fos.close();
        } catch (Exception ignored) {}
    }

    private void adicionarCard(LayoutInflater inflater, LinearLayout container, String titulo, String subtitulo,
                                String ssid, String senhaParaCopiar, boolean mostrarBotaoCopiar) {
        View cardView = inflater.inflate(R.layout.card_item, container, false);
        TextView title = cardView.findViewById(R.id.txtCardTitle);
        TextView subtitle = cardView.findViewById(R.id.txtCardSubtitle);
        Button btnCopiar = cardView.findViewById(R.id.btnCopiarSenha);
        Button btnQrCode = cardView.findViewById(R.id.btnGerarQrWifi);
        Button btnCompartilharTexto = cardView.findViewById(R.id.btnCompartilharTexto);

        title.setText(titulo);
        subtitle.setText(subtitulo);

        if (mostrarBotaoCopiar && senhaParaCopiar != null) {
            btnCopiar.setVisibility(View.VISIBLE);
            btnCopiar.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("Senha Wi-Fi", senhaParaCopiar));
                Toast.makeText(WifiPasswordsActivity.this, "Senha copiada!", Toast.LENGTH_SHORT).show();
            });
        } else {
            btnCopiar.setVisibility(View.GONE);
        }

        if (ssid != null) {
            btnQrCode.setVisibility(View.VISIBLE);
            btnQrCode.setOnClickListener(v -> mostrarDialogoQrCode(ssid, senhaParaCopiar));

            btnCompartilharTexto.setVisibility(View.VISIBLE);
            btnCompartilharTexto.setOnClickListener(v -> compartilharComoTexto(ssid, senhaParaCopiar));
        } else {
            btnQrCode.setVisibility(View.GONE);
            btnCompartilharTexto.setVisibility(View.GONE);
        }

        container.addView(cardView);
    }

    /** Gera e mostra um QR Code que outros aparelhos podem escanear para conectar direto à rede. */
    private void mostrarDialogoQrCode(String ssid, String senha) {
        try {
            String conteudo = QrCodeUtils.montarStringWifiQr(ssid, senha);
            Bitmap bitmap = QrCodeUtils.gerarQrCode(conteudo, 600);

            ImageView imageView = new ImageView(this);
            imageView.setImageBitmap(bitmap);
            int padding = (int) (24 * getResources().getDisplayMetrics().density);
            imageView.setPadding(padding, padding, padding, padding);

            new AlertDialog.Builder(this)
                    .setTitle("📷 " + ssid)
                    .setView(imageView)
                    .setMessage("Aponte a câmera de outro celular pra esse QR Code pra conectar direto na rede, ou compartilhe a imagem por qualquer app.")
                    .setPositiveButton("Fechar", null)
                    .setNeutralButton("📤 Compartilhar", (dialog, which) -> compartilharQrCode(bitmap, ssid))
                    .show();
        } catch (Exception e) {
            Toast.makeText(this, "Não foi possível gerar o QR Code: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    /** Compartilha o nome da rede e a senha como texto simples, via qualquer app (WhatsApp, SMS, e-mail...). */
    private void compartilharComoTexto(String ssid, String senha) {
        String texto = "📶 Rede Wi-Fi: " + ssid + "\n🔑 Senha: " + senha;
        android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(android.content.Intent.EXTRA_TEXT, texto);
        startActivity(android.content.Intent.createChooser(intent, "Compartilhar rede Wi-Fi"));
    }

    /** Salva o QR Code como imagem e abre o menu de compartilhamento do Android (WhatsApp, e-mail, Bluetooth, etc). */
    private void compartilharQrCode(Bitmap bitmap, String ssid) {
        try {
            String nomeArquivo = "qrcode_wifi_" + ssid.replaceAll("[^a-zA-Z0-9]", "_") + ".png";
            File arquivo = new File(getCacheDir(), nomeArquivo);
            FileOutputStream fos = new FileOutputStream(arquivo);
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();

            android.net.Uri uri = androidx.core.content.FileProvider.getUriForFile(
                    this, getPackageName() + ".fileprovider", arquivo);

            android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_SEND);
            intent.setType("image/png");
            intent.putExtra(android.content.Intent.EXTRA_STREAM, uri);
            intent.putExtra(android.content.Intent.EXTRA_TEXT, "QR Code para conectar na rede Wi-Fi: " + ssid);
            intent.addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(android.content.Intent.createChooser(intent, "Compartilhar QR Code"));
        } catch (Exception e) {
            Toast.makeText(this, "Erro ao compartilhar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
