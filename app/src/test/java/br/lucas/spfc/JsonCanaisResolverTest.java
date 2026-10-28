package br.lucas.spfc;

import org.json.JSONObject;
import org.junit.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class JsonCanaisResolverTest {
    @Test
    public void overrideManualPorDataETimes() {
        JsonCanaisResolver resolver = new JsonCanaisResolver(() -> """
                {"jogos":[{"data":"2026-10-28","casa":"Bahia","fora":"São Paulo","canais":["Premiere","Globo"]}]}
                """);
        List<String> canais = resolver.canais(
                new JSONObject(), Instant.parse("2026-10-29T00:30:00Z"), "Bahia", "São Paulo");
        assertEquals(List.of("Premiere", "Globo"), canais);
    }

    @Test
    public void jsonQuebradoNaoQuebraACadeia() {
        JsonCanaisResolver resolver = new JsonCanaisResolver(() -> "isso não é json");
        assertTrue(resolver.canais(
                new JSONObject(), Instant.parse("2026-10-29T00:30:00Z"), "Bahia", "São Paulo").isEmpty());
    }
}
