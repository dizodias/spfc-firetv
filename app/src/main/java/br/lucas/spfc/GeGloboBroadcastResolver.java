package br.lucas.spfc;

import org.json.JSONObject;

import java.io.File;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Agenda do São Paulo no ge.globo. Cache em disco por 6 horas, timeout curto,
 * falha em silêncio. A allowlist corta Cartola e Ge TV.
 */
final class GeGloboBroadcastResolver implements BroadcastResolver {
    static final String URL =
            "https://ge.globo.com/futebol/times/sao-paulo/agenda-de-jogos-do-sao-paulo/";
    static final long TTL_MS = 6L * 60L * 60L * 1000L;
    private static final int ESPERA_MS = 5_000;
    private static final String ARQUIVO = "ge-agenda.json";

    interface FonteHtml {
        String html();
    }

    private final File arquivo;
    private final FonteHtml fonte;
    private List<GeGloboParser.Jogo> carregados;

    GeGloboBroadcastResolver(File pasta) {
        this(pasta, () -> {
            try {
                return Rede.get(URL, ESPERA_MS, Rede.UA_NAVEGADOR);
            } catch (Exception e) {
                return "";
            }
        });
    }

    GeGloboBroadcastResolver(FonteHtml fonte) {
        this(null, fonte);
    }

    GeGloboBroadcastResolver(File pasta, FonteHtml fonte) {
        this.arquivo = pasta == null ? null : new File(pasta, ARQUIVO);
        this.fonte = fonte;
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
                if (direto || invertido) {
                    return CanaisPermitidos.filtrar(jogo.canais);
                }
            }
        } catch (RuntimeException ignorado) {
            return new ArrayList<>();
        }
        return new ArrayList<>();
    }

    private List<GeGloboParser.Jogo> agenda() {
        if (carregados != null) return carregados;
        String html = lerCache();
        if (html == null) {
            try {
                html = fonte.html();
            } catch (Exception e) {
                html = "";
            }
            if (html != null && !html.isEmpty()) gravarCache(html);
        }
        carregados = GeGloboParser.jogos(html == null ? "" : html);
        return carregados;
    }

    private String lerCache() {
        if (arquivo == null || !arquivo.exists()) return null;
        if (System.currentTimeMillis() - arquivo.lastModified() >= TTL_MS) return null;
        try {
            JSONObject raiz = new JSONObject(Rede.arquivo(arquivo));
            String html = raiz.optString("html", "");
            return html.isEmpty() ? null : html;
        } catch (Exception e) {
            return null;
        }
    }

    private void gravarCache(String html) {
        if (arquivo == null) return;
        try {
            JSONObject raiz = new JSONObject();
            raiz.put("obtidoEm", System.currentTimeMillis());
            raiz.put("html", html);
            Rede.gravar(arquivo, raiz.toString());
        } catch (Exception ignorado) {
            // Cache é opcional.
        }
    }
}
