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
                "Arena Fonte Nova", Collections.emptyList(), false);
        StandingRow linha = new StandingRow(9, "São Paulo", 32, 12, 7, 13, 37, "+1", 43, true);
        long obtido = 1_000L;
        repo.gravar(new Snapshot(List.of(jogo), List.of(linha), obtido));

        Snapshot lido = repo.ler();
        assertEquals("Bahia", lido.matches.get(0).opponent);
        assertEquals("Arena Fonte Nova", lido.matches.get(0).venue);
        assertEquals(9, lido.standings.get(0).rank);
        assertTrue(lido.standings.get(0).isSpfc);
        assertEquals(obtido, lido.fetchedAtMillis);
        assertTrue(repo.fresco(lido, obtido + 44L * 60L * 1000L));
        assertFalse(repo.fresco(lido, obtido + 46L * 60L * 1000L));
    }

    @Test
    public void arquivoAusenteNaoQuebra() throws Exception {
        SpfcRepository repo = new SpfcRepository(Files.createTempDirectory("spfc-vazio").toFile());
        assertNull(repo.ler());
    }
}
