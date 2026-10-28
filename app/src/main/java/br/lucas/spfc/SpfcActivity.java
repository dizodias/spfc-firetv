package br.lucas.spfc;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Tela cheia em duas colunas: próximos jogos do São Paulo e a tabela do Brasileirão.
 * Só fala com a ESPN enquanto está aberta. Não há serviço em segundo plano.
 */
public final class SpfcActivity extends Activity {
    private static final String TAG = "Spfc";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private SpfcRepository repositorio;
    private MatchesAdapter jogosAdapter;
    private TabelaFixa tabelaFixa;
    private View conteudo;
    private TextView mensagem;
    private TextView atualizado;
    private TextView semJogos;
    private TextView semTabela;
    private View cabecalho;
    private View legenda;
    private RecyclerView jogos;
    private LinearLayout tabela;
    private boolean carregando;
    private boolean jaFocou;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_spfc);
        repositorio = new SpfcRepository(getFilesDir());
        conteudo = findViewById(R.id.conteudo);
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

    private void mostrar(Snapshot snapshot, boolean offline) {
        mensagem.setVisibility(View.GONE);
        conteudo.setVisibility(View.VISIBLE);
        String hora = Horario.hora(snapshot.fetchedAtMillis);
        atualizado.setText(getString(offline ? R.string.sem_rede_cache : R.string.atualizado, hora));

        jogosAdapter.definir(snapshot.matches);
        tabelaFixa.definir(snapshot.standings);
        semJogos.setVisibility(snapshot.matches.isEmpty() ? View.VISIBLE : View.GONE);
        jogos.setVisibility(snapshot.matches.isEmpty() ? View.GONE : View.VISIBLE);
        boolean semClassificacao = snapshot.standings.isEmpty();
        semTabela.setVisibility(semClassificacao ? View.VISIBLE : View.GONE);
        tabela.setVisibility(semClassificacao ? View.GONE : View.VISIBLE);
        cabecalho.setVisibility(semClassificacao ? View.GONE : View.VISIBLE);
        legenda.setVisibility(semClassificacao ? View.GONE : View.VISIBLE);

        Log.i(TAG, "pronto jogos=" + snapshot.matches.size() + " tabela=" + snapshot.standings.size());
        if (!jaFocou) {
            if (snapshot.matches.isEmpty()) {
                tabela.post(this::focarPrimeiraLinha);
            } else {
                jogos.post(this::focarPrimeiroJogo);
            }
        }
    }
}
