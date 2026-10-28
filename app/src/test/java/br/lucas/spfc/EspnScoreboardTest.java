package br.lucas.spfc;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class EspnScoreboardTest {
    private static final Instant AGORA = Instant.parse("2026-10-28T18:00:00Z");

    @Test
    public void leAgendadoAoVivoEEncerrado() throws Exception {
        List<JogoRodada> jogos = EspnScoreboard.jogos(eventos("""
                {"events":[
                  {"id":"agendado","date":"2026-10-29T00:30Z","competitions":[{
                    "date":"2026-10-29T00:30Z",
                    "venue":{"fullName":"Arena Fonte Nova","address":{"city":"Salvador"}},
                    "status":{"type":{"state":"pre","completed":false}},
                    "competitors":[
                      {"homeAway":"home","score":"0","team":{
                        "id":"9967","shortDisplayName":"Bahia",
                        "logo":"https://a.espncdn.com/i/teamlogos/soccer/500/9967.png"}},
                      {"homeAway":"away","score":"0","team":{
                        "id":"2026","shortDisplayName":"São Paulo",
                        "logo":"https://a.espncdn.com/i/teamlogos/soccer/500/2026.png"}}
                    ],
                    "geoBroadcasts":[
                      {"region":"br","lang":"pt","media":{"shortName":"Premiere"}}
                    ]
                  }]},
                  {"id":"aovivo","date":"2026-10-28T17:00Z","competitions":[{
                    "date":"2026-10-28T17:00Z",
                    "venue":{"fullName":"Allianz Parque"},
                    "status":{"displayClock":"67'","period":2,"type":{"state":"in","completed":false}},
                    "competitors":[
                      {"homeAway":"home","score":"2","team":{"id":"2029","shortDisplayName":"Palmeiras"}},
                      {"homeAway":"away","score":"1","team":{"id":"2674","shortDisplayName":"Santos"}}
                    ],
                    "broadcasts":[],"geoBroadcasts":[]
                  }]},
                  {"id":"encerrado","date":"2026-10-26T22:00Z","competitions":[{
                    "date":"2026-10-26T22:00Z",
                    "venue":{"fullName":"Maracanã"},
                    "status":{"displayClock":"90'+4'","period":2,
                      "type":{"state":"post","completed":true,"name":"STATUS_FULL_TIME"}},
                    "competitors":[
                      {"homeAway":"home","score":{"value":3.0,"displayValue":"3"},
                        "team":{"id":"819","shortDisplayName":"Flamengo"}},
                      {"homeAway":"away","score":{"value":0.0,"displayValue":"0"},
                        "team":{"id":"1234","shortDisplayName":"Vasco"}}
                    ],
                    "geoBroadcasts":[
                      {"region":"br","lang":"pt","media":{"shortName":"Globo"}}
                    ]
                  }]}
                ]}
                """), AGORA, new EspnBroadcastResolver());

        assertEquals(3, jogos.size());

        JogoRodada agendado = porId(jogos, "agendado");
        assertEquals(JogoRodada.Status.AGENDADO, agendado.status);
        assertEquals("Bahia", agendado.casaNome);
        assertEquals("São Paulo", agendado.foraNome);
        assertEquals("9967", agendado.casaId);
        assertEquals("2026", agendado.foraId);
        assertEquals("https://a.espncdn.com/i/teamlogos/soccer/500/9967.png", agendado.casaLogo);
        assertEquals("https://a.espncdn.com/i/teamlogos/soccer/500/2026.png", agendado.foraLogo);
        assertEquals("Arena Fonte Nova, Salvador", agendado.venue);
        assertEquals(List.of("Premiere"), agendado.channels);
        assertNull(agendado.casaGols);
        assertNull(agendado.foraGols);
        assertEquals("", agendado.minuto);
        assertTrue(agendado.saoPaulo);
        assertEquals(Instant.parse("2026-10-29T00:30:00Z"), agendado.kickoff);

        JogoRodada aoVivo = porId(jogos, "aovivo");
        assertEquals(JogoRodada.Status.AO_VIVO, aoVivo.status);
        assertEquals(Integer.valueOf(2), aoVivo.casaGols);
        assertEquals(Integer.valueOf(1), aoVivo.foraGols);
        assertEquals("67'", aoVivo.minuto);
        assertEquals("Palmeiras", aoVivo.casaNome);
        assertEquals("Santos", aoVivo.foraNome);
        assertTrue(aoVivo.channels.isEmpty());
        assertFalse(aoVivo.saoPaulo);

        JogoRodada encerrado = porId(jogos, "encerrado");
        assertEquals(JogoRodada.Status.ENCERRADO, encerrado.status);
        assertEquals(Integer.valueOf(3), encerrado.casaGols);
        assertEquals(Integer.valueOf(0), encerrado.foraGols);
        assertEquals("", encerrado.minuto);
        assertEquals(List.of("Globo"), encerrado.channels);
        assertEquals("Maracanã", encerrado.venue);
    }

    @Test
    public void aoVivoSemRelogioUsaOPeriodo() throws Exception {
        List<JogoRodada> jogos = EspnScoreboard.jogos(eventos("""
                {"events":[{"id":"1","date":"2026-10-28T17:00Z","competitions":[{
                  "status":{"period":1,"type":{"state":"in","completed":false}},
                  "competitors":[
                    {"homeAway":"home","score":"0","team":{"id":"1","shortDisplayName":"Bahia"}},
                    {"homeAway":"away","score":"0","team":{"id":"2","shortDisplayName":"Fortaleza"}}
                  ]
                }]}]}
                """), AGORA, null);
        assertEquals(1, jogos.size());
        assertEquals(JogoRodada.Status.AO_VIVO, jogos.get(0).status);
        assertEquals("1º tempo", jogos.get(0).minuto);
        assertEquals(Integer.valueOf(0), jogos.get(0).casaGols);
    }

    @Test
    public void ordenaSaoPauloPrimeiroDepoisPorHorario() throws Exception {
        List<JogoRodada> jogos = EspnScoreboard.jogos(eventos("""
                {"events":[
                  {"id":"vasco","date":"2026-10-28T22:00Z","competitions":[{
                    "date":"2026-10-28T22:00Z",
                    "status":{"type":{"state":"pre","completed":false}},
                    "competitors":[
                      {"homeAway":"home","team":{"id":"1234","shortDisplayName":"Vasco"}},
                      {"homeAway":"away","team":{"id":"819","shortDisplayName":"Flamengo"}}
                    ]
                  }]},
                  {"id":"sp","date":"2026-10-29T00:30Z","competitions":[{
                    "date":"2026-10-29T00:30Z",
                    "status":{"type":{"state":"pre","completed":false}},
                    "competitors":[
                      {"homeAway":"home","team":{"id":"9967","shortDisplayName":"Bahia"}},
                      {"homeAway":"away","team":{"id":"2026","shortDisplayName":"São Paulo"}}
                    ]
                  }]},
                  {"id":"cruzeiro","date":"2026-10-28T22:00Z","competitions":[{
                    "date":"2026-10-28T22:00Z",
                    "status":{"type":{"state":"pre","completed":false}},
                    "competitors":[
                      {"homeAway":"home","team":{"id":"5","shortDisplayName":"Cruzeiro"}},
                      {"homeAway":"away","team":{"id":"6","shortDisplayName":"Botafogo"}}
                    ]
                  }]}
                ]}
                """), AGORA, null);
        assertEquals(3, jogos.size());
        assertTrue(jogos.get(0).saoPaulo);
        assertEquals("Bahia", jogos.get(0).casaNome);
        assertEquals("São Paulo", jogos.get(0).foraNome);
        assertEquals("Cruzeiro", jogos.get(1).casaNome);
        assertEquals("Vasco", jogos.get(2).casaNome);
        assertFalse(jogos.get(1).saoPaulo);
    }

    @Test
    public void filtraPelaRodadaDoJogoAoVivo() throws Exception {
        List<JogoRodada> jogos = EspnScoreboard.jogos(eventos("""
                {"events":[
                  {"id":"antiga","date":"2026-10-26T22:30Z","week":{"number":32},"competitions":[{
                    "date":"2026-10-26T22:30Z",
                    "status":{"type":{"state":"post","completed":true}},
                    "competitors":[
                      {"homeAway":"home","score":"0","team":{"id":"1","shortDisplayName":"Coritiba"}},
                      {"homeAway":"away","score":"1","team":{"id":"2","shortDisplayName":"Grêmio"}}
                    ]
                  }]},
                  {"id":"vivo","date":"2026-10-28T22:30Z","week":{"number":33},"competitions":[{
                    "date":"2026-10-28T22:30Z",
                    "status":{"displayClock":"12'","period":1,"type":{"state":"in","completed":false}},
                    "competitors":[
                      {"homeAway":"home","score":"0","team":{"id":"3","shortDisplayName":"Palmeiras"}},
                      {"homeAway":"away","score":"0","team":{"id":"4","shortDisplayName":"Santos"}}
                    ]
                  }]},
                  {"id":"sp","date":"2026-10-29T00:30Z","week":{"number":33},"competitions":[{
                    "date":"2026-10-29T00:30Z",
                    "status":{"type":{"state":"pre","completed":false}},
                    "competitors":[
                      {"homeAway":"home","team":{"id":"9967","shortDisplayName":"Bahia"}},
                      {"homeAway":"away","team":{"id":"2026","shortDisplayName":"São Paulo"}}
                    ]
                  }]}
                ]}
                """), Instant.parse("2026-10-28T23:00:00Z"), null);
        assertEquals(2, jogos.size());
        assertTrue(jogos.get(0).saoPaulo);
        assertEquals(JogoRodada.Status.AO_VIVO, jogos.get(1).status);
        assertEquals(Integer.valueOf(33), jogos.get(0).rodada);
        assertEquals("Palmeiras", jogos.get(1).casaNome);
    }

    @Test
    public void semJogoAoVivoFicaNaRodadaDoSaoPaulo() throws Exception {
        List<JogoRodada> jogos = EspnScoreboard.jogos(eventos("""
                {"events":[
                  {"id":"30","date":"2026-10-26T22:00Z","week":30,"competitions":[{
                    "date":"2026-10-26T22:00Z",
                    "status":{"type":{"state":"post","completed":true}},
                    "competitors":[
                      {"homeAway":"home","score":"1","team":{"id":"1","shortDisplayName":"Mirassol"}},
                      {"homeAway":"away","score":"1","team":{"id":"2","shortDisplayName":"Cuiabá"}}
                    ]
                  }]},
                  {"id":"sp","date":"2026-10-29T00:30Z","week":{"number":31},"competitions":[{
                    "date":"2026-10-29T00:30Z",
                    "status":{"type":{"state":"pre","completed":false}},
                    "competitors":[
                      {"homeAway":"home","team":{"id":"9967","shortDisplayName":"Bahia"}},
                      {"homeAway":"away","team":{"id":"2026","shortDisplayName":"São Paulo"}}
                    ]
                  }]}
                ]}
                """), AGORA, null);
        assertEquals(1, jogos.size());
        assertTrue(jogos.get(0).saoPaulo);
        assertEquals(Integer.valueOf(31), jogos.get(0).rodada);
    }

    @Test
    public void textoRodadaFiltraEDescartaQuemNaoTemNumero() throws Exception {
        List<JogoRodada> jogos = EspnScoreboard.jogos(eventos("""
                {"events":[
                  {"id":"30","date":"2026-10-26T22:00Z","competitions":[{
                    "date":"2026-10-26T22:00Z",
                    "notes":[{"headline":"Rodada 30"}],
                    "status":{"type":{"state":"post","completed":true}},
                    "competitors":[
                      {"homeAway":"home","score":"1","team":{"id":"1","shortDisplayName":"Mirassol"}},
                      {"homeAway":"away","score":"0","team":{"id":"2","shortDisplayName":"Vitória"}}
                    ]
                  }]},
                  {"id":"solto","date":"2026-10-28T22:00Z","competitions":[{
                    "date":"2026-10-28T22:00Z",
                    "status":{"type":{"state":"pre","completed":false}},
                    "competitors":[
                      {"homeAway":"home","team":{"id":"3","shortDisplayName":"Cuiabá"}},
                      {"homeAway":"away","team":{"id":"4","shortDisplayName":"Sport"}}
                    ]
                  }]},
                  {"id":"sp","date":"2026-10-29T00:30Z","competitions":[{
                    "date":"2026-10-29T00:30Z",
                    "notes":[{"headline":"Rodada 31"}],
                    "status":{"type":{"state":"pre","completed":false}},
                    "competitors":[
                      {"homeAway":"home","team":{"id":"9967","shortDisplayName":"Bahia"}},
                      {"homeAway":"away","team":{"id":"2026","shortDisplayName":"São Paulo"}}
                    ]
                  }]}
                ]}
                """), AGORA, null);
        assertEquals(1, jogos.size());
        assertEquals("sp", jogos.get(0).id);
        assertEquals(Integer.valueOf(31), jogos.get(0).rodada);
    }

    @Test
    public void intervaloCobreTresDiasAntesEQuatroDepois() {
        assertEquals("20261025-20261101", EspnScoreboard.intervalo(LocalDate.of(2026, 10, 28)));
        assertEquals("20261030-20261106", EspnScoreboard.intervalo(LocalDate.of(2026, 11, 2)));
    }

    @Test
    public void intervaloUnicoNaoVarreOsDias() throws Exception {
        List<String> urls = Collections.synchronizedList(new ArrayList<>());
        List<JogoRodada> jogos = EspnScoreboard.buscar(null, Instant.parse("2026-10-28T15:00:00Z"), url -> {
            urls.add(url);
            return jogoDoSaoPaulo();
        });
        assertEquals(1, urls.size());
        assertTrue(urls.get(0), urls.get(0).contains("dates=20261025-20261101"));
        assertEquals(1, jogos.size());
        assertTrue(jogos.get(0).saoPaulo);
        assertEquals(JogoRodada.Status.AGENDADO, jogos.get(0).status);
        assertNull(jogos.get(0).casaGols);
    }

    @Test
    public void http400BuscaDiaADia() throws Exception {
        List<String> urls = Collections.synchronizedList(new ArrayList<>());
        List<JogoRodada> jogos = EspnScoreboard.buscar(null, Instant.parse("2026-10-28T15:00:00Z"), url -> {
            urls.add(url);
            if (url.contains("dates=20261025-20261101")) throw new IOException("HTTP 400");
            if (url.contains("dates=20261028&")) return jogoDoSaoPaulo();
            return "{\"events\":[]}";
        });
        assertEquals(9, urls.size());
        boolean range = false;
        boolean dia = false;
        for (int i = 0; i < urls.size(); i++) {
            if (urls.get(i).contains("dates=20261025-20261101")) range = true;
            if (urls.get(i).contains("dates=20261028&")) dia = true;
        }
        assertTrue(range);
        assertTrue(dia);
        assertEquals(1, jogos.size());
        assertEquals("Bahia", jogos.get(0).casaNome);
        assertTrue(jogos.get(0).saoPaulo);
    }

    @Test
    public void erroQueNaoEh400NaoVarreOsDias() throws Exception {
        List<String> urls = Collections.synchronizedList(new ArrayList<>());
        try {
            EspnScoreboard.buscar(null, Instant.parse("2026-10-28T15:00:00Z"), url -> {
                urls.add(url);
                throw new IOException("HTTP 500");
            });
            fail("deveria falhar");
        } catch (IOException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("500"));
        }
        assertEquals(1, urls.size());
    }

    private static JSONArray eventos(String corpo) throws Exception {
        return new JSONObject(corpo).getJSONArray("events");
    }

    private static JogoRodada porId(List<JogoRodada> jogos, String id) {
        for (int i = 0; i < jogos.size(); i++) {
            if (id.equals(jogos.get(i).id)) return jogos.get(i);
        }
        throw new AssertionError(id);
    }

    private static String jogoDoSaoPaulo() {
        return """
                {"events":[{"id":"99","date":"2026-10-29T00:30Z","competitions":[{
                  "date":"2026-10-29T00:30Z",
                  "venue":{"fullName":"Arena Fonte Nova"},
                  "status":{"type":{"state":"pre","completed":false}},
                  "competitors":[
                    {"homeAway":"home","score":"0","team":{
                      "id":"9967","shortDisplayName":"Bahia","logo":"https://a.espncdn.com/bah.png"}},
                    {"homeAway":"away","score":"0","team":{
                      "id":"2026","shortDisplayName":"São Paulo","logo":"https://a.espncdn.com/sp.png"}}
                  ]
                }]}]}
                """;
    }
}
