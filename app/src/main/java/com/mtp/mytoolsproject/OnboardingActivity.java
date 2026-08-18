package com.mtp.mytoolsproject;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Tutorial de primeira abertura: apresenta rapidamente as principais
 * categorias de ferramentas do app. Aparece uma vez só (controlado pela
 * flag "onboarding_concluido"), mas pode ser revisto manualmente pelas
 * Configurações.
 */
public class OnboardingActivity extends AppCompatActivity {

    private static class Pagina {
        final String emoji, titulo, descricao;
        Pagina(String emoji, String titulo, String descricao) {
            this.emoji = emoji;
            this.titulo = titulo;
            this.descricao = descricao;
        }
    }

    private final Pagina[] paginas = {
            new Pagina("🛠️", "Bem-vindo ao My Toolkit",
                    "Um conjunto de ferramentas de rede, segurança e utilidades, tudo em um só app."),
            new Pagina("📡", "Ferramentas de Rede",
                    "Radar de Dispositivos, Radar Wi-Fi, Radar Bluetooth, Scanner de Porta, Traceroute, Consulta DNS e Teste de Velocidade."),
            new Pagina("📲", "NFC Completo",
                    "Leia e escreva tags NDEF, faça dump e clonagem de tags MIFARE Classic, leia dados públicos de cartões e crie cartões de visita."),
            new Pagina("🔒", "Segurança em Primeiro Lugar",
                    "Proteja o app com PIN ou biometria. Suas senhas Wi-Fi ficam salvas mesmo se você esquecer a rede no sistema."),
            new Pagina("🔑", "Root Necessário",
                    "Várias ferramentas (Senhas Wi-Fi, Radar de Dispositivos, mDNS) dependem de acesso root para funcionar por completo."),
            new Pagina("⚠️", "Sem Root?",
                    "Sem root você ainda pode usar: Radar Wi-Fi, Radar Bluetooth, Scanner de Portas, Consulta DNS, Calculadora de Sub-rede, Ferramentas NFC, Auditoria de Apps, Gerador de Senhas, Calculadora de Hash e Criptografia de Arquivos."),
    };

    private int paginaAtual = 0;

    private TextView txtEmoji, txtTitulo, txtDescricao;
    private LinearLayout containerPontos;
    private Button btnProximo, btnPular;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding);

        txtEmoji = findViewById(R.id.txtOnboardingEmoji);
        txtTitulo = findViewById(R.id.txtOnboardingTitulo);
        txtDescricao = findViewById(R.id.txtOnboardingDescricao);
        containerPontos = findViewById(R.id.containerOnboardingPontos);
        btnProximo = findViewById(R.id.btnOnboardingProximo);
        btnPular = findViewById(R.id.btnOnboardingPular);

        criarPontos();
        atualizarTela();

        btnProximo.setOnClickListener(v -> {
            if (paginaAtual < paginas.length - 1) {
                paginaAtual++;
                atualizarTela();
            } else {
                concluirOnboarding();
            }
        });

        btnPular.setOnClickListener(v -> concluirOnboarding());
    }

    private void criarPontos() {
        containerPontos.removeAllViews();
        for (int i = 0; i < paginas.length; i++) {
            TextView ponto = new TextView(this);
            ponto.setText("●");
            ponto.setTextSize(14f);
            ponto.setPadding(8, 0, 8, 0);
            containerPontos.addView(ponto);
        }
    }

    private void atualizarTela() {
        Pagina p = paginas[paginaAtual];
        txtEmoji.setText(p.emoji);
        txtTitulo.setText(p.titulo);
        txtDescricao.setText(p.descricao);

        for (int i = 0; i < containerPontos.getChildCount(); i++) {
            TextView ponto = (TextView) containerPontos.getChildAt(i);
            ponto.setTextColor(i == paginaAtual ? 0xFF2196F3 : 0xFF444444);
        }

        boolean ultimaPagina = paginaAtual == paginas.length - 1;
        btnProximo.setText(ultimaPagina ? "🚀 Começar" : "Próximo");
        btnPular.setVisibility(ultimaPagina ? View.INVISIBLE : View.VISIBLE);
    }

    private void concluirOnboarding() {
        SharedPreferences prefs = getSharedPreferences("NetworkPrefs", MODE_PRIVATE);
        prefs.edit().putBoolean("onboarding_concluido", true).apply();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
