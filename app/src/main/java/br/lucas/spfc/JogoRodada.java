package br.lucas.spfc;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Um jogo do Brasileirão na rodada, com os dois times e o placar. */
final class JogoRodada {
    enum Status { AGENDADO, AO_VIVO, ENCERRADO }

    final String id;
    /** Nulo quando a ESPN não manda o número da rodada. */
    final Integer rodada;
    final Instant kickoff;
    final String venue;
    final String casaNome;
    final String foraNome;
    final String casaId;
    final String foraId;
    final String casaLogo;
    final String foraLogo;
    final Integer casaGols;
    final Integer foraGols;
    final Status status;
    /** Relógio da ESPN ({@code 45'}, {@code 90'+4'}) só enquanto o jogo está ao vivo. */
    final String minuto;
    final List<String> channels;
    final boolean saoPaulo;

    JogoRodada(String id, Integer rodada, Instant kickoff, String venue,
            String casaNome, String foraNome, String casaId, String foraId,
            String casaLogo, String foraLogo, Integer casaGols, Integer foraGols,
            Status status, String minuto, List<String> channels, boolean saoPaulo) {
        this.id = id == null ? "" : id;
        this.rodada = rodada;
        this.kickoff = kickoff;
        this.venue = venue == null ? "" : venue;
        this.casaNome = casaNome == null ? "" : casaNome;
        this.foraNome = foraNome == null ? "" : foraNome;
        this.casaId = casaId == null ? "" : casaId;
        this.foraId = foraId == null ? "" : foraId;
        this.casaLogo = casaLogo == null ? "" : casaLogo;
        this.foraLogo = foraLogo == null ? "" : foraLogo;
        this.casaGols = casaGols;
        this.foraGols = foraGols;
        this.status = status == null ? Status.AGENDADO : status;
        this.minuto = minuto == null ? "" : minuto;
        List<String> lista = channels == null ? new ArrayList<>() : channels;
        this.channels = Collections.unmodifiableList(new ArrayList<>(lista));
        this.saoPaulo = saoPaulo;
    }
}
