package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.JCODE.domain.Intencao;
import com.JCODE.domain.RespostaIntencao;
import com.JCODE.util.Normalizador;

import org.junit.Before;
import org.junit.Test;

/**
 * #65 (baterias rodada 2 / RELATORIO) - Rota de calculo honesta.
 *
 * "quanto e 7 vezes 8" e "144 dividido por 12" caiam no fallback de
 * ensino ("me ensina") - o bot pedia para ser ensinado em vez de
 * assumir que nao calcula. A feature e DADO PURO: a intencao `calculo`
 * com os literais do corpus, variantes e respostas honestas.
 *
 * Medido (sonda de 28/10, base real isolada):
 * - literais do corpus: frase exata, score 1.00;
 * - variantes com digitos vistos: 1.00 no fuzzy ("4 vezes 4", "3 mais 4");
 * - raiz com numero novo: 0.65 (acima do limiar de 0.6);
 * - numero fora da base ("30 dividido por 6"): 0.45 - aposta com o
 *   palpite certo + confirmacao, sem ancorar topico (o topico da sessao
 *   fica intacto).
 */
public class CalculoHonestoTest {

	@Before
	public void zera() {
		ControleSessao.reset();
		ControleResposta.carregaCache();
		ControlePalavra.carregaCache();
		ControleSinonimo.carregaCache();
	}

	private void assertIntencao(String frase, String esperado) {
		Intencao i = ControleResposta.buscaResposta(Normalizador.texto(frase));
		assertNotNull("frase devia classificar: " + frase, i);
		assertEquals(frase + " -> " + esperado, esperado, i.getIntento());
	}

	/** Os dois literais que as baterias expuseram. */
	@Test
	public void literaisDasBateriasCaemNoCalculo() {
		assertIntencao("quanto e 7 vezes 8", "calculo");
		assertIntencao("quanto e 144 dividido por 12", "calculo");
	}

	/** Variantes com digitos vistos no pool - o fuzzy cobre. */
	@Test
	public void variantesComNumerosPequenosCaemNoCalculo() {
		assertIntencao("quanto e 4 vezes 4", "calculo");
		assertIntencao("quanto e 3 mais 4", "calculo");
	}

	/** Raiz com numero fora do pool ainda passa do limiar (0.65 medido). */
	@Test
	public void raizComNumeroNovoAindaCaeNoCalculo() {
		assertIntencao("qual e a raiz quadrada de 25", "calculo");
	}

	/** A resposta e do pool honesto do calculo - nunca o "me ensina". */
	@Test
	public void respostaEHonestaENaoEnsino() {
		FormulaResposta.bufferFrases = null;
		FormulaResposta.respostaEnsino = null;
		ControleFace.resposta = null;
		FormulaResposta.decifrar("quanto e 7 vezes 8");

		String resposta = ControleFace.resposta;
		assertNotNull(resposta);
		assertFalse("calculo nao pode cair no fallback de ensino: " + resposta,
				resposta.contains("me ensina"));
		assertTrue("resposta devia vir do pool de calculo: " + resposta,
				veioDoPool("calculo", resposta));
	}

	/** Numero fora da base: aposta com palpite certo, sem poluir o topico. */
	@Test
	public void numeroForaDaBaseFicaNaApostaSemPoluirTopico() {
		ControleSessao.get().setTopicoAtual("musica");
		FormulaResposta.bufferFrases = null;
		FormulaResposta.respostaEnsino = null;
		ControleFace.resposta = null;
		FormulaResposta.decifrar("quanto e 30 dividido por 6");

		assertTrue("o match deve apontar o calculo: " + ControleResposta.getUltimoMatchLog(),
				ControleResposta.getUltimoMatchLog().contains("intento=calculo"));
		assertEquals("aposta nao pode trocar o topico", "musica",
				ControleSessao.get().getTopicoAtual());
		assertTrue("aposta devia pedir confirmacao: " + ControleFace.resposta,
				ControleFace.resposta.contains("quis dizer"));
	}

	/** O calculo nao rouba dinheiro nem identidade (frases que ja casam). */
	@Test
	public void calculoNaoRoubaDinheiroNemIdentidade() {
		assertIntencao("quanto e que voce ganha", "identidade");
		assertNotEquals("calculo", nomeDoMatch("quanto custa um carro"));
	}

	private boolean veioDoPool(String intento, String resposta) {
		for (Intencao i : ControleResposta.getIntencoes())
			if (intento.equals(i.getIntento()))
				for (RespostaIntencao r : i.getRespostas())
					if (resposta.contains(r.getTexto()))
						return true;
		return false;
	}

	private String nomeDoMatch(String fala) {
		FormulaResposta.bufferFrases = null;
		FormulaResposta.respostaEnsino = null;
		ControleFace.resposta = null;
		FormulaResposta.decifrar(fala);
		String log = ControleResposta.getUltimoMatchLog();
		if (log == null || !log.contains("intento="))
			return null;
		int inicio = log.indexOf("intento=") + "intento=".length();
		int fim = inicio;
		while (fim < log.length() && log.charAt(fim) != ' ')
			fim++;
		return log.substring(inicio, fim);
	}
}
