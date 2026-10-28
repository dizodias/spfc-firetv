package br.lucas.spfc;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Nome em pt-BR e slug da liga da ESPN. */
final class Competicao {
    static final String SLUG_BRASILEIRAO = "bra.1";
    private static final String[] ORDEM = {
            "bra.1",
            "bra.copa_do_brazil",
            "conmebol.libertadores",
            "conmebol.sudamericana",
            "bra.camp.paulista"
    };

    private Competicao() { }

    static final class Aba {
        final String slug;
        final String nome;

        Aba(String slug, String nome) {
            this.slug = slug;
            this.nome = nome;
        }
    }

    static String nome(JSONObject evento) {
        JSONObject liga = evento == null ? null : evento.optJSONObject("league");
        if (liga == null) return "";
        String escrito = liga.optString("name", "").trim();
        String traduzido = traduzir(escrito);
        if (!traduzido.isEmpty()) return traduzido;
        return traduzirSlug(liga.optString("slug", ""));
    }

    static String slug(JSONObject evento) {
        JSONObject liga = evento == null ? null : evento.optJSONObject("league");
        return liga == null ? "" : liga.optString("slug", "").trim();
    }

    static String traduzir(String original) {
        if (original == null) return "";
        String chave = Nomes.normalizar(original);
        if (chave.isEmpty()) return "";
        switch (chave) {
            case "brazilian serie a":
            case "campeonato brasileiro":
            case "serie a":
                return "Brasileirão";
            case "copa do brasil":
                return "Copa do Brasil";
            case "conmebol sudamericana":
            case "sulamericana":
            case "sul americana":
                return "Sul-Americana";
            case "conmebol libertadores":
            case "libertadores":
                return "Libertadores";
            case "brazilian campeonato paulista":
            case "campeonato paulista":
            case "paulista":
                return "Paulistão";
            default:
                return original.trim();
        }
    }

    static String traduzirSlug(String slug) {
        if (slug == null) return "";
        switch (slug) {
            case "bra.1":
                return "Brasileirão";
            case "bra.copa_do_brazil":
                return "Copa do Brasil";
            case "conmebol.sudamericana":
                return "Sul-Americana";
            case "conmebol.libertadores":
                return "Libertadores";
            case "bra.camp.paulista":
                return "Paulistão";
            default:
                return "";
        }
    }

    static List<Aba> abas(List<Match> jogos) {
        Map<String, String> porSlug = new LinkedHashMap<>();
        if (jogos != null) {
            for (int i = 0; i < jogos.size(); i++) {
                Match jogo = jogos.get(i);
                if (jogo.leagueSlug.isEmpty()) continue;
                if (!porSlug.containsKey(jogo.leagueSlug)) {
                    String nome = jogo.leagueName.isEmpty() ? jogo.leagueSlug : jogo.leagueName;
                    porSlug.put(jogo.leagueSlug, nome);
                }
            }
        }
        List<Aba> saida = new ArrayList<>();
        for (int i = 0; i < ORDEM.length; i++) {
            String slug = ORDEM[i];
            if (porSlug.containsKey(slug)) {
                saida.add(new Aba(slug, porSlug.remove(slug)));
            }
        }
        for (Map.Entry<String, String> item : porSlug.entrySet()) {
            saida.add(new Aba(item.getKey(), item.getValue()));
        }
        return saida;
    }
}
