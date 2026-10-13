package com.JCODE.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Testa o Caminhos (Tarefa #4 - G7).
 *
 * O KAIRA tinha 12 caminhos "KAIRA/resources/*.json" hardcoded relativos ao
 * CWD: rodar o app de outra pasta fazia ele achar que era 1a instalacao. O
 * Caminhos resolve a base UMA vez (sysprop > env > sobe do code source > CWD)
 * e todo acesso a dado passa por Caminhos.recurso(...).
 *
 * Roda com CWD na raiz do projeto (la existe a pasta KAIRA/).
 */
public class CaminhosTest {

	@Before
	@After
	public void limpaEstadoEstatico() {
		Caminhos.resetar();
		System.clearProperty("kaira.home");
	}

	/** Sobe a partir de um dir profundo e acha a raiz (a que contem KAIRA/). */
	@Test
	public void enraizarSobeAteAcharKAIRA() {
		Path profundo = Paths.get("src", "com", "JCODE");

		Path raiz = Caminhos.enraizar(profundo);

		assertNotNull("devia achar uma raiz contendo KAIRA/ subindo de " + profundo, raiz);
		assertTrue("a raiz achada deve conter a pasta KAIRA/",
				Files.isDirectory(raiz.resolve("KAIRA")));
	}

	/** Sem override, a base resolvida tem que conter a pasta KAIRA/. */
	@Test
	public void basePadraoContemPastaKAIRA() {
		assertTrue("base() devia apontar pra um dir que contem KAIRA/",
				Files.isDirectory(Caminhos.base().resolve("KAIRA")));
	}

	/** O sysprop kaira.home tem prioridade sobre tudo. */
	@Test
	public void kairaHomeTemPrioridade() {
		Path esperado = Paths.get("C:\\fake-kaira-home");
		System.setProperty("kaira.home", esperado.toString());

		assertEquals(esperado, Caminhos.base());
	}

	/** recurso(nome) monta <base>/KAIRA/resources/<nome>. */
	@Test
	public void recursoMontaCaminhoDeRecurso() {
		System.setProperty("kaira.home", "C:\\fake");

		String obtido = Caminhos.recurso("respostas.json").toString().replace('\\', '/');

		assertTrue("recurso() devia apontar pra .../KAIRA/resources/respostas.json, veio: " + obtido,
				obtido.endsWith("KAIRA/resources/respostas.json"));
	}
}
