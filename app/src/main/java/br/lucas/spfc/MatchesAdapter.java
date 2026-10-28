package br.lucas.spfc;

import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Lista vertical dos jogos. O D-pad foca o cartão inteiro. */
final class MatchesAdapter extends RecyclerView.Adapter<MatchesAdapter.Item> {
    private final List<Match> itens = new ArrayList<>();
    private boolean esconderChip;
    private int focoCima;
    private int focoDireita;

    void definir(List<Match> jogos, boolean esconderChip, int focoCima, int focoDireita) {
        itens.clear();
        itens.addAll(jogos);
        this.esconderChip = esconderChip;
        this.focoCima = focoCima;
        this.focoDireita = focoDireita;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Item onCreateViewHolder(@NonNull ViewGroup pai, int tipo) {
        View view = LayoutInflater.from(pai.getContext()).inflate(R.layout.item_jogo, pai, false);
        return new Item(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Item item, int posicao) {
        item.itemView.setNextFocusUpId(posicao == 0 && focoCima != 0 ? focoCima : View.NO_ID);
        item.itemView.setNextFocusRightId(focoDireita != 0 ? focoDireita : View.NO_ID);
        item.preencher(itens.get(posicao), esconderChip);
    }

    @Override
    public int getItemCount() {
        return itens.size();
    }

    static final class Item extends RecyclerView.ViewHolder {
        private final TextView quando;
        private final TextView competicao;
        private final TextView mando;
        private final TextView adversario;
        private final TextView placar;
        private final TextView estadio;
        private final TextView canal;

        Item(View view) {
            super(view);
            quando = view.findViewById(R.id.quando);
            competicao = view.findViewById(R.id.competicao);
            mando = view.findViewById(R.id.mando);
            adversario = view.findViewById(R.id.adversario);
            placar = view.findViewById(R.id.placar);
            estadio = view.findViewById(R.id.estadio);
            canal = view.findViewById(R.id.canal);
        }

        void preencher(Match jogo, boolean esconderChip) {
            String aoVivo = jogo.live ? itemView.getContext().getString(R.string.ao_vivo) : null;
            quando.setText(Horario.jogo(jogo.kickoffInstant, aoVivo));
            int mandoId;
            if ("home".equals(jogo.homeAway)) mandoId = R.string.casa;
            else if ("away".equals(jogo.homeAway)) mandoId = R.string.fora;
            else mandoId = R.string.neutro;
            mando.setText(mandoId);
            adversario.setText(jogo.opponent);

            boolean mostraChip = !esconderChip && !jogo.leagueName.isEmpty();
            competicao.setVisibility(mostraChip ? View.VISIBLE : View.GONE);
            competicao.setText(jogo.leagueName);

            if (jogo.finished) {
                estadio.setVisibility(View.GONE);
                canal.setVisibility(View.GONE);
                if (jogo.goalsFor != null && jogo.goalsAgainst != null) {
                    placar.setVisibility(View.VISIBLE);
                    placar.setText(jogo.goalsFor + " × " + jogo.goalsAgainst);
                    Match.Resultado resultado = jogo.resultado();
                    int cor = R.color.empate;
                    if (resultado == Match.Resultado.VITORIA) cor = R.color.vitoria;
                    else if (resultado == Match.Resultado.DERROTA) cor = R.color.derrota;
                    placar.setTextColor(itemView.getContext().getColor(cor));
                } else {
                    placar.setVisibility(View.GONE);
                }
            } else {
                placar.setVisibility(View.GONE);
                estadio.setVisibility(View.VISIBLE);
                canal.setVisibility(View.VISIBLE);
                estadio.setText(jogo.venue.isEmpty()
                        ? itemView.getContext().getString(R.string.estadio_a_confirmar)
                        : jogo.venue);
                canal.setText(jogo.channels.isEmpty()
                        ? itemView.getContext().getString(R.string.canal_a_confirmar)
                        : TextUtils.join(" · ", jogo.channels));
            }
        }
    }
}
