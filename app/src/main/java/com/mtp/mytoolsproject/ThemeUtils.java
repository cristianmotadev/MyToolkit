package com.mtp.mytoolsproject;

import android.content.Context;
import androidx.appcompat.app.AppCompatDelegate;

/**
 * Controla o modo de tema do app: "escuro" (padrão, preserva o visual
 * original), "claro" ou "sistema" (segue o tema do Android).
 */
public final class ThemeUtils {

    private static final String CHAVE_MODO = "tema_modo";

    private ThemeUtils() {}

    public static void aplicarTemaSalvo(Context context) {
        aplicarModo(modoSalvo(context));
    }

    public static String modoSalvo(Context context) {
        return context.getSharedPreferences("NetworkPrefs", Context.MODE_PRIVATE)
                .getString(CHAVE_MODO, "escuro");
    }

    public static void definirModo(Context context, String modo) {
        context.getSharedPreferences("NetworkPrefs", Context.MODE_PRIVATE)
                .edit().putString(CHAVE_MODO, modo).apply();
        aplicarModo(modo);
    }

    private static void aplicarModo(String modo) {
        switch (modo) {
            case "claro":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case "sistema":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
                break;
            default: // "escuro"
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
        }
    }
}
