package com.mtp.mytoolsproject;

import android.app.Application;

import timber.log.Timber;

/**
 * Classe Application para inicialização global do app.
 * Configura Timber para logging e aplica o tema salvo.
 */
public class MyToolsApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        
        // Inicializa Timber para logging estruturado
        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());
            Timber.i("MyToolsApplication iniciado (modo DEBUG)");
        } else {
            // Em produção, poderíamos plantar uma árvore de logging customizada
            // que envia logs para um serviço remoto ou ignora logs verbose/debug
            Timber.i("MyToolsApplication iniciado (modo RELEASE)");
        }
        
        ThemeUtils.aplicarTemaSalvo(this);
    }
}
