package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.List;

/**
 * Fallbacks inteligentes (issue #17, Secao 7.3): a frase seca "Nao sei como
 * responder a isso" morre e vira um POOL de frases honestas + curiosas que
 * sempre convidam ao ensino ("ensin") - o modo ensino ja esta armado no
 * enviarResposta (ativarAprendizado + bufferFrases), o fallback so muda o
 * TOM da recusa.
 *
 * Segundo criterio de aceite da secao: o fallback NUNCA repete duas vezes
 * seguidas a mesma frase (anti-repeticao simples via ultimoFallback).
 *
 * O pool e o metodo respostaFallback() sao puros - nao tocam cache nem UI -
 * entao os testes nao dependem de dados locais.
 */
public class ControleRespostaFallbackTest {

	private static final String FRASE_MORTA = "Não sei como responder a isso";

	/**
	 * O contrato do pool: >= 3 frases, distintas, nenhuma igual a frase
	 * morta, todas com o convite ao ensino e todas terminando em interrogacao
	 * (honesto + curioso, no sentido do plano).
	 */
	@Test
	public void poolDeFallbackHonestoECurioso() {
		List<String> pool = ControleResposta.frasesFallback();

		assertTrue("pool tem que ter pelo menos 3 frases", pool.size() >= 3);
		assertEquals("pool sem repetidas", pool.size(), pool.stream().distinct().count());

		for (String frase : pool) {
			assertFalse("frase morta nao pode voltar: " + frase, FRASE_MORTA.equals(frase));
			assertTrue("fallback convida ao ensino (contem 'ensin'): " + frase,
					frase.contains("ensin"));
			assertTrue("fallback e curioso (termina com ?): " + frase, frase.endsWith("?"));
		}
	}

	/**
	 * Anti-repeticao: 10 chamadas seguidas nunca devolvem a mesma frase duas
	 * vezes seguidas, e toda escolhida pertence ao pool. O estado e o
	 * ultimoFallback estatico - as assercoes sao por CONSECUTIVAS, entao o
	 * teste e imune a ordem de execucao com as outras classes.
	 */
	@Test
	public void fallbackNuncaRepeteDuasVezes() {
		List<String> pool = ControleResposta.frasesFallback();

		String anterior = null;
		for (int i = 0; i < 10; i++) {
			String atual = ControleResposta.respostaFallback();

			assertTrue("respostaFallback devolve frases do pool: " + atual, pool.contains(atual));
			if (anterior != null)
				assertNotEquals("fallback repetiu a frase anterior", anterior, atual);
			anterior = atual;
		}
	}
}
