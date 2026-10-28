package br.lucas.spfc;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lê {@code window.dataSportsSchedule.scheduleTeam} no HTML da agenda do ge.
 * Se o formato mudar, devolve lista vazia.
 */
final class GeGloboParser {
    private GeGloboParser() { }

    static final class Jogo {
        final LocalDate data;
        final String casaPopular;
        final String casaNome;
        final String foraPopular;
        final String foraNome;
        final List<String> canais;

        Jogo(LocalDate data, String casaPopular, String casaNome,
                String foraPopular, String foraNome, List<String> canais) {
            this.data = data;
            this.casaPopular = casaPopular;
            this.casaNome = casaNome;
            this.foraPopular = foraPopular;
            this.foraNome = foraNome;
            this.canais = canais;
        }
    }

    static List<Jogo> jogos(String html) {
        List<Jogo> saida = new ArrayList<>();
        if (html == null || html.isEmpty()) return saida;
        try {
            String json = jsonDoScheduleTeam(html);
            if (json == null) return saida;
            JSONObject raiz = new JSONObject(json);
            JSONObject agenda = raiz.optJSONObject("teamAgenda");
            if (agenda == null) return saida;
            coletar(agenda.optJSONArray("now"), saida);
            coletar(agenda.optJSONArray("future"), saida);
        } catch (Exception ignorado) {
            return new ArrayList<>();
        }
        return saida;
    }

    static String jsonDoScheduleTeam(String html) {
        int ancora = html.indexOf("window.dataSportsSchedule");
        if (ancora < 0) return null;
        int chave = html.indexOf("scheduleTeam:", ancora);
        if (chave < 0) return null;
        int inicio = html.indexOf('{', chave);
        if (inicio < 0) return null;
        return objetoBalanceado(html, inicio);
    }

    static String objetoBalanceado(String texto, int inicio) {
        int profundidade = 0;
        boolean emString = false;
        boolean escape = false;
        for (int i = inicio; i < texto.length(); i++) {
            char c = texto.charAt(i);
            if (emString) {
                if (escape) escape = false;
                else if (c == '\\') escape = true;
                else if (c == '"') emString = false;
                continue;
            }
            if (c == '"') emString = true;
            else if (c == '{') profundidade++;
            else if (c == '}') {
                profundidade--;
                if (profundidade == 0) return texto.substring(inicio, i + 1);
            }
        }
        return null;
    }

    private static void coletar(JSONArray lista, List<Jogo> saida) {
        if (lista == null) return;
        for (int i = 0; i < lista.length(); i++) {
            JSONObject evento = lista.optJSONObject(i);
            if (evento == null) continue;
            JSONObject match = evento.optJSONObject("match");
            if (match == null) match = evento;
            Jogo jogo = jogo(match);
            if (jogo != null) saida.add(jogo);
        }
    }

    private static Jogo jogo(JSONObject match) {
        LocalDate data = data(match.optString("startDate", ""));
        if (data == null) return null;
        JSONObject casa = match.optJSONObject("firstContestant");
        JSONObject fora = match.optJSONObject("secondContestant");
        if (casa == null || fora == null) return null;
        return new Jogo(
                data,
                casa.optString("popularName", ""),
                casa.optString("name", ""),
                fora.optString("popularName", ""),
                fora.optString("name", ""),
                fontes(match.optJSONArray("liveWatchSources")));
    }

    private static LocalDate data(String texto) {
        if (texto == null || texto.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(texto.trim());
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
