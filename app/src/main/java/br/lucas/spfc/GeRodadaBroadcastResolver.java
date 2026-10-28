package br.lucas.spfc;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Canais de todos os jogos da rodada do Brasileirão.
 *
 * A página https://ge.globo.com/futebol/brasileirao-serie-a/ embute a rodada
 * em {@code listaJogos}, mas {@code transmissao} vem só como "fique por dentro".
 * O JSON com {@code liveWatchSources} por partida é o da agenda
 * (https://ge.globo.com/agenda/): uma consulta {@code championshipsAgenda}
 * em https://geql.globo.com/graphql, com a janela de 3 dias atrás a 4 à frente
 * na mesma requisição. Cache de 6 horas. Falha devolve lista vazia.
 */
final class GeRodadaBroadcastResolver implements BroadcastResolver {
    static final String URL = "https://geql.globo.com/graphql";
    static final long TTL_MS = 6L * 60L * 60L * 1000L;
    private static final int ESPERA_MS = 12_000;
    private static final int DIAS_ANTES = 3;
    private static final int DIAS_DEPOIS = 4;
    private static final String ARQUIVO = "ge-rodada.json";
    private static final String PARTIDA =
            "{ startDate "
                    + "firstContestant { ... on Team { popularName name } } "
                    + "secondContestant { ... on Team { popularName name } } "
                    + "liveWatchSources { name } }";

    interface Fonte {
        String post(String url, String corpo) throws IOException;
    }

    private final File arquivo;
    private final Fonte fonte;
    private List<GeGloboParser.Jogo> carregados;

    GeRodadaBroadcastResolver(File pasta) {
        this(pasta, (url, corpo) -> Rede.post(url, corpo, ESPERA_MS, Rede.UA_NAVEGADOR));
    }

    GeRodadaBroadcastResolver(Fonte fonte) {
        this(null, fonte);
    }

    GeRodadaBroadcastResolver(File pasta, Fonte fonte) {
        this.arquivo = pasta == null ? null : new File(pasta, ARQUIVO);
        this.fonte = fonte;
    }

    /** Uma consulta, oito dias, o mesmo recorte do scoreboard. */
    static String pedido(LocalDate hoje) throws JSONException {
        StringBuilder consulta = new StringBuilder("query {");
        int indice = 0;
        for (int deslocamento = -DIAS_ANTES; deslocamento <= DIAS_DEPOIS; deslocamento++) {
            LocalDate dia = hoje.plusDays(deslocamento);
            consulta.append(" d").append(indice++)
                    .append(": championshipsAgenda(filter: { date: \"").append(dia).append("\" }) {")
                    .append(" championship { name } ")
                    .append(bloco("NOW"))
                    .append(bloco("FUTURE"))
                    .append(bloco("PAST"))
                    .append("}");
        }
        consulta.append(" }");
        JSONObject raiz = new JSONObject();
        raiz.put("query", consulta.toString());
        return raiz.toString();
    }

    @Override
    public List<String> canais(JSONObject competicao, Instant kickoff, String casa, String fora) {
        if (kickoff == null) return new ArrayList<>();
        try {
            LocalDate dia = kickoff.atZone(Horario.SAO_PAULO).toLocalDate();
            List<GeGloboParser.Jogo> jogos = agenda();
            for (int i = 0; i < jogos.size(); i++) {
                GeGloboParser.Jogo jogo = jogos.get(i);
                if (!dia.equals(jogo.data)) continue;
                boolean direto = Nomes.time(casa, jogo.casaPopular, jogo.casaNome)
                        && Nomes.time(fora, jogo.foraPopular, jogo.foraNome);
                boolean invertido = Nomes.time(casa, jogo.foraPopular, jogo.foraNome)
                        && Nomes.time(fora, jogo.casaPopular, jogo.casaNome);
                if (direto || invertido) return CanaisPermitidos.filtrar(jogo.canais);
            }
        } catch (RuntimeException ignorado) {
            return new ArrayList<>();
        }
        return new ArrayList<>();
    }

    private List<GeGloboParser.Jogo> agenda() {
        if (carregados != null) return carregados;
        String corpo = lerCache();
        if (corpo == null) {
            corpo = baixar();
            if (util(corpo)) gravarCache(corpo);
        }
        carregados = GeRodadaParser.jogos(corpo == null ? "" : corpo);
        return carregados;
    }

    private String baixar() {
        String pedido;
        try {
            pedido = pedido(LocalDate.now(Horario.SAO_PAULO));
        } catch (JSONException e) {
            return "";
        }
        try {
            String lido = fonte.post(URL, pedido);
            return lido == null ? "" : lido;
        } catch (Exception e) {
            return "";
        }
    }

    private static boolean util(String corpo) {
        if (corpo == null || corpo.isEmpty()) return false;
        try {
            return new JSONObject(corpo).optJSONObject("data") != null;
        } catch (JSONException e) {
            return false;
        }
    }

    private static String bloco(String momento) {
        String nome = momento.toLowerCase(Locale.ROOT);
        return nome + ": items(filter: { moment: " + momento + " }) { ... on SoccerEvent { match "
                + PARTIDA + " } } ";
    }

    private String lerCache() {
        if (arquivo == null || !arquivo.exists()) return null;
        if (System.currentTimeMillis() - arquivo.lastModified() >= TTL_MS) return null;
        try {
            JSONObject raiz = new JSONObject(Rede.arquivo(arquivo));
            String corpo = raiz.optString("corpo", "");
            return corpo.isEmpty() ? null : corpo;
        } catch (Exception e) {
            return null;
        }
    }

    private void gravarCache(String corpo) {
        if (arquivo == null) return;
        try {
            JSONObject raiz = new JSONObject();
            raiz.put("obtidoEm", System.currentTimeMillis());
            raiz.put("corpo", corpo);
            Rede.gravar(arquivo, raiz.toString());
        } catch (Exception ignorado) {
            // Cache é opcional.
        }
    }
}
