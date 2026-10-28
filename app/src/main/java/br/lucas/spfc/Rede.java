package br.lucas.spfc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** GET curto e leitura de arquivo. {@code toString(Charset)} estoura no Fire OS 6. */
final class Rede {
    static final String UA_APP = "SPFC-FireTV/1.4";
    static final String UA_NAVEGADOR =
            "Mozilla/5.0 (Linux; Android 7.1.2; AFTMM) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private Rede() { }

    static String get(String url, int esperaMs, String userAgent) throws IOException {
        return new String(bytes(url, esperaMs, userAgent), StandardCharsets.UTF_8);
    }

    static byte[] bytes(String url, int esperaMs, String userAgent) throws IOException {
        HttpURLConnection conexao = (HttpURLConnection) new URL(url).openConnection();
        conexao.setConnectTimeout(esperaMs);
        conexao.setReadTimeout(esperaMs);
        conexao.setRequestProperty("Accept", "*/*");
        conexao.setRequestProperty("User-Agent", userAgent);
        try {
            int codigo = conexao.getResponseCode();
            InputStream entrada = codigo >= 200 && codigo < 300
                    ? conexao.getInputStream()
                    : conexao.getErrorStream();
            byte[] corpo = entrada == null ? new byte[0] : dados(entrada);
            if (codigo < 200 || codigo >= 300) {
                throw new IOException("HTTP " + codigo);
            }
            return corpo;
        } finally {
            conexao.disconnect();
        }
    }

    static String post(String url, String corpo, int esperaMs, String userAgent) throws IOException {
        HttpURLConnection conexao = (HttpURLConnection) new URL(url).openConnection();
        conexao.setConnectTimeout(esperaMs);
        conexao.setReadTimeout(esperaMs);
        conexao.setDoOutput(true);
        conexao.setRequestMethod("POST");
        conexao.setRequestProperty("Accept", "application/json");
        conexao.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        conexao.setRequestProperty("User-Agent", userAgent);
        byte[] bytes = corpo == null ? new byte[0] : corpo.getBytes(StandardCharsets.UTF_8);
        try {
            try (OutputStream saida = conexao.getOutputStream()) {
                saida.write(bytes);
            }
            int codigo = conexao.getResponseCode();
            InputStream entrada = codigo >= 200 && codigo < 300
                    ? conexao.getInputStream()
                    : conexao.getErrorStream();
            byte[] resposta = entrada == null ? new byte[0] : dados(entrada);
            if (codigo < 200 || codigo >= 300) throw new IOException("HTTP " + codigo);
            return new String(resposta, StandardCharsets.UTF_8);
        } finally {
            conexao.disconnect();
        }
    }

    static byte[] dados(InputStream entrada) throws IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        byte[] bloco = new byte[4096];
        int lido;
        while ((lido = entrada.read(bloco)) >= 0) saida.write(bloco, 0, lido);
        return saida.toByteArray();
    }

    static String texto(InputStream entrada) throws IOException {
        return new String(dados(entrada), StandardCharsets.UTF_8);
    }

    static String arquivo(File origem) throws IOException {
        try (InputStream entrada = new FileInputStream(origem)) {
            return texto(entrada);
        }
    }

    static void gravar(File destino, String corpo) throws IOException {
        gravar(destino, corpo.getBytes(StandardCharsets.UTF_8));
    }

    static void gravar(File destino, byte[] corpo) throws IOException {
        File pasta = destino.getParentFile();
        if (pasta != null && !pasta.exists() && !pasta.mkdirs()) {
            throw new IOException("sem pasta de cache");
        }
        File temporario = new File(pasta, destino.getName() + ".tmp");
        try (FileOutputStream saida = new FileOutputStream(temporario)) {
            saida.write(corpo);
        }
        if (destino.exists() && !destino.delete()) {
            throw new IOException("não apagou o cache antigo");
        }
        if (!temporario.renameTo(destino)) {
            throw new IOException("não gravou o cache");
        }
    }
}
