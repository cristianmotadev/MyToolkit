package com.mtp.mytoolsproject;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

/**
 * Tela de bloqueio exibida antes de qualquer outra tela do app.
 *
 * - Se ainda não existe PIN configurado (primeiro uso): pede pra criar um.
 * - Se já existe: pede o PIN, e oferece biometria (digital/rosto) como atalho
 *   quando o aparelho tiver suporte e biometria cadastrada no sistema.
 * - Se o usuário desativou o bloqueio nas Configurações, essa tela nem aparece
 *   (MainActivity/SplashActivity são abertas direto — ver checagem no manifest/flow).
 */
public class LockActivity extends AppCompatActivity {

    private enum Etapa { CRIAR_PASSO_1, CRIAR_PASSO_2, DESBLOQUEAR }

    private Etapa etapaAtual;
    private String pinTemporario;

    private TextView txtTitulo;
    private TextView txtSubtitulo;
    private EditText editPin;
    private Button btnConfirmar;
    private Button btnBiometria;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lock);

        txtTitulo = findViewById(R.id.txtLockTitulo);
        txtSubtitulo = findViewById(R.id.txtLockSubtitulo);
        editPin = findViewById(R.id.editLockPin);
        btnConfirmar = findViewById(R.id.btnLockConfirmar);
        btnBiometria = findViewById(R.id.btnLockBiometria);

        boolean jaTemPin = SecurityUtils.existePinConfigurado(this);

        // Se já existe PIN configurado mas o usuário desativou o bloqueio nas
        // Configurações, pula direto — não força ninguém a usar o recurso.
        if (jaTemPin && !SecurityUtils.bloqueioAtivo(this)) {
            liberarAcesso();
            return;
        }

        etapaAtual = jaTemPin ? Etapa.DESBLOQUEAR : Etapa.CRIAR_PASSO_1;
        atualizarTela();

        btnConfirmar.setOnClickListener(v -> processarEntrada());
        btnBiometria.setOnClickListener(v -> mostrarPromptBiometrico());

        if (etapaAtual == Etapa.DESBLOQUEAR && biometriaDisponivel()) {
            mostrarPromptBiometrico();
        }
    }

    private void atualizarTela() {
        editPin.setText("");
        switch (etapaAtual) {
            case CRIAR_PASSO_1:
                txtTitulo.setText("🔒 Configurar Bloqueio");
                txtSubtitulo.setText("Crie um PIN de 4 a 6 dígitos para proteger o app.");
                btnBiometria.setVisibility(android.view.View.GONE);
                break;
            case CRIAR_PASSO_2:
                txtTitulo.setText("🔒 Confirme o PIN");
                txtSubtitulo.setText("Digite o mesmo PIN novamente.");
                btnBiometria.setVisibility(android.view.View.GONE);
                break;
            case DESBLOQUEAR:
                txtTitulo.setText("🔒 My Toolkit");
                txtSubtitulo.setText("Digite seu PIN ou use a biometria.");
                btnBiometria.setVisibility(biometriaDisponivel() ? android.view.View.VISIBLE : android.view.View.GONE);
                break;
        }
    }

    private boolean biometriaDisponivel() {
        BiometricManager biometricManager = BiometricManager.from(this);
        int resultado = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK);
        return resultado == BiometricManager.BIOMETRIC_SUCCESS;
    }

    private void mostrarPromptBiometrico() {
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Desbloquear My Toolkit")
                .setSubtitle("Use sua digital ou rosto cadastrado")
                .setNegativeButtonText("Usar PIN")
                .build();

        BiometricPrompt biometricPrompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        liberarAcesso();
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        // Usuário cancelou ou escolheu usar PIN — não faz nada, só volta pra tela de PIN
                    }
                });

        biometricPrompt.authenticate(promptInfo);
    }

    private void processarEntrada() {
        String valor = editPin.getText().toString().trim();

        if (valor.length() < 4 || valor.length() > 6) {
            Toast.makeText(this, "O PIN deve ter entre 4 e 6 dígitos.", Toast.LENGTH_SHORT).show();
            return;
        }

        switch (etapaAtual) {
            case CRIAR_PASSO_1:
                pinTemporario = valor;
                etapaAtual = Etapa.CRIAR_PASSO_2;
                atualizarTela();
                break;

            case CRIAR_PASSO_2:
                if (valor.equals(pinTemporario)) {
                    SecurityUtils.salvarNovoPin(this, valor);
                    Toast.makeText(this, "PIN configurado com sucesso!", Toast.LENGTH_SHORT).show();
                    liberarAcesso();
                } else {
                    Toast.makeText(this, "Os PINs não coincidem. Tente novamente.", Toast.LENGTH_SHORT).show();
                    pinTemporario = null;
                    etapaAtual = Etapa.CRIAR_PASSO_1;
                    atualizarTela();
                }
                break;

            case DESBLOQUEAR:
                if (SecurityUtils.validarPin(this, valor)) {
                    liberarAcesso();
                } else {
                    Toast.makeText(this, "PIN incorreto.", Toast.LENGTH_SHORT).show();
                    editPin.setText("");
                }
                break;
        }
    }

    private void liberarAcesso() {
        startActivity(new Intent(this, SplashActivity.class));
        finish();
    }
}
