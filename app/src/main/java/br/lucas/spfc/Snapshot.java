package br.lucas.spfc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Todos os jogos da temporada e a tabela, com o instante em que foram obtidos. */
final class Snapshot {
    final List<Match> matches;
    final List<StandingRow> standings;
    final long fetchedAtMillis;

    Snapshot(List<Match> matches, List<StandingRow> standings, long fetchedAtMillis) {
        this.matches = Collections.unmodifiableList(new ArrayList<>(matches));
        this.standings = Collections.unmodifiableList(new ArrayList<>(standings));
        this.fetchedAtMillis = fetchedAtMillis;
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
