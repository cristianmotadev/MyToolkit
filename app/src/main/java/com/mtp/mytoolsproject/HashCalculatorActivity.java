package com.mtp.mytoolsproject;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;

public class HashCalculatorActivity extends AppCompatActivity {

    private static final int REQUEST_CODE_ARQUIVO = 501;

    private EditText editTexto;
    private TextView txtMd5, txtSha1, txtSha256, txtSha512, txtArquivoSelecionado;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hash_calculator);

        editTexto = findViewById(R.id.editHashTexto);
        txtMd5 = findViewById(R.id.txtHashMd5);
        txtSha1 = findViewById(R.id.txtHashSha1);
        txtSha256 = findViewById(R.id.txtHashSha256);
        txtSha512 = findViewById(R.id.txtHashSha512);
        txtArquivoSelecionado = findViewById(R.id.txtArquivoSelecionadoHash);

        Button btnCalcularTexto = findViewById(R.id.btnCalcularHashTexto);
        Button btnEscolherArquivo = findViewById(R.id.btnEscolherArquivoHash);

        btnCalcularTexto.setOnClickListener(v -> {
            txtArquivoSelecionado.setText("");
            calcularHashes(editTexto.getText().toString().getBytes());
        });

        btnEscolherArquivo.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            startActivityForResult(intent, REQUEST_CODE_ARQUIVO);
        });

        View.OnClickListener copiarHash = v -> {
            String texto = ((TextView) v).getText().toString();
            if (texto.isEmpty()) return;
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Hash", texto));
            Toast.makeText(this, "Hash copiado!", Toast.LENGTH_SHORT).show();
        };
        txtMd5.setOnClickListener(copiarHash);
        txtSha1.setOnClickListener(copiarHash);
        txtSha256.setOnClickListener(copiarHash);
        txtSha512.setOnClickListener(copiarHash);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_ARQUIVO && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            new Thread(() -> {
                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    byte[] chunk = new byte[8192];
                    int lidos;
                    while ((lidos = is.read(chunk)) != -1) buffer.write(chunk, 0, lidos);
                    byte[] bytesArquivo = buffer.toByteArray();

                    runOnUiThread(() -> {
                        txtArquivoSelecionado.setText("📄 Arquivo selecionado (" + bytesArquivo.length + " bytes)");
                        calcularHashes(bytesArquivo);
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> Toast.makeText(this, "Erro ao ler arquivo: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            }).start();
        }
    }

    private void calcularHashes(byte[] dados) {
        txtMd5.setText(calcular("MD5", dados));
        txtSha1.setText(calcular("SHA-1", dados));
        txtSha256.setText(calcular("SHA-256", dados));
        txtSha512.setText(calcular("SHA-512", dados));
    }

    private String calcular(String algoritmo, byte[] dados) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algoritmo);
            byte[] resultado = digest.digest(dados);
            return NfcUtils.bytesParaHex(resultado).toLowerCase();
        } catch (Exception e) {
            return "erro";
        }
    }
}
