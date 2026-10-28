package br.lucas.spfc;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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
 * API pública da ESPN, sem chave. O placar de um dia não traz a temporada: o calendário da liga
 * lista as datas e cada data vira um placar. A agenda do time só devolve jogo já disputado.
 * Bloqueia a thread; quem chama está fora da UI.
 */
final class EspnClient {
    static final String TIME_SP = "2026";
    private static final String PLACAR =
            "https://site.api.espn.com/apis/site/v2/sports/soccer/bra.1/scoreboard";
    private static final String TABELA =
            "https://site.api.espn.com/apis/v2/sports/soccer/bra.1/standings";
    private static final String CONSULTA = "lang=pt&region=br";
    private static final int ESPERA_MS = 12_000;
    private static final Duration AINDA_EM_JOGO = Duration.ofHours(3);

    private EspnClient() { }

    static Snapshot buscar(BroadcastResolver canais) throws IOException {
        try {
            JSONObject placar = new JSONObject(ler(PLACAR + "?" + CONSULTA));
            LocalDate hoje = LocalDate.now(Horario.SAO_PAULO);
            String chaveHoje = hoje.format(DateTimeFormatter.BASIC_ISO_DATE);
            List<String> dias = dias(calendario(placar), hoje);

            ExecutorService pool = Executors.newFixedThreadPool(4);
            try {
                Future<List<StandingRow>> tabelaFutura = pool.submit(() ->
                        tabela(new JSONObject(ler(TABELA + "?" + CONSULTA))));
                List<Future<JSONArray>> diasFuturos = new ArrayList<>();
                for (int i = 0; i < dias.size(); i++) {
                    String dia = dias.get(i);
                    if (dia.equals(chaveHoje)) continue;
                    diasFuturos.add(pool.submit(() -> placarDoDia(dia)));
                }

                JSONArray eventos = new JSONArray();
                copiar(eventos, placar.optJSONArray("events"));
                for (int i = 0; i < diasFuturos.size(); i++) {
                    try {
                        copiar(eventos, diasFuturos.get(i).get(25, TimeUnit.SECONDS));
                    } catch (Exception ignorado) {
                        // Um dia que falhou não derruba o resto da rodada.
                    }
                }

                List<Match> jogos = jogos(eventos, Instant.now(), canais);
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
        } catch (JSONException e) {
            throw new IOException("Resposta da ESPN ilegível", e);
        }
    }

    /** Datas {@code yyyyMMdd} do calendário, de {@code hoje} em diante, na ordem da ESPN. */
    static List<String> dias(JSONArray calendario, LocalDate hoje) {
        List<String> saida = new ArrayList<>();
        if (calendario == null) return saida;
        for (int i = 0; i < calendario.length(); i++) {
            String texto = calendario.optString(i, "");
            if (texto.length() < 10) {
                JSONObject item = calendario.optJSONObject(i);
                texto = item == null ? "" : item.optString("startDate", "");
            }
            if (texto.length() < 10) continue;
            LocalDate dia;
            try {
                dia = LocalDate.parse(texto.substring(0, 10));
            } catch (DateTimeParseException e) {
                continue;
            }
            if (dia.isBefore(hoje)) continue;
            String chave = texto.substring(0, 4) + texto.substring(5, 7) + texto.substring(8, 10);
            if (!saida.contains(chave)) saida.add(chave);
        }
        return saida;
    }

    /** Próximos jogos do São Paulo. Encerrado e jogo de outro time ficam de fora. */
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

    private static JSONArray placarDoDia(String dia) throws IOException, JSONException {
        JSONObject json = new JSONObject(ler(PLACAR + "?dates=" + dia + "&" + CONSULTA));
        JSONArray eventos = json.optJSONArray("events");
        return eventos == null ? new JSONArray() : eventos;
    }

    private static JSONArray calendario(JSONObject placar) {
        JSONArray ligas = placar.optJSONArray("leagues");
        if (ligas == null || ligas.length() == 0) return new JSONArray();
        JSONObject liga = ligas.optJSONObject(0);
        if (liga == null) return new JSONArray();
        JSONArray calendario = liga.optJSONArray("calendar");
        return calendario == null ? new JSONArray() : calendario;
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
        boolean encerrado = tipo != null && tipo.optBoolean("completed", false);
        if (encerrado || "post".equals(estado)) return null;

        String data = competicao.optString("date", "");
        if (data.isEmpty()) data = evento.optString("date", "");
        Instant instante = instante(data);
        if (instante == null) return null;
        boolean aoVivo = "in".equals(estado);
        if (!aoVivo && instante.isBefore(agora.minus(AINDA_EM_JOGO))) return null;

        JSONObject timeAdversario = adversario.optJSONObject("team");
        String nome = timeAdversario == null ? "" : nomeDoTime(timeAdversario);
        if (nome.isEmpty()) return null;

        String mando = competicao.optBoolean("neutralSite", false)
                ? "neutral"
                : sp.optString("homeAway", "");
        return new Match(nome, mando, instante, estadio(competicao), canais.canais(competicao), aoVivo);
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

    private static String ler(String url) throws IOException {
        HttpURLConnection conexao = (HttpURLConnection) new URL(url).openConnection();
        conexao.setConnectTimeout(ESPERA_MS);
        conexao.setReadTimeout(ESPERA_MS);
        conexao.setRequestProperty("Accept", "application/json");
        conexao.setRequestProperty("User-Agent", "SPFC-FireTV/1.0");
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

    private static String texto(InputStream entrada) throws IOException {
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        byte[] bloco = new byte[4096];
        int lido;
        while ((lido = entrada.read(bloco)) >= 0) saida.write(bloco, 0, lido);
        // toString(Charset) só existe no Android 13. No Fire OS 6 o app fecha.
        return new String(saida.toByteArray(), StandardCharsets.UTF_8);
    }
}
