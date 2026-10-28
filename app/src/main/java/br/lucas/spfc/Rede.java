package br.lucas.spfc;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** GET curto e leitura de arquivo. {@code toString(Charset)} estoura no Fire OS 6. */
final class Rede {
    static final String UA_APP = "SPFC-FireTV/1.3";
    static final String UA_NAVEGADOR =
            "Mozilla/5.0 (Linux; Android 7.1.2; AFTMM) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private Rede() { }

    static String get(String url, int esperaMs, String userAgent) throws IOException {
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
            String corpo = entrada == null ? "" : texto(entrada);
            if (codigo < 200 || codigo >= 300) {
                throw new IOException("HTTP " + codigo);
            }
            return corpo;
        } finally {
            conexao.disconnect();
        }
    }

    static String texto(InputStream entrada) throws IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        byte[] bloco = new byte[4096];
        int lido;
        while ((lido = entrada.read(bloco)) >= 0) saida.write(bloco, 0, lido);
        return new String(saida.toByteArray(), StandardCharsets.UTF_8);
    }

    static String arquivo(File origem) throws IOException {
        try (InputStream entrada = new FileInputStream(origem)) {
            return texto(entrada);
        }
    }

    static void gravar(File destino, String corpo) throws IOException {
        File pasta = destino.getParentFile();
        if (pasta != null && !pasta.exists() && !pasta.mkdirs()) {
            throw new IOException("sem pasta de cache");
        }
        File temporario = new File(pasta, destino.getName() + ".tmp");
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        try (FileOutputStream saida = new FileOutputStream(temporario)) {
            saida.write(bytes);
        }
        if (destino.exists() && !destino.delete()) {
            throw new IOException("não apagou o cache antigo");
        }
        if (!temporario.renameTo(destino)) {
            throw new IOException("não gravou o cache");
        }
    }
}
