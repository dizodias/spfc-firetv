package br.lucas.spfc;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Junta a leitura da ESPN com o arquivo {@code spfc-cache.json}. Vale por 45 minutos.
 * A rodada ao vivo é regravada por cima sem renovar esse prazo.
 */
final class SpfcRepository {
    static final long TTL_MS = 45L * 60L * 1000L;
    private final File pasta;
    private final File arquivo;

    SpfcRepository(File pasta) {
        this.pasta = pasta;
        this.arquivo = new File(pasta, "spfc-cache.json");
    }

    Snapshot ler() {
        if (!arquivo.exists()) return null;
        try {
            return interpretar(new JSONObject(Rede.arquivo(arquivo)));
        } catch (Exception e) {
            return null;
        }
    }

    boolean fresco(Snapshot snapshot, long agora) {
        return snapshot != null
                && agora >= snapshot.fetchedAtMillis
                && agora - snapshot.fetchedAtMillis < TTL_MS;
    }

    Snapshot atualizar() throws IOException {
        return atualizar(null);
    }

    /** Busca na ESPN e na cadeia de canais. Grava o cache só quando a tabela veio junto. */
    Snapshot atualizar(Snapshot anterior) throws IOException {
        BroadcastResolver cadeia = cadeia();
        Snapshot novo = EspnClient.buscar(cadeia);
        List<JogoRodada> rodada = anterior == null ? new ArrayList<>() : anterior.rodada;
        boolean rodadaOk = false;
        try {
            rodada = EspnScoreboard.buscar(cadeiaRodada(), Instant.now());
            rodadaOk = true;
        } catch (IOException e) {
            Log.w("Spfc", "rodada indisponível", e);
        }
        Snapshot completo = new Snapshot(
                novo.matches, novo.standings, rodada, novo.fetchedAtMillis, rodadaOk);
        if (!completo.standings.isEmpty()) gravar(completo);
        return completo;
    }

    /** Só o scoreboard. Se um jogo ao vivo acabou, a tabela vem junto. */
    Snapshot atualizarRodada(Snapshot base) throws IOException {
        List<JogoRodada> rodada = base == null ? new ArrayList<>() : base.rodada;
        boolean rodadaOk = false;
        try {
            rodada = EspnScoreboard.buscar(cadeiaRodada(), Instant.now());
            rodadaOk = true;
        } catch (IOException e) {
            Log.w("Spfc", "rodada indisponível", e);
        }
        List<Match> jogos = base == null ? new ArrayList<>() : base.matches;
        List<StandingRow> tabela = base == null ? new ArrayList<>() : base.standings;
        long obtido = base == null ? System.currentTimeMillis() : base.fetchedAtMillis;
        if (algumEncerrou(base, rodada)) {
            try {
                Snapshot novo = EspnClient.buscar(cadeia());
                if (!novo.matches.isEmpty()) jogos = novo.matches;
                if (!novo.standings.isEmpty()) {
                    tabela = novo.standings;
                    obtido = novo.fetchedAtMillis;
                }
            } catch (IOException ignorado) {
                // O placar da rodada segue mesmo sem a tabela nova.
            }
        }
        Snapshot completo = new Snapshot(jogos, tabela, rodada, obtido, rodadaOk);
        if (!completo.standings.isEmpty()) gravar(completo);
        return completo;
    }

    void gravar(Snapshot snapshot) throws IOException {
        try {
            Rede.gravar(arquivo, json(snapshot).toString());
        } catch (JSONException e) {
            throw new IOException("não montou o cache", e);
        }
    }

    private BroadcastResolver cadeia() {
        return new ChainBroadcastResolver(
                new EspnBroadcastResolver(),
                new GeGloboBroadcastResolver(pasta),
                new JsonCanaisResolver());
    }

    /** Rodada: ge (uma consulta) e, se faltar canal, data/canais.json. */
    private BroadcastResolver cadeiaRodada() {
        return new ChainBroadcastResolver(
                new GeRodadaBroadcastResolver(pasta),
                new JsonCanaisResolver());
    }

    private static boolean algumEncerrou(Snapshot antes, List<JogoRodada> agora) {
        if (antes == null) return false;
        for (int i = 0; i < antes.rodada.size(); i++) {
            JogoRodada antigo = antes.rodada.get(i);
            if (antigo.status != JogoRodada.Status.AO_VIVO) continue;
            for (int j = 0; j < agora.size(); j++) {
                JogoRodada novo = agora.get(j);
                if (antigo.id.equals(novo.id) && novo.status == JogoRodada.Status.ENCERRADO) {
                    return true;
                }
            }
        }
        return false;
    }

    private static JSONObject json(Snapshot snapshot) throws JSONException {
        JSONObject raiz = new JSONObject();
        raiz.put("fetchedAt", snapshot.fetchedAtMillis);
        JSONArray jogos = new JSONArray();
        for (int i = 0; i < snapshot.matches.size(); i++) {
            Match jogo = snapshot.matches.get(i);
            JSONObject item = new JSONObject();
            item.put("opponent", jogo.opponent);
            item.put("homeAway", jogo.homeAway);
            item.put("kickoff", jogo.kickoffInstant.toString());
            item.put("venue", jogo.venue);
            item.put("live", jogo.live);
            item.put("league", jogo.leagueName);
            item.put("leagueSlug", jogo.leagueSlug);
            item.put("finished", jogo.finished);
            if (jogo.goalsFor != null) item.put("goalsFor", jogo.goalsFor.intValue());
            if (jogo.goalsAgainst != null) item.put("goalsAgainst", jogo.goalsAgainst.intValue());
            JSONArray canais = new JSONArray();
            for (int c = 0; c < jogo.channels.size(); c++) canais.put(jogo.channels.get(c));
            item.put("channels", canais);
            jogos.put(item);
        }
        raiz.put("matches", jogos);
        JSONArray rodada = new JSONArray();
        for (int i = 0; i < snapshot.rodada.size(); i++) {
            rodada.put(jsonRodada(snapshot.rodada.get(i)));
        }
        raiz.put("rodada", rodada);
        raiz.put("rodadaOk", snapshot.rodadaOk);
        JSONArray tabela = new JSONArray();
        for (int i = 0; i < snapshot.standings.size(); i++) {
            StandingRow linha = snapshot.standings.get(i);
            JSONObject item = new JSONObject();
            item.put("rank", linha.rank);
            item.put("team", linha.team);
            item.put("played", linha.played);
            item.put("wins", linha.wins);
            item.put("draws", linha.draws);
            item.put("losses", linha.losses);
            item.put("goalsFor", linha.goalsFor);
            item.put("gd", linha.gd);
            item.put("pts", linha.pts);
            item.put("spfc", linha.isSpfc);
            tabela.put(item);
        }
        raiz.put("standings", tabela);
        return raiz;
    }

    private static JSONObject jsonRodada(JogoRodada jogo) throws JSONException {
        JSONObject item = new JSONObject();
        item.put("id", jogo.id);
        if (jogo.rodada != null) item.put("round", jogo.rodada.intValue());
        item.put("kickoff", jogo.kickoff.toString());
        item.put("venue", jogo.venue);
        item.put("home", jogo.casaNome);
        item.put("away", jogo.foraNome);
        item.put("homeId", jogo.casaId);
        item.put("awayId", jogo.foraId);
        item.put("homeLogo", jogo.casaLogo);
        item.put("awayLogo", jogo.foraLogo);
        if (jogo.casaGols != null) item.put("homeGoals", jogo.casaGols.intValue());
        if (jogo.foraGols != null) item.put("awayGoals", jogo.foraGols.intValue());
        item.put("status", statusTexto(jogo.status));
        item.put("clock", jogo.minuto);
        item.put("spfc", jogo.saoPaulo);
        JSONArray canais = new JSONArray();
        for (int c = 0; c < jogo.channels.size(); c++) canais.put(jogo.channels.get(c));
        item.put("channels", canais);
        return item;
    }

    private static Snapshot interpretar(JSONObject raiz) throws JSONException {
        List<Match> jogos = new ArrayList<>();
        JSONArray matches = raiz.optJSONArray("matches");
        if (matches != null) {
            for (int i = 0; i < matches.length(); i++) {
                JSONObject item = matches.optJSONObject(i);
                if (item == null) continue;
                List<String> canais = new ArrayList<>();
                JSONArray lista = item.optJSONArray("channels");
                if (lista != null) {
                    for (int c = 0; c < lista.length(); c++) {
                        String canal = lista.optString(c, "").trim();
                        if (!canal.isEmpty()) canais.add(canal);
                    }
                }
                Instant inicio = EspnClient.instante(item.optString("kickoff", ""));
                if (inicio == null) continue;
                jogos.add(new Match(
                        item.optString("opponent", ""),
                        item.optString("homeAway", ""),
                        inicio,
                        item.optString("venue", ""),
                        canais,
                        item.optBoolean("live", false),
                        item.optString("league", ""),
                        item.optString("leagueSlug", ""),
                        inteiroOuNulo(item, "goalsFor"),
                        inteiroOuNulo(item, "goalsAgainst"),
                        item.optBoolean("finished", false)));
            }
        }
        List<JogoRodada> rodada = new ArrayList<>();
        JSONArray jogosRodada = raiz.optJSONArray("rodada");
        if (jogosRodada != null) {
            for (int i = 0; i < jogosRodada.length(); i++) {
                JSONObject item = jogosRodada.optJSONObject(i);
                if (item == null) continue;
                Instant inicio = EspnClient.instante(item.optString("kickoff", ""));
                if (inicio == null) continue;
                List<String> canais = new ArrayList<>();
                JSONArray lista = item.optJSONArray("channels");
                if (lista != null) {
                    for (int c = 0; c < lista.length(); c++) {
                        String canal = lista.optString(c, "").trim();
                        if (!canal.isEmpty()) canais.add(canal);
                    }
                }
                String casaId = item.optString("homeId", "");
                String foraId = item.optString("awayId", "");
                boolean sp = item.has("spfc")
                        ? item.optBoolean("spfc")
                        : EspnClient.TIME_SP.equals(casaId) || EspnClient.TIME_SP.equals(foraId);
                Integer numero = inteiroOuNulo(item, "round");
                if (numero != null && numero <= 0) numero = null;
                rodada.add(new JogoRodada(
                        item.optString("id", ""),
                        numero,
                        inicio,
                        item.optString("venue", ""),
                        item.optString("home", ""),
                        item.optString("away", ""),
                        casaId,
                        foraId,
                        item.optString("homeLogo", ""),
                        item.optString("awayLogo", ""),
                        inteiroOuNulo(item, "homeGoals"),
                        inteiroOuNulo(item, "awayGoals"),
                        statusDe(item.optString("status", "")),
                        item.optString("clock", ""),
                        canais,
                        sp));
            }
        }
        List<StandingRow> tabela = new ArrayList<>();
        JSONArray standings = raiz.optJSONArray("standings");
        if (standings != null) {
            for (int i = 0; i < standings.length(); i++) {
                JSONObject item = standings.optJSONObject(i);
                if (item == null) continue;
                tabela.add(new StandingRow(
                        item.optInt("rank", i + 1),
                        item.optString("team", ""),
                        item.optInt("played", 0),
                        item.optInt("wins", 0),
                        item.optInt("draws", 0),
                        item.optInt("losses", 0),
                        item.optInt("goalsFor", 0),
                        item.optString("gd", "0"),
                        item.optInt("pts", 0),
                        item.optBoolean("spfc", false)));
            }
        }
        boolean rodadaOk = raiz.has("rodadaOk")
                ? raiz.optBoolean("rodadaOk", false)
                : !rodada.isEmpty();
        return new Snapshot(jogos, tabela, rodada, raiz.optLong("fetchedAt", 0), rodadaOk);
    }

    private static String statusTexto(JogoRodada.Status status) {
        if (status == JogoRodada.Status.AO_VIVO) return "ao_vivo";
        if (status == JogoRodada.Status.ENCERRADO) return "encerrado";
        return "agendado";
    }

    private static JogoRodada.Status statusDe(String texto) {
        if ("ao_vivo".equals(texto)) return JogoRodada.Status.AO_VIVO;
        if ("encerrado".equals(texto)) return JogoRodada.Status.ENCERRADO;
        return JogoRodada.Status.AGENDADO;
    }

    private static Integer inteiroOuNulo(JSONObject item, String chave) {
        if (!item.has(chave) || item.isNull(chave)) return null;
        return item.optInt(chave);
    }
}
