package com.JCODE.avalia;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Testa o Avaliador (Tarefa #3) - a regua de medição do pipeline.
 *
 * O Avaliador carrega test/resources/avaliacao.json, manda cada entrada por
 * Normalizador.texto() -> ControleResposta.fraseExata() e compara com o
 * esperado. Dois tiers:
 *
 *   - REGRESSAO (fraseEsperada != null): tem que casar HOJE. Assertado aqui.
 *   - GAP (fraseEsperada == null + alvoFase1): nao resolve hoje. So REPORTADO,
 *     nao assertado - porque e exatamente o que o classificador da Fase 1 vai
 *     consertar. Quando a Fase 1 entrar, o gap vira caso de regressao.
 *
 * O numero que a Fase 1 precisa vencer e a contagem de gaps.
 *
 * Roda com CWD na raiz do projeto.
 */
public class AvaliadorTest {

	private static final String DATASET = "test/resources/avaliacao.json";

	@Test
	public void tierRegressaoTemCemPorCento() {
		Avaliador.Resultado r = Avaliador.avaliar(DATASET);

		assertTrue("dataset devia ter casos de regressão", r.casosRegressao > 0);
		assertEquals(
				"casos de regressão falhando: " + r.falhasRegressao,
				r.casosRegressao, r.acertosRegressao);
	}

	@Test
	public void registraGapsComoPlacarParaAFase1() {
		Avaliador.Resultado r = Avaliador.avaliar(DATASET);

		assertTrue(
				"dataset devia registrar gaps (alvo do classificador da Fase 1)",
				r.casosGap > 0);
	}
}
