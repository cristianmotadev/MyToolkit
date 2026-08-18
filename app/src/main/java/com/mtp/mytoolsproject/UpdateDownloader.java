package com.mtp.mytoolsproject;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.widget.Toast;

/**
 * Baixa o .apk anexado a uma Release usando o DownloadManager nativo do
 * Android (mostra progresso na barra de notificações do sistema) e, quando
 * termina, dispara a tela de instalação. O Android sempre vai pedir a
 * confirmação manual do usuário para instalar — isso é uma trava de
 * segurança do sistema, não uma limitação deste código.
 */
public final class UpdateDownloader {

    private UpdateDownloader() {}

    public static void baixarEInstalar(Context context, String urlApk, String nomeArquivo) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.getPackageManager().canRequestPackageInstalls()) {
            Toast.makeText(context, "Autorize \"Instalar apps desconhecidos\" para o My Toolkit nas configurações do Android, e tente de novo.", Toast.LENGTH_LONG).show();
            Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:" + context.getPackageName()));
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
            return;
        }

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(urlApk));
        request.setTitle("Atualizando My Toolkit");
        request.setDescription(nomeArquivo);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, nomeArquivo);

        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        if (downloadManager == null) {
            Toast.makeText(context, "Não foi possível iniciar o download.", Toast.LENGTH_SHORT).show();
            return;
        }
        long idDownload = downloadManager.enqueue(request);

        Toast.makeText(context, "⬇️ Baixando atualização... acompanhe na barra de notificações.", Toast.LENGTH_LONG).show();

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ctx, Intent intent) {
                long idRecebido = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
                if (idRecebido != idDownload) return;

                try {
                    Uri uriArquivo = downloadManager.getUriForDownloadedFile(idDownload);
                    if (uriArquivo == null) {
                        Toast.makeText(ctx, "Falha no download da atualização.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Intent instalarIntent = new Intent(Intent.ACTION_VIEW);
                    instalarIntent.setDataAndType(uriArquivo, "application/vnd.android.package-archive");
                    instalarIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    ctx.startActivity(instalarIntent);
                } catch (Exception e) {
                    Toast.makeText(ctx, "Erro ao abrir o instalador: " + e.getMessage(), Toast.LENGTH_LONG).show();
                } finally {
                    try { ctx.unregisterReceiver(this); } catch (Exception ignored) {}
                }
            }
        };

        IntentFilter filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            context.registerReceiver(receiver, filter);
        }
    }
}
