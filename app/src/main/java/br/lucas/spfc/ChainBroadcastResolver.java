package br.lucas.spfc;

import org.json.JSONObject;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Tenta cada fonte na ordem e para no primeiro canal encontrado. */
final class ChainBroadcastResolver implements BroadcastResolver {
    private final BroadcastResolver[] elos;

    ChainBroadcastResolver(BroadcastResolver... elos) {
        this.elos = elos == null ? new BroadcastResolver[0] : elos;
    }

    @Override
    public List<String> canais(JSONObject competicao, Instant kickoff, String casa, String fora) {
        for (int i = 0; i < elos.length; i++) {
            BroadcastResolver elo = elos[i];
            if (elo == null) continue;
            try {
                List<String> achados = elo.canais(competicao, kickoff, casa, fora);
                if (achados != null && !achados.isEmpty()) return achados;
            } catch (RuntimeException ignorado) {
                // Fonte quebrada não derruba a cadeia.
            }
        }
        return new ArrayList<>();
    }
}
