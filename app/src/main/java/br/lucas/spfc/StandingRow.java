package br.lucas.spfc;

/** Uma linha da Série A. GP é gols pró, SG é o saldo como a ESPN escreve ("+1"). */
final class StandingRow {
    final int rank;
    final String team;
    final int played;
    final int wins;
    final int draws;
    final int losses;
    final int goalsFor;
    final String gd;
    final int pts;
    final boolean isSpfc;

    StandingRow(int rank, String team, int played, int wins, int draws, int losses,
            int goalsFor, String gd, int pts, boolean isSpfc) {
        this.rank = rank;
        this.team = team;
        this.played = played;
        this.wins = wins;
        this.draws = draws;
        this.losses = losses;
        this.goalsFor = goalsFor;
        this.gd = gd;
        this.pts = pts;
        this.isSpfc = isSpfc;
    }
}
