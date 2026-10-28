package br.lucas.spfc;

import android.content.res.ColorStateList;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/** Grade de 2 colunas da rodada. O D-pad anda no cartão inteiro. */
final class RodadaAdapter extends RecyclerView.Adapter<RodadaAdapter.Item> {
    private static final int COLUNAS = 2;

    interface Navegacao {
        void aoFocar(JogoRodada jogo, int viewId);

        void abrirTabela(JogoRodada jogo);

        void acima();

        void abaixo();
    }

    private final List<JogoRodada> itens = new ArrayList<>();
    private final Escudos escudos;
    private final Navegacao navegacao;

    RodadaAdapter(Escudos escudos, Navegacao navegacao) {
        this.escudos = escudos;
        this.navegacao = navegacao;
    }

    void definir(List<JogoRodada> jogos) {
        List<JogoRodada> nova = jogos == null ? new ArrayList<>() : jogos;
        if (mesmaOrdem(nova)) {
            for (int i = 0; i < nova.size(); i++) {
                if (!igual(itens.get(i), nova.get(i))) {
                    itens.set(i, nova.get(i));
                    notifyItemChanged(i);
                }
            }
            return;
        }
        itens.clear();
        itens.addAll(nova);
        notifyDataSetChanged();
    }

    private boolean mesmaOrdem(List<JogoRodada> nova) {
        if (nova.size() != itens.size()) return false;
        for (int i = 0; i < nova.size(); i++) {
            if (!itens.get(i).id.equals(nova.get(i).id)) return false;
        }
        return true;
    }

    private static boolean igual(JogoRodada a, JogoRodada b) {
        return a.status == b.status
                && a.minuto.equals(b.minuto)
                && iguais(a.casaGols, b.casaGols)
                && iguais(a.foraGols, b.foraGols)
                && a.channels.equals(b.channels)
                && a.casaNome.equals(b.casaNome)
                && a.foraNome.equals(b.foraNome);
    }

    private static boolean iguais(Integer a, Integer b) {
        return a == null ? b == null : a.equals(b);
    }

    @NonNull
    @Override
    public Item onCreateViewHolder(@NonNull ViewGroup pai, int tipo) {
        View view = LayoutInflater.from(pai.getContext()).inflate(R.layout.item_rodada, pai, false);
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

    final class Item extends RecyclerView.ViewHolder {
        private final View linhaAoVivo;
        private final View ponto;
        private final TextView selo;
        private final TextView quando;
        private final ImageView escudoCasa;
        private final ImageView escudoFora;
        private final TextView casa;
        private final TextView fora;
        private final TextView placar;
        private final TextView estadio;
        private final TextView canal;
        private JogoRodada jogoAtual;

        Item(View view) {
            super(view);
            if (view.getId() == View.NO_ID) view.setId(View.generateViewId());
            linhaAoVivo = view.findViewById(R.id.linha_ao_vivo);
            ponto = view.findViewById(R.id.ponto_ao_vivo);
            selo = view.findViewById(R.id.selo_ao_vivo);
            quando = view.findViewById(R.id.quando);
            escudoCasa = view.findViewById(R.id.escudo_casa);
            escudoFora = view.findViewById(R.id.escudo_fora);
            casa = view.findViewById(R.id.time_casa);
            fora = view.findViewById(R.id.time_fora);
            placar = view.findViewById(R.id.placar);
            estadio = view.findViewById(R.id.estadio);
            canal = view.findViewById(R.id.canal);
            view.setOnFocusChangeListener((v, focado) -> {
                pintarPonto(focado);
                if (focado && jogoAtual != null) navegacao.aoFocar(jogoAtual, v.getId());
            });
            view.setOnKeyListener((v, keyCode, event) -> tecla(keyCode, event));
        }

        void preencher(JogoRodada jogo) {
            jogoAtual = jogo;
            itemView.setActivated(jogo.saoPaulo);
            quando.setText(Horario.jogo(jogo.kickoff, null));
            casa.setText(jogo.casaNome);
            fora.setText(jogo.foraNome);
            escudoCasa.setContentDescription(jogo.casaNome);
            escudoFora.setContentDescription(jogo.foraNome);
            escudos.carregar(escudoCasa, jogo.casaId, jogo.casaLogo);
            escudos.carregar(escudoFora, jogo.foraId, jogo.foraLogo);
            estadio.setText(jogo.venue.isEmpty()
                    ? itemView.getContext().getString(R.string.estadio_a_confirmar)
                    : jogo.venue);
            canal.setText(jogo.channels.isEmpty()
                    ? itemView.getContext().getString(R.string.canal_a_confirmar)
                    : TextUtils.join(" · ", jogo.channels));

            if (jogo.status == JogoRodada.Status.AO_VIVO) {
                linhaAoVivo.setVisibility(View.VISIBLE);
                selo.setText(jogo.minuto.isEmpty()
                        ? itemView.getContext().getString(R.string.ao_vivo_marca)
                        : itemView.getContext().getString(R.string.ao_vivo_minuto, jogo.minuto));
                pintarPonto(itemView.isFocused());
                mostrarPlacar(jogo);
            } else if (jogo.status == JogoRodada.Status.ENCERRADO) {
                linhaAoVivo.setVisibility(View.GONE);
                mostrarPlacar(jogo);
            } else {
                linhaAoVivo.setVisibility(View.GONE);
                placar.setVisibility(View.GONE);
            }
            if (itemView.isFocused()) navegacao.aoFocar(jogo, itemView.getId());
        }

        private void mostrarPlacar(JogoRodada jogo) {
            placar.setVisibility(View.VISIBLE);
            if (jogo.casaGols == null || jogo.foraGols == null) placar.setText("— × —");
            else placar.setText(jogo.casaGols + " × " + jogo.foraGols);
        }

        private void pintarPonto(boolean focado) {
            int cor = focado ? 0xFFFFFFFF : 0xFFC60000;
            ponto.setBackgroundTintList(ColorStateList.valueOf(cor));
        }

        private boolean tecla(int keyCode, KeyEvent event) {
            boolean dpad = keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                    || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                    || keyCode == KeyEvent.KEYCODE_DPAD_UP
                    || keyCode == KeyEvent.KEYCODE_DPAD_DOWN;
            if (!dpad) return false;
            if (event.getAction() == KeyEvent.ACTION_UP) return true;
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            int posicao = getBindingAdapterPosition();
            if (posicao == RecyclerView.NO_POSITION || jogoAtual == null) return true;
            int destino = -1;
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                if (posicao % COLUNAS == 0 && posicao + 1 < getItemCount()) destino = posicao + 1;
                else {
                    navegacao.abrirTabela(jogoAtual);
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (posicao % COLUNAS == 1) destino = posicao - 1;
                else return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (posicao + COLUNAS < getItemCount()) destino = posicao + COLUNAS;
                else {
                    navegacao.abaixo();
                    return true;
                }
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (posicao - COLUNAS >= 0) destino = posicao - COLUNAS;
                else {
                    navegacao.acima();
                    return true;
                }
            }
            if (destino >= 0) focar(destino);
            return true;
        }

        private void focar(int posicao) {
            ViewParent pai = itemView.getParent();
            if (!(pai instanceof RecyclerView)) return;
            RecyclerView lista = (RecyclerView) pai;
            RecyclerView.ViewHolder holder = lista.findViewHolderForAdapterPosition(posicao);
            if (holder != null) {
                holder.itemView.requestFocus();
                return;
            }
            lista.scrollToPosition(posicao);
            lista.post(() -> {
                RecyclerView.ViewHolder pronto = lista.findViewHolderForAdapterPosition(posicao);
                if (pronto != null) pronto.itemView.requestFocus();
            });
        }
    }
}
