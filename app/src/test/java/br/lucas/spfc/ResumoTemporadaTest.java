package br.lucas.spfc;

import org.junit.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ResumoTemporadaTest {
    @Test
    public void totaisAproveitamentoEForma() {
        List<Match> jogos = List.of(
                encerrado("Corinthians", "2026-01-20T19:00:00Z", 2, 0, "Paulistão", "bra.camp.paulista"),
                encerrado("Palmeiras", "2026-03-12T00:00:00Z", 1, 1, "Paulistão", "bra.camp.paulista"),
                encerrado("Flamengo", "2026-05-01T00:00:00Z", 0, 3, "Brasileirão", "bra.1"),
                encerrado("Mirassol", "2026-08-10T19:00:00Z", 4, 0, "Brasileirão", "bra.1"),
                encerrado("Bahia", "2026-09-02T00:00:00Z", 1, 0, "Copa do Brasil", "bra.copa_do_brazil"),
                encerrado("Millonarios", "2026-07-15T00:00:00Z", 2, 2, "Sul-Americana", "conmebol.sudamericana"),
                proximo("Remo", "2026-11-28T20:00:00Z", "Brasileirão", "bra.1"));

        ResumoTemporada resumo = ResumoTemporada.de(jogos);
        assertEquals(6, resumo.jogos);
        assertEquals(3, resumo.vitorias);
        assertEquals(2, resumo.empates);
        assertEquals(1, resumo.derrotas);
        assertEquals(10, resumo.golsPro);
        assertEquals(6, resumo.golsContra);
        assertEquals(4, resumo.saldo);
        assertEquals("+4", resumo.saldoTexto());
        // (9 + 2) / 18 = 61,11 → 61%
        assertEquals(61, resumo.aproveitamento);
        assertEquals("61%", resumo.aproveitamentoTexto());

        assertEquals(List.of(
                Match.Resultado.EMPATE,
                Match.Resultado.DERROTA,
                Match.Resultado.EMPATE,
                Match.Resultado.VITORIA,
                Match.Resultado.VITORIA), resumo.ultimos5);
        assertEquals("Mirassol", resumo.maiorVitoria.opponent);
        assertEquals(Integer.valueOf(4), resumo.maiorVitoria.goalsFor);
        assertEquals("Flamengo", resumo.piorDerrota.opponent);
        assertEquals(Integer.valueOf(3), resumo.piorDerrota.goalsAgainst);
    }

    @Test
    public void ultimosCincoFicamComOMaisRecenteADireita() {
        List<Match> jogos = List.of(
                encerrado("A", "2026-01-01T00:00:00Z", 1, 0, "Brasileirão", "bra.1"),
                encerrado("B", "2026-01-02T00:00:00Z", 0, 0, "Brasileirão", "bra.1"),
                encerrado("C", "2026-01-03T00:00:00Z", 0, 1, "Brasileirão", "bra.1"),
                encerrado("D", "2026-01-04T00:00:00Z", 2, 1, "Brasileirão", "bra.1"),
                encerrado("E", "2026-01-05T00:00:00Z", 1, 1, "Brasileirão", "bra.1"),
                encerrado("F", "2026-01-06T00:00:00Z", 0, 2, "Brasileirão", "bra.1"));
        List<Match.Resultado> forma = ResumoTemporada.de(jogos).ultimos5;
        assertEquals(5, forma.size());
        assertEquals(Match.Resultado.EMPATE, forma.get(0));
        assertEquals(Match.Resultado.DERROTA, forma.get(1));
        assertEquals(Match.Resultado.VITORIA, forma.get(2));
        assertEquals(Match.Resultado.EMPATE, forma.get(3));
        assertEquals(Match.Resultado.DERROTA, forma.get(4));
    }

    @Test
    public void maiorVitoriaPeloSaldoDepoisPelosGols() {
        List<Match> jogos = List.of(
                encerrado("Santos", "2026-02-01T00:00:00Z", 2, 1, "Brasileirão", "bra.1"),
                encerrado("Vasco", "2026-03-01T00:00:00Z", 2, 0, "Brasileirão", "bra.1"),
                encerrado("Remo", "2026-04-01T00:00:00Z", 2, 0, "Brasileirão", "bra.1"));
        Match maior = ResumoTemporada.de(jogos).maiorVitoria;
        assertEquals("Remo", maior.opponent);
    }

    @Test
    public void piorDerrotaPeloSaldoDepoisPelosGolsSofridos() {
        List<Match> jogos = List.of(
                encerrado("Flamengo", "2026-02-01T00:00:00Z", 1, 2, "Brasileirão", "bra.1"),
                encerrado("Palmeiras", "2026-03-01T00:00:00Z", 0, 2, "Brasileirão", "bra.1"),
                encerrado("Botafogo", "2026-04-01T00:00:00Z", 0, 2, "Brasileirão", "bra.1"));
        Match pior = ResumoTemporada.de(jogos).piorDerrota;
        assertEquals("Botafogo", pior.opponent);
    }

    @Test
    public void soACompeticaoAtualEIgnoraJogoEmAberto() {
        List<Match> jogos = List.of(
                encerrado("Corinthians", "2026-01-20T19:00:00Z", 2, 0, "Paulistão", "bra.camp.paulista"),
                encerrado("Flamengo", "2026-05-01T00:00:00Z", 0, 1, "Brasileirão", "bra.1"),
                proximo("Mirassol", "2026-11-01T00:00:00Z", "Paulistão", "bra.camp.paulista"));
        ResumoTemporada resumo = ResumoTemporada.de(List.of(jogos.get(0), jogos.get(2)));
        assertEquals(1, resumo.jogos);
        assertEquals(1, resumo.vitorias);
        assertEquals(100, resumo.aproveitamento);
        assertEquals(1, resumo.ligas.size());
        assertEquals("Paulistão", resumo.ligas.get(0).nome);
        assertNull(resumo.piorDerrota);
    }

    @Test
    public void dividePorCompeticaoNaOrdemDasAbas() {
        List<Match> jogos = List.of(
                encerrado("Corinthians", "2026-01-20T19:00:00Z", 1, 0, "Paulistão", "bra.camp.paulista"),
                encerrado("Cruzeiro", "2026-04-10T00:00:00Z", 0, 1, "Copa do Brasil", "bra.copa_do_brazil"),
                encerrado("Millonarios", "2026-07-15T00:00:00Z", 1, 1, "Sul-Americana", "conmebol.sudamericana"),
                encerrado("Bahia", "2026-09-02T00:00:00Z", 2, 1, "Brasileirão", "bra.1"));
        List<ResumoTemporada.LinhaLiga> ligas = ResumoTemporada.de(jogos).ligas;
        assertEquals(4, ligas.size());
        assertEquals("bra.1", ligas.get(0).slug);
        assertEquals(1, ligas.get(0).vitorias);
        assertEquals("bra.copa_do_brazil", ligas.get(1).slug);
        assertEquals(1, ligas.get(1).derrotas);
        assertEquals("conmebol.sudamericana", ligas.get(2).slug);
        assertEquals(1, ligas.get(2).empates);
        assertEquals("bra.camp.paulista", ligas.get(3).slug);
        assertEquals(1, ligas.get(3).jogos);
    }

    @Test
    public void listaVaziaFicaEmZero() {
        ResumoTemporada resumo = ResumoTemporada.de(Collections.emptyList());
        assertEquals(0, resumo.jogos);
        assertEquals(0, resumo.aproveitamento);
        assertEquals("0", resumo.saldoTexto());
        assertTrue(resumo.ultimos5.isEmpty());
        assertNull(resumo.maiorVitoria);
        assertNull(resumo.piorDerrota);
        assertTrue(resumo.ligas.isEmpty());
    }

    private static Match encerrado(String adversario, String kickoff, int gp, int gc,
            String liga, String slug) {
        return new Match(adversario, "home", Instant.parse(kickoff), "",
                Collections.emptyList(), false, liga, slug, gp, gc, true);
    }

    private static Match proximo(String adversario, String kickoff, String liga, String slug) {
        return new Match(adversario, "home", Instant.parse(kickoff), "",
                Collections.emptyList(), false, liga, slug, null, null, false);
    }
}
