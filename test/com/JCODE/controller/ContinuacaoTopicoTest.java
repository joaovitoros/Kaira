package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.JCODE.domain.Intencao;
import com.JCODE.util.Normalizador;

import org.junit.Before;
import org.junit.Test;

import java.util.Map;

/**
 * #27 (briefing P2 / §12.2) - Continuação de assunto.
 *
 * O usuario pede pra continuar ("fala mais disso") e hoje cai em
 * mudar_assunto / convite_conversa / cotidiano - o contrario do que
 * ele quer. A feature tem duas metades:
 *
 * - DADOS: intencao `continuacao_topico` com os literais de
 *   continuacao (frases de 1-2 tokens precisam do literal, regra do
 *   briefing);
 * - CODIGO: o hook de topico (melhorIntencaoDetalhado) gravava
 *   `topicoAtual` incondicionalmente - continuar o assunto NAO pode
 *   virar o novo topico, senao o slot {topico} renderiza
 *   "continuacao_topico" na propria resposta de continuacao.
 *
 * Guardas de colisao: "falar de outra coisa" e cia continuam em
 * mudar_assunto (os exemplos das duas intents nao podem dividir
 * token-set - briefing regra 6).
 */
public class ContinuacaoTopicoTest {

	@Before
	public void zera() {
		ControleSessao.reset();
	}

	private void assertIntencao(String frase, String esperado) {
		Map<String, Intencao> r = ControleResposta.melhorIntencao(Normalizador.texto(frase));
		assertNotNull("frase devia classificar: " + frase, r);
		assertEquals(frase + " -> " + esperado, esperado,
				r.values().iterator().next().getIntento());
	}

	/** RED: a intencao continuacao_topico nao existe na base ainda. */
	@Test
	public void frasesDeContinuacaoCaemNaIntencaoCerta() {
		assertIntencao("fala mais disso", "continuacao_topico");
		assertIntencao("conta mais sobre isso", "continuacao_topico");
		assertIntencao("e depois o que aconteceu", "continuacao_topico");
		assertIntencao("me explica melhor essa parte", "continuacao_topico");
	}

	/** Guarda: o que E de mudar assunto nao migra. */
	@Test
	public void mudarAssuntoNaoPerdeAsFrasesDele() {
		assertIntencao("falar de outra coisa", "mudar_assunto");
		assertIntencao("muda de assunto", "mudar_assunto");
		assertIntencao("cansei desse tema", "mudar_assunto");
	}

	/** RED: o hook de topico sobrescrevia com "continuacao_topico". */
	@Test
	public void continuarNaoSobrescreveOTopico() {
		ControleSessao.topico("filme");

		ControleResposta.melhorIntencao(Normalizador.texto("fala mais disso"));

		assertEquals("o topico continua sendo o assunto de verdade",
				"filme", ControleSessao.get().getTopicoAtual());
	}

	/** RED de novo: {topico} renderizaria o nome da propria continuacao. */
	@Test
	public void slotTopicoRenderizaOTemaAnterior() {
		ControleSessao.topico("filme");

		ControleResposta.melhorIntencao(Normalizador.texto("fala mais disso"));

		assertEquals("Conta mais sobre filme",
				ControleResposta.preencheSlots("Conta mais sobre {topico}"));
	}
}
