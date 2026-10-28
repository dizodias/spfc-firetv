package br.lucas.spfc;

import org.json.JSONObject;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class GeGloboBroadcastResolverTest {
    @Test
    public void allowlistMatchEFusoDeBrasilia() throws Exception {
        GeGloboBroadcastResolver resolver = new GeGloboBroadcastResolver(this::fixture);
        Instant kickoff = Instant.parse("2026-10-29T00:30:00Z");
        List<String> canais = resolver.canais(new JSONObject(), kickoff, "Bahia", "São Paulo");
        assertEquals(List.of("Premiere", "Globo", "globoplay"), canais);
    }

    @Test
    public void dataUtcDoDiaSeguinteNaoCasaSemFuso() throws Exception {
        GeGloboBroadcastResolver resolver = new GeGloboBroadcastResolver(this::fixture);
        Instant meioDiaUtc = Instant.parse("2026-10-29T12:00:00Z");
        assertTrue(resolver.canais(new JSONObject(), meioDiaUtc, "Bahia", "São Paulo").isEmpty());
    }

    @Test
    public void htmlLixoCaiNoFallback() {
        GeGloboBroadcastResolver resolver = new GeGloboBroadcastResolver(() -> "<html>sem agenda</html>");
        Instant kickoff = Instant.parse("2026-10-29T00:30:00Z");
        assertTrue(resolver.canais(new JSONObject(), kickoff, "Bahia", "São Paulo").isEmpty());
    }

    @Test
    public void cadeiaUsaGeQuandoEspnVemVazia() throws Exception {
        BroadcastResolver espnVazio = (c, i, casa, fora) -> List.of();
        GeGloboBroadcastResolver ge = new GeGloboBroadcastResolver(this::fixture);
        ChainBroadcastResolver cadeia = new ChainBroadcastResolver(espnVazio, ge);
        List<String> canais = cadeia.canais(
                new JSONObject(), Instant.parse("2026-10-29T00:30:00Z"), "Bahia", "São Paulo");
        assertEquals(List.of("Premiere", "Globo", "globoplay"), canais);
    }

    private String fixture() {
        try (InputStream entrada = getClass().getResourceAsStream("/ge-agenda-sao-paulo.html")) {
            ByteArrayOutputStream saida = new ByteArrayOutputStream();
            byte[] bloco = new byte[4096];
            int lido;
            while ((lido = entrada.read(bloco)) >= 0) saida.write(bloco, 0, lido);
            return new String(saida.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
