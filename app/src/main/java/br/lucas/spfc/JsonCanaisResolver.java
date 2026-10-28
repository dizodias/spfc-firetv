package br.lucas.spfc;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Override manual em {@code data/canais.json} no raw do GitHub.
 * Sem cache longo: o refresh do snapshot já puxa a correção.
 */
final class JsonCanaisResolver implements BroadcastResolver {
    static final String URL =
            "https://raw.githubusercontent.com/dizodias/spfc-firetv/main/data/canais.json";
    private static final int ESPERA_MS = 5_000;

    interface FonteJson {
        String json();
    }

    private final FonteJson fonte;
    private String corpo;

    JsonCanaisResolver() {
        this(() -> {
            try {
                return Rede.get(URL, ESPERA_MS, Rede.UA_APP);
            } catch (Exception e) {
                return "";
            }
        });
    }

    JsonCanaisResolver(FonteJson fonte) {
        this.fonte = fonte;
    }

    @Override
    public List<String> canais(JSONObject competicao, Instant kickoff, String casa, String fora) {
        if (kickoff == null) return new ArrayList<>();
        try {
            JSONObject raiz = new JSONObject(json());
            JSONArray jogos = raiz.optJSONArray("jogos");
            if (jogos == null) return new ArrayList<>();
            LocalDate dia = kickoff.atZone(Horario.SAO_PAULO).toLocalDate();
            for (int i = 0; i < jogos.length(); i++) {
                JSONObject item = jogos.optJSONObject(i);
                if (item == null) continue;
                LocalDate data = data(item.optString("data", ""));
                if (data == null || !data.equals(dia)) continue;
                if (!Nomes.mesmoConfronto(casa, fora,
                        item.optString("casa", ""), item.optString("fora", ""))) {
                    continue;
                }
                return nomes(item.optJSONArray("canais"));
            }
        } catch (Exception ignorado) {
            return new ArrayList<>();
        }
        return new ArrayList<>();
    }

    private String json() {
        if (corpo != null) return corpo;
        try {
            String lido = fonte.json();
            corpo = lido == null ? "" : lido;
        } catch (Exception e) {
            corpo = "";
        }
        return corpo;
    }

    private static LocalDate data(String texto) {
        if (texto == null || texto.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static List<String> nomes(JSONArray lista) {
        List<String> saida = new ArrayList<>();
        if (lista == null) return saida;
        for (int i = 0; i < lista.length(); i++) {
            String nome = lista.optString(i, "").trim();
            if (!nome.isEmpty() && !saida.contains(nome)) saida.add(nome);
        }
        return saida;
    }
}
