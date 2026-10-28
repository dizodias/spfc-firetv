package br.lucas.spfc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Jogos da temporada, a rodada do Brasileirão e a tabela. */
final class Snapshot {
    final List<Match> matches;
    final List<StandingRow> standings;
    final List<JogoRodada> rodada;
    /** Falso se a rodada não foi lida. Lista vazia com verdadeiro é resposta sem jogo. */
    final boolean rodadaOk;
    final long fetchedAtMillis;

    Snapshot(List<Match> matches, List<StandingRow> standings, long fetchedAtMillis) {
        this(matches, standings, new ArrayList<>(), fetchedAtMillis, false);
    }

    Snapshot(List<Match> matches, List<StandingRow> standings, List<JogoRodada> rodada,
            long fetchedAtMillis) {
        this(matches, standings, rodada, fetchedAtMillis, true);
    }

    Snapshot(List<Match> matches, List<StandingRow> standings, List<JogoRodada> rodada,
            long fetchedAtMillis, boolean rodadaOk) {
        this.matches = Collections.unmodifiableList(new ArrayList<>(matches));
        this.standings = Collections.unmodifiableList(new ArrayList<>(standings));
        List<JogoRodada> jogos = rodada == null ? new ArrayList<>() : rodada;
        this.rodada = Collections.unmodifiableList(new ArrayList<>(jogos));
        this.rodadaOk = rodadaOk;
        this.fetchedAtMillis = fetchedAtMillis;
    }

    boolean temAoVivo() {
        for (int i = 0; i < rodada.size(); i++) {
            if (rodada.get(i).status == JogoRodada.Status.AO_VIVO) return true;
        }
        return false;
    }

    List<Match> proximos() {
        List<Match> saida = new ArrayList<>();
        for (int i = 0; i < matches.size(); i++) {
            Match jogo = matches.get(i);
            if (!jogo.finished) saida.add(jogo);
        }
        saida.sort(Comparator.comparing(jogo -> jogo.kickoffInstant));
        return saida;
    }

    List<Match> resultados() {
        List<Match> saida = new ArrayList<>();
        for (int i = 0; i < matches.size(); i++) {
            Match jogo = matches.get(i);
            if (jogo.finished) saida.add(jogo);
        }
        saida.sort(Comparator.comparing((Match jogo) -> jogo.kickoffInstant).reversed());
        return saida;
    }

    List<Match> daCompeticao(String slug) {
        List<Match> saida = new ArrayList<>();
        if (slug == null) return saida;
        for (int i = 0; i < matches.size(); i++) {
            Match jogo = matches.get(i);
            if (slug.equals(jogo.leagueSlug)) saida.add(jogo);
        }
        saida.sort(Comparator.comparing((Match jogo) -> jogo.kickoffInstant).reversed());
        return saida;
    }
}
