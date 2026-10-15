package com.JCODE.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Testa o item A e o item C da Tarefa #15 (issue #15, Secao 5.6 + Ap. B.2):
 * a distancia de Levenshtein (nova) e o calculoPorcentagemFrase corrigido
 * pra comparar palavra-a-palavra (bug G8 - contains na frase inteira).
 *
 * Os valores do levenshtein sao a "verdade de campo" apurada com o proprio
 * algoritmo do Ap. B.2 antes de fechar o contrato - incluindo o caso que
 * ILUDE: lista/sltia = 4 (Levenshtein puro nao ve anagrama), logo fora do
 * limiar 1-2 da correcao de digitacao.
 *
 * Roda com CWD na raiz do projeto (o algoritmo e puro, sem arquivo).
 */
public class CalculosLevenshteinTest {

	/** Formula do Ap. B.2: matriz d, custo 0/1, min de 3 vizinhos. */
	@Test
	public void levenshteinSegueAFormulaDoApendiceB2() {
		assertEquals("igual devolve 0", 0, Calculos.levenshtein("casa", "casa"));
		assertEquals("erro de tecla devolve 1 (exemplo verbatim do B.2)",
				1, Calculos.levenshtein("ola", "ila"));
		assertEquals("letra faltando devolve 1", 1, Calculos.levenshtein("musica", "musca"));
		assertEquals("letra inserida no meio devolve 2", 2, Calculos.levenshtein("volta", "vlota"));
		assertEquals("duas trocas devolve 2 (caso do Ap. B.1)", 2, Calculos.levenshtein("abacate", "abacaxi"));
		assertEquals("vazio vs palavra = tamanho da palavra", 4, Calculos.levenshtein("gato", ""));
		assertEquals("os dois vazios = 0", 0, Calculos.levenshtein("", ""));
		assertEquals("scramble lista/sltia = 4: anagrama nao e erro de tecla",
				4, Calculos.levenshtein("lista", "sltia"));
	}

	/**
	 * G8: a contagem tem que ser por palavra EXATA da frase do usuario.
	 * O "sol" em "ensolarado" nao pode contar - e era exatamente isso que o
	 * contains na frase inteira fazia (devolvia 100).
	 */
	@Test
	public void fraseComparaPalavraAPalavraNuncaSubstring() {
		assertEquals("G8: 'sol' dentro de 'ensolarado' nao conta", 0.0,
				Calculos.calculoPorcentagemFrase("ensolarado", "sol", null), 1e-9);
		assertEquals("uma palavra exata de duas = 50", 50.0,
				Calculos.calculoPorcentagemFrase("ola mundo", "ola terra", null), 1e-9);
		assertEquals("frase inteira igual = 100 (caminho exato do correcaoFrases)",
				100.0, Calculos.calculoPorcentagemFrase("bom dia", "bom dia", null), 1e-9);
		assertEquals("palavras a mais do usuario nao penalizam (e cobertura)",
				50.0, Calculos.calculoPorcentagemFrase("ola", "ola terra", null), 1e-9);
	}

	/** A media com porcentagemAtual (usada pelo buffer do correcaoFrases) fica. */
	@Test
	public void mediaComPorcentagemAtualPreservada() {
		assertEquals("(80 + 50) / 2 = 65", 65.0,
				Calculos.calculoPorcentagemFrase("ola mundo", "ola terra", 80.0), 1e-9);
	}
}
