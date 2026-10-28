package br.lucas.spfc;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Lê o JSON de {@code championshipsAgenda} do ge.
 * Fica só o Campeonato Brasileiro. Série B e o resto saem.
 * Se o formato mudar, devolve lista vazia.
 */
final class GeRodadaParser {
    private GeRodadaParser() { }

    static List<GeGloboParser.Jogo> jogos(String corpo) {
        List<GeGloboParser.Jogo> saida = new ArrayList<>();
        if (corpo == null || corpo.isEmpty()) return saida;
        try {
            JSONObject data = new JSONObject(corpo).optJSONObject("data");
            if (data == null) return saida;
            Iterator<String> chaves = data.keys();
            while (chaves.hasNext()) {
                JSONArray dias = data.optJSONArray(chaves.next());
                if (dias == null) continue;
                for (int i = 0; i < dias.length(); i++) {
                    JSONObject grupo = dias.optJSONObject(i);
                    if (grupo == null || !serieA(grupo)) continue;
                    coletar(grupo.optJSONArray("now"), saida);
                    coletar(grupo.optJSONArray("future"), saida);
                    coletar(grupo.optJSONArray("past"), saida);
                }
            }
        } catch (Exception ignorado) {
            return new ArrayList<>();
        }
        return saida;
    }

    private static boolean serieA(JSONObject grupo) {
        JSONObject campeonato = grupo.optJSONObject("championship");
        if (campeonato == null) return false;
        String nome = campeonato.optString("name", "");
        return "Campeonato Brasileiro".equals(nome)
                || "Campeonato Brasileiro Série A".equals(nome);
    }

    private static void coletar(JSONArray lista, List<GeGloboParser.Jogo> saida) {
        if (lista == null) return;
        for (int i = 0; i < lista.length(); i++) {
            JSONObject evento = lista.optJSONObject(i);
            if (evento == null) continue;
            JSONObject partida = evento.optJSONObject("match");
            if (partida == null) partida = evento;
            GeGloboParser.Jogo jogo = jogo(partida);
            if (jogo != null) saida.add(jogo);
        }
    }

    private static GeGloboParser.Jogo jogo(JSONObject partida) {
        LocalDate data = data(partida.optString("startDate", ""));
        if (data == null) return null;
        JSONObject casa = partida.optJSONObject("firstContestant");
        JSONObject fora = partida.optJSONObject("secondContestant");
        if (casa == null || fora == null) return null;
        return new GeGloboParser.Jogo(
                data,
                casa.optString("popularName", ""),
                casa.optString("name", ""),
                fora.optString("popularName", ""),
                fora.optString("name", ""),
                fontes(partida.optJSONArray("liveWatchSources")));
    }

    private static LocalDate data(String texto) {
        if (texto == null) return null;
        String limpo = texto.trim();
        if (limpo.length() >= 10) limpo = limpo.substring(0, 10);
        if (limpo.isEmpty()) return null;
        try {
            return LocalDate.parse(limpo);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static List<String> fontes(JSONArray lista) {
        List<String> saida = new ArrayList<>();
        if (lista == null) return saida;
        for (int i = 0; i < lista.length(); i++) {
            JSONObject item = lista.optJSONObject(i);
            String nome = item == null ? lista.optString(i, "") : item.optString("name", "");
            nome = nome.trim();
            if (!nome.isEmpty() && !saida.contains(nome)) saida.add(nome);
        }
        return saida;
    }
}
