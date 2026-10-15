package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.JCODE.domain.Exemplo;
import com.JCODE.domain.Intencao;
import com.JCODE.domain.Intencoes;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Map;

/**
 * Testa a cobertura ponderada por IDF (Tarefa #14, issue #14, Secao 5.2):
 * em vez de contar 1 ponto por palavra casada, o score passa a SOMAR PESOS -
 * palavra rara no corpus pesa mais que palavra de preenchimento.
 *
 * Fixture de 6 exemplos, todos com "quero" (df=6 -> idf 0), "saudar" em 2
 * (df=2 -> log 3) e "musica" em 1 (df=1 -> log 6). Os numeros do flip:
 *
 *   entrada "quero saudar musica" = {quero, saudar, musica}
 *   UNIFORME (a conta de hoje): "quero saudar" casa 2/3 = 0.667 e "quero
 *     ouvir musica" casa 2/3 = 0.667 - empate, e o desempate por resto
 *     (0 < 1) leva pra SAUDACAO. O bot responderia um OLA pra um pedido
 *     de musica.
 *   PONDERADA: saudacao = log3/(log3+log6) = 0.38 < LIMIAR; musica =
 *     log6/(log3+log6) = 0.62 >= LIMIAR - vira "musica". E este flip que
 *     o teste amarra.
 *
 * Caso-limite do denominador: base de 1 exemplo faz tudo ter idf 0 (log de
 * 1/1) - o contrato e cair na cobertura uniforme de hoje, nao devolver null
 * por divisao por zero.
 *
 * Roda com CWD na raiz do projeto (stopwords e grupos REAIS via caches).
 * Nao toca comandosResposta - puro.
 */
public class ControleRespostaPesosIdfTest {

	/** 6 exemplos: "quero" em todos, "saudar" em 2, "musica" em 1. */
	private Intencoes baseSeisExemplos() {
		Intencoes raiz = new Intencoes();
		raiz.setIntencoes(new ArrayList<>());

		raiz.getIntencoes().add(intencao("saudacao", "quero saudar", "quero saudar bom dia"));
		raiz.getIntencoes().add(intencao("musica", "quero ouvir musica"));
		raiz.getIntencoes().add(intencao("tempo", "quero tempo hoje"));
		raiz.getIntencoes().add(intencao("comida", "quero comer comida"));
		raiz.getIntencoes().add(intencao("jogo", "quero jogar jogo"));

		return raiz;
	}

	private Intencao intencao(String nome, String... frases) {
		Intencao i = new Intencao();
		i.setIntento(nome);
		i.setExemplos(new ArrayList<>());
		for (String f : frases)
			i.getExemplos().add(new Exemplo(f, "autor"));
		i.setRespostas(new ArrayList<>());
		return i;
	}

	@Before
	public void carregaCaches() {
		ControleSinonimo.carregaCache();
		ControleResposta.carregaCache();
	}

	/**
	 * A formula do contrato: log(N / max(df,1)). Base do log e livre (canceia
	 * na razao), mas o VALOR tem que ser esse. As CHAVES do mapa sao os
	 * mesmos tokens do matcher (pos-stem, sem stopword) - "saudar" entra
	 * como "saud" - porque o score vai olhar pesos pela mesma chave.
	 */
	@Test
	public void pesosIdfSegueLogDeNSobreDF() {
		Map<String, Double> pesos = ControleResposta.pesosIdf(baseSeisExemplos());

		assertEquals("palavra em todos os exemplos tem peso 0", 0.0, pesos.get("quero"), 1e-9);
		assertEquals("df 2 de 6 -> log(6/2) = log 3 (chave stem 'saud')", Math.log(3), pesos.get("saud"), 1e-9);
		assertEquals("df 1 de 6 -> log(6/1) = log 6", Math.log(6), pesos.get("musica"), 1e-9);

		Intencoes vazio = new Intencoes();
		vazio.setIntencoes(new ArrayList<>());
		assertTrue("base sem exemplos -> mapa vazio (guarda contra log de 0)",
				ControleResposta.pesosIdf(vazio).isEmpty());
	}

	/**
	 * O flip da Section 5.2 documentado no Javadoc da classe: sem o IDF, o
	 * preenchimento "quero" empata a saudacao com a musica e o resto leva
	 * pra saudacao; com o IDF, a palavra rara "musica" puxa a intencao
	 * certa. Hoje (conta uniforme) este teste cai - e tem que cair, ele e a
	 * spec vermelha.
	 */
	@Test
	public void palavraRaraDiscriminaENaoPreenchimento() {
		Map<String, Intencao> r = ControleResposta.melhorIntencao(baseSeisExemplos(), "quero saudar musica");

		assertNotNull("a entrada devia achar alguma intencao", r);
		assertEquals("a palavra rara 'musica' tem que puxar a intencao certa",
				"musica", r.values().iterator().next().getIntento());
	}

	/**
	 * Guarda do denominador: base de 1 exemplo -> todo token tem idf
	 * log(1/1) = 0 -> soma 0 -> em vez de NaN/null, cai na cobertura
	 * uniforme de hoje e casa 2/2. Verde hoje tambem (trava o fallback).
	 */
	@Test
	public void denominadorZeroCaiNaCoberturaUniforme() {
		Intencoes raiz = new Intencoes();
		raiz.setIntencoes(new ArrayList<>());
		raiz.getIntencoes().add(intencao("musica", "quero musica"));

		Map<String, Intencao> r = ControleResposta.melhorIntencao(raiz, "quero musica");

		assertNotNull("denominador zero devia cair na cobertura uniforme, nao devolver null", r);
		assertEquals("musica", r.values().iterator().next().getIntento());
	}
}
