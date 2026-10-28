package br.lucas.spfc;

import org.json.JSONObject;
import org.junit.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class CompeticaoTest {
    @Test
    public void traduzNomesInglesEPortugues() {
        assertEquals("Brasileirão", Competicao.traduzir("Brazilian Serie A"));
        assertEquals("Brasileirão", Competicao.traduzir("Campeonato Brasileiro"));
        assertEquals("Copa do Brasil", Competicao.traduzir("Copa do Brasil"));
        assertEquals("Sul-Americana", Competicao.traduzir("CONMEBOL Sudamericana"));
        assertEquals("Libertadores", Competicao.traduzir("CONMEBOL Libertadores"));
        assertEquals("Paulistão", Competicao.traduzir("Brazilian Campeonato Paulista"));
        assertEquals("Paulistão", Competicao.traduzir("Campeonato Paulista"));
        assertEquals("Mundial de Clubes", Competicao.traduzir("Mundial de Clubes"));
    }

    @Test
    public void leNomeESlugDoEvento() throws Exception {
        JSONObject evento = new JSONObject("""
                {"league":{"name":"Brazilian Serie A","slug":"bra.1"}}
                """);
        assertEquals("Brasileirão", Competicao.nome(evento));
        assertEquals("bra.1", Competicao.slug(evento));
    }

    @Test
    public void ordenaAbasDaTemporada() {
        Match brasileirao = jogo("Brasileirão", "bra.1");
        Match sul = jogo("Sul-Americana", "conmebol.sudamericana");
        Match copa = jogo("Copa do Brasil", "bra.copa_do_brazil");
        Match paulista = jogo("Paulistão", "bra.camp.paulista");
        List<Competicao.Aba> abas = Competicao.abas(List.of(sul, paulista, copa, brasileirao));
        assertEquals("bra.1", abas.get(0).slug);
        assertEquals("bra.copa_do_brazil", abas.get(1).slug);
        assertEquals("conmebol.sudamericana", abas.get(2).slug);
        assertEquals("bra.camp.paulista", abas.get(3).slug);
    }

    private static Match jogo(String liga, String slug) {
        return new Match(
                "Bahia", "away", Instant.parse("2026-10-29T00:30:00Z"),
                "Arena Fonte Nova", Collections.emptyList(), false,
                liga, slug, null, null, false);
    }
}
