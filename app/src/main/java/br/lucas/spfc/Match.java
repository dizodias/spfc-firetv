package br.lucas.spfc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Um jogo do São Paulo: adversário, mando, horário, estádio, canais, liga e placar. */
final class Match {
    enum Resultado { VITORIA, EMPATE, DERROTA }

    final String opponent;
    /** "home", "away" ou "neutral". */
    final String homeAway;
    final Instant kickoffInstant;
    /** Vazio quando a ESPN não manda o estádio. */
    final String venue;
    final List<String> channels;
    final boolean live;
    final String leagueName;
    final String leagueSlug;
    final Integer goalsFor;
    final Integer goalsAgainst;
    final boolean finished;

    Match(String opponent, String homeAway, Instant kickoffInstant, String venue,
            List<String> channels, boolean live, String leagueName, String leagueSlug,
            Integer goalsFor, Integer goalsAgainst, boolean finished) {
        this.opponent = opponent;
        this.homeAway = homeAway;
        this.kickoffInstant = kickoffInstant;
        this.venue = venue;
        this.channels = Collections.unmodifiableList(new ArrayList<>(channels));
        this.live = live;
        this.leagueName = leagueName == null ? "" : leagueName;
        this.leagueSlug = leagueSlug == null ? "" : leagueSlug;
        this.goalsFor = goalsFor;
        this.goalsAgainst = goalsAgainst;
        this.finished = finished;
    }

    Resultado resultado() {
        if (!finished || goalsFor == null || goalsAgainst == null) return null;
        if (goalsFor > goalsAgainst) return Resultado.VITORIA;
        if (goalsFor < goalsAgainst) return Resultado.DERROTA;
        return Resultado.EMPATE;
    }
}
