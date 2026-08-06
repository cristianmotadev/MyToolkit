package com.mtp.mytoolsproject;

import android.graphics.Bitmap;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

/**
 * Gera QR Codes localmente (sem internet) usando a biblioteca ZXing.
 */
public final class QrCodeUtils {

    private QrCodeUtils() {}

    public static Bitmap gerarQrCode(String conteudo, int tamanhoPx) throws Exception {
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matriz = writer.encode(conteudo, BarcodeFormat.QR_CODE, tamanhoPx, tamanhoPx);

        Bitmap bitmap = Bitmap.createBitmap(tamanhoPx, tamanhoPx, Bitmap.Config.RGB_565);
        for (int x = 0; x < tamanhoPx; x++) {
            for (int y = 0; y < tamanhoPx; y++) {
                bitmap.setPixel(x, y, matriz.get(x, y) ? 0xFF000000 : 0xFFFFFFFF);
            }
        }
        return bitmap;
    }

    /**
     * Monta a string no formato padrão que o Android (e outros sistemas)
     * reconhecem para conectar automaticamente a uma rede Wi-Fi ao escanear.
     */
    public static String montarStringWifiQr(String ssid, String senha) {
        boolean aberta = senha == null || senha.equals("Sem senha / Aberta");
        String tipo = aberta ? "nopass" : "WPA";
        String senhaEscapada = aberta ? "" : escaparCampo(senha);
        return "WIFI:T:" + tipo + ";S:" + escaparCampo(ssid) + ";P:" + senhaEscapada + ";;";
    }

    private static String escaparCampo(String texto) {
        return texto
                .replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\"", "\\\"");
    }
}
