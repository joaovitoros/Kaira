package com.JCODE.controller;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;

/**
 * Testa o item B da Tarefa #15 (issue #15, Secao 5.6): correcaoPalavra
 * re-wireada por distancia de edicao com limiar DEPENDENTE DO TAMANHO
 * do que foi digitado - <=4 letras aceita ate distancia 1, maior aceita 2.
 * Desde a issue #46 a distancia e o Calculos.damerau (Levenshtein + troca
 * de letra vizinha = 1); os casos deste arquivo valem igual nas duas formulas.
 *
 * O vocabulario e fixture injetado direto no cache (o palavras.json real e
 * dado do autor, nao fixture de teste) - e restaurado no @After.
 *
 * Sem os casos "gol" e "vlota" o limiar vira decorativo: "gol" (3 letras,
 * melhor candidato a distancia 2) tem que passar REPROVADO enquanto
 * "vlota" (5 letras, tambem distancia 2) tem que ser CORRIGIDO - os dois
 * sao a mesma distancia so mudando o tamanho.
 */
public class ControlePalavraCorrecaoTest {

	@Before
	public void injetaVocabularioDeTeste() {
		ControlePalavra.palavrasCache = Arrays.asList("ola", "lista", "casa", "musica", "volta", "abacaxi");
	}

	@After
	public void restauraVocabularioReal() {
		ControlePalavra.carregaCache();
	}

	/** Erro de tecla de uma letra corrige em qualquer tamanho. */
	@Test
	public void erroDeTeclaDeUmaLetraCorrige() {
		assertEquals("'ila' -> 'ola' (dist 1, 3 letras, limiar 1)",
				"ola", ControlePalavra.correcaoPalavra("ila"));
		assertEquals("'musca' -> 'musica' (dist 1)",
				"musica", ControlePalavra.correcaoPalavra("musca"));
	}

	/** Palavra longa (>=5) aceita distancia 2. */
	@Test
	public void palavraLongaAceitaDuasDistancias() {
		assertEquals("'vlota' -> 'volta' (dist 2, 5 letras, limiar 2)",
				"volta", ControlePalavra.correcaoPalavra("vlota"));
		assertEquals("'abacate' -> 'abacaxi' (dist 2, 7 letras - Ap. B.1)",
				"abacaxi", ControlePalavra.correcaoPalavra("abacate"));
	}

	/** Curta (<=4) NAO aceita distancia 2 - e este teste que derruba limiar decorativo. */
	@Test
	public void palavraCurtaNaoAceitaDuasDistancias() {
		// melhor candidato e "ola" a distancia 2, mas "gol" tem 3 letras ->
		// limiar 1 -> nao corrige, devolve como o usuario digitou.
		assertEquals("'gol' devolve 'gol' (dist 2 excede o limiar 1 de palavra curta)",
				"gol", ControlePalavra.correcaoPalavra("gol"));
	}

	/** Nada dentro do limiar devolve o original - sem chute. */
	@Test
	public void nadaProximoDevolveOriginal() {
		assertEquals("'colega' sem candidato dentro do limiar",
				"colega", ControlePalavra.correcaoPalavra("colega"));
	}
}
