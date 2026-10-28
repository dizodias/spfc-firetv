package br.lucas.spfc;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Junta a leitura da ESPN com o arquivo {@code spfc-cache.json}. Vale por 45 minutos. */
final class SpfcRepository {
    static final long TTL_MS = 45L * 60L * 1000L;
    private final File arquivo;

    SpfcRepository(File pasta) {
        this.arquivo = new File(pasta, "spfc-cache.json");
    }

    Snapshot ler() {
        if (!arquivo.exists()) return null;
        try {
            return interpretar(new JSONObject(lerTexto(arquivo)));
        } catch (Exception e) {
            return null;
        }
    }

    boolean fresco(Snapshot snapshot, long agora) {
        return snapshot != null
                && agora >= snapshot.fetchedAtMillis
                && agora - snapshot.fetchedAtMillis < TTL_MS;
    }

    /** Busca na ESPN. Grava o cache só quando a tabela veio junto. */
    Snapshot atualizar() throws IOException {
        Snapshot novo = EspnClient.buscar(new EspnBroadcastResolver());
        if (!novo.standings.isEmpty()) gravar(novo);
        return novo;
    }

    void gravar(Snapshot snapshot) throws IOException {
        File pasta = arquivo.getParentFile();
        if (pasta != null && !pasta.exists() && !pasta.mkdirs()) {
            throw new IOException("sem pasta de cache");
        }
        File temporario = new File(pasta, "spfc-cache.json.tmp");
        final byte[] bytes;
        try {
            bytes = json(snapshot).toString().getBytes(StandardCharsets.UTF_8);
        } catch (JSONException e) {
            throw new IOException("não montou o cache", e);
        }
        try (FileOutputStream saida = new FileOutputStream(temporario)) {
            saida.write(bytes);
        }
        if (arquivo.exists() && !arquivo.delete()) {
            throw new IOException("não apagou o cache antigo");
        }
        if (!temporario.renameTo(arquivo)) {
            throw new IOException("não gravou o cache");
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
                        item.optBoolean("live", false)));
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

    private static String lerTexto(File origem) throws IOException {
        try (InputStream entrada = new FileInputStream(origem)) {
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            byte[] bloco = new byte[4096];
            int lido;
            while ((lido = entrada.read(bloco)) >= 0) saida.write(bloco, 0, lido);
            // toString(Charset) só existe no Android 13. No Fire OS 6 o app fecha.
            return new String(saida.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
