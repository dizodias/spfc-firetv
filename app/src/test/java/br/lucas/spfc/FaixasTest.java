package br.lucas.spfc;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class FaixasTest {
    @Test
    public void faixasAtuaisDoBrasileirao() {
        assertEquals(Faixas.Tipo.CAMPEAO, Faixas.de(1));
        assertEquals(Faixas.Tipo.LIBERTADORES, Faixas.de(2));
        assertEquals(Faixas.Tipo.LIBERTADORES, Faixas.de(4));
        assertEquals(Faixas.Tipo.PRE_LIBERTADORES, Faixas.de(5));
        assertEquals(Faixas.Tipo.SUL_AMERICANA, Faixas.de(6));
        assertEquals(Faixas.Tipo.SUL_AMERICANA, Faixas.de(11));
        assertEquals(Faixas.Tipo.NENHUMA, Faixas.de(12));
        assertEquals(Faixas.Tipo.NENHUMA, Faixas.de(16));
        assertEquals(Faixas.Tipo.REBAIXAMENTO, Faixas.de(17));
        assertEquals(Faixas.Tipo.REBAIXAMENTO, Faixas.de(20));
    }

    @Test
    public void fundoLeveEZonaSemVeil() {
        assertEquals(0x1F, Faixas.fundo(Faixas.Tipo.LIBERTADORES) >>> 24);
        assertEquals(0x001E6FD9, Faixas.fundo(Faixas.Tipo.LIBERTADORES) & 0x00FFFFFF);
        assertEquals(0, Faixas.fundo(Faixas.Tipo.REBAIXAMENTO));
        assertEquals(0, Faixas.fundo(Faixas.Tipo.NENHUMA));
        assertEquals(Faixas.COR_CAMPEAO, Faixas.cor(Faixas.Tipo.CAMPEAO));
    }
}
