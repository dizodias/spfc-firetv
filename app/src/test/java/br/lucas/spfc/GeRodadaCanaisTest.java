package br.lucas.spfc;

import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GeRodadaCanaisTest {
    private static final Instant DIA_29 = Instant.parse("2026-10-29T23:00:00Z");
    private static final Instant DIA_28 = Instant.parse("2026-10-28T22:30:00Z");
    private static final Instant DIA_30 = Instant.parse("2026-10-30T15:00:00Z");

    @Test
    public void parserLeBrasileiraoEIgnoraSerieB() {
        List<GeGloboParser.Jogo> jogos = GeRodadaParser.jogos(fixture());
        assertEquals(5, jogos.size());
        assertEquals("Athletico-PR", jogos.get(0).casaPopular);
        assertEquals(List.of("Globo"), jogos.get(0).canais);
        assertEquals(LocalDate.of(2026, 10, 29), jogos.get(1).data);
        assertEquals(List.of("Premiere", "Cartola"), jogos.get(1).canais);
        for (int i = 0; i < jogos.size(); i++) {
            assertFalse(jogos.get(i).canais.contains("Band"));
        }
    }

    @Test
    public void jsonInvalidoNaoQuebra() {
        assertTrue(GeRodadaParser.jogos("sem json").isEmpty());
        assertTrue(GeRodadaParser.jogos("{\"errors\":[{\"message\":\"falhou\"}]}").isEmpty());
        assertTrue(GeRodadaParser.jogos(null).isEmpty());
    }

    @Test
    public void apelidosCasamMandanteEVisitante() throws Exception {
        assertTrue(Nomes.iguais("Athletico-PR", "Athletico Paranaense"));
        assertTrue(Nomes.iguais("Atlético-MG", "Atlético Mineiro"));
        assertTrue(Nomes.iguais("Vasco da Gama", "Vasco"));
        assertTrue(Nomes.iguais("RB Bragantino", "Bragantino"));
        assertTrue(Nomes.iguais("Red Bull Bragantino", "Bragantino"));

        GeRodadaBroadcastResolver resolver = new GeRodadaBroadcastResolver((url, corpo) -> fixture());
        assertEquals(List.of("Premiere"),
                resolver.canais(new JSONObject(), DIA_29, "Vasco da Gama", "Athletico Paranaense"));
        assertEquals(List.of("Premiere"),
                resolver.canais(new JSONObject(), DIA_29, "Atlético Mineiro", "Cruzeiro"));
        assertEquals(List.of("Prime Vídeo"),
                resolver.canais(new JSONObject(), DIA_29, "Vasco", "Flamengo"));
        assertEquals(List.of("sportv"),
                resolver.canais(new JSONObject(), DIA_28, "RB Bragantino", "Chapecoense"));
    }

    @Test
    public void dataDesempataOMesmoPar() throws Exception {
        GeRodadaBroadcastResolver resolver = new GeRodadaBroadcastResolver((url, corpo) -> fixture());
        assertEquals(List.of("Globo"),
                resolver.canais(new JSONObject(), DIA_28, "Athletico Paranaense", "Vasco"));
        assertEquals(List.of("Premiere"),
                resolver.canais(new JSONObject(), DIA_29, "Athletico Paranaense", "Vasco"));
        assertTrue(resolver.canais(new JSONObject(), DIA_30, "Athletico Paranaense", "Vasco").isEmpty());
    }

    @Test
    public void umaConsultaCobreAJanelaDaRodada() throws Exception {
        String pedido = GeRodadaBroadcastResolver.pedido(LocalDate.of(2026, 10, 28));
        assertEquals(8, contar(pedido, "championshipsAgenda"));
        assertEquals(24, contar(pedido, "liveWatchSources"));
        assertTrue(pedido.contains("2026-10-25"));
        assertTrue(pedido.contains("2026-11-01"));
        assertFalse(pedido.contains("2026-10-24"));
        assertFalse(pedido.contains("2026-11-02"));
        assertTrue(pedido.contains("\"query\""));
    }

    @Test
    public void umaRequisicaoServeARodadaInteira() throws Exception {
        int[] chamadas = {0};
        GeRodadaBroadcastResolver resolver = new GeRodadaBroadcastResolver((url, corpo) -> {
            chamadas[0]++;
            assertEquals(GeRodadaBroadcastResolver.URL, url);
            assertEquals(8, contar(corpo, "championshipsAgenda"));
            return fixture();
        });
        resolver.canais(new JSONObject(), DIA_29, "Atlético-MG", "Cruzeiro");
        resolver.canais(new JSONObject(), DIA_28, "Bragantino", "Chapecoense");
        assertEquals(1, chamadas[0]);
    }

    @Test
    public void falhaDoGeCaiNoCanaisJson() throws Exception {
        GeRodadaBroadcastResolver ge = new GeRodadaBroadcastResolver((url, corpo) -> {
            throw new IOException("sem rede");
        });
        assertTrue(ge.canais(new JSONObject(), DIA_29, "Vasco da Gama", "Flamengo").isEmpty());
        JsonCanaisResolver manual = new JsonCanaisResolver(() -> """
                {"jogos":[{"data":"2026-10-29","casa":"Vasco","fora":"Flamengo","canais":["Globo"]}]}
                """);
        List<String> canais = new ChainBroadcastResolver(ge, manual)
                .canais(new JSONObject(), DIA_29, "Vasco da Gama", "Flamengo");
        assertEquals(List.of("Globo"), canais);
    }

    private static int contar(String texto, String trecho) {
        int total = 0;
        int de = 0;
        while (true) {
            int em = texto.indexOf(trecho, de);
            if (em < 0) return total;
            total++;
            de = em + trecho.length();
        }
    }

    private static String fixture() {
        return """
                {"data":{"d0":[
                  {"championship":{"name":"Campeonato Brasileiro Série B"},"future":[{"match":{
                    "startDate":"2026-10-29",
                    "firstContestant":{"popularName":"Athletico-PR","name":"Club Athletico Paranaense"},
                    "secondContestant":{"popularName":"Vasco","name":"Vasco da Gama"},
                    "liveWatchSources":[{"name":"Band"}]
                  }}]},
                  {"championship":{"name":"Campeonato Brasileiro"},"now":[],"past":[],"future":[
                    {"match":{
                      "startDate":"2026-10-28",
                      "firstContestant":{"popularName":"Athletico-PR","name":"Club Athletico Paranaense"},
                      "secondContestant":{"popularName":"Vasco","name":"Vasco da Gama"},
                      "liveWatchSources":[{"name":"Globo"}]
                    }},
                    {"match":{
                      "startDate":"2026-10-29",
                      "firstContestant":{"popularName":"Athletico-PR","name":"Club Athletico Paranaense"},
                      "secondContestant":{"popularName":"Vasco","name":"Vasco da Gama"},
                      "liveWatchSources":[{"name":"Premiere"},{"name":"Cartola"}]
                    }},
                    {"match":{
                      "startDate":"2026-10-29",
                      "firstContestant":{"popularName":"Atlético-MG","name":"Clube Atlético Mineiro"},
                      "secondContestant":{"popularName":"Cruzeiro","name":"Cruzeiro Esporte Clube"},
                      "liveWatchSources":[{"name":"Premiere"}]
                    }},
                    {"match":{
                      "startDate":"2026-10-29",
                      "firstContestant":{"popularName":"Vasco","name":"Vasco da Gama"},
                      "secondContestant":{"popularName":"Flamengo","name":"Clube de Regatas do Flamengo"},
                      "liveWatchSources":[{"name":"Prime Vídeo"}]
                    }},
                    {"match":{
                      "startDate":"2026-10-28",
                      "firstContestant":{"popularName":"Bragantino","name":"Red Bull Bragantino"},
                      "secondContestant":{"popularName":"Chapecoense","name":"Associação Chapecoense de Futebol"},
                      "liveWatchSources":[{"name":"sportv"},{"name":"Ge TV"}]
                    }}
                  ]}
                ]}}
                """;
    }
}
