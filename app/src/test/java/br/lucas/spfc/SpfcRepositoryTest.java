package br.lucas.spfc;

import org.junit.Test;

import java.io.File;
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

    @Test
    public void cacheDaRodadaRedondo() throws Exception {
        SpfcRepository repo = new SpfcRepository(Files.createTempDirectory("spfc-rodada").toFile());
        JogoRodada jogo = new JogoRodada(
                "99", 33, Instant.parse("2026-10-29T00:30:00Z"),
                "Arena Fonte Nova",
                "Bahia", "São Paulo", "9967", "2026",
                "https://a.espncdn.com/bah.png", "https://a.espncdn.com/sp.png",
                1, 0,
                JogoRodada.Status.AO_VIVO, "67'",
                List.of("Premiere"), true);
        repo.gravar(new Snapshot(List.of(), List.of(new StandingRow(
                9, "São Paulo", 32, 12, 7, 13, 37, "+1", 43, true)), List.of(jogo), 50L));
        Snapshot lido = repo.ler();
        assertEquals(1, lido.rodada.size());
        JogoRodada volta = lido.rodada.get(0);
        assertEquals("99", volta.id);
        assertEquals(Integer.valueOf(33), volta.rodada);
        assertEquals("Bahia", volta.casaNome);
        assertEquals("São Paulo", volta.foraNome);
        assertEquals("9967", volta.casaId);
        assertEquals("2026", volta.foraId);
        assertEquals("https://a.espncdn.com/bah.png", volta.casaLogo);
        assertEquals(Integer.valueOf(1), volta.casaGols);
        assertEquals(Integer.valueOf(0), volta.foraGols);
        assertEquals("67'", volta.minuto);
        assertEquals(JogoRodada.Status.AO_VIVO, volta.status);
        assertEquals(List.of("Premiere"), volta.channels);
        assertTrue(volta.saoPaulo);
        assertTrue(lido.temAoVivo());
        assertTrue(lido.rodadaOk);
    }

    @Test
    public void cacheAntigoSemRodadaContinuaLegivel() throws Exception {
        File pasta = Files.createTempDirectory("spfc-sem-rodada").toFile();
        Rede.gravar(new File(pasta, "spfc-cache.json"),
                "{\"fetchedAt\":5,\"matches\":[],\"standings\":[]}");
        Snapshot lido = new SpfcRepository(pasta).ler();
        assertEquals(5L, lido.fetchedAtMillis);
        assertTrue(lido.rodada.isEmpty());
        assertFalse(lido.rodadaOk);
        assertFalse(lido.temAoVivo());
    }

    @Test
    public void rodadaVaziaConfirmadaNaoEFalha() throws Exception {
        SpfcRepository repo = new SpfcRepository(Files.createTempDirectory("spfc-flag").toFile());
        StandingRow linha = new StandingRow(1, "São Paulo", 1, 1, 0, 0, 1, "+1", 3, true);
        repo.gravar(new Snapshot(List.of(), List.of(linha), List.of(), 9L, true));
        Snapshot vazia = repo.ler();
        assertTrue(vazia.rodada.isEmpty());
        assertTrue(vazia.rodadaOk);

        repo.gravar(new Snapshot(List.of(), List.of(linha), List.of(), 9L, false));
        assertFalse(repo.ler().rodadaOk);
    }

    @Test
    public void cacheAntigoComRodadaSegueValido() throws Exception {
        File pasta = Files.createTempDirectory("spfc-rodada-velha").toFile();
        Rede.gravar(new File(pasta, "spfc-cache.json"),
                "{\"fetchedAt\":5,\"matches\":[],\"standings\":[],\"rodada\":["
                        + "{\"id\":\"1\",\"kickoff\":\"2026-10-29T00:30:00Z\","
                        + "\"home\":\"Bahia\",\"away\":\"São Paulo\",\"status\":\"agendado\"}]}");
        Snapshot lido = new SpfcRepository(pasta).ler();
        assertEquals(1, lido.rodada.size());
        assertTrue(lido.rodadaOk);
    }
}
