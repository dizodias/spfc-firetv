package br.lucas.spfc;

import org.junit.Test;

import java.nio.file.Files;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SpfcRepositoryTest {
    @Test
    public void cacheRedondoEExpiraEm45Minutos() throws Exception {
        SpfcRepository repo = new SpfcRepository(Files.createTempDirectory("spfc").toFile());
        Match jogo = new Match(
                "Bahia", "away", Instant.parse("2026-10-29T00:30:00Z"),
                "Arena Fonte Nova", List.of("Premiere"), false,
                "Brasileirão", "bra.1", null, null, false);
        StandingRow linha = new StandingRow(9, "São Paulo", 32, 12, 7, 13, 37, "+1", 43, true);
        long obtido = 1_000L;
        repo.gravar(new Snapshot(List.of(jogo), List.of(linha), obtido));

        Snapshot lido = repo.ler();
        assertEquals("Bahia", lido.matches.get(0).opponent);
        assertEquals("Arena Fonte Nova", lido.matches.get(0).venue);
        assertEquals("Brasileirão", lido.matches.get(0).leagueName);
        assertEquals("bra.1", lido.matches.get(0).leagueSlug);
        assertEquals(List.of("Premiere"), lido.matches.get(0).channels);
        assertFalse(lido.matches.get(0).finished);
        assertEquals(9, lido.standings.get(0).rank);
        assertTrue(lido.standings.get(0).isSpfc);
        assertEquals(obtido, lido.fetchedAtMillis);
        assertTrue(repo.fresco(lido, obtido + 44L * 60L * 1000L));
        assertFalse(repo.fresco(lido, obtido + 46L * 60L * 1000L));
        assertEquals(1, lido.proximos().size());
        assertTrue(lido.resultados().isEmpty());
    }

    @Test
    public void competicaoListaDoMaisRecente() {
        Match abril = new Match(
                "Cruzeiro", "away", Instant.parse("2026-04-21T22:00:00Z"),
                "", Collections.emptyList(), false,
                "Copa do Brasil", "bra.copa_do_brazil", 0, 1, true);
        Match maio = new Match(
                "Cruzeiro", "home", Instant.parse("2026-05-13T22:00:00Z"),
                "", Collections.emptyList(), false,
                "Copa do Brasil", "bra.copa_do_brazil", 1, 1, true);
        Match futuro = new Match(
                "Bahia", "away", Instant.parse("2026-10-29T00:30:00Z"),
                "", Collections.emptyList(), false,
                "Brasileirão", "bra.1", null, null, false);
        Snapshot snapshot = new Snapshot(List.of(abril, futuro, maio), List.of(), 1L);
        List<Match> copa = snapshot.daCompeticao("bra.copa_do_brazil");
        assertEquals(2, copa.size());
        assertEquals(maio.kickoffInstant, copa.get(0).kickoffInstant);
        assertEquals(abril.kickoffInstant, copa.get(1).kickoffInstant);
    }

    @Test
    public void arquivoAusenteNaoQuebra() throws Exception {
        SpfcRepository repo = new SpfcRepository(Files.createTempDirectory("spfc-vazio").toFile());
        assertNull(repo.ler());
    }

    @Test
    public void cacheAntigoSemLigaContinuaLegivel() throws Exception {
        SpfcRepository repo = new SpfcRepository(Files.createTempDirectory("spfc-velho").toFile());
        Match jogo = new Match(
                "Bahia", "away", Instant.parse("2026-10-29T00:30:00Z"),
                "Arena Fonte Nova", Collections.emptyList(), false,
                "", "", null, null, false);
        repo.gravar(new Snapshot(List.of(jogo), List.of(), 1_000L));
        Snapshot lido = repo.ler();
        assertEquals("Bahia", lido.matches.get(0).opponent);
        assertEquals("", lido.matches.get(0).leagueSlug);
        assertFalse(lido.matches.get(0).finished);
    }
}
