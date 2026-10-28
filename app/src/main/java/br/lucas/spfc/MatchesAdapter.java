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

/** Lista vertical dos próximos jogos. O D-pad foca o cartão inteiro. */
final class MatchesAdapter extends RecyclerView.Adapter<MatchesAdapter.Item> {
    private final List<Match> itens = new ArrayList<>();

    void definir(List<Match> jogos) {
        itens.clear();
        itens.addAll(jogos);
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
        item.preencher(itens.get(posicao));
    }

    @Override
    public int getItemCount() {
        return itens.size();
    }

    static final class Item extends RecyclerView.ViewHolder {
        private final TextView quando;
        private final TextView mando;
        private final TextView adversario;
        private final TextView estadio;
        private final TextView canal;

        Item(View view) {
            super(view);
            quando = view.findViewById(R.id.quando);
            mando = view.findViewById(R.id.mando);
            adversario = view.findViewById(R.id.adversario);
            estadio = view.findViewById(R.id.estadio);
            canal = view.findViewById(R.id.canal);
        }

        void preencher(Match jogo) {
            String aoVivo = jogo.live ? itemView.getContext().getString(R.string.ao_vivo) : null;
            quando.setText(Horario.jogo(jogo.kickoffInstant, aoVivo));
            int mandoId;
            if ("home".equals(jogo.homeAway)) mandoId = R.string.casa;
            else if ("away".equals(jogo.homeAway)) mandoId = R.string.fora;
            else mandoId = R.string.neutro;
            mando.setText(mandoId);
            adversario.setText(jogo.opponent);
            estadio.setText(jogo.venue.isEmpty()
                    ? itemView.getContext().getString(R.string.estadio_a_confirmar)
                    : jogo.venue);
            canal.setText(jogo.channels.isEmpty()
                    ? itemView.getContext().getString(R.string.canal_a_confirmar)
                    : TextUtils.join(" · ", jogo.channels));
        }
    }
}
