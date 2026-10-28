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

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tela cheia com abas no D-pad: próximos, rodada, resultados e uma aba por competição.
 * Só fala com a rede enquanto está aberta. Não há serviço em segundo plano.
 */
public final class SpfcActivity extends Activity {
    private static final String TAG = "Spfc";
    private static final String ABA_PROXIMOS = "proximos";
    private static final String ABA_RODADA = "rodada";
    private static final String ABA_RESULTADOS = "resultados";
    private static final long PULSO_AO_VIVO_MS = 60_000L;

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService imagens = Executors.newFixedThreadPool(2);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable pulsoAoVivo = this::pulsarRodada;
    private final List<TextView> botoesAba = new ArrayList<>();
    private final List<String> idsAbas = new ArrayList<>();
    private SpfcRepository repositorio;
    private MatchesAdapter jogosAdapter;
    private RodadaAdapter rodadaAdapter;
    private TabelaFixa tabelaFixa;
    private ResumoPainel resumoPainel;
    private Snapshot snapshot;
    private JogoRodada jogoEmFoco;
    private View conteudo;
    private LinearLayout abas;
    private LinearLayout miolo;
    private LinearLayout colunaJogos;
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
    private RecyclerView grade;
    private LinearLayout tabela;
    private String abaAtual = ABA_PROXIMOS;
    private boolean carregando;
    private boolean jaFocou;
    private boolean visivel;
    private boolean empilhado;
    private boolean colunasDefinidas;
    private boolean colunasDaRodada;
    private boolean colunasEmpilhadas;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_spfc);
        repositorio = new SpfcRepository(getFilesDir());
        conteudo = findViewById(R.id.conteudo);
        abas = findViewById(R.id.abas);
        miolo = findViewById(R.id.miolo);
        colunaJogos = findViewById(R.id.coluna_jogos);
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
        grade = findViewById(R.id.rodada);
        tabela = findViewById(R.id.tabela);

        jogosAdapter = new MatchesAdapter();
        rodadaAdapter = new RodadaAdapter(new Escudos(getFilesDir(), imagens), new RodadaAdapter.Navegacao() {
            @Override
            public void aoFocar(JogoRodada jogo, int viewId) {
                jogoEmFoco = jogo;
                tabelaFixa.destacar(jogo.casaNome, jogo.foraNome);
                tabelaFixa.definirRetorno(viewId);
            }

            @Override
            public void abrirTabela(JogoRodada jogo) {
                if (!tabelaFixa.focar(jogo.casaNome) && tabela.getChildCount() > 0) {
                    tabela.getChildAt(0).requestFocus();
                }
            }

            @Override
            public void acima() {
                focarAbaAtual();
            }

            @Override
            public void abaixo() {
                abaixoDaGrade();
            }
        });
        tabelaFixa = new TabelaFixa(getLayoutInflater(), tabela);
        tabelaFixa.pintarLegenda(legenda);
        resumoPainel = new ResumoPainel(getLayoutInflater(), resumo);
        jogos.setLayoutManager(new LinearLayoutManager(this));
        jogos.setAdapter(jogosAdapter);
        jogos.setItemAnimator(null);
        grade.setLayoutManager(new GridLayoutManager(this, 2));
        grade.setAdapter(rodadaAdapter);
        grade.setItemAnimator(null);

        findViewById(R.id.atualizar).setOnClickListener(v -> carregar(true));
    }

    @Override
    protected void onResume() {
        super.onResume();
        visivel = true;
        carregar(false);
    }

    @Override
    protected void onPause() {
        visivel = false;
        main.removeCallbacks(pulsoAoVivo);
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        visivel = false;
        main.removeCallbacks(pulsoAoVivo);
        super.onDestroy();
        io.shutdownNow();
        imagens.shutdownNow();
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
            boolean soRodada = !vaiBuscar && cache != null
                    && (cache.temAoVivo() || !cache.rodadaOk);
            main.post(() -> {
                if (isDestroyed()) return;
                if (cache != null) {
                    mostrar(cache, false);
                    if (vaiBuscar) atualizado.setText(R.string.atualizando);
                } else if (vaiBuscar) {
                    mensagem.setText(R.string.carregando);
                }
            });
            if (!vaiBuscar && !soRodada) {
                main.post(() -> {
                    if (isDestroyed()) return;
                    liberar();
                    agendarPulso(cache);
                });
                return;
            }
            try {
                Snapshot novo = vaiBuscar
                        ? repositorio.atualizar(cache)
                        : repositorio.atualizarRodada(cache);
                main.post(() -> {
                    if (isDestroyed()) return;
                    mostrar(novo, false);
                    liberar();
                    agendarPulso(novo);
                });
            } catch (IOException e) {
                Log.w(TAG, "falha ao atualizar", e);
                main.post(() -> {
                    if (isDestroyed()) return;
                    if (cache != null) {
                        Snapshot mostrado = cache.rodada.isEmpty()
                                ? new Snapshot(cache.matches, cache.standings, cache.rodada,
                                        cache.fetchedAtMillis, false)
                                : cache;
                        mostrar(mostrado, vaiBuscar);
                        agendarPulso(mostrado);
                    } else {
                        mensagem.setText(R.string.sem_rede);
                        mensagem.setVisibility(View.VISIBLE);
                        conteudo.setVisibility(View.GONE);
                    }
                    liberar();
                });
            }
        });
    }

    private void pulsarRodada() {
        if (!visivel || isDestroyed()) return;
        if (carregando) {
            agendarPulso(snapshot);
            return;
        }
        Snapshot base = snapshot;
        if (base == null || !base.temAoVivo()) return;
        carregando = true;
        io.execute(() -> {
            try {
                Snapshot novo = repositorio.atualizarRodada(base);
                main.post(() -> {
                    if (isDestroyed()) return;
                    publicarPulso(novo);
                    liberar();
                    if (visivel) agendarPulso(novo);
                });
            } catch (IOException e) {
                Log.w(TAG, "rodada ao vivo", e);
                main.post(() -> {
                    if (isDestroyed()) return;
                    liberar();
                    if (visivel) agendarPulso(snapshot);
                });
            }
        });
    }

    private void agendarPulso(Snapshot dados) {
        main.removeCallbacks(pulsoAoVivo);
        if (!visivel || dados == null || !dados.temAoVivo()) return;
        main.postDelayed(pulsoAoVivo, PULSO_AO_VIVO_MS);
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
        Log.i(TAG, "pronto jogos=" + novo.matches.size()
                + " rodada=" + novo.rodada.size()
                + " tabela=" + novo.standings.size());
        if (!jaFocou) {
            jogos.post(this::focarInicio);
        }
    }

    private void publicarPulso(Snapshot novo) {
        Snapshot anterior = snapshot;
        snapshot = novo;
        mensagem.setVisibility(View.GONE);
        conteudo.setVisibility(View.VISIBLE);
        if (novo.rodadaOk) {
            atualizado.setText(getString(R.string.atualizado, Horario.hora(System.currentTimeMillis())));
        }
        boolean estrutura = anterior == null
                || !tabelaIgual(anterior.standings, novo.standings)
                || !jogosIguais(anterior.matches, novo.matches);
        if (estrutura || ABA_RODADA.equals(abaAtual)) aplicarAba();
        Log.i(TAG, "rodada ao vivo jogos=" + novo.rodada.size());
    }

    private void garantirAbas(Snapshot dados) {
        List<String> novos = new ArrayList<>();
        novos.add(ABA_PROXIMOS);
        novos.add(ABA_RODADA);
        novos.add(ABA_RESULTADOS);
        List<Competicao.Aba> ligas = Competicao.abas(dados.matches);
        for (int i = 0; i < ligas.size(); i++) novos.add(ligas.get(i).slug);
        if (novos.equals(idsAbas) && !botoesAba.isEmpty()) return;

        idsAbas.clear();
        idsAbas.addAll(novos);
        abas.removeAllViews();
        botoesAba.clear();
        adicionarAba(ABA_PROXIMOS, getString(R.string.proximos));
        adicionarAba(ABA_RODADA, getString(R.string.rodada));
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
        if (ABA_RODADA.equals(id) && snapshot != null && !snapshot.rodadaOk) carregar(false);
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
        boolean rodada = ABA_RODADA.equals(abaAtual);
        boolean resultados = ABA_RESULTADOS.equals(abaAtual);
        boolean mostraTabela = proximos || rodada || Competicao.SLUG_BRASILEIRAO.equals(abaAtual);
        boolean mostraResumo = !mostraTabela;
        int focoCima = idAbaAtual();
        ajustarColunas(rodada);

        if (rodada) {
            mostrarLista(jogos, false);
            List<JogoRodada> lista = snapshot.rodada;
            int posicaoFocada = posicaoFocadaNaGrade();
            rodadaAdapter.definir(lista);
            semJogos.setText(lista.isEmpty() && !snapshot.rodadaOk
                    ? R.string.sem_rodada_rede
                    : R.string.sem_rodada);
            semJogos.setVisibility(lista.isEmpty() ? View.VISIBLE : View.GONE);
            mostrarLista(grade, !lista.isEmpty());
            if (posicaoFocada >= 0 && !lista.isEmpty()) {
                int alvo = Math.min(posicaoFocada, lista.size() - 1);
                grade.post(() -> restaurarFoco(alvo));
            }
            if (!contemJogo(lista, jogoEmFoco)) {
                jogoEmFoco = null;
                tabelaFixa.destacar("", "");
                tabelaFixa.definirRetorno(R.id.rodada);
            } else {
                tabelaFixa.destacar(jogoEmFoco.casaNome, jogoEmFoco.foraNome);
            }
        } else {
            mostrarLista(grade, false);
            jogoEmFoco = null;
            tabelaFixa.destacar("", "");
            tabelaFixa.definirRetorno(R.id.jogos);
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
            mostrarLista(jogos, !lista.isEmpty());
        }

        colunaTabela.setVisibility(View.VISIBLE);
        blocoTabela.setVisibility(mostraTabela ? View.VISIBLE : View.GONE);
        resumo.setVisibility(mostraResumo ? View.VISIBLE : View.GONE);
        if (mostraTabela) {
            int cimaTabela = rodada && empilhado ? R.id.rodada : focoCima;
            tabelaFixa.definirFocoCima(cimaTabela);
            if (!tabelaFixa.mesma(snapshot.standings)) tabelaFixa.definir(snapshot.standings);
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
        if (grade.getVisibility() == View.VISIBLE) baixo = R.id.rodada;
        else if (jogos.getVisibility() == View.VISIBLE) baixo = R.id.jogos;
        else if (mostraTabela && tabela.getVisibility() == View.VISIBLE) baixo = R.id.tabela;
        else if (mostraResumo) baixo = R.id.resumo;
        else baixo = R.id.atualizar;
        for (int i = 0; i < botoesAba.size(); i++) {
            botoesAba.get(i).setNextFocusDownId(baixo);
        }
        int cima = focoCima == 0 ? View.NO_ID : focoCima;
        jogos.setNextFocusUpId(cima);
        grade.setNextFocusUpId(cima);
        int cimaAtualizar;
        if (grade.getVisibility() == View.VISIBLE) cimaAtualizar = R.id.rodada;
        else if (jogos.getVisibility() == View.VISIBLE) cimaAtualizar = R.id.jogos;
        else if (mostraResumo) cimaAtualizar = R.id.resumo;
        else if (mostraTabela && tabela.getVisibility() == View.VISIBLE) cimaAtualizar = R.id.tabela;
        else cimaAtualizar = View.NO_ID;
        findViewById(R.id.atualizar).setNextFocusUpId(cimaAtualizar);
    }

    /** 1080p fica com a tabela à direita. Tela estreita empilha a grade e a tabela. */
    private void ajustarColunas(boolean rodada) {
        boolean empilhar = rodada && getResources().getConfiguration().screenWidthDp < 800;
        if (colunasDefinidas && rodada == colunasDaRodada && empilhar == colunasEmpilhadas) return;
        colunasDefinidas = true;
        colunasDaRodada = rodada;
        colunasEmpilhadas = empilhar;
        empilhado = empilhar;
        miolo.setOrientation(empilhar ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);

        LinearLayout.LayoutParams lpJogos = (LinearLayout.LayoutParams) colunaJogos.getLayoutParams();
        LinearLayout.LayoutParams lpTabela = (LinearLayout.LayoutParams) colunaTabela.getLayoutParams();
        if (empilhar) {
            lpJogos.width = LinearLayout.LayoutParams.MATCH_PARENT;
            lpJogos.height = 0;
            lpJogos.weight = 55f;
            lpTabela.width = LinearLayout.LayoutParams.MATCH_PARENT;
            lpTabela.height = 0;
            lpTabela.weight = 45f;
            lpTabela.setMarginStart(0);
        } else {
            lpJogos.width = 0;
            lpJogos.height = LinearLayout.LayoutParams.MATCH_PARENT;
            lpJogos.weight = rodada ? 58f : 50f;
            lpTabela.width = 0;
            lpTabela.height = LinearLayout.LayoutParams.MATCH_PARENT;
            lpTabela.weight = rodada ? 42f : 50f;
            lpTabela.setMarginStart(dp(rodada ? 8 : 12));
        }
        colunaJogos.setLayoutParams(lpJogos);
        colunaTabela.setLayoutParams(lpTabela);
    }

    private void mostrarLista(RecyclerView lista, boolean mostra) {
        int visibilidade = mostra ? View.VISIBLE : View.GONE;
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) lista.getLayoutParams();
        float peso = mostra ? 1f : 0f;
        if (lista.getVisibility() == visibilidade && lp.height == 0 && lp.weight == peso) return;
        lista.setVisibility(visibilidade);
        lp.height = 0;
        lp.weight = peso;
        lista.setLayoutParams(lp);
    }

    private int posicaoFocadaNaGrade() {
        View foco = getCurrentFocus();
        if (foco == null) return -1;
        RecyclerView.ViewHolder holder = grade.findContainingViewHolder(foco);
        if (holder == null) return -1;
        return holder.getBindingAdapterPosition();
    }

    private void restaurarFoco(int alvo) {
        View foco = getCurrentFocus();
        boolean perdido = foco == null;
        boolean naGrade = foco != null && grade.findContainingViewHolder(foco) != null;
        if (!perdido && !naGrade) return;
        RecyclerView.ViewHolder holder = grade.findViewHolderForAdapterPosition(alvo);
        if (holder != null && !holder.itemView.isFocused()) holder.itemView.requestFocus();
    }

    private void abaixoDaGrade() {
        if (empilhado && tabela.getVisibility() == View.VISIBLE && tabela.getChildCount() > 0) {
            if (jogoEmFoco == null || !tabelaFixa.focar(jogoEmFoco.casaNome)) {
                tabela.getChildAt(0).requestFocus();
            }
            return;
        }
        findViewById(R.id.atualizar).requestFocus();
    }

    private void focarAbaAtual() {
        for (int i = 0; i < botoesAba.size(); i++) {
            if (abaAtual.equals(botoesAba.get(i).getTag())) {
                botoesAba.get(i).requestFocus();
                return;
            }
        }
    }

    private int idAbaAtual() {
        for (int i = 0; i < botoesAba.size(); i++) {
            if (abaAtual.equals(botoesAba.get(i).getTag())) return botoesAba.get(i).getId();
        }
        return botoesAba.isEmpty() ? 0 : botoesAba.get(0).getId();
    }

    private static boolean abaValida(String id, Snapshot dados) {
        if (ABA_PROXIMOS.equals(id) || ABA_RODADA.equals(id) || ABA_RESULTADOS.equals(id)) return true;
        List<Competicao.Aba> ligas = Competicao.abas(dados.matches);
        for (int i = 0; i < ligas.size(); i++) {
            if (id.equals(ligas.get(i).slug)) return true;
        }
        return false;
    }

    private static boolean contemJogo(List<JogoRodada> lista, JogoRodada jogo) {
        if (jogo == null) return false;
        for (int i = 0; i < lista.size(); i++) {
            if (jogo.id.equals(lista.get(i).id)) return true;
        }
        return false;
    }

    private static boolean tabelaIgual(List<StandingRow> a, List<StandingRow> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            StandingRow x = a.get(i);
            StandingRow y = b.get(i);
            if (x.rank != y.rank || x.pts != y.pts || x.played != y.played
                    || x.isSpfc != y.isSpfc || !x.team.equals(y.team)) {
                return false;
            }
        }
        return true;
    }

    private static boolean jogosIguais(List<Match> a, List<Match> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            Match x = a.get(i);
            Match y = b.get(i);
            if (x.finished != y.finished || x.live != y.live) return false;
            if (!x.opponent.equals(y.opponent) || !x.kickoffInstant.equals(y.kickoffInstant)) return false;
            if (!iguais(x.goalsFor, y.goalsFor) || !iguais(x.goalsAgainst, y.goalsAgainst)) return false;
        }
        return true;
    }

    private static boolean iguais(Integer a, Integer b) {
        return a == null ? b == null : a.equals(b);
    }

    private void focarInicio() {
        if (jaFocou) return;
        if (ABA_RODADA.equals(abaAtual) && grade.getVisibility() == View.VISIBLE) {
            RecyclerView.ViewHolder holder = grade.findViewHolderForAdapterPosition(0);
            if (holder == null) return;
            jaFocou = true;
            holder.itemView.requestFocus();
            return;
        }
        if (jogos.getVisibility() == View.VISIBLE) {
            focarPrimeiroJogo();
            return;
        }
        focarPrimeiraLinha();
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
