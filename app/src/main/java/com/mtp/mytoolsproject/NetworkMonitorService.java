package com.mtp.mytoolsproject;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class NetworkMonitorService extends Service {

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scheduledTask;

    @Override
    public void onCreate() {
        super.onCreate();
        criarCanalNotificacao();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            android.app.Notification notification = new NotificationCompat.Builder(this, "network_channel")
                    .setContentTitle("Monitor de Rede Ativo")
                    .setContentText("Executando varredura manual em segundo plano...")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .build();
            startForeground(1, notification);
        }

        SharedPreferences prefs = getSharedPreferences("NetworkPrefs", MODE_PRIVATE);
        int intervaloSegundos = prefs.getInt("intervalo_segundos", 30);

        // scheduleAtFixedRate já roda em uma thread própria do executor,
        // então executarVarreduraCompleta() nunca toca a UI thread.
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduledTask = scheduler.scheduleAtFixedRate(this::executarVarreduraCompleta, 5, intervaloSegundos, TimeUnit.SECONDS);
    }

    private synchronized void salvarDadosNoJson(String ip, String macKey, String apelido, String statusPortas, String nomeRede, String fabricante) {
        JSONObject cache = NetworkUtils.carregarCache(this);

        String chaveAlvo = macKey;
        if (chaveAlvo == null) {
            java.util.Iterator<String> keys = cache.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                JSONObject obj = cache.optJSONObject(key);
                if (obj != null && ip.equals(obj.optString("ip"))) {
                    chaveAlvo = key;
                    break;
                }
            }
        }

        if (chaveAlvo == null) {
            chaveAlvo = "ARP_" + ip.replace(".", "_");
        }

        try {
            boolean ehNovo = !cache.has(chaveAlvo);
            JSONObject obj;
            if (!ehNovo) {
                obj = cache.getJSONObject(chaveAlvo);
            } else {
                obj = new JSONObject();
                obj.put("apelido", apelido != null ? apelido : "Novo Aparelho (" + ip + ")");
                obj.put("fabricante", fabricante != null ? fabricante : "NÃO ENCONTRADO");
            }

            obj.put("ip", ip);
            obj.put("portas", statusPortas);
            // Antes salvava na chave "rede", mas ManageDevicesActivity lê "rede_wifi"
            // (mesma chave usada pelo NetworkScannerActivity). Por isso o nome da rede
            // sempre aparecia como "Desconhecida" nos dispositivos achados pelo mDNS.
            obj.put("rede_wifi", nomeRede);
            if (apelido != null && !apelido.isEmpty()) {
                obj.put("apelido", apelido);
            }

            cache.put(chaveAlvo, obj);
            NetworkUtils.salvarCache(this, cache);

            boolean notificacoesAtivas = getSharedPreferences("NetworkPrefs", MODE_PRIVATE)
                    .getBoolean("notificar_novos_dispositivos", true);
            if (ehNovo && macKey != null && notificacoesAtivas) {
                enviarNotificacaoNovoDispositivo(ip, macKey, obj.getString("fabricante"));
            }
        } catch (Exception ignored) {}
    }

    private void executarVarreduraCompleta() {
        try {
            String nomeRede = NetworkUtils.obterNomeRedeWifi(this);
            // Descobre a sub-rede real do aparelho em vez de usar 192.168.1.x fixo
            String prefixoRede = NetworkUtils.descobrirPrefixoRedeLocal();

            for (int i = 1; i < 255; i++) {
                final String targetIp = prefixoRede + i;
                new Thread(() -> {
                    try {
                        Runtime.getRuntime().exec("ping -c 1 -w 1 " + targetIp);
                    } catch (Exception ignored) {}
                }).start();
            }
            Thread.sleep(1500);

            Process process = Runtime.getRuntime().exec(new String[]{"su", "-c", "cat /proc/net/arp"});
            BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            boolean firstLine = true;

            while ((line = br.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue;
                }
                String[] tokens = line.split("\\s+");
                if (tokens.length >= 4) {
                    String ip = tokens[0];
                    String mac = tokens[3];

                    if (mac == null || mac.equals("00:00:00:00:00:00") || mac.contains("00:00:00")) {
                        continue;
                    }

                    String macKey = mac.toUpperCase();
                    String statusPortas = NetworkUtils.verificarPortasComuns(ip);
                    String fabricante = NetworkUtils.consultarFabricante(macKey);
                    salvarDadosNoJson(ip, macKey, null, statusPortas, nomeRede, fabricante);
                }
            }
            br.close();
            process.waitFor();

        } catch (Exception ignored) {}
    }

    private void enviarNotificacaoNovoDispositivo(String ip, String mac, String fabricante) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "network_channel")
                .setContentTitle("Novo Dispositivo Detectado!")
                .setContentText("IP: " + ip + " | Marca: " + fabricante)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setAutoCancel(true);

        if (notificationManager != null) {
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    private void criarCanalNotificacao() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    "network_channel",
                    "Monitor de Rede",
                    NotificationManager.IMPORTANCE_DEFAULT
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
        if (scheduledTask != null) {
            scheduledTask.cancel(true);
        }
        if (scheduler != null) {
            scheduler.shutdown();
        }
    }
}
