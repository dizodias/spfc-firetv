package br.lucas.spfc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Jogos e tabela já unidos, com o instante em que foram obtidos. */
final class Snapshot {
    final List<Match> matches;
    final List<StandingRow> standings;
    final long fetchedAtMillis;

    Snapshot(List<Match> matches, List<StandingRow> standings, long fetchedAtMillis) {
        this.matches = Collections.unmodifiableList(new ArrayList<>(matches));
        this.standings = Collections.unmodifiableList(new ArrayList<>(standings));
        this.fetchedAtMillis = fetchedAtMillis;
    }
}
