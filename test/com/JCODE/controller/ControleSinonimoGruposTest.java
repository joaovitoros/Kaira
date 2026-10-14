package com.JCODE.controller;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

/**
 * Testa o indice de grupos de sinonimos (Tarefa #9, issue #9, Secao 5.5 - mata
 * o G2).
 *
 * O modelo antigo era a "roleta russa": procuraSinonimo(palavra) escolhia UM
 * sinonimo ALEATORIO (Math.random) e substituia na frase - a mesma frase
 * acertava uma vez e errava outra, e a frase remontada nao casava com nada. O
 * novo modelo e deterministico: duas palavras casam se pertencem ao mesmo
 * GRUPO.
 *
 * Porque mesmaPalavra e NAO um canonico unico (chaveDe): o arquivo real tem
 * palavra em MAIS DE UM grupo - "falar" esta em ["dizer","contar","falar",
 * "relatar"] E em ["falar","comunicar"]. Um canonico unico por palavra faria
 * mesmaPalavra("falar","comunicar") dar false mesmo existindo o grupo. O
 * contrato e "existe grupo contendo ambas".
 *
 * Todos os pares destes testes foram VERIFICADOS contra o grupos_sinonimos.json
 * real (nao assumidos): ["voce","tu"], ["voce","amece"],
 * ["obrigado","gracias","valeu"], ["dizer","falar"], ["falar","comunicar"],
 * ["ele","ela","este"].
 */
public class ControleSinonimoGruposTest {

	@Before
	public void carrega() {
		ControleSinonimo.carregaCache();
	}

	/** O par mais util pra KAIRA: ["voce","tu"] - casamento simetrico. */
	@Test
	public void tuCasaComVoce() {
		assertTrue("tu devia casar com voce (grupo [voce, tu])",
				ControleSinonimo.mesmaPalavra("tu", "voce"));
		assertTrue("o casamento devia ser simetrico",
				ControleSinonimo.mesmaPalavra("voce", "tu"));
	}

	/** Forma arcaica de voce ("amece", de Vossa Mercê) casa no mesmo grupo. */
	@Test
	public void formaArcaicaDeVoceCasa() {
		assertTrue("amece devia casar com voce (grupo [voce, amece])",
				ControleSinonimo.mesmaPalavra("amece", "voce"));
	}

	/** gracias (uso emprestado em pt-BR) casa com obrigado. */
	@Test
	public void graciasCasaComObrigado() {
		assertTrue("gracias devia casar com obrigado (grupo [obrigado, gracias, valeu])",
				ControleSinonimo.mesmaPalavra("gracias", "obrigado"));
		assertTrue("valeu devia casar com obrigado",
				ControleSinonimo.mesmaPalavra("valeu", "obrigado"));
	}

	/**
	 * O caso que DERRUBA o canonico unico: "falar" esta nos grupos do dizer E
	 * e membro de ["falar","comunicar"]. Tem que casar com os dois lados.
	 */
	@Test
	public void palavraEmDoisGruposCasaComOsDois() {
		assertTrue("falar devia casar com dizer (grupo [dizer, falar])",
				ControleSinonimo.mesmaPalavra("falar", "dizer"));
		assertTrue("falar devia casar com comunicar - e aqui que o canonico unico quebra",
				ControleSinonimo.mesmaPalavra("falar", "comunicar"));
	}

	/**
	 * Grupos NAO sao fecho transitivo: dizer~falar e falar~comunicar, mas nao
	 * existe grupo com dizer E comunicar - entao NAO casam. A granularidade e
	 * a do grupo curado, nao a da cadeia.
	 */
	@Test
	public void naoEFechoTransitivo() {
		assertFalse("dizer nao devia casar com comunicar (nao ha grupo com os dois)",
				ControleSinonimo.mesmaPalavra("dizer", "comunicar"));
	}

	/** tu (grupo do voce) e ela (grupo [ele, ela, este]) nao casam: grupos diferentes. */
	@Test
	public void gruposDiferentesNaoCasam() {
		assertFalse("tu nao devia casar com ela (grupos diferentes)",
				ControleSinonimo.mesmaPalavra("tu", "ela"));
	}

	/** Palavra sem grupo so casa com ela mesma. */
	@Test
	public void palavraSemGrupoSoCasaConsigo() {
		assertTrue("palavra devia casar consigo mesma",
				ControleSinonimo.mesmaPalavra("zzqqxx", "zzqqxx"));
		assertFalse("palavras sem grupo nao deviam casar entre si",
				ControleSinonimo.mesmaPalavra("zzqqxx", "yyqqxx"));
	}

	/**
	 * Comparacao ignora caixa (o velho procuraSinonimo usava equalsIgnoreCase
	 * - o contrato mantem).
	 */
	@Test
	public void comparacaoIgnoraCaixa() {
		assertTrue("Tu devia casar com VOCE (equalsIgnoreCase)",
				ControleSinonimo.mesmaPalavra("Tu", "VOCE"));
	}

	/**
	 * Determinismo (o coracao do G2): 20 chamadas seguidas tem que dar sempre
	 * o mesmo resultado. A roleta antiga (Math.random) nao sobreviveria a
	 * isto.
	 */
	@Test
	public void eDeterministico() {
		for (int i = 0; i < 20; i++) {
			assertTrue("iteracao " + i + ": falar devia casar com dizer sempre",
					ControleSinonimo.mesmaPalavra("falar", "dizer"));
			assertFalse("iteracao " + i + ": tu nao devia casar com ela nunca",
					ControleSinonimo.mesmaPalavra("tu", "ela"));
		}
	}
}
