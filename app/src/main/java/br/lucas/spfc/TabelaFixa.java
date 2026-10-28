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
    private int retornoEsquerda = R.id.jogos;
    private String casaDestaque = "";
    private String foraDestaque = "";

    TabelaFixa(LayoutInflater inflater, LinearLayout linhas) {
        this.inflater = inflater;
        this.linhas = linhas;
    }

    void definirFocoCima(int id) {
        this.focoCima = id;
        if (linhas.getChildCount() > 0) {
            linhas.getChildAt(0).setNextFocusUpId(id != 0 ? id : View.NO_ID);
        }
    }

    void definirRetorno(int id) {
        retornoEsquerda = id == 0 ? R.id.jogos : id;
        for (int i = 0; i < linhas.getChildCount(); i++) {
            linhas.getChildAt(i).setNextFocusLeftId(retornoEsquerda);
        }
    }

    /** Marca os dois times do cartão em foco, sem cobrir a cor da faixa. */
    void destacar(String casa, String fora) {
        casaDestaque = casa == null ? "" : casa;
        foraDestaque = fora == null ? "" : fora;
        for (int i = 0; i < linhas.getChildCount(); i++) {
            View linha = linhas.getChildAt(i);
            Object tag = linha.getTag();
            if (!(tag instanceof StandingRow)) continue;
            StandingRow time = (StandingRow) tag;
            pintar(linha, Faixas.de(time.rank), linha.isFocused());
        }
    }

    boolean focar(String nome) {
        if (nome == null || nome.isEmpty()) return false;
        for (int i = 0; i < linhas.getChildCount(); i++) {
            View linha = linhas.getChildAt(i);
            Object tag = linha.getTag();
            if (!(tag instanceof StandingRow)) continue;
            if (Nomes.iguais(((StandingRow) tag).team, nome)) {
                linha.requestFocus();
                return true;
            }
        }
        return false;
    }

    boolean mesma(List<StandingRow> times) {
        if (times == null || linhas.getChildCount() != times.size()) return false;
        for (int i = 0; i < times.size(); i++) {
            Object tag = linhas.getChildAt(i).getTag();
            if (!(tag instanceof StandingRow)) return false;
            StandingRow atual = (StandingRow) tag;
            StandingRow novo = times.get(i);
            if (atual.rank != novo.rank || atual.pts != novo.pts || atual.played != novo.played
                    || atual.wins != novo.wins || atual.goalsFor != novo.goalsFor
                    || atual.isSpfc != novo.isSpfc || !atual.team.equals(novo.team)
                    || !atual.gd.equals(novo.gd)) {
                return false;
            }
        }
        return true;
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

    private void ligar(View linha, StandingRow time, int focoCimaLinha) {
        Faixas.Tipo tipo = Faixas.de(time.rank);
        linha.setTag(time);
        ((TextView) linha.findViewById(R.id.pos)).setText(Integer.toString(time.rank));
        ((TextView) linha.findViewById(R.id.time)).setText(time.team);
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

        linha.setNextFocusLeftId(retornoEsquerda);
        if (focoCimaLinha != 0) linha.setNextFocusUpId(focoCimaLinha);
        else linha.setNextFocusUpId(View.NO_ID);
        linha.setOnFocusChangeListener((v, focado) -> pintar(v, tipo, focado));
        pintar(linha, tipo, linha.isFocused());
    }

    private boolean marcado(String time) {
        return Nomes.iguais(time, casaDestaque) || Nomes.iguais(time, foraDestaque);
    }

    private void pintar(View linha, Faixas.Tipo tipo, boolean focado) {
        Object tag = linha.getTag();
        StandingRow time = tag instanceof StandingRow ? (StandingRow) tag : null;
        boolean marca = time != null && marcado(time.team);
        if (time != null) {
            TextView nome = linha.findViewById(R.id.time);
            nome.setTypeface(Typeface.create("sans-serif",
                    (time.isSpfc || marca) ? Typeface.BOLD : Typeface.NORMAL));
        }
        linha.findViewById(R.id.barra).setBackgroundColor(Faixas.cor(tipo));
        int fundo = Faixas.fundo(tipo);
        if (focado) {
            fundo = fundo == 0 ? 0x33FFFFFF : (0x47 << 24) | (Faixas.cor(tipo) & 0x00FFFFFF);
        } else if (marca) {
            fundo = fundo == 0 ? 0x33FFFFFF : (0x55 << 24) | (fundo & 0x00FFFFFF);
        }
        linha.setBackgroundColor(fundo);
    }
}
