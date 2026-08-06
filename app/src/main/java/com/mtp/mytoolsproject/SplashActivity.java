package com.mtp.mytoolsproject;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.AlphaAnimation;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Animação de aparecimento suave (Fade In)
        View layout = findViewById(R.id.mainLayout);
        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(1200);
        layout.startAnimation(fadeIn);

        // Tempo de transição para a próxima tela
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                SharedPreferences prefs = getSharedPreferences("NetworkPrefs", MODE_PRIVATE);
                boolean onboardingConcluido = prefs.getBoolean("onboarding_concluido", false);

                Class<?> proximaTela = onboardingConcluido ? MainActivity.class : OnboardingActivity.class;
                startActivity(new Intent(SplashActivity.this, proximaTela));
                finish();
            }
        }, 2200);
    }
}