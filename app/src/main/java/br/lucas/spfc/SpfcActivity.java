package br.lucas.spfc;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tela cheia com abas no D-pad: próximos, resultados e uma aba por competição.
 * Só fala com a rede enquanto está aberta. Não há serviço em segundo plano.
 */
public final class SpfcActivity extends Activity {
    private static final String TAG = "Spfc";
    private static final String ABA_PROXIMOS = "proximos";
    private static final String ABA_RESULTADOS = "resultados";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<TextView> botoesAba = new ArrayList<>();
    private final List<String> idsAbas = new ArrayList<>();
    private SpfcRepository repositorio;
    private MatchesAdapter jogosAdapter;
    private TabelaFixa tabelaFixa;
    private ResumoPainel resumoPainel;
    private Snapshot snapshot;
    private View conteudo;
    private LinearLayout abas;
    private LinearLayout colunaTabela;
    private View blocoTabela;
    private View resumo;
    private TextView mensagem;
    private TextView atualizado;
    private TextView semJogos;
    private TextView semTabela;
    private View cabecalho;
    private View legenda;
    private RecyclerView jogos;
    private LinearLayout tabela;
    private String abaAtual = ABA_PROXIMOS;
    private boolean carregando;
    private boolean jaFocou;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_spfc);
        repositorio = new SpfcRepository(getFilesDir());
        conteudo = findViewById(R.id.conteudo);
        abas = findViewById(R.id.abas);
        colunaTabela = findViewById(R.id.coluna_tabela);
        blocoTabela = findViewById(R.id.bloco_tabela);
        resumo = findViewById(R.id.resumo);
        mensagem = findViewById(R.id.mensagem);
        atualizado = findViewById(R.id.atualizado);
        semJogos = findViewById(R.id.sem_jogos);
        semTabela = findViewById(R.id.sem_tabela);
        cabecalho = findViewById(R.id.cabecalho);
        legenda = findViewById(R.id.legenda);
        jogos = findViewById(R.id.jogos);
        tabela = findViewById(R.id.tabela);

        jogosAdapter = new MatchesAdapter();
        tabelaFixa = new TabelaFixa(getLayoutInflater(), tabela);
        tabelaFixa.pintarLegenda(legenda);
        resumoPainel = new ResumoPainel(getLayoutInflater(), resumo);
        jogos.setLayoutManager(new LinearLayoutManager(this));
        jogos.setAdapter(jogosAdapter);
        jogos.setItemAnimator(null);

        findViewById(R.id.atualizar).setOnClickListener(v -> carregar(true));
    }

    @Override
    protected void onResume() {
        super.onResume();
        carregar(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        io.shutdownNow();
    }

    private void carregar(boolean forcar) {
        if (carregando) return;
        carregando = true;
        if (forcar && conteudo.getVisibility() == View.VISIBLE) {
            atualizado.setText(R.string.atualizando);
        }
        io.execute(() -> {
            Snapshot cache = repositorio.ler();
            boolean fresco = repositorio.fresco(cache, System.currentTimeMillis());
            boolean vaiBuscar = forcar || !fresco;
            main.post(() -> {
                if (isDestroyed()) return;
                if (cache != null) {
                    mostrar(cache, false);
                    if (vaiBuscar) atualizado.setText(R.string.atualizando);
                } else {
                    mensagem.setText(R.string.carregando);
                }
            });
            if (!vaiBuscar) {
                main.post(this::liberar);
                return;
            }
            try {
                Snapshot novo = repositorio.atualizar();
                main.post(() -> {
                    if (isDestroyed()) return;
                    mostrar(novo, false);
                    liberar();
                });
            } catch (IOException e) {
                Log.w(TAG, "falha ao atualizar", e);
                main.post(() -> {
                    if (isDestroyed()) return;
                    if (cache != null) mostrar(cache, true);
                    else {
                        mensagem.setText(R.string.sem_rede);
                        mensagem.setVisibility(View.VISIBLE);
                        conteudo.setVisibility(View.GONE);
                    }
                    liberar();
                });
            }
        });
    }

    private void liberar() {
        carregando = false;
    }

    private void mostrar(Snapshot novo, boolean offline) {
        snapshot = novo;
        mensagem.setVisibility(View.GONE);
        conteudo.setVisibility(View.VISIBLE);
        String hora = Horario.hora(novo.fetchedAtMillis);
        atualizado.setText(getString(offline ? R.string.sem_rede_cache : R.string.atualizado, hora));
        garantirAbas(novo);
        if (!abaValida(abaAtual, novo)) abaAtual = ABA_PROXIMOS;
        pintarAbas();
        aplicarAba();
        Log.i(TAG, "pronto jogos=" + novo.matches.size() + " tabela=" + novo.standings.size());
        if (!jaFocou) {
            jogos.post(() -> {
                if (jogos.getVisibility() == View.VISIBLE) focarPrimeiroJogo();
                else focarPrimeiraLinha();
            });
        }
    }

    private void garantirAbas(Snapshot dados) {
        List<String> novos = new ArrayList<>();
        novos.add(ABA_PROXIMOS);
        novos.add(ABA_RESULTADOS);
        List<Competicao.Aba> ligas = Competicao.abas(dados.matches);
        for (int i = 0; i < ligas.size(); i++) novos.add(ligas.get(i).slug);
        if (novos.equals(idsAbas) && !botoesAba.isEmpty()) return;

        idsAbas.clear();
        idsAbas.addAll(novos);
        abas.removeAllViews();
        botoesAba.clear();
        adicionarAba(ABA_PROXIMOS, getString(R.string.proximos));
        adicionarAba(ABA_RESULTADOS, getString(R.string.resultados));
        for (int i = 0; i < ligas.size(); i++) {
            Competicao.Aba liga = ligas.get(i);
            adicionarAba(liga.slug, liga.nome);
        }
        ligarFocoAbas();
    }

    private void adicionarAba(String id, String titulo) {
        TextView botao = new TextView(this);
        botao.setId(View.generateViewId());
        botao.setTag(id);
        botao.setText(titulo);
        botao.setFocusable(true);
        botao.setFocusableInTouchMode(true);
        botao.setBackgroundResource(R.drawable.aba);
        botao.setTextColor(getResources().getColorStateList(R.color.texto_botao, getTheme()));
        botao.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        botao.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        botao.setPadding(dp(16), dp(8), dp(16), dp(8));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        if (!botoesAba.isEmpty()) params.setMarginStart(dp(8));
        botao.setLayoutParams(params);
        botao.setOnFocusChangeListener((v, focado) -> {
            if (focado) selecionarAba((String) v.getTag());
        });
        botao.setOnClickListener(v -> {
            v.requestFocus();
            selecionarAba((String) v.getTag());
        });
        abas.addView(botao);
        botoesAba.add(botao);
    }

    private void ligarFocoAbas() {
        for (int i = 0; i < botoesAba.size(); i++) {
            TextView aba = botoesAba.get(i);
            if (i > 0) aba.setNextFocusLeftId(botoesAba.get(i - 1).getId());
            else aba.setNextFocusLeftId(aba.getId());
            if (i < botoesAba.size() - 1) aba.setNextFocusRightId(botoesAba.get(i + 1).getId());
            else aba.setNextFocusRightId(aba.getId());
            aba.setNextFocusDownId(R.id.jogos);
        }
    }

    private void selecionarAba(String id) {
        if (id == null || id.equals(abaAtual)) {
            pintarAbas();
            return;
        }
        abaAtual = id;
        pintarAbas();
        aplicarAba();
    }

    private void pintarAbas() {
        for (int i = 0; i < botoesAba.size(); i++) {
            TextView botao = botoesAba.get(i);
            botao.setActivated(abaAtual.equals(botao.getTag()));
        }
    }

    private void aplicarAba() {
        if (snapshot == null) return;
        boolean proximos = ABA_PROXIMOS.equals(abaAtual);
        boolean resultados = ABA_RESULTADOS.equals(abaAtual);
        boolean mostraTabela = proximos || Competicao.SLUG_BRASILEIRAO.equals(abaAtual);
        boolean mostraResumo = !mostraTabela;
        int focoCima = idAbaAtual();

        List<Match> lista;
        boolean esconderChip;
        int vazio;
        if (proximos) {
            lista = snapshot.proximos();
            esconderChip = false;
            vazio = R.string.sem_jogos;
        } else if (resultados) {
            lista = snapshot.resultados();
            esconderChip = false;
            vazio = R.string.sem_resultados;
        } else {
            lista = snapshot.daCompeticao(abaAtual);
            esconderChip = true;
            vazio = R.string.sem_jogos_competicao;
        }

        jogosAdapter.definir(lista, esconderChip, focoCima, mostraResumo ? R.id.resumo : 0);
        semJogos.setText(vazio);
        semJogos.setVisibility(lista.isEmpty() ? View.VISIBLE : View.GONE);
        jogos.setVisibility(lista.isEmpty() ? View.GONE : View.VISIBLE);

        colunaTabela.setVisibility(View.VISIBLE);
        blocoTabela.setVisibility(mostraTabela ? View.VISIBLE : View.GONE);
        resumo.setVisibility(mostraResumo ? View.VISIBLE : View.GONE);
        if (mostraTabela) {
            tabelaFixa.definirFocoCima(focoCima);
            tabelaFixa.definir(snapshot.standings);
            boolean semClassificacao = snapshot.standings.isEmpty();
            semTabela.setVisibility(semClassificacao ? View.VISIBLE : View.GONE);
            tabela.setVisibility(semClassificacao ? View.GONE : View.VISIBLE);
            cabecalho.setVisibility(semClassificacao ? View.GONE : View.VISIBLE);
            legenda.setVisibility(semClassificacao ? View.GONE : View.VISIBLE);
        }
        if (mostraResumo) {
            List<Match> base = resultados ? snapshot.resultados() : snapshot.daCompeticao(abaAtual);
            resumoPainel.definirFocoCima(focoCima);
            resumoPainel.definir(ResumoTemporada.de(base), resultados);
        }

        int baixo;
        if (!lista.isEmpty()) baixo = R.id.jogos;
        else if (mostraTabela && tabela.getVisibility() == View.VISIBLE) baixo = R.id.tabela;
        else if (mostraResumo) baixo = R.id.resumo;
        else baixo = R.id.atualizar;
        for (int i = 0; i < botoesAba.size(); i++) {
            botoesAba.get(i).setNextFocusDownId(baixo);
        }
        jogos.setNextFocusUpId(focoCima == 0 ? View.NO_ID : focoCima);
        int cimaAtualizar = !lista.isEmpty()
                ? R.id.jogos
                : (mostraResumo ? R.id.resumo : (mostraTabela ? R.id.tabela : View.NO_ID));
        findViewById(R.id.atualizar).setNextFocusUpId(cimaAtualizar);
    }

    private int idAbaAtual() {
        for (int i = 0; i < botoesAba.size(); i++) {
            if (abaAtual.equals(botoesAba.get(i).getTag())) return botoesAba.get(i).getId();
        }
        return botoesAba.isEmpty() ? 0 : botoesAba.get(0).getId();
    }

    private static boolean abaValida(String id, Snapshot dados) {
        if (ABA_PROXIMOS.equals(id) || ABA_RESULTADOS.equals(id)) return true;
        List<Competicao.Aba> ligas = Competicao.abas(dados.matches);
        for (int i = 0; i < ligas.size(); i++) {
            if (id.equals(ligas.get(i).slug)) return true;
        }
        return false;
    }

    private void focarPrimeiroJogo() {
        if (jaFocou) return;
        RecyclerView.ViewHolder holder = jogos.findViewHolderForAdapterPosition(0);
        if (holder == null) return;
        jaFocou = true;
        holder.itemView.requestFocus();
    }

    private void focarPrimeiraLinha() {
        if (jaFocou || tabela.getChildCount() == 0) return;
        jaFocou = true;
        tabela.getChildAt(0).requestFocus();
    }

    private int dp(int valor) {
        return Math.round(valor * getResources().getDisplayMetrics().density);
    }
}
