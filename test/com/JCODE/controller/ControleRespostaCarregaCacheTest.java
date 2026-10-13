package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.JCODE.domain.Resposta;
import com.JCODE.util.Normalizador;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * Teste de INTEGRACAO do carregamento do cache (G3 / Tarefa #2).
 *
 * O NormalizadorTest cobre o Normalizador isolado (unidade). Este cobre o
 * WIRING: a hora em que carregaCache() aplica o normalizador nos campos
 * CERTOS.
 *
 * Pegou um bug real da Tarefa #2: a primeira versao normalizava
 * `respostas` (texto exibido) em vez de `frase` (chave de match). Unidade
 * 13/13 verde, app com match quebrado + resposta sem acento. Bug de
 * integracao so aparece aqui.
 *
 * Roda contra o KAIRA/resources/respostas.json REAL (caminho relativo),
 * entao o CWD tem que ser a raiz do projeto.
 *
 * Nao usar frases de assunto "comando de desligamento" aqui: comandosResposta()
 * chama System.exit(0) e derruba o JVM do teste.
 */
public class ControleRespostaCarregaCacheTest {

	@Before
	public void carregaCacheReal() {
		ControleResposta.carregaCache();
	}

	private List<Resposta> falas() {
		return ControleResposta.falasCache.getFalas();
	}

	/** A chave de match deve chegar normalizada (e ja normalizada = idempotente). */
	@Test
	public void fraseEhNormalizadaAoCarregar() {
		for (Resposta r : falas()) {
			String frase = r.getFrase();
			assertNotNull("frase nao pode ser null", frase);
			assertEquals(
					"frase devia estar em forma normalizada: " + frase,
					Normalizador.texto(frase), frase);
		}
	}

	/** O texto EXIBIDO deve manter acento e caixa alta. */
	@Test
	public void respostasExibidasMantemAcento() {
		Resposta oi = falas().stream()
				.filter(r -> "oi".equals(r.getFrase()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("frase 'oi' nao encontrada no cache"));

		assertTrue(
				"resposta exibida perdeu o acento: " + oi.getRespostas(),
				oi.getRespostas().contains("Bom falar com você"));
	}

	/** O assunto deve ficar intacto (comandosResposta compara com texto acentuado). */
	@Test
	public void assuntoFicaIntacto() {
		boolean temTecnico = falas().stream()
				.anyMatch(r -> "informações técnicas".equals(r.getAssunto()));
		assertTrue("assunto 'informações técnicas' devia sobreviver a normalizacao", temTecnico);
	}

	/** Cenario ponta a ponta: digitado com acento e pontuacao, casa com a base. */
	@Test
	public void entradaAcentuadaCasaComABase() {
		String entrada = Normalizador.texto("Você é legal?");

		String resposta = ControleResposta.lerResposta(entrada);

		assertNotNull(
				"entrada '" + entrada + "' devia casar com a base (frase normalizada)",
				resposta);
	}
}
