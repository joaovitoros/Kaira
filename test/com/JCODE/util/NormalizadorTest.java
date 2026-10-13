package com.JCODE.util;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * Testa o Normalizador (G3 do PLANO_DE_EVOLUCAO.md).
 *
 * O Normalizador e a funcao central de limpeza de texto da KAIRA: entrada
 * e base de dados precisam passar por ele para ficarem no mesmo "idioma".
 * Sem ele, "voce" != "voce com acento", e o match exato falha a toa.
 */
public class NormalizadorTest {

	// ---- casos basicos de limpeza ----

	@Test
	public void tiraAcento() {
		assertEquals("coracao", Normalizador.texto("coração"));
	}

	@Test
	public void minusculas() {
		assertEquals("ola", Normalizador.texto("OLÁ"));
	}

	@Test
	public void pontuacaoViraEspaco() {
		assertEquals("ola tudo bem", Normalizador.texto("Olá, tudo bem?"));
	}

	@Test
	public void colapsaEspacos() {
		assertEquals("muitos espacos", Normalizador.texto("  muitos   espaços  "));
	}

	@Test
	public void limpaAntesEDepois() {
		assertEquals("boa noite", Normalizador.texto("   boa   noite   "));
	}

	// ---- casos de borda ----

	@Test
	public void nullViraStringVazia() {
		assertEquals("", Normalizador.texto(null));
	}

	@Test
	public void stringVaziaPassaPela() {
		assertEquals("", Normalizador.texto(""));
	}

	@Test
	public void soPontuacaoViraVazio() {
		assertEquals("", Normalizador.texto("?!.,;:"));
	}

	@Test
	public void soEspacosViraVazio() {
		assertEquals("", Normalizador.texto("   "));
	}

	// ---- o criterio que destrava a Fase 1 ----
	// "Voce esta bem?" e "voce esta bem" precisam virar a MESMA string

	@Test
	public void frasesEquivalentesViramAMesmaString() {
		String comAcentoEPontuacao = Normalizador.texto("Você está bem?");
		String semAcento = Normalizador.texto("voce esta bem");
		assertEquals(semAcento, comAcentoEPontuacao);
	}

	@Test
	public void caixaAltaEPontuacaoNaoAlteramResultado() {
		String comAcentoEPontuacao = Normalizador.texto("VOCÊ ESTÁ BEM?!!");
		String simples = Normalizador.texto("voce esta bem");
		assertEquals(simples, comAcentoEPontuacao);
	}

	// ---- preserva conteudo que importa ----

	@Test
	public void preservaDigitos() {
		assertEquals("versao 2 0", Normalizador.texto("versão 2.0"));
	}

	@Test
	public void preservaLetraComCedilhaComoC() {
		// cedilha vira "c" (NFD + remove marcas): "voce" no padrao da base
		assertEquals("voce e legal", Normalizador.texto("você é legal"));
	}
}
