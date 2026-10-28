package br.lucas.spfc;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Compara times sem acento, sem pontuação e com apelidos da ESPN. */
final class Nomes {
    private static final Map<String, String> APELIDOS = new HashMap<>();

    static {
        canonico("vasco da gama", "vasco");
        canonico("red bull bragantino", "bragantino", "rb bragantino");
        canonico("athletico paranaense", "athletico pr", "athletico-pr");
        canonico("atletico mg", "atletico mineiro", "atletico-mg");
        canonico("boca juniors", "boca");
        canonico("sao paulo", "spfc");
    }

    private Nomes() { }

    static String normalizar(String nome) {
        if (nome == null) return "";
        String nfd = Normalizer.normalize(nome.trim(), Normalizer.Form.NFD);
        String semAcento = nfd.replaceAll("\\p{M}+", "");
        return semAcento.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim();
    }

    static String chave(String nome) {
        String normal = normalizar(nome);
        String canonico = APELIDOS.get(normal);
        return canonico != null ? canonico : normal;
    }

    static boolean iguais(String a, String b) {
        String esquerda = chave(a);
        String direita = chave(b);
        return !esquerda.isEmpty() && esquerda.equals(direita);
    }

    static boolean time(String alvo, String... candidatos) {
        for (int i = 0; i < candidatos.length; i++) {
            if (iguais(alvo, candidatos[i])) return true;
        }
        return false;
    }

    static boolean mesmoConfronto(String casa, String fora, String outroCasa, String outroFora) {
        return (iguais(casa, outroCasa) && iguais(fora, outroFora))
                || (iguais(casa, outroFora) && iguais(fora, outroCasa));
    }

    private static void canonico(String destino, String... apelidos) {
        APELIDOS.put(normalizar(destino), normalizar(destino));
        for (int i = 0; i < apelidos.length; i++) {
            APELIDOS.put(normalizar(apelidos[i]), normalizar(destino));
        }
    }
}
