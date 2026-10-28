package br.lucas.spfc;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Horário de exibição em America/Sao_Paulo, com dia da semana em português. */
final class Horario {
    static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final Locale PT_BR = new Locale("pt", "BR");
    private static final DateTimeFormatter DATA =
            DateTimeFormatter.ofPattern("EEE dd/MM · HH:mm", PT_BR);
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm", PT_BR);

    private Horario() { }

    /** {@code prefixoAoVivo} nulo mostra data e hora. Preenchido, mostra "Ao vivo · HH:mm". */
    static String jogo(Instant instante, String prefixoAoVivo) {
        ZonedDateTime local = instante.atZone(SAO_PAULO);
        if (prefixoAoVivo != null) return prefixoAoVivo + " · " + local.format(HORA);
        return local.format(DATA);
    }

    static String hora(long epochMillis) {
        return Instant.ofEpochMilli(epochMillis).atZone(SAO_PAULO).format(HORA);
    }
}
