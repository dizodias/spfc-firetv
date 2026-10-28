package br.lucas.spfc;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scoreboard do Brasileirão. A janela é de 3 dias atrás a 4 à frente.
 * O intervalo {@code dates=AAAAMMDD-AAAAMMDD} responde HTTP 400 neste campeonato;
 * nesse caso cada dia é buscado e os eventos se unem pelo id.
 */
final class EspnScoreboard {
    private static final String BASE =
            "https://site.api.espn.com/apis/site/v2/sports/soccer/bra.1/scoreboard";
    private static final String CONSULTA = "lang=pt&region=br";
    private static final int ESPERA_MS = 12_000;
    private static final int DIAS_ANTES = 3;
    private static final int DIAS_DEPOIS = 4;
    private static final DateTimeFormatter DIA = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Pattern TEXTO_RODADA = Pattern.compile("(?i)rodada\\s+(\\d+)");

    private EspnScoreboard() { }

    @FunctionalInterface
    interface Fonte {
        String get(String url) throws IOException;
    }

    static List<JogoRodada> buscar(BroadcastResolver canais, Instant agora) throws IOException {
        return buscar(canais, agora, url -> Rede.get(url, ESPERA_MS, Rede.UA_APP));
    }

    static List<JogoRodada> buscar(BroadcastResolver canais, Instant agora, Fonte fonte)
            throws IOException {
        LocalDate hoje = agora.atZone(Horario.SAO_PAULO).toLocalDate();
        JSONArray eventos;
        try {
            eventos = eventos(fonte.get(urlIntervalo(hoje)));
        } catch (IOException e) {
            if (!http400(e)) throw e;
            eventos = porDia(fonte, hoje);
        }
        return jogos(eventos, agora, canais);
    }

    /** {@code 20261025-20261101} para o dia 28/10/2026. */
    static String intervalo(LocalDate hoje) {
        return hoje.minusDays(DIAS_ANTES).format(DIA) + "-" + hoje.plusDays(DIAS_DEPOIS).format(DIA);
    }

    static List<JogoRodada> jogos(JSONArray eventos, Instant agora, BroadcastResolver canais) {
        List<JogoRodada> lidos = ler(eventos, canais);
        List<JogoRodada> daRodada = daRodadaAtual(lidos, agora);
        daRodada.sort(Comparator
                .comparing((JogoRodada jogo) -> !jogo.saoPaulo)
                .thenComparing(jogo -> jogo.kickoff)
                .thenComparing(jogo -> jogo.casaNome));
        return daRodada;
    }

    private static String urlIntervalo(LocalDate hoje) {
        return BASE + "?dates=" + intervalo(hoje) + "&" + CONSULTA;
    }

    private static String urlDia(LocalDate dia) {
        return BASE + "?dates=" + dia.format(DIA) + "&" + CONSULTA;
    }

    private static boolean http400(IOException erro) {
        String mensagem = erro.getMessage();
        return mensagem != null && mensagem.contains("HTTP 400");
    }

    private static JSONArray porDia(Fonte fonte, LocalDate hoje) throws IOException {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<JSONArray>> futuros = new ArrayList<>();
            for (int deslocamento = -DIAS_ANTES; deslocamento <= DIAS_DEPOIS; deslocamento++) {
                LocalDate dia = hoje.plusDays(deslocamento);
                futuros.add(pool.submit(() -> baixarOuNulo(fonte, dia)));
            }
            JSONArray todos = new JSONArray();
            Set<String> ids = new HashSet<>();
            int ok = 0;
            for (int i = 0; i < futuros.size(); i++) {
                try {
                    JSONArray dia = futuros.get(i).get(20, TimeUnit.SECONDS);
                    if (dia == null) continue;
                    ok++;
                    copiarSemRepetir(todos, dia, ids);
                } catch (Exception ignorado) {
                    // Um dia falhou; os outros ainda entram.
                }
            }
            if (ok == 0) throw new IOException("scoreboard da rodada indisponível");
            return todos;
        } finally {
            pool.shutdownNow();
        }
    }

    private static JSONArray baixarOuNulo(Fonte fonte, LocalDate dia) {
        try {
            return eventos(fonte.get(urlDia(dia)));
        } catch (IOException e) {
            return null;
        }
    }

    private static void copiarSemRepetir(JSONArray destino, JSONArray origem, Set<String> ids)
            throws JSONException {
        for (int i = 0; i < origem.length(); i++) {
            JSONObject evento = origem.optJSONObject(i);
            if (evento == null) continue;
            String id = evento.optString("id", "");
            if (!id.isEmpty() && !ids.add(id)) continue;
            destino.put(evento);
        }
    }

    private static JSONArray eventos(String corpo) throws IOException {
        try {
            JSONObject raiz = new JSONObject(corpo);
            JSONArray lista = raiz.optJSONArray("events");
            return lista == null ? new JSONArray() : lista;
        } catch (JSONException e) {
            throw new IOException("scoreboard inválido", e);
        }
    }

    private static List<JogoRodada> ler(JSONArray eventos, BroadcastResolver canais) {
        List<JogoRodada> saida = new ArrayList<>();
        if (eventos == null) return saida;
        Set<String> vistos = new HashSet<>();
        for (int i = 0; i < eventos.length(); i++) {
            JSONObject evento = eventos.optJSONObject(i);
            if (evento == null) continue;
            String id = evento.optString("id", "");
            if (!id.isEmpty() && !vistos.add(id)) continue;
            JogoRodada jogo = jogo(evento, canais);
            if (jogo != null) saida.add(jogo);
        }
        return saida;
    }

    /**
     * Se houver número, fica a rodada de quem está jogando.
     * Sem jogo ao vivo, fica a do São Paulo. Sem número em ninguém, fica a janela inteira.
     */
    private static List<JogoRodada> daRodadaAtual(List<JogoRodada> jogos, Instant agora) {
        boolean temNumero = false;
        for (int i = 0; i < jogos.size(); i++) {
            if (jogos.get(i).rodada != null) temNumero = true;
        }
        if (!temNumero) return new ArrayList<>(jogos);

        JogoRodada escolhido = null;
        for (int i = 0; i < jogos.size(); i++) {
            JogoRodada jogo = jogos.get(i);
            if (jogo.rodada == null || jogo.status != JogoRodada.Status.AO_VIVO) continue;
            if (escolhido == null || maisPerto(jogo, escolhido, agora)) escolhido = jogo;
        }
        if (escolhido == null) {
            for (int i = 0; i < jogos.size(); i++) {
                JogoRodada jogo = jogos.get(i);
                if (jogo.rodada == null || !jogo.saoPaulo) continue;
                if (escolhido == null || maisPerto(jogo, escolhido, agora)) escolhido = jogo;
            }
        }
        if (escolhido == null) {
            for (int i = 0; i < jogos.size(); i++) {
                JogoRodada jogo = jogos.get(i);
                if (jogo.rodada == null) continue;
                if (escolhido == null || maisPerto(jogo, escolhido, agora)) escolhido = jogo;
            }
        }
        List<JogoRodada> saida = new ArrayList<>();
        if (escolhido == null) return saida;
        for (int i = 0; i < jogos.size(); i++) {
            JogoRodada jogo = jogos.get(i);
            if (escolhido.rodada.equals(jogo.rodada)) saida.add(jogo);
        }
        return saida;
    }

    private static boolean maisPerto(JogoRodada candidato, JogoRodada atual, Instant agora) {
        long distanciaCandidato = Math.abs(candidato.kickoff.toEpochMilli() - agora.toEpochMilli());
        long distanciaAtual = Math.abs(atual.kickoff.toEpochMilli() - agora.toEpochMilli());
        if (distanciaCandidato != distanciaAtual) return distanciaCandidato < distanciaAtual;
        boolean candidatoAberto = candidato.status != JogoRodada.Status.ENCERRADO;
        boolean atualAberto = atual.status != JogoRodada.Status.ENCERRADO;
        if (candidatoAberto != atualAberto) return candidatoAberto;
        return candidato.kickoff.isBefore(atual.kickoff);
    }

    private static JogoRodada jogo(JSONObject evento, BroadcastResolver canais) {
        JSONArray competicoes = evento.optJSONArray("competitions");
        if (competicoes == null || competicoes.length() == 0) return null;
        JSONObject competicao = competicoes.optJSONObject(0);
        if (competicao == null) return null;

        JSONArray times = competicao.optJSONArray("competitors");
        if (times == null || times.length() < 2) return null;
        JSONObject casa = null;
        JSONObject fora = null;
        for (int i = 0; i < times.length(); i++) {
            JSONObject lado = times.optJSONObject(i);
            if (lado == null) continue;
            String mando = lado.optString("homeAway", "");
            if ("home".equals(mando) && casa == null) casa = lado;
            else if ("away".equals(mando) && fora == null) fora = lado;
        }
        if (casa == null || fora == null) {
            JSONObject primeiro = times.optJSONObject(0);
            JSONObject segundo = times.optJSONObject(1);
            if (casa == null) casa = fora == primeiro ? segundo : primeiro;
            if (fora == null) fora = casa == segundo ? primeiro : segundo;
        }
        if (casa == null || fora == null || casa == fora) return null;

        JSONObject timeCasa = casa.optJSONObject("team");
        JSONObject timeFora = fora.optJSONObject("team");
        String casaNome = nome(timeCasa);
        String foraNome = nome(timeFora);
        if (casaNome.isEmpty() || foraNome.isEmpty()) return null;

        String data = competicao.optString("date", "");
        if (data.isEmpty()) data = evento.optString("date", "");
        Instant instante = EspnClient.instante(data);
        if (instante == null) return null;

        JSONObject status = competicao.optJSONObject("status");
        if (status == null) status = evento.optJSONObject("status");
        JSONObject tipo = status == null ? null : status.optJSONObject("type");
        String estado = tipo == null ? "" : tipo.optString("state", "");
        boolean encerrado = tipo != null
                && (tipo.optBoolean("completed", false) || "post".equals(estado));
        boolean aoVivo = !encerrado && "in".equals(estado);
        JogoRodada.Status situacao = aoVivo
                ? JogoRodada.Status.AO_VIVO
                : (encerrado ? JogoRodada.Status.ENCERRADO : JogoRodada.Status.AGENDADO);

        String minuto = "";
        if (aoVivo && status != null) {
            minuto = status.optString("displayClock", "").trim();
            if (minuto.isEmpty()) minuto = minutoDoPeriodo(status.optInt("period", 0));
        }

        Integer casaGols = null;
        Integer foraGols = null;
        if (aoVivo || encerrado) {
            casaGols = EspnClient.gols(casa);
            foraGols = EspnClient.gols(fora);
        }

        String casaId = timeCasa == null ? "" : timeCasa.optString("id", "");
        String foraId = timeFora == null ? "" : timeFora.optString("id", "");
        boolean sp = EspnClient.TIME_SP.equals(casaId) || EspnClient.TIME_SP.equals(foraId)
                || Nomes.iguais(casaNome, "São Paulo") || Nomes.iguais(foraNome, "São Paulo");

        List<String> canaisJogo = new ArrayList<>();
        if (canais != null) {
            canaisJogo = canais.canais(competicao, instante, casaNome, foraNome);
            if (canaisJogo == null) canaisJogo = new ArrayList<>();
        }

        String id = evento.optString("id", "");
        if (id.isEmpty()) id = casaId + "-" + foraId + "-" + instante.toEpochMilli();

        return new JogoRodada(
                id,
                numeroRodada(evento, competicao),
                instante,
                estadio(competicao, evento),
                casaNome,
                foraNome,
                casaId,
                foraId,
                timeCasa == null ? "" : timeCasa.optString("logo", "").trim(),
                timeFora == null ? "" : timeFora.optString("logo", "").trim(),
                casaGols,
                foraGols,
                situacao,
                minuto,
                canaisJogo,
                sp);
    }

    private static Integer numeroRodada(JSONObject evento, JSONObject competicao) {
        Integer semana = numeroDe(evento.opt("week"));
        if (semana == null) semana = numeroDe(competicao.opt("week"));
        if (semana == null) semana = textoRodada(evento.optJSONArray("notes"));
        if (semana == null) semana = textoRodada(competicao.optJSONArray("notes"));
        if (semana == null) semana = numeroNoTexto(competicao.optString("altGameNote", ""));
        return semana;
    }

    private static Integer numeroDe(Object valor) {
        if (valor == null || valor == JSONObject.NULL) return null;
        if (valor instanceof Number) {
            int n = ((Number) valor).intValue();
            return n > 0 ? n : null;
        }
        if (valor instanceof JSONObject) {
            JSONObject objeto = (JSONObject) valor;
            if (!objeto.has("number") || objeto.isNull("number")) return null;
            int n = objeto.optInt("number", 0);
            return n > 0 ? n : null;
        }
        if (valor instanceof String) return numeroNoTexto((String) valor);
        return null;
    }

    private static Integer textoRodada(JSONArray notes) {
        if (notes == null) return null;
        for (int i = 0; i < notes.length(); i++) {
            Object item = notes.opt(i);
            Integer n = null;
            if (item instanceof String) n = numeroNoTexto((String) item);
            else if (item instanceof JSONObject) {
                JSONObject objeto = (JSONObject) item;
                n = numeroNoTexto(objeto.optString("headline", ""));
                if (n == null) n = numeroNoTexto(objeto.optString("text", ""));
                if (n == null) n = numeroNoTexto(objeto.optString("summary", ""));
            }
            if (n != null) return n;
        }
        return null;
    }

    private static Integer numeroNoTexto(String texto) {
        if (texto == null || texto.isEmpty()) return null;
        Matcher busca = TEXTO_RODADA.matcher(texto);
        if (!busca.find()) return null;
        try {
            int n = Integer.parseInt(busca.group(1));
            return n > 0 ? n : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String minutoDoPeriodo(int periodo) {
        if (periodo == 1) return "1º tempo";
        if (periodo == 2) return "2º tempo";
        if (periodo > 2) return "prorrogação";
        return "";
    }

    private static String nome(JSONObject time) {
        if (time == null) return "";
        String escrito = time.optString("shortDisplayName", "").trim();
        if (escrito.isEmpty()) escrito = time.optString("displayName", "").trim();
        if (escrito.isEmpty()) escrito = time.optString("abbreviation", "").trim();
        return escrito;
    }

    private static String estadio(JSONObject competicao, JSONObject evento) {
        JSONObject venue = competicao.optJSONObject("venue");
        if (venue == null) venue = evento.optJSONObject("venue");
        if (venue == null) return "";
        String nome = venue.optString("fullName", "").trim();
        JSONObject endereco = venue.optJSONObject("address");
        String cidade = endereco == null ? "" : endereco.optString("city", "").trim();
        if (!nome.isEmpty() && !cidade.isEmpty()
                && !nome.toLowerCase(Locale.ROOT).contains(cidade.toLowerCase(Locale.ROOT))) {
            nome = nome + ", " + cidade;
        }
        return nome;
    }
}
