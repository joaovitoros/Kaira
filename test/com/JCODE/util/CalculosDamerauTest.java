package com.JCODE.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Os valores do damerau sao a "verdade de campo" apurada com o proprio probe
 * (ProbeAnagrama), mesma disciplina do CalculosLevenshteinTest.
 *
 * O que muda em relacao ao levenshtein puro: a troca de duas letras VIZINHAS
 * cai de 2 pra 1 - e e o que resolve o "fimle" do log de 17/10 sem depender
 * da ordem do palavras.json (CorrecaoOrdemArquivoTest).
 *
 * O que NAO muda: insercao, apagamento e troca solta continuam igual ao
 * levenshtein, e anagrama de duas ou mais trocas continua acima do limiar -
 * lista/sltia segue 4 (o Ap. B.2 do PLANO continua verdadeiro) e amor/roma = 3.
 * Anagrama livre casaria palavra de letras iguais como "identica" e criaria
 * correcao falsa, entao fica de fora DE PROPOSITO.
 */
public class CalculosDamerauTest {

	/** Troca de letra vizinha = 1 erro (o caso que o levenshtein cobrava 2). */
	@Test
	public void trocaDeLetrasVizinhasCustaUm() {
		assertEquals("'fimle' -> 'filme' = 1 (swap m/l, caso real do log de 17/10)",
				1, Calculos.damerau("fimle", "filme"));
		assertEquals("'vlota' -> 'volta' = 1 (levenshtein cobrava 2)",
				1, Calculos.damerau("vlota", "volta"));
		assertEquals("'casa' -> 'caas' = 1 (troca de letras vizinhas)",
				1, Calculos.damerau("casa", "caas"));
	}

	/** Fora da troca vizinha, a distancia e a mesma do levenshtein. */
	@Test
	public void foraDaTrocaVizinhaIgualAoLevenshtein() {
		assertEquals("'musica' -> 'musca' = 1 (insercao)",
				1, Calculos.damerau("musica", "musca"));
		assertEquals("'abacate' -> 'abacaxi' = 2 (duas trocas soltas, Ap. B.1)",
				2, Calculos.damerau("abacate", "abacaxi"));
		assertEquals("'gato' vs vazio = 4 (tamanho da palavra)",
				4, Calculos.damerau("gato", ""));
	}

	/** Anagrama de duas ou mais trocas NAO aproxima - de proposito. */
	@Test
	public void anagramaLivreContinuaFora() {
		assertEquals("'lista' -> 'sltia' = 4: o ILUDE do Ap. B.2 continua verdadeiro",
				4, Calculos.damerau("lista", "sltia"));
		assertEquals("'amor' -> 'roma' = 3: anagrama livre nunca vira correcao",
				3, Calculos.damerau("amor", "roma"));
		assertEquals("'gol' -> 'ola' = 2: congelado continua reprovado no limiar 1",
				2, Calculos.damerau("gol", "ola"));
	}
}
