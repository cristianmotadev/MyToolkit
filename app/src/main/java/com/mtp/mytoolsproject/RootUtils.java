package com.mtp.mytoolsproject;

import android.content.Context;
import android.content.pm.PackageManager;

import com.scottyab.rootbeer.RootBeer;

import timber.log.Timber;

/**
 * Utilitário para detecção robusta de dispositivo com root usando RootBeer.
 * 
 * Implementa múltiplas verificações para detectar root de forma confiável:
 * - Verificação de binários su comuns
 * - Teste de permissões de escrita em partições do sistema
 * - Detecção de aplicativos de root (SuperSU, Magisk, etc.)
 * - Verificação de variáveis de ambiente
 * - Análise de paths suspeitos
 * 
 * Uso recomendado:
 * - Executar em thread background para não bloquear UI
 * - Cachear resultado por sessão (detecção é custosa)
 * - Usar verificação simples para checks rápidos
 */
public final class RootUtils {

    private static RootBeer rootBeerInstance;
    private static Boolean cachedRootStatus = null;
    private static final String TAG = "RootUtils";

    private RootUtils() {
        // Previne instanciação
    }

    /**
     * Verifica se o dispositivo tem root usando todas as técnicas disponíveis.
     * Operação custosa - execute em thread background.
     * 
     * @param context Contexto da aplicação
     * @return true se root detectado, false caso contrário
     */
    public static boolean isRooted(Context context) {
        if (cachedRootStatus != null) {
            Timber.v("Retornando status de root em cache: %b", cachedRootStatus);
            return cachedRootStatus;
        }

        try {
            RootBeer rootBeer = getRootBeer(context);
            
            // Executa todas as verificações disponíveis
            boolean rooted = rootBeer.isRooted();
            
            cachedRootStatus = rooted;
            
            if (rooted) {
                Timber.w("⚠️ ROOT DETECTADO - Dispositivo comprometido");
                logRootDetails(context);
            } else {
                Timber.i("✅ Dispositivo sem root detectado");
            }
            
            return rooted;
            
        } catch (Exception e) {
            Timber.e(e, "Erro durante detecção de root");
            // Em caso de erro, assume seguro (sem root)
            return false;
        }
    }

    /**
     * Verificação rápida de root usando apenas métodos básicos.
     * Mais rápida que isRooted() mas menos precisa.
     * 
     * @param context Contexto da aplicação
     * @return true se root provavelmente detectado
     */
    public static boolean isRootedQuick(Context context) {
        try {
            RootBeer rootBeer = getRootBeer(context);
            boolean rooted = rootBeer.checkForBinary("su");
            Timber.v("Verificação rápida de root (binário su): %b", rooted);
            return rooted;
        } catch (Exception e) {
            Timber.e(e, "Erro na verificação rápida de root");
            return false;
        }
    }

    /**
     * Limpa o cache do status de root para forçar nova verificação.
     * Use quando houver mudança no estado do dispositivo.
     */
    public static void clearCache() {
        cachedRootStatus = null;
        rootBeerInstance = null;
        Timber.d("Cache de root limpo");
    }

    /**
     * Retorna detalhes sobre quais testes de root falharam.
     * Útil para debugging e auditoria de segurança.
     * 
     * @param context Contexto da aplicação
     * @return RootBeer instance para inspeção detalhada
     */
    public static RootBeer getRootBeer(Context context) {
        if (rootBeerInstance == null) {
            rootBeerInstance = new RootBeer(context);
        }
        return rootBeerInstance;
    }

    /**
     * Verifica se um aplicativo específico de root está instalado.
     * 
     * @param context Contexto da aplicação
     * @param packageName Pacote do app de root (ex: com.noshufou.android.su)
     * @return true se o app estiver instalado
     */
    public static boolean isRootAppInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            Timber.v("App de root detectado: %s", packageName);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * Lista de pacotes comuns de aplicativos de root.
     */
    private static final String[] ROOT_APP_PACKAGES = {
        "com.noshufou.android.su",
        "com.noshufou.android.su.elite",
        "eu.chainfire.supersu",
        "com.koushikdutta.superuser",
        "com.thirdparty.superuser",
        "com.yellowes.su",
        "com.kingouser.com",
        "com.topjohnwu.magisk",
        "com.ramdroid.amphorae",
        "me.phh.superuser",
        "com.devadvance.rootcloak",
        "com.devadvance.rootcloakplus",
        "de.robv.android.xposed.installer",
        "com.saurik.substratum",
        "com.androixposedinstaller",
        "org.meowcat.xposedinstaller"
    };

    /**
     * Verifica se algum aplicativo de root conhecido está instalado.
     * 
     * @param context Contexto da aplicação
     * @return true se algum app de root foi encontrado
     */
    public static boolean hasKnownRootApps(Context context) {
        for (String packageName : ROOT_APP_PACKAGES) {
            if (isRootAppInstalled(context, packageName)) {
                Timber.w("Aplicativo de root detectado: %s", packageName);
                return true;
            }
        }
        return false;
    }

    /**
     * Realiza verificação completa de root incluindo apps conhecidos.
     * Combina isRooted() com hasKnownRootApps().
     * 
     * @param context Contexto da aplicação
     * @return true se root ou apps de root forem detectados
     */
    public static boolean isRootedWithAppCheck(Context context) {
        boolean rooted = isRooted(context);
        boolean hasApps = hasKnownRootApps(context);
        
        if (hasApps && !rooted) {
            Timber.w("Apps de root instalados mas root não detectado (pode estar escondido)");
        }
        
        return rooted || hasApps;
    }

    /**
     * Registra detalhes das verificações de root que falharam.
     * Apenas para logging/debugging.
     */
    private static void logRootDetails(Context context) {
        try {
            RootBeer rootBeer = getRootBeer(context);
            
            Timber.d("=== Detalhes da Detecção de Root ===");
            Timber.d("checkForBinary('su'): %b", rootBeer.checkForBinary("su"));
            Timber.d("checkForDangerousProps(): %b", rootBeer.checkForDangerousProps());
            Timber.d("detectTestKeys(): %b", rootBeer.detectTestKeys());
            Timber.d("checkSuExists(): %b", rootBeer.checkSuExists());
            Timber.d("checkForRWPaths(): %b", rootBeer.checkForRWPaths());
            Timber.d("detectRootManagementApps(): %b", rootBeer.detectRootManagementApps());
            Timber.d("detectPotentiallyDangerousApps(): %b", rootBeer.detectPotentiallyDangerousApps());
            Timber.d("checkForMagiskBinary(): %b", rootBeer.checkForMagiskBinary());
            Timber.d("=======================================");
            
        } catch (Exception e) {
            Timber.e(e, "Erro ao logar detalhes de root");
        }
    }

    /**
     * Interface para callbacks assíncronos de verificação de root.
     */
    public interface RootCheckCallback {
        void onResult(boolean isRooted);
        void onError(Exception error);
    }

    /**
     * Verifica root de forma assíncrona em thread background.
     * Ideal para usar durante inicialização do app.
     * 
     * @param context Contexto da aplicação
     * @param callback Callback para receber o resultado
     */
    public static void checkRootAsync(Context context, RootCheckCallback callback) {
        new Thread(() -> {
            try {
                boolean result = isRooted(context);
                if (callback != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(() -> callback.onResult(result));
                }
            } catch (Exception e) {
                Timber.e(e, "Erro na verificação assíncrona de root");
                if (callback != null) {
                    new android.os.Handler(android.os.Looper.getMainLooper())
                        .post(() -> callback.onError(e));
                }
            }
        }).start();
    }
}
