package com.mtp.mytoolsproject;

/**
 * Calculadora de sub-rede/CIDR: a partir de um IP + prefixo (ex: 192.168.1.10/24),
 * calcula máscara, endereço de rede, broadcast, faixa de hosts utilizáveis e
 * quantidade de hosts. Puramente matemático — não faz nenhuma chamada de rede.
 */
public final class SubnetUtils {

    private SubnetUtils() {}

    public static class ResultadoSubnet {
        public String mascara;
        public String enderecoRede;
        public String enderecoBroadcast;
        public String primeiroHost;
        public String ultimoHost;
        public long totalHostsUtilizaveis;
        public int prefixo;
    }

    /** @param ipComPrefixo ex: "192.168.1.10/24" */
    public static ResultadoSubnet calcular(String ipComPrefixo) throws IllegalArgumentException {
        String[] partes = ipComPrefixo.trim().split("/");
        if (partes.length != 2) {
            throw new IllegalArgumentException("Use o formato IP/prefixo, ex: 192.168.1.10/24");
        }

        long ip = ipParaLong(partes[0].trim());
        int prefixo = Integer.parseInt(partes[1].trim());
        if (prefixo < 0 || prefixo > 32) {
            throw new IllegalArgumentException("O prefixo deve estar entre 0 e 32");
        }

        long mascaraLong = prefixo == 0 ? 0 : (0xFFFFFFFFL << (32 - prefixo)) & 0xFFFFFFFFL;
        long enderecoRede = ip & mascaraLong;
        long broadcast = enderecoRede | (~mascaraLong & 0xFFFFFFFFL);

        ResultadoSubnet resultado = new ResultadoSubnet();
        resultado.prefixo = prefixo;
        resultado.mascara = longParaIp(mascaraLong);
        resultado.enderecoRede = longParaIp(enderecoRede);
        resultado.enderecoBroadcast = longParaIp(broadcast);

        if (prefixo >= 31) {
            // /31 e /32 não têm host/broadcast utilizável tradicional (RFC 3021 / host único)
            resultado.primeiroHost = resultado.enderecoRede;
            resultado.ultimoHost = resultado.enderecoBroadcast;
            resultado.totalHostsUtilizaveis = prefixo == 32 ? 1 : 2;
        } else {
            resultado.primeiroHost = longParaIp(enderecoRede + 1);
            resultado.ultimoHost = longParaIp(broadcast - 1);
            resultado.totalHostsUtilizaveis = (1L << (32 - prefixo)) - 2;
        }

        return resultado;
    }

    private static long ipParaLong(String ip) {
        String[] octetos = ip.split("\\.");
        if (octetos.length != 4) {
            throw new IllegalArgumentException("IP inválido: " + ip);
        }
        long resultado = 0;
        for (String octeto : octetos) {
            int valor = Integer.parseInt(octeto);
            if (valor < 0 || valor > 255) {
                throw new IllegalArgumentException("Octeto inválido: " + octeto);
            }
            resultado = (resultado << 8) | valor;
        }
        return resultado;
    }

    private static String longParaIp(long valor) {
        return ((valor >> 24) & 0xFF) + "." + ((valor >> 16) & 0xFF) + "." + ((valor >> 8) & 0xFF) + "." + (valor & 0xFF);
    }
}
