package com.mtp.mytoolsproject;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Criptografa/descriptografa qualquer arquivo com uma senha, usando
 * AES-256/GCM (autenticado) com chave derivada por PBKDF2-HMAC-SHA256
 * (100.000 iterações). Formato do arquivo de saída: [salt 16B][IV 12B][dados cifrados + tag].
 */
public class FileEncryptionActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_ARQUIVO = 601;
    private static final int TAMANHO_SALT = 16;
    private static final int TAMANHO_IV = 12;
    private static final int ITERACOES_PBKDF2 = 100_000;

    private Uri arquivoSelecionado;
    private String nomeArquivoSelecionado;
    private TextView txtArquivo, txtStatus;
    private EditText editSenha;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_encryption);

        txtArquivo = findViewById(R.id.txtArquivoSelecionadoCripto);
        txtStatus = findViewById(R.id.txtStatusCripto);
        editSenha = findViewById(R.id.editSenhaCripto);

        Button btnEscolher = findViewById(R.id.btnEscolherArquivoCripto);
        Button btnCriptografar = findViewById(R.id.btnCriptografar);
        Button btnDescriptografar = findViewById(R.id.btnDescriptografar);

        btnEscolher.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, REQUEST_CODE_ARQUIVO);
        });

        btnCriptografar.setOnClickListener(v -> processar(true));
        btnDescriptografar.setOnClickListener(v -> processar(false));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_ARQUIVO && resultCode == RESULT_OK && data != null && data.getData() != null) {
            arquivoSelecionado = data.getData();
            nomeArquivoSelecionado = obterNomeArquivo(arquivoSelecionado);
            txtArquivo.setText("📄 " + nomeArquivoSelecionado);
            txtStatus.setText("");
        }
    }

    private String obterNomeArquivo(Uri uri) {
        String nome = "arquivo";
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) nome = cursor.getString(idx);
            }
        } catch (Exception ignored) {}
        return nome;
    }

    private void processar(boolean criptografar) {
        if (arquivoSelecionado == null) {
            Toast.makeText(this, "Escolha um arquivo primeiro.", Toast.LENGTH_SHORT).show();
            return;
        }
        String senha = editSenha.getText().toString();
        if (senha.isEmpty()) {
            Toast.makeText(this, "Digite uma senha.", Toast.LENGTH_SHORT).show();
            return;
        }

        txtStatus.setText("⏳ Processando...");
        txtStatus.setTextColor(0xFFAAAAAA);

        new Thread(() -> {
            try {
                byte[] dadosEntrada = lerBytesUri(arquivoSelecionado);
                byte[] resultado = criptografar ? criptografar(dadosEntrada, senha) : descriptografar(dadosEntrada, senha);

                String nomeSaida = criptografar
                        ? nomeArquivoSelecionado + ".enc"
                        : (nomeArquivoSelecionado.endsWith(".enc")
                                ? nomeArquivoSelecionado.substring(0, nomeArquivoSelecionado.length() - 4)
                                : "descriptografado_" + nomeArquivoSelecionado);

                File arquivoSaida = new File(getCacheDir(), nomeSaida);
                FileOutputStream fos = new FileOutputStream(arquivoSaida);
                fos.write(resultado);
                fos.close();

                runOnUiThread(() -> {
                    txtStatus.setText("✅ Concluído: " + nomeSaida);
                    txtStatus.setTextColor(0xFF4CAF50);
                    compartilharArquivo(arquivoSaida);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    txtStatus.setText("❌ Erro: " + (criptografar
                            ? "falha ao criptografar (" + e.getMessage() + ")"
                            : "senha incorreta ou arquivo inválido/corrompido."));
                    txtStatus.setTextColor(0xFFFF5252);
                });
            }
        }).start();
    }

    private byte[] lerBytesUri(Uri uri) throws Exception {
        InputStream is = getContentResolver().openInputStream(uri);
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int lidos;
        while ((lidos = is.read(chunk)) != -1) buffer.write(chunk, 0, lidos);
        is.close();
        return buffer.toByteArray();
    }

    private byte[] criptografar(byte[] dados, String senha) throws Exception {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[TAMANHO_SALT];
        random.nextBytes(salt);
        byte[] iv = new byte[TAMANHO_IV];
        random.nextBytes(iv);

        SecretKeySpec chave = derivarChave(senha, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(128, iv));
        byte[] textoCifrado = cipher.doFinal(dados);

        byte[] saida = new byte[salt.length + iv.length + textoCifrado.length];
        System.arraycopy(salt, 0, saida, 0, salt.length);
        System.arraycopy(iv, 0, saida, salt.length, iv.length);
        System.arraycopy(textoCifrado, 0, saida, salt.length + iv.length, textoCifrado.length);
        return saida;
    }

    private byte[] descriptografar(byte[] dados, String senha) throws Exception {
        byte[] salt = Arrays.copyOfRange(dados, 0, TAMANHO_SALT);
        byte[] iv = Arrays.copyOfRange(dados, TAMANHO_SALT, TAMANHO_SALT + TAMANHO_IV);
        byte[] textoCifrado = Arrays.copyOfRange(dados, TAMANHO_SALT + TAMANHO_IV, dados.length);

        SecretKeySpec chave = derivarChave(senha, salt);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(128, iv));
        return cipher.doFinal(textoCifrado); // GCM falha aqui automaticamente se a senha estiver errada
    }

    private SecretKeySpec derivarChave(String senha, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(senha.toCharArray(), salt, ITERACOES_PBKDF2, 256);
        byte[] chaveBytes = factory.generateSecret(spec).getEncoded();
        return new SecretKeySpec(chaveBytes, "AES");
    }

    private void compartilharArquivo(File arquivo) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", arquivo);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_STREAM, uri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivity(Intent.createChooser(intent, "Salvar/compartilhar arquivo"));
    }
}
