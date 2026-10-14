package com.JCODE.avalia;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Testa o Avaliador (Tarefa #3, atualizado na #5 e #7) - a regua do pipeline.
 *
 * O Avaliador carrega test/resources/avaliacao.json, manda cada entrada por
 * Normalizador.texto() -> ControleResposta.resolver() (exato -> classificador)
 * e compara com o esperado. Dois tiers:
 *
 *   - REGRESSAO (fraseEsperada != null): tem que casar HOJE. Assertado aqui.
 *     Na Tarefa #5 o classificador fechou 2 gaps; na #7 (intencoes.json,
 *     §5.1) fechou os 2 ultimos ('e ai beleza' e 'qual seu jogo favorito'),
 *     que tambem viraram regressao. Hoje TODOS os casos sao regressao e
 *     regressaoPermaneceIntegra e o guarda de integridade.
 *   - GAP (fraseEsperada == null + alvoFase1): nao resolve hoje. So REPORTADO.
 *     A Tarefa #7 zera esse placar - nenhumGapRestante trava que ele nao volte.
 *
 * Roda com CWD na raiz do projeto.
 */
public class AvaliadorTest {

	private static final String DATASET = "test/resources/avaliacao.json";

	@Test
	public void regressaoPermaneceIntegra() {
		Avaliador.Resultado r = Avaliador.avaliar(DATASET);

		assertTrue("dataset devia ter casos de regressão", r.casosRegressao > 0);
		assertEquals(
				"casos de regressão falhando: " + r.falhasRegressao,
				r.casosRegressao, r.acertosRegressao);
	}

	/**
	 * A Tarefa #7 (migração para intencoes.json, §5.1) fecha os 2 últimos
	 * gaps medidos: 'e ai beleza' (via intento cumprimentar) e 'qual seu jogo
	 * favorito' (via intento jogo_favorito). Com eles promovidos a regressão,
	 * o placar de gaps cai para zero - e esse teste é o que trava isso: se
	 * alguém regredir o classificador a ponto de um gap voltar, ele estoura.
	 */
	@Test
	public void nenhumGapRestante() {
		Avaliador.Resultado r = Avaliador.avaliar(DATASET);

		assertEquals(
				"gaps ainda abertos (a Tarefa #7 tinha fechado todos): " + r.detalhesGap,
				0, r.casosGap);
	}
}
