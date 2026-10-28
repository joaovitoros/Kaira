package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * #67 - A negacao curta de reacao (baterias: "nao gostei" -> aparencia,
 * "nao entendi" -> "Serio? Conta mais!", "nao quero falar disso" ->
 * continuacao_topico, "nao" -> procrastinacao).
 *
 * Medido na sonda de 28/10 (base real isolada): o classificador remove
 * "nao"/"nem" como stopword, entao a fala negativa casa a forma POSITIVA
 * do exemplo. A triagem de fatos so responde `ehPergunta` e as declaracoes
 * de fato nao passam por ela - a reacao curta cai no classificador.
 *
 * A porta (#67) e curada: fala curta de negacao -> reparo por familia
 * (gostei/entendi/quero/sei/seco), sem ancorar topico, sem gravar fato.
 * Guardas: adversativa com positiva depois ("nao, fiquei feliz...") e
 * declaracao longa ("eu nao gosto de musica") NAO disparam.
 */
public class NegacaoCurtaTest {

	@Before
	public void zera() {
		ControleSessao.reset();
		ControleResposta.carregaCache();
		ControlePalavra.carregaCache();
		ControleSinonimo.carregaCache();
	}

	private String responde(String fala) {
		ControleSessao.get().setNomeUsuario("teste");
		FormulaResposta.bufferFrases = null;
		FormulaResposta.respostaEnsino = null;
		ControleFace.resposta = null;
		FormulaResposta.decifrar(fala);
		return ControleFace.resposta;
	}

	/** "nao gostei" nao pode virar resposta de aparencia. */
	@Test
	public void naoGosteiPedeReparoENaoViraAparencia() {
		String r = responde("nao gostei");
		assertNotNull(r);
		assertFalse("classificador respondeu o positivo: " + r,
				r.contains("tatuagem") || r.contains("visual") || r.contains("espelho"));
		assertTrue("nao pediu reparo: " + r, ControleResposta.reparosGostei().contains(r));
	}

	/** Interjeicao inicial nao salva a fala da negacao. */
	@Test
	public void interjeicaoInicialAindaDisparaOReparo() {
		String r = responde("ah, nao gostei");
		assertTrue("nao pediu reparo: " + r, ControleResposta.reparosGostei().contains(r));
	}

	/** "nao entendi" nao pode virar "Serio? Conta mais!". */
	@Test
	public void naoEntendiPedeReparo() {
		String r = responde("nao entendi");
		assertTrue(r, ControleResposta.reparosEntendi().contains(r));
	}

	/** "nao quero falar disso" respeita e nao continua o topico. */
	@Test
	public void naoQueroFalarNaoContinuaOTopico() {
		ControleSessao.get().setTopicoAtual("musica");
		String r = responde("nao quero falar disso");
		assertTrue(r, ControleResposta.reparosQuero().contains(r));
		assertEquals("o topico nao pode mudar", "musica", ControleSessao.get().getTopicoAtual());
	}

	/** "nao" seco nao vira procrastinacao. */
	@Test
	public void naoSecoNaoViraTema() {
		String r = responde("nao");
		assertTrue(r, ControleResposta.reparosSeco().contains(r));
	}

	/** Guarda: adversativa com positiva depois NAO e reparo. */
	@Test
	public void negacaoComPositivaNaoDispara() {
		assertNull(ControleResposta.respostaDeNegacao("nao, fiquei feliz em te conhecer"));
	}

	/** Guarda: declaracao negativa longa nao e reacao curta. */
	@Test
	public void declaracaoNegativaLongaNaoDispara() {
		assertNull(ControleResposta.respostaDeNegacao("eu nao gosto de musica"));
		assertNull(ControleResposta.respostaDeNegacao("minha mae nao gosta muito"));
	}
}
