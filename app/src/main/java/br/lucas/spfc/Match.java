package br.lucas.spfc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Um jogo do São Paulo: adversário, mando, horário, estádio e canais quando a ESPN manda. */
final class Match {
    final String opponent;
    /** "home", "away" ou "neutral". */
    final String homeAway;
    final Instant kickoffInstant;
    /** Vazio quando a ESPN não manda o estádio. */
    final String venue;
    final List<String> channels;
    final boolean live;

    Match(String opponent, String homeAway, Instant kickoffInstant, String venue,
            List<String> channels, boolean live) {
        this.opponent = opponent;
        this.homeAway = homeAway;
        this.kickoffInstant = kickoffInstant;
        this.venue = venue;
        this.channels = Collections.unmodifiableList(new ArrayList<>(channels));
        this.live = live;
    }
}
