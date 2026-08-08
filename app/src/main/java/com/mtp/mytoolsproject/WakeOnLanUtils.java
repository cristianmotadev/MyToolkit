package com.mtp.mytoolsproject;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

/**
 * Wake-on-LAN: envia o "pacote mágico" (magic packet) via broadcast UDP para
 * ligar remotamente um dispositivo que suporte essa funcionalidade (precisa
 * estar habilitada na BIOS/placa de rede do aparelho alvo, e ele precisa
 * estar conectado à energia, mesmo desligado).
 */
public final class WakeOnLanUtils {

    private WakeOnLanUtils() {}

    public static boolean enviarPacoteMagico(String macAddress, String enderecoBroadcast) {
        try {
            byte[] macBytes = parseMac(macAddress);

            byte[] pacote = new byte[6 + 16 * macBytes.length];
            for (int i = 0; i < 6; i++) pacote[i] = (byte) 0xFF;
            for (int i = 6; i < pacote.length; i += macBytes.length) {
                System.arraycopy(macBytes, 0, pacote, i, macBytes.length);
            }

            InetAddress address = InetAddress.getByName(enderecoBroadcast);
            DatagramPacket datagramPacket = new DatagramPacket(pacote, pacote.length, address, 9);
            DatagramSocket socket = new DatagramSocket();
            socket.setBroadcast(true);
            socket.send(datagramPacket);
            socket.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] parseMac(String mac) throws Exception {
        String[] partes = mac.split("[:\\-]");
        if (partes.length != 6) throw new IllegalArgumentException("MAC inválido: " + mac);
        byte[] bytes = new byte[6];
        for (int i = 0; i < 6; i++) bytes[i] = (byte) Integer.parseInt(partes[i], 16);
        return bytes;
    }

    /** Deriva o endereço de broadcast a partir do prefixo da sub-rede local (ex: "192.168.1." -> "192.168.1.255"). */
    public static String broadcastDoPrefixo(String prefixoRede) {
        return prefixoRede + "255";
    }
}
