package br.lucas.spfc;

import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Preenche o cartão de resumo. Um único foco no D-pad. */
final class ResumoPainel {
    private final LayoutInflater inflater;
    private final View raiz;
    private final TextView jogos;
    private final TextView vitorias;
    private final TextView empates;
    private final TextView derrotas;
    private final TextView golsPro;
    private final TextView golsContra;
    private final TextView saldo;
    private final TextView aproveitamento;
    private final TextView ultimosTitulo;
    private final LinearLayout ultimos;
    private final TextView maior;
    private final TextView pior;
    private final View blocoLigas;
    private final LinearLayout ligas;

    ResumoPainel(LayoutInflater inflater, View raiz) {
        this.inflater = inflater;
        this.raiz = raiz;
        jogos = raiz.findViewById(R.id.resumo_jogos);
        vitorias = raiz.findViewById(R.id.resumo_vitorias);
        empates = raiz.findViewById(R.id.resumo_empates);
        derrotas = raiz.findViewById(R.id.resumo_derrotas);
        golsPro = raiz.findViewById(R.id.resumo_gp);
        golsContra = raiz.findViewById(R.id.resumo_gc);
        saldo = raiz.findViewById(R.id.resumo_sg);
        aproveitamento = raiz.findViewById(R.id.resumo_aproveitamento);
        ultimosTitulo = raiz.findViewById(R.id.resumo_ultimos_titulo);
        ultimos = raiz.findViewById(R.id.resumo_ultimos);
        maior = raiz.findViewById(R.id.resumo_maior);
        pior = raiz.findViewById(R.id.resumo_pior);
        blocoLigas = raiz.findViewById(R.id.resumo_bloco_ligas);
        ligas = raiz.findViewById(R.id.resumo_ligas);
    }

    void definirFocoCima(int id) {
        raiz.setNextFocusUpId(id == 0 ? View.NO_ID : id);
    }

    void definir(ResumoTemporada resumo, boolean mostraLigas) {
        jogos.setText(Integer.toString(resumo.jogos));
        vitorias.setText(Integer.toString(resumo.vitorias));
        empates.setText(Integer.toString(resumo.empates));
        derrotas.setText(Integer.toString(resumo.derrotas));
        golsPro.setText(Integer.toString(resumo.golsPro));
        golsContra.setText(Integer.toString(resumo.golsContra));
        saldo.setText(resumo.saldoTexto());
        int corSaldo = R.color.branco;
        if (resumo.saldo > 0) corSaldo = R.color.vitoria;
        else if (resumo.saldo < 0) corSaldo = R.color.derrota;
        saldo.setTextColor(raiz.getContext().getColor(corSaldo));
        aproveitamento.setText(resumo.aproveitamentoTexto());

        pintarForma(resumo.ultimos5);
        maior.setText(destaque(resumo.maiorVitoria, R.string.resumo_nenhuma_vitoria));
        pior.setText(destaque(resumo.piorDerrota, R.string.resumo_nenhuma_derrota));

        boolean temLigas = mostraLigas && !resumo.ligas.isEmpty();
        blocoLigas.setVisibility(temLigas ? View.VISIBLE : View.GONE);
        ligas.removeAllViews();
        if (temLigas) {
            for (int i = 0; i < resumo.ligas.size(); i++) {
                ResumoTemporada.LinhaLiga linha = resumo.ligas.get(i);
                View item = inflater.inflate(R.layout.item_resumo_liga, ligas, false);
                ((TextView) item.findViewById(R.id.liga_nome)).setText(linha.nome);
                ((TextView) item.findViewById(R.id.liga_j)).setText(Integer.toString(linha.jogos));
                ((TextView) item.findViewById(R.id.liga_v)).setText(Integer.toString(linha.vitorias));
                ((TextView) item.findViewById(R.id.liga_e)).setText(Integer.toString(linha.empates));
                ((TextView) item.findViewById(R.id.liga_d)).setText(Integer.toString(linha.derrotas));
                ligas.addView(item);
            }
        }
    }

    private void pintarForma(java.util.List<Match.Resultado> forma) {
        ultimos.removeAllViews();
        boolean vazio = forma.isEmpty();
        ultimosTitulo.setVisibility(vazio ? View.GONE : View.VISIBLE);
        ultimos.setVisibility(vazio ? View.GONE : View.VISIBLE);
        if (vazio) return;
        int tamanho = dp(28);
        for (int i = 0; i < forma.size(); i++) {
            Match.Resultado resultado = forma.get(i);
            TextView bola = new TextView(raiz.getContext());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(tamanho, tamanho);
            if (i > 0) params.setMarginStart(dp(6));
            bola.setLayoutParams(params);
            bola.setGravity(Gravity.CENTER);
            bola.setIncludeFontPadding(false);
            bola.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            bola.setTextSize(14);
            bola.setBackgroundResource(R.drawable.bolinha_resultado);
            int fundo;
            int letra;
            String texto;
            if (resultado == Match.Resultado.VITORIA) {
                fundo = R.color.vitoria;
                letra = R.color.branco;
                texto = "V";
            } else if (resultado == Match.Resultado.DERROTA) {
                fundo = R.color.derrota;
                letra = R.color.branco;
                texto = "D";
            } else {
                fundo = R.color.empate_bola;
                letra = R.color.fundo;
                texto = "E";
            }
            bola.getBackground().mutate().setTintList(
                    ColorStateList.valueOf(raiz.getContext().getColor(fundo)));
            bola.setTextColor(raiz.getContext().getColor(letra));
            bola.setText(texto);
            bola.setFocusable(false);
            ultimos.addView(bola);
        }
    }

    private String destaque(Match jogo, int vazio) {
        if (jogo == null || jogo.goalsFor == null || jogo.goalsAgainst == null) {
            return raiz.getContext().getString(vazio);
        }
        return raiz.getContext().getString(
                R.string.resumo_placar, jogo.opponent, jogo.goalsFor, jogo.goalsAgainst);
    }

    private int dp(int valor) {
        return Math.round(valor * raiz.getResources().getDisplayMetrics().density);
    }
}
