package br.lucas.spfc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Totais da temporada a partir dos jogos já carregados. Sem rede. */
final class ResumoTemporada {
    static final class LinhaLiga {
        final String slug;
        final String nome;
        final int jogos;
        final int vitorias;
        final int empates;
        final int derrotas;

        LinhaLiga(String slug, String nome, int jogos, int vitorias, int empates, int derrotas) {
            this.slug = slug;
            this.nome = nome;
            this.jogos = jogos;
            this.vitorias = vitorias;
            this.empates = empates;
            this.derrotas = derrotas;
        }
    }

    final int jogos;
    final int vitorias;
    final int empates;
    final int derrotas;
    final int golsPro;
    final int golsContra;
    final int saldo;
    final int aproveitamento;
    final List<Match.Resultado> ultimos5;
    final Match maiorVitoria;
    final Match piorDerrota;
    final List<LinhaLiga> ligas;

    ResumoTemporada(int jogos, int vitorias, int empates, int derrotas,
            int golsPro, int golsContra, int saldo, int aproveitamento,
            List<Match.Resultado> ultimos5, Match maiorVitoria, Match piorDerrota,
            List<LinhaLiga> ligas) {
        this.jogos = jogos;
        this.vitorias = vitorias;
        this.empates = empates;
        this.derrotas = derrotas;
        this.golsPro = golsPro;
        this.golsContra = golsContra;
        this.saldo = saldo;
        this.aproveitamento = aproveitamento;
        this.ultimos5 = Collections.unmodifiableList(new ArrayList<>(ultimos5));
        this.maiorVitoria = maiorVitoria;
        this.piorDerrota = piorDerrota;
        this.ligas = Collections.unmodifiableList(new ArrayList<>(ligas));
    }

    static ResumoTemporada de(List<Match> origem) {
        List<Match> encerrados = encerrados(origem);
        encerrados.sort(Comparator.comparing(jogo -> jogo.kickoffInstant));

        int vitorias = 0;
        int empates = 0;
        int derrotas = 0;
        int golsPro = 0;
        int golsContra = 0;
        Match maior = null;
        Match pior = null;
        for (int i = 0; i < encerrados.size(); i++) {
            Match jogo = encerrados.get(i);
            golsPro += jogo.goalsFor;
            golsContra += jogo.goalsAgainst;
            Match.Resultado resultado = jogo.resultado();
            if (resultado == Match.Resultado.VITORIA) {
                vitorias++;
                if (melhorVitoria(maior, jogo)) maior = jogo;
            } else if (resultado == Match.Resultado.EMPATE) {
                empates++;
            } else if (resultado == Match.Resultado.DERROTA) {
                derrotas++;
                if (piorDerrota(pior, jogo)) pior = jogo;
            }
        }

        int jogos = encerrados.size();
        int saldo = golsPro - golsContra;
        int aproveitamento = jogos == 0
                ? 0
                : (int) Math.round(100.0 * (3 * vitorias + empates) / (3.0 * jogos));
        return new ResumoTemporada(
                jogos, vitorias, empates, derrotas, golsPro, golsContra, saldo, aproveitamento,
                ultimos(encerrados), maior, pior, ligas(encerrados));
    }

    String saldoTexto() {
        return saldo > 0 ? "+" + saldo : Integer.toString(saldo);
    }

    String aproveitamentoTexto() {
        return aproveitamento + "%";
    }

    private static List<Match> encerrados(List<Match> origem) {
        List<Match> saida = new ArrayList<>();
        if (origem == null) return saida;
        for (int i = 0; i < origem.size(); i++) {
            Match jogo = origem.get(i);
            if (jogo != null && jogo.finished && jogo.goalsFor != null && jogo.goalsAgainst != null) {
                saida.add(jogo);
            }
        }
        return saida;
    }

    private static List<Match.Resultado> ultimos(List<Match> cronologico) {
        List<Match.Resultado> saida = new ArrayList<>();
        int inicio = Math.max(0, cronologico.size() - 5);
        for (int i = inicio; i < cronologico.size(); i++) {
            Match.Resultado resultado = cronologico.get(i).resultado();
            if (resultado != null) saida.add(resultado);
        }
        return saida;
    }

    private static List<LinhaLiga> ligas(List<Match> encerrados) {
        Map<String, int[]> totais = new LinkedHashMap<>();
        Map<String, String> nomes = new LinkedHashMap<>();
        for (int i = 0; i < encerrados.size(); i++) {
            Match jogo = encerrados.get(i);
            String slug = jogo.leagueSlug.isEmpty() ? jogo.leagueName : jogo.leagueSlug;
            if (slug.isEmpty()) continue;
            int[] n = totais.get(slug);
            if (n == null) {
                n = new int[4];
                totais.put(slug, n);
                String nome = jogo.leagueName.isEmpty() ? slug : jogo.leagueName;
                nomes.put(slug, nome);
            }
            n[0]++;
            Match.Resultado resultado = jogo.resultado();
            if (resultado == Match.Resultado.VITORIA) n[1]++;
            else if (resultado == Match.Resultado.EMPATE) n[2]++;
            else if (resultado == Match.Resultado.DERROTA) n[3]++;
        }

        List<LinhaLiga> saida = new ArrayList<>();
        List<Competicao.Aba> ordem = Competicao.abas(encerrados);
        for (int i = 0; i < ordem.size(); i++) {
            Competicao.Aba aba = ordem.get(i);
            int[] n = totais.remove(aba.slug);
            if (n == null) continue;
            saida.add(new LinhaLiga(aba.slug, aba.nome, n[0], n[1], n[2], n[3]));
        }
        for (Map.Entry<String, int[]> item : totais.entrySet()) {
            int[] n = item.getValue();
            saida.add(new LinhaLiga(item.getKey(), nomes.get(item.getKey()), n[0], n[1], n[2], n[3]));
        }
        return saida;
    }

    private static boolean melhorVitoria(Match atual, Match candidato) {
        if (atual == null) return true;
        int atualSaldo = atual.goalsFor - atual.goalsAgainst;
        int novoSaldo = candidato.goalsFor - candidato.goalsAgainst;
        if (novoSaldo != atualSaldo) return novoSaldo > atualSaldo;
        if (!candidato.goalsFor.equals(atual.goalsFor)) return candidato.goalsFor > atual.goalsFor;
        return candidato.kickoffInstant.isAfter(atual.kickoffInstant);
    }

    private static boolean piorDerrota(Match atual, Match candidato) {
        if (atual == null) return true;
        int atualSaldo = atual.goalsFor - atual.goalsAgainst;
        int novoSaldo = candidato.goalsFor - candidato.goalsAgainst;
        if (novoSaldo != atualSaldo) return novoSaldo < atualSaldo;
        if (!candidato.goalsAgainst.equals(atual.goalsAgainst)) {
            return candidato.goalsAgainst > atual.goalsAgainst;
        }
        return candidato.kickoffInstant.isAfter(atual.kickoffInstant);
    }
}
