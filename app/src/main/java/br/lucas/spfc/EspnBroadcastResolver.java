package br.lucas.spfc;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lê {@code broadcasts} e {@code geoBroadcasts} da ESPN. Se houver canal com região BR
 * ou idioma pt, fica só com esses. Nome genérico "TV" não entra.
 */
final class EspnBroadcastResolver implements BroadcastResolver {
    @Override
    public List<String> canais(JSONObject competicao) {
        List<String> brasil = new ArrayList<>();
        List<String> outros = new ArrayList<>();
        coletar(competicao.optJSONArray("geoBroadcasts"), brasil, outros);
        coletar(competicao.optJSONArray("broadcasts"), brasil, outros);
        String unico = competicao.optString("broadcast", "").trim();
        if (!unico.isEmpty() && !generico(unico)) {
            outros.add(unico);
        }
        return distintos(brasil.isEmpty() ? outros : brasil);
    }

    private static void coletar(JSONArray lista, List<String> brasil, List<String> outros) {
        if (lista == null) return;
        for (int i = 0; i < lista.length(); i++) {
            JSONObject item = lista.optJSONObject(i);
            if (item == null) continue;
            List<String> destino = brasileiro(item) ? brasil : outros;
            JSONArray nomes = item.optJSONArray("names");
            if (nomes != null) {
                for (int j = 0; j < nomes.length(); j++) {
                    adicionar(destino, nomes.optString(j, ""));
                }
            }
            JSONObject midia = item.optJSONObject("media");
            if (midia != null) {
                String nome = midia.optString("shortName", "").trim();
                if (nome.isEmpty()) nome = midia.optString("name", "").trim();
                adicionar(destino, nome);
            }
        }
    }

    private static boolean brasileiro(JSONObject item) {
        String regiao = item.optString("region", "");
        String idioma = item.optString("lang", "");
        return "br".equalsIgnoreCase(regiao) || idioma.toLowerCase(Locale.ROOT).startsWith("pt");
    }

    private static void adicionar(List<String> destino, String nome) {
        if (nome == null) return;
        String limpo = nome.trim();
        if (limpo.isEmpty() || generico(limpo)) return;
        destino.add(limpo);
    }

    private static boolean generico(String nome) {
        return "tv".equalsIgnoreCase(nome);
    }

    private static List<String> distintos(List<String> nomes) {
        List<String> saida = new ArrayList<>();
        for (int i = 0; i < nomes.size(); i++) {
            String nome = nomes.get(i);
            if (!saida.contains(nome)) saida.add(nome);
        }
        return saida;
    }
}
