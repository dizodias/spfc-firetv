package br.lucas.spfc;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * API pública da ESPN, sem chave. Agenda do time 2026 (próximos com {@code fixture=true},
 * resultados sem) e tabela do Brasileirão. Bloqueia a thread; quem chama está fora da UI.
 */
final class EspnClient {
    static final String TIME_SP = "2026";
    private static final String AGENDA =
            "https://site.api.espn.com/apis/site/v2/sports/soccer/all/teams/2026/schedule";
    private static final String TABELA =
            "https://site.api.espn.com/apis/v2/sports/soccer/bra.1/standings";
    private static final String CONSULTA = "lang=pt&region=br";
    private static final int ESPERA_MS = 12_000;
    private static final Duration AINDA_EM_JOGO = Duration.ofHours(3);

    private EspnClient() { }

    static Snapshot buscar(BroadcastResolver canais) throws IOException {
        ExecutorService pool = Executors.newFixedThreadPool(3);
        try {
            Future<JSONArray> proximos = pool.submit(() ->
                    eventos(Rede.get(AGENDA + "?fixture=true&" + CONSULTA, ESPERA_MS, Rede.UA_APP)));
            Future<JSONArray> resultados = pool.submit(() ->
                    eventos(Rede.get(AGENDA + "?" + CONSULTA, ESPERA_MS, Rede.UA_APP)));
            Future<List<StandingRow>> tabelaFutura = pool.submit(() ->
                    tabela(new JSONObject(Rede.get(TABELA + "?" + CONSULTA, ESPERA_MS, Rede.UA_APP))));

            JSONArray todos = new JSONArray();
            try {
                copiar(todos, proximos.get(25, TimeUnit.SECONDS));
            } catch (Exception ignorado) {
                // Sem próximos a temporada ainda pode ter resultados.
            }
            try {
                copiar(todos, resultados.get(25, TimeUnit.SECONDS));
            } catch (Exception ignorado) {
                // Sem resultados a tela ainda pode mostrar os próximos.
            }

            List<Match> jogos = jogos(todos, Instant.now(), canais);
            List<StandingRow> linhas;
            try {
                linhas = tabelaFutura.get(25, TimeUnit.SECONDS);
            } catch (Exception ignorado) {
                linhas = new ArrayList<>();
            }
            if (jogos.isEmpty() && linhas.isEmpty()) {
                throw new IOException("ESPN não trouxe jogos nem tabela");
            }
            return new Snapshot(jogos, linhas, System.currentTimeMillis());
        } finally {
            pool.shutdownNow();
        }
    }

    /** Jogos do São Paulo, próximos e encerrados, sem repetir id. */
    static List<Match> jogos(JSONArray eventos, Instant agora, BroadcastResolver canais) {
        List<Match> saida = new ArrayList<>();
        if (eventos == null) return saida;
        Set<String> vistos = new HashSet<>();
        for (int i = 0; i < eventos.length(); i++) {
            JSONObject evento = eventos.optJSONObject(i);
            if (evento == null) continue;
            String id = evento.optString("id", "");
            if (!id.isEmpty() && !vistos.add(id)) continue;
            Match jogo = jogo(evento, agora, canais);
            if (jogo != null) saida.add(jogo);
        }
        saida.sort(Comparator.comparing(jogo -> jogo.kickoffInstant));
        return saida;
    }

    static List<StandingRow> tabela(JSONObject raiz) throws JSONException {
        JSONArray filhos = raiz.optJSONArray("children");
        if (filhos == null || filhos.length() == 0) return new ArrayList<>();
        JSONObject bloco = filhos.optJSONObject(0);
        if (bloco == null) return new ArrayList<>();
        JSONObject classificacao = bloco.optJSONObject("standings");
        if (classificacao == null) return new ArrayList<>();
        JSONArray linhas = classificacao.optJSONArray("entries");
        if (linhas == null) return new ArrayList<>();

        List<StandingRow> saida = new ArrayList<>();
        for (int i = 0; i < linhas.length(); i++) {
            JSONObject linha = linhas.optJSONObject(i);
            if (linha == null) continue;
            JSONObject time = linha.optJSONObject("team");
            if (time == null) continue;
            JSONArray stats = linha.optJSONArray("stats");
            int posicao = inteiro(estatistica(stats, "rank"));
            if (posicao <= 0) posicao = i + 1;
            String saldo = texto(estatistica(stats, "pointDifferential"));
            saida.add(new StandingRow(
                    posicao,
                    nomeDoTime(time),
                    inteiro(estatistica(stats, "gamesPlayed")),
                    inteiro(estatistica(stats, "wins")),
                    inteiro(estatistica(stats, "ties")),
                    inteiro(estatistica(stats, "losses")),
                    inteiro(estatistica(stats, "pointsFor")),
                    saldo,
                    inteiro(estatistica(stats, "points")),
                    TIME_SP.equals(time.optString("id"))));
        }
        saida.sort(Comparator.comparingInt(linha -> linha.rank));
        return saida;
    }

    private static JSONArray eventos(String corpo) throws JSONException {
        JSONObject raiz = new JSONObject(corpo);
        JSONArray lista = raiz.optJSONArray("events");
        return lista == null ? new JSONArray() : lista;
    }

    private static Match jogo(JSONObject evento, Instant agora, BroadcastResolver canais) {
        JSONArray competicoes = evento.optJSONArray("competitions");
        if (competicoes == null || competicoes.length() == 0) return null;
        JSONObject competicao = competicoes.optJSONObject(0);
        if (competicao == null) return null;

        JSONObject sp = null;
        JSONObject adversario = null;
        JSONArray times = competicao.optJSONArray("competitors");
        if (times == null) return null;
        for (int i = 0; i < times.length(); i++) {
            JSONObject lado = times.optJSONObject(i);
            if (lado == null) continue;
            JSONObject time = lado.optJSONObject("team");
            if (time == null) continue;
            if (TIME_SP.equals(time.optString("id"))) sp = lado;
            else adversario = lado;
        }
        if (sp == null || adversario == null) return null;

        JSONObject tipo = tipoDoStatus(competicao, evento);
        String estado = tipo == null ? "" : tipo.optString("state", "");
        boolean encerrado = tipo != null
                && (tipo.optBoolean("completed", false) || "post".equals(estado));

        String data = competicao.optString("date", "");
        if (data.isEmpty()) data = evento.optString("date", "");
        Instant instante = instante(data);
        if (instante == null) return null;
        boolean aoVivo = "in".equals(estado);
        if (!encerrado && !aoVivo && instante.isBefore(agora.minus(AINDA_EM_JOGO))) return null;

        JSONObject timeAdversario = adversario.optJSONObject("team");
        String nome = timeAdversario == null ? "" : nomeDoTime(timeAdversario);
        if (nome.isEmpty()) return null;

        String mando = competicao.optBoolean("neutralSite", false)
                ? "neutral"
                : sp.optString("homeAway", "");
        String casa;
        String fora;
        String nomeSp = nomeDoTime(sp.optJSONObject("team"));
        if ("home".equals(sp.optString("homeAway", ""))) {
            casa = nomeSp;
            fora = nome;
        } else {
            casa = nome;
            fora = nomeSp;
        }

        List<String> canaisJogo = new ArrayList<>();
        if (!encerrado && canais != null) {
            canaisJogo = canais.canais(competicao, instante, casa, fora);
            if (canaisJogo == null) canaisJogo = new ArrayList<>();
        }

        return new Match(
                nome,
                mando,
                instante,
                estadio(competicao),
                canaisJogo,
                aoVivo,
                Competicao.nome(evento),
                Competicao.slug(evento),
                encerrado ? gols(sp) : null,
                encerrado ? gols(adversario) : null,
                encerrado);
    }

    /** A ESPN manda {@code 2026-10-29T00:30Z}, sem os segundos. {@link Instant#parse} exige os segundos. */
    static Instant instante(String data) {
        if (data == null) return null;
        String texto = data.trim();
        if (texto.isEmpty()) return null;
        int t = texto.indexOf('T');
        if (t > 0 && texto.indexOf(':', t) == texto.lastIndexOf(':')) {
            int fim = texto.endsWith("Z") ? texto.length() - 1 : texto.length();
            texto = texto.substring(0, fim) + ":00" + texto.substring(fim);
        }
        try {
            return Instant.parse(texto);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    static Integer gols(JSONObject lado) {
        if (lado == null || !lado.has("score") || lado.isNull("score")) return null;
        Object score = lado.opt("score");
        if (score instanceof JSONObject) {
            JSONObject objeto = (JSONObject) score;
            if (objeto.has("value") && !objeto.isNull("value")) {
                return (int) Math.round(objeto.optDouble("value"));
            }
            return inteiroTexto(objeto.optString("displayValue", ""));
        }
        if (score instanceof Number) {
            return (int) Math.round(((Number) score).doubleValue());
        }
        return inteiroTexto(String.valueOf(score));
    }

    private static Integer inteiroTexto(String texto) {
        if (texto == null) return null;
        String limpo = texto.trim();
        if (limpo.isEmpty()) return null;
        try {
            return (int) Math.round(Double.parseDouble(limpo));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static JSONObject tipoDoStatus(JSONObject competicao, JSONObject evento) {
        JSONObject status = competicao.optJSONObject("status");
        if (status == null) status = evento.optJSONObject("status");
        return status == null ? null : status.optJSONObject("type");
    }

    private static String estadio(JSONObject competicao) {
        JSONObject venue = competicao.optJSONObject("venue");
        if (venue == null) return "";
        String nome = venue.optString("fullName", "").trim();
        JSONObject endereco = venue.optJSONObject("address");
        String cidade = endereco == null ? "" : endereco.optString("city", "").trim();
        if (!nome.isEmpty() && !cidade.isEmpty()
                && !nome.toLowerCase(Locale.ROOT).contains(cidade.toLowerCase(Locale.ROOT))) {
            nome = nome + ", " + cidade;
        }
        return nome;
    }

    private static String nomeDoTime(JSONObject time) {
        if (time == null) return "";
        String nome = time.optString("shortDisplayName", "").trim();
        if (nome.isEmpty()) nome = time.optString("displayName", "").trim();
        if (nome.isEmpty()) nome = time.optString("abbreviation", "").trim();
        return nome;
    }

    private static JSONObject estatistica(JSONArray stats, String nome) {
        if (stats == null) return null;
        for (int i = 0; i < stats.length(); i++) {
            JSONObject item = stats.optJSONObject(i);
            if (item != null && nome.equals(item.optString("name"))) return item;
        }
        return null;
    }

    private static int inteiro(JSONObject stat) {
        if (stat == null) return 0;
        return (int) Math.round(stat.optDouble("value", 0));
    }

    private static String texto(JSONObject stat) {
        if (stat == null) return "0";
        String escrito = stat.optString("displayValue", "").trim();
        if (!escrito.isEmpty()) return escrito;
        int n = inteiro(stat);
        return n > 0 ? "+" + n : Integer.toString(n);
    }

    private static void copiar(JSONArray destino, JSONArray origem) throws JSONException {
        if (origem == null) return;
        for (int i = 0; i < origem.length(); i++) destino.put(origem.get(i));
    }
}
