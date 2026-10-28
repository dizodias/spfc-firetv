package br.lucas.spfc;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** Vinte linhas sem rolagem. Cada uma divide o que sobra da coluna. */
final class TabelaFixa {
    private final LayoutInflater inflater;
    private final LinearLayout linhas;
    private int focoCima;

    TabelaFixa(LayoutInflater inflater, LinearLayout linhas) {
        this.inflater = inflater;
        this.linhas = linhas;
    }

    void definirFocoCima(int id) {
        this.focoCima = id;
    }

    void pintarLegenda(View legenda) {
        tinta(legenda, R.id.bolinha_campeao, Faixas.COR_CAMPEAO);
        tinta(legenda, R.id.bolinha_libertadores, Faixas.COR_LIBERTADORES);
        tinta(legenda, R.id.bolinha_pre, Faixas.COR_PRE_LIBERTADORES);
        tinta(legenda, R.id.bolinha_sul, Faixas.COR_SUL_AMERICANA);
        tinta(legenda, R.id.bolinha_z4, Faixas.COR_REBAIXAMENTO);
    }

    void definir(List<StandingRow> times) {
        linhas.removeAllViews();
        for (int i = 0; i < times.size(); i++) {
            View linha = inflater.inflate(R.layout.item_tabela, linhas, false);
            linha.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
            ligar(linha, times.get(i), i == 0 ? focoCima : 0);
            linhas.addView(linha);
        }
    }

    private static void tinta(View raiz, int id, int cor) {
        raiz.findViewById(id).setBackgroundTintList(ColorStateList.valueOf(cor));
    }

    private static void ligar(View linha, StandingRow time, int focoCima) {
        Faixas.Tipo tipo = Faixas.de(time.rank);
        ((TextView) linha.findViewById(R.id.pos)).setText(Integer.toString(time.rank));
        TextView nome = linha.findViewById(R.id.time);
        nome.setText(time.team);
        nome.setTypeface(Typeface.create("sans-serif", time.isSpfc ? Typeface.BOLD : Typeface.NORMAL));
        ((TextView) linha.findViewById(R.id.jogos)).setText(Integer.toString(time.played));
        ((TextView) linha.findViewById(R.id.vitorias)).setText(Integer.toString(time.wins));
        ((TextView) linha.findViewById(R.id.empates)).setText(Integer.toString(time.draws));
        ((TextView) linha.findViewById(R.id.derrotas)).setText(Integer.toString(time.losses));
        ((TextView) linha.findViewById(R.id.gols)).setText(Integer.toString(time.goalsFor));
        ((TextView) linha.findViewById(R.id.saldo)).setText(time.gd);
        ((TextView) linha.findViewById(R.id.pontos)).setText(Integer.toString(time.pts));

        ImageView trofeu = linha.findViewById(R.id.trofeu);
        trofeu.setVisibility(tipo == Faixas.Tipo.CAMPEAO ? View.VISIBLE : View.INVISIBLE);
        ImageView escudo = linha.findViewById(R.id.escudo);
        escudo.setVisibility(time.isSpfc ? View.VISIBLE : View.GONE);

        linha.setNextFocusLeftId(R.id.jogos);
        if (focoCima != 0) linha.setNextFocusUpId(focoCima);
        else linha.setNextFocusUpId(View.NO_ID);
        linha.setOnFocusChangeListener((v, focado) -> pintar(v, tipo, focado));
        pintar(linha, tipo, linha.isFocused());
    }

    private static void pintar(View linha, Faixas.Tipo tipo, boolean focado) {
        linha.findViewById(R.id.barra).setBackgroundColor(Faixas.cor(tipo));
        int fundo = Faixas.fundo(tipo);
        if (focado) {
            fundo = fundo == 0 ? 0x33FFFFFF : (0x47 << 24) | (Faixas.cor(tipo) & 0x00FFFFFF);
        }
        linha.setBackgroundColor(fundo);
    }
}
