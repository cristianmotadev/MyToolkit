package com.mtp.mytoolsproject;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.security.SecureRandom;

public class PasswordGeneratorActivity extends AppCompatActivity {

    private static final String MAIUSCULAS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String MINUSCULAS = "abcdefghijklmnopqrstuvwxyz";
    private static final String NUMEROS = "0123456789";
    private static final String SIMBOLOS = "!@#$%^&*()-_=+[]{}<>?";

    private SeekBar seekTamanho;
    private TextView txtTamanho, txtSenhaGerada, txtForca;
    private CheckBox checkMaiusculas, checkMinusculas, checkNumeros, checkSimbolos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_password_generator);

        seekTamanho = findViewById(R.id.seekTamanhoSenha);
        txtTamanho = findViewById(R.id.txtTamanhoSenha);
        txtSenhaGerada = findViewById(R.id.txtSenhaGerada);
        txtForca = findViewById(R.id.txtForcaSenha);
        checkMaiusculas = findViewById(R.id.checkMaiusculas);
        checkMinusculas = findViewById(R.id.checkMinusculas);
        checkNumeros = findViewById(R.id.checkNumeros);
        checkSimbolos = findViewById(R.id.checkSimbolos);
        Button btnGerar = findViewById(R.id.btnGerarSenha);
        Button btnCopiar = findViewById(R.id.btnCopiarSenhaGerada);

        seekTamanho.setMax(24); // progresso 0-24 -> tamanho 8-32
        seekTamanho.setProgress(8); // 16 caracteres por padrão
        atualizarTamanhoTexto();

        seekTamanho.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                atualizarTamanhoTexto();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnGerar.setOnClickListener(v -> gerarSenha());

        btnCopiar.setOnClickListener(v -> {
            String senha = txtSenhaGerada.getText().toString();
            if (senha.isEmpty() || senha.equals("—")) return;
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("Senha gerada", senha));
            Toast.makeText(this, "Senha copiada!", Toast.LENGTH_SHORT).show();
        });

        gerarSenha();
    }

    private int lerTamanho() {
        return seekTamanho.getProgress() + 8;
    }

    private void atualizarTamanhoTexto() {
        txtTamanho.setText("Tamanho: " + lerTamanho() + " caracteres");
    }

    private void gerarSenha() {
        StringBuilder alfabeto = new StringBuilder();
        if (checkMaiusculas.isChecked()) alfabeto.append(MAIUSCULAS);
        if (checkMinusculas.isChecked()) alfabeto.append(MINUSCULAS);
        if (checkNumeros.isChecked()) alfabeto.append(NUMEROS);
        if (checkSimbolos.isChecked()) alfabeto.append(SIMBOLOS);

        if (alfabeto.length() == 0) {
            Toast.makeText(this, "Selecione ao menos um tipo de caractere.", Toast.LENGTH_SHORT).show();
            return;
        }

        int tamanho = lerTamanho();
        SecureRandom random = new SecureRandom();
        StringBuilder senha = new StringBuilder();
        for (int i = 0; i < tamanho; i++) {
            senha.append(alfabeto.charAt(random.nextInt(alfabeto.length())));
        }

        txtSenhaGerada.setText(senha.toString());
        avaliarForca(tamanho, alfabeto.length());
    }

    /** Estima a força pela entropia (bits) — log2(tamanho_alfabeto ^ comprimento). */
    private void avaliarForca(int tamanho, int tamanhoAlfabeto) {
        double entropiaBits = tamanho * (Math.log(tamanhoAlfabeto) / Math.log(2));
        String forca;
        int cor;
        if (entropiaBits < 40) { forca = "🔴 Fraca"; cor = 0xFFFF5252; }
        else if (entropiaBits < 60) { forca = "🟠 Razoável"; cor = 0xFFFFA726; }
        else if (entropiaBits < 80) { forca = "🟡 Boa"; cor = 0xFFFFEB3B; }
        else { forca = "🟢 Muito forte"; cor = 0xFF4CAF50; }

        txtForca.setText(forca + " (" + Math.round(entropiaBits) + " bits de entropia)");
        txtForca.setTextColor(cor);
    }
}
