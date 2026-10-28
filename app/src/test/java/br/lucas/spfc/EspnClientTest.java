package br.lucas.spfc;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class EspnClientTest {
    @Test
    public void converteHorarioParaSaoPaulo() {
        String texto = Horario.jogo(Instant.parse("2026-10-29T00:30:00Z"), null);
        assertTrue(texto, texto.contains("28/10"));
        assertTrue(texto, texto.contains("21:30"));
    }

    @Test
    public void calendarioIgnoraDiaPassado() throws Exception {
        JSONArray calendario = new JSONArray();
        calendario.put("2026-10-27T07:00Z");
        calendario.put("2026-10-28T07:00Z");
        calendario.put("2026-11-04T08:00Z");
        assertEquals(List.of("20261028", "20261104"),
                EspnClient.dias(calendario, LocalDate.of(2026, 10, 28)));
    }

    @Test
    public void filtraProximoJogoECanalBrasileiro() throws Exception {
        JSONObject raiz = new JSONObject("""
                {"events":[
                  {"id":"1","date":"2026-10-29T00:30Z","competitions":[{
                    "date":"2026-10-29T00:30Z","neutralSite":false,
                    "venue":{"fullName":"Arena Fonte Nova","address":{"city":"Salvador"}},
                    "status":{"type":{"state":"pre","completed":false}},
                    "competitors":[
                      {"homeAway":"home","team":{"id":"9967","shortDisplayName":"Bahia"}},
                      {"homeAway":"away","team":{"id":"2026","shortDisplayName":"São Paulo"}}
                    ],
                    "broadcasts":[{"region":"us","names":["ESPN+"]}],
                    "geoBroadcasts":[
                      {"region":"us","media":{"shortName":"ESPN"}},
                      {"region":"br","lang":"pt","media":{"shortName":"Premiere"}}
                    ]
                  }]},
                  {"id":"2","date":"2026-10-24T19:30Z","competitions":[{
                    "status":{"type":{"state":"post","completed":true}},
                    "competitors":[
                      {"homeAway":"home","team":{"id":"2026","shortDisplayName":"São Paulo"}},
                      {"homeAway":"away","team":{"id":"9169","shortDisplayName":"Mirassol"}}
                    ]
                  }]},
                  {"id":"3","date":"2026-11-01T22:00Z","competitions":[{
                    "status":{"type":{"state":"pre","completed":false}},
                    "competitors":[
                      {"homeAway":"home","team":{"id":"819","shortDisplayName":"Flamengo"}},
                      {"homeAway":"away","team":{"id":"2674","shortDisplayName":"Santos"}}
                    ]
                  }]}
                ]}
                """);
        List<Match> jogos = EspnClient.jogos(
                raiz.getJSONArray("events"),
                Instant.parse("2026-10-28T12:00:00Z"),
                new EspnBroadcastResolver());
        assertEquals(1, jogos.size());
        Match jogo = jogos.get(0);
        assertEquals("Bahia", jogo.opponent);
        assertEquals("away", jogo.homeAway);
        assertEquals("Arena Fonte Nova, Salvador", jogo.venue);
        assertEquals(List.of("Premiere"), jogo.channels);
        assertFalse(jogo.live);
    }

    @Test
    public void semCanalQuandoAListaVemVazia() throws Exception {
        JSONObject competicao = new JSONObject("""
                {"broadcasts":[],"geoBroadcasts":[]}
                """);
        assertTrue(new EspnBroadcastResolver().canais(competicao).isEmpty());
    }

    @Test
    public void destacaSaoPauloEOrdenaPelaPosicao() throws Exception {
        JSONObject raiz = new JSONObject("""
                {"children":[{"standings":{"entries":[
                  {"team":{"id":"2026","shortDisplayName":"São Paulo"},"stats":[
                    {"name":"rank","value":9},
                    {"name":"gamesPlayed","value":32},
                    {"name":"wins","value":12},
                    {"name":"ties","value":7},
                    {"name":"losses","value":13},
                    {"name":"pointsFor","value":37},
                    {"name":"pointDifferential","value":1,"displayValue":"+1"},
                    {"name":"points","value":43}
                  ]},
                  {"team":{"id":"819","shortDisplayName":"Flamengo"},"stats":[
                    {"name":"rank","value":1},
                    {"name":"gamesPlayed","value":32},
                    {"name":"wins","value":21},
                    {"name":"ties","value":7},
                    {"name":"losses","value":4},
                    {"name":"pointsFor","value":64},
                    {"name":"pointDifferential","value":37,"displayValue":"+37"},
                    {"name":"points","value":70}
                  ]}
                ]}}]}
                """);
        List<StandingRow> linhas = EspnClient.tabela(raiz);
        assertEquals(2, linhas.size());
        assertEquals("Flamengo", linhas.get(0).team);
        assertEquals(70, linhas.get(0).pts);
        assertFalse(linhas.get(0).isSpfc);
        StandingRow sp = linhas.get(1);
        assertTrue(sp.isSpfc);
        assertEquals(9, sp.rank);
        assertEquals(32, sp.played);
        assertEquals(12, sp.wins);
        assertEquals(7, sp.draws);
        assertEquals(13, sp.losses);
        assertEquals(37, sp.goalsFor);
        assertEquals("+1", sp.gd);
        assertEquals(43, sp.pts);
    }
}
