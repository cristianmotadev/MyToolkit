package com.mtp.mytoolsproject;

import android.app.Application;

/**
 * Aplica o modo de tema salvo (claro/escuro/sistema) antes de qualquer
 * Activity ser criada, evitando qualquer "piscada" de tema errado.
 */
public class MyToolsApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeUtils.aplicarTemaSalvo(this);
    }
}
