package br.lucas.spfc;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/** Escudo da ESPN em memória e em {@code filesDir/escudos}. A rede fica fora da UI. */
final class Escudos {
    private static final long VALIDADE_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final int ESPERA_MS = 8_000;
    private static final int ALVO_PX = 128;

    private final File pasta;
    private final ExecutorService io;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LruCache<String, Bitmap> memoria = new LruCache<>(4 * 1024) {
        @Override
        protected int sizeOf(String chave, Bitmap valor) {
            return Math.max(1, valor.getByteCount() / 1024);
        }
    };

    Escudos(File filesDir, ExecutorService io) {
        this.pasta = new File(filesDir, "escudos");
        this.io = io;
        if (!pasta.exists()) pasta.mkdirs();
    }

    void carregar(ImageView destino, String timeId, String url) {
        if (timeId == null || timeId.isEmpty() || url == null || url.isEmpty()) {
            destino.setTag(null);
            destino.setImageDrawable(null);
            return;
        }
        destino.setTag(timeId);
        Bitmap pronto = memoria.get(timeId);
        if (pronto != null) {
            destino.setImageBitmap(pronto);
            return;
        }
        destino.setImageDrawable(null);
        try {
            io.execute(() -> {
                Bitmap bitmap;
                try {
                    bitmap = lerOuBaixar(timeId, url);
                } catch (Exception ignorado) {
                    return;
                }
                if (bitmap == null) return;
                memoria.put(timeId, bitmap);
                main.post(() -> {
                    if (!timeId.equals(destino.getTag())) return;
                    destino.setImageBitmap(bitmap);
                });
            });
        } catch (RejectedExecutionException ignorado) {
            // A tela já fechou.
        }
    }

    private Bitmap lerOuBaixar(String timeId, String url) {
        File arquivo = new File(pasta, nomeArquivo(timeId));
        if (valido(arquivo)) {
            Bitmap salvo = decodificar(ler(arquivo));
            if (salvo != null) return salvo;
        }
        try {
            byte[] dados = Rede.bytes(url, ESPERA_MS, Rede.UA_APP);
            try {
                Rede.gravar(arquivo, dados);
            } catch (IOException ignorado) {
                // O bitmap desta resposta ainda serve nesta sessão.
            }
            return decodificar(dados);
        } catch (IOException e) {
            if (arquivo.exists()) return decodificar(ler(arquivo));
            return null;
        }
    }

    private static boolean valido(File arquivo) {
        if (!arquivo.exists() || arquivo.length() == 0) return false;
        long idade = System.currentTimeMillis() - arquivo.lastModified();
        return idade >= 0 && idade < VALIDADE_MS;
    }

    private static byte[] ler(File arquivo) {
        try (InputStream entrada = new FileInputStream(arquivo)) {
            return Rede.dados(entrada);
        } catch (IOException e) {
            return null;
        }
    }

    private static Bitmap decodificar(byte[] dados) {
        if (dados == null || dados.length == 0) return null;
        BitmapFactory.Options limites = new BitmapFactory.Options();
        limites.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(dados, 0, dados.length, limites);
        if (limites.outWidth <= 0 || limites.outHeight <= 0) return null;
        int amostra = 1;
        while (limites.outWidth / amostra > ALVO_PX && limites.outHeight / amostra > ALVO_PX) {
            amostra *= 2;
        }
        BitmapFactory.Options opcoes = new BitmapFactory.Options();
        opcoes.inSampleSize = Math.max(1, amostra);
        return BitmapFactory.decodeByteArray(dados, 0, dados.length, opcoes);
    }

    private static String nomeArquivo(String timeId) {
        String limpo = timeId.replaceAll("[^A-Za-z0-9._-]", "");
        if (limpo.isEmpty()) limpo = "sem-id";
        return limpo + ".png";
    }
}
