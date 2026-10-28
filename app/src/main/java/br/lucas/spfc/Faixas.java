package br.lucas.spfc;

/**
 * Vagas do Brasileirão. Os intervalos mudam quando a CBF repassa vaga;
 * o visual lê só daqui. Para desligar uma faixa, deixe o início maior que o fim.
 * O 1º também está na Libertadores: o campeão tem prioridade e pinta de dourado.
 */
final class Faixas {
    static final int CAMPEAO_DE = 1;
    static final int CAMPEAO_ATE = 1;
    static final int LIBERTADORES_DE = 1;
    static final int LIBERTADORES_ATE = 4;
    static final int PRE_LIBERTADORES_DE = 5;
    static final int PRE_LIBERTADORES_ATE = 5;
    static final int SUL_AMERICANA_DE = 6;
    static final int SUL_AMERICANA_ATE = 11;
    static final int REBAIXAMENTO_DE = 17;
    static final int REBAIXAMENTO_ATE = 20;

    static final int COR_CAMPEAO = 0xFFD4AF37;
    static final int COR_LIBERTADORES = 0xFF1E6FD9;
    static final int COR_PRE_LIBERTADORES = 0xFF6FB1FF;
    static final int COR_SUL_AMERICANA = 0xFF2EAD5B;
    static final int COR_REBAIXAMENTO = 0xFFE53935;
    /** 31/255 ≈ 12%. O Z-4 não usa fundo, só a barrinha. */
    static final int ALFA_FUNDO = 0x1F;

    enum Tipo {
        CAMPEAO, LIBERTADORES, PRE_LIBERTADORES, SUL_AMERICANA, REBAIXAMENTO, NENHUMA
    }

    private Faixas() { }

    static Tipo de(int posicao) {
        if (entre(posicao, CAMPEAO_DE, CAMPEAO_ATE)) return Tipo.CAMPEAO;
        if (entre(posicao, REBAIXAMENTO_DE, REBAIXAMENTO_ATE)) return Tipo.REBAIXAMENTO;
        if (entre(posicao, PRE_LIBERTADORES_DE, PRE_LIBERTADORES_ATE)) return Tipo.PRE_LIBERTADORES;
        if (entre(posicao, LIBERTADORES_DE, LIBERTADORES_ATE)) return Tipo.LIBERTADORES;
        if (entre(posicao, SUL_AMERICANA_DE, SUL_AMERICANA_ATE)) return Tipo.SUL_AMERICANA;
        return Tipo.NENHUMA;
    }

    static int cor(Tipo tipo) {
        switch (tipo) {
            case CAMPEAO: return COR_CAMPEAO;
            case LIBERTADORES: return COR_LIBERTADORES;
            case PRE_LIBERTADORES: return COR_PRE_LIBERTADORES;
            case SUL_AMERICANA: return COR_SUL_AMERICANA;
            case REBAIXAMENTO: return COR_REBAIXAMENTO;
            default: return 0;
        }
    }

    /** Fundo leve. Rebaixamento e meio da tabela ficam sem véu. */
    static int fundo(Tipo tipo) {
        if (tipo == Tipo.REBAIXAMENTO || tipo == Tipo.NENHUMA) return 0;
        return (ALFA_FUNDO << 24) | (cor(tipo) & 0x00FFFFFF);
    }

    private static boolean entre(int posicao, int de, int ate) {
        return de <= ate && posicao >= de && posicao <= ate;
    }
}
