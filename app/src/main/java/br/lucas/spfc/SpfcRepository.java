package br.lucas.spfc;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Junta a leitura da ESPN com o arquivo {@code spfc-cache.json}. Vale por 45 minutos. */
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

    /** Busca na ESPN e na cadeia de canais. Grava o cache só quando a tabela veio junto. */
    Snapshot atualizar() throws IOException {
        BroadcastResolver cadeia = new ChainBroadcastResolver(
                new EspnBroadcastResolver(),
                new GeGloboBroadcastResolver(pasta),
                new JsonCanaisResolver());
        Snapshot novo = EspnClient.buscar(cadeia);
        if (!novo.standings.isEmpty()) gravar(novo);
        return novo;
    }

    void gravar(Snapshot snapshot) throws IOException {
        try {
            Rede.gravar(arquivo, json(snapshot).toString());
        } catch (JSONException e) {
            throw new IOException("não montou o cache", e);
        }
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
        return new Snapshot(jogos, tabela, raiz.optLong("fetchedAt", 0));
    }

    private static Integer inteiroOuNulo(JSONObject item, String chave) {
        if (!item.has(chave) || item.isNull(chave)) return null;
        return item.optInt(chave);
    }
}
