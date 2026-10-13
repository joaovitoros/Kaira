package com.JCODE.avalia;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Testa o Avaliador (Tarefa #3, atualizado na #5) - a regua do pipeline.
 *
 * O Avaliador carrega test/resources/avaliacao.json, manda cada entrada por
 * Normalizador.texto() -> ControleResposta.resolver() (exato -> classificador)
 * e compara com o esperado. Dois tiers:
 *
 *   - REGRESSAO (fraseEsperada != null): tem que casar HOJE. Assertado aqui.
 *     Na Tarefa #5 os 2 gaps fechados pelo classificador foram promovidos pra
 *     regressao - entao tierRegressaoTemCemPorCento e o guarda de que o
 *     classificador fechou eles SEM quebrar os 10 casos exatos.
 *   - GAP (fraseEsperada == null + alvoFase1): nao resolve hoje. So REPORTADO.
 *     Ficam 2, cada um motivando uma task: sinonimos (§5.5) e intents (§5.1).
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
