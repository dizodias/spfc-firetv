package br.lucas.spfc;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Canais de TV e streaming que entram no card. Cartola e Ge TV ficam de fora. */
final class CanaisPermitidos {
    private static final Set<String> PERMITIDOS = new HashSet<>(Arrays.asList(
            "premiere",
            "globo",
            "sportv",
            "prime video",
            "paramount",
            "disney",
            "espn",
            "hbo max",
            "max",
            "band",
            "record",
            "cazetv",
            "caze tv",
            "globoplay"
    ));

    private CanaisPermitidos() { }

    static boolean permitido(String nome) {
        String chave = Nomes.normalizar(nome);
        return !chave.isEmpty() && PERMITIDOS.contains(chave);
    }

    static List<String> filtrar(List<String> nomes) {
        List<String> saida = new ArrayList<>();
        if (nomes == null) return saida;
        for (int i = 0; i < nomes.size(); i++) {
            String nome = nomes.get(i);
            if (nome == null) continue;
            String limpo = nome.trim();
            if (limpo.isEmpty() || !permitido(limpo) || saida.contains(limpo)) continue;
            saida.add(limpo);
        }
        return saida;
    }
}
