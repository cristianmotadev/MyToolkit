package com.mtp.mytoolsproject;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Mantém o Radar Wi-Fi escaneando redes próximas em segundo plano, mesmo com
 * a tela fechada — assim como o monitoramento mDNS já faz para dispositivos
 * da rede local. Os resultados ficam disponíveis via WifiManager.getScanResults()
 * (cache do próprio sistema), então a WifiScannerActivity só precisa reabrir
 * pra mostrar o que já foi escaneado, sem esperar uma nova varredura.
 */
public class WifiScanBackgroundService extends Service {

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledTask;
    private WifiManager wifiManager;

    @Override
    public void onCreate() {
        super.onCreate();
        wifiManager = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        criarCanalNotificacao();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification notification = new NotificationCompat.Builder(this, "wifi_radar_channel")
                    .setContentTitle("Radar Wi-Fi ativo")
                    .setContentText("Escaneando redes próximas em segundo plano...")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .build();
            startForeground(2, notification);
        }

        SharedPreferences prefs = getSharedPreferences("NetworkPrefs", MODE_PRIVATE);
        int intervaloSegundos = prefs.getInt("wifi_auto_scan_intervalo_segundos", 30);

        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduledTask = scheduler.scheduleAtFixedRate(() -> {
            try {
                if (wifiManager != null && wifiManager.isWifiEnabled()) {
                    wifiManager.startScan();
                }
            } catch (Exception ignored) {}
        }, 2, intervaloSegundos, TimeUnit.SECONDS);
    }

    private void criarCanalNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "wifi_radar_channel",
                    "Radar Wi-Fi",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (scheduledTask != null) scheduledTask.cancel(true);
        if (scheduler != null) scheduler.shutdown();
    }
}
