package com.JCODE.controller;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.JCODE.domain.Intencao;
import com.JCODE.util.Normalizador;

import org.junit.Test;

/**
 * Spec da issue #40 - o MATCH do log acompanha o match FINAL do turno:
 *
 *   - frase exata: o scan de frase-exata curto-circuita antes do
 *     classificador, mas o log precisa registrar o match (senao vaza o match
 *     morto do 1o passo - ao vivo, "conte mais" corrigido logava "identidade
 *     score=0.00" e o match real de continuacao_topico nunca aparecia);
 *   - score 0.00 e "sem match", nao um match fraco.
 */
public class LogMatchTest {

	/** "conta mais" e frase exata de continuacao_topico na base real. */
	@Test
	public void fraseExataRegistraNoLog() {
		ControleResposta.carregaCache();
		// zera o log estatico antes: sem isso o resto da suite engana
		ControleResposta.melhorIntencao("zzqqxx");

		Intencao resolvida = ControleResposta.buscaResposta("conta mais");
		assertNotNull("frase exata tem que resolver", resolvida);

		String log = ControleResposta.getUltimoMatchLog();
		assertNotNull("frase exata tem que registrar o match no log", log);
		assertTrue("log ficou com o passo morto em vez do match exato: " + log,
				log.contains("intento=continuacao_topico"));
		assertTrue("log exato marca score 1.00: " + log, log.contains("score=1.00"));
	}

	/** Score 0.00 nao e match fraco - e ausencia de match. */
	@Test
	public void zeroViraSemMatchNoLog() {
		ControleResposta.carregaCache();

		ControleResposta.melhorIntencao("zzqqxx");

		String log = ControleResposta.getUltimoMatchLog();
		assertNotNull(log);
		assertTrue("0.00 tinha que aparecer como sem match: " + log,
				log.startsWith("sem match"));
		assertTrue("log marca o score zerado: " + log, log.contains("score=0.00"));
	}

	/** Guarda: match de verdade continua logando intento + score. */
	@Test
	public void matchDeVerdadeSeguiriaComIntento() {
		ControleResposta.carregaCache();

		ControleResposta.melhorIntencao(Normalizador.texto("quem e voce"));

		String log = ControleResposta.getUltimoMatchLog();
		assertNotNull(log);
		assertTrue("match real loga o intento: " + log,
				log.startsWith("intento=identidade"));
		assertTrue("match real loga o score: " + log, log.contains("score=1.00"));
	}

	/** O desempate por contexto (#41) nao e frase exata. Ao vivo (17/10 16:12:26)
	 *  "qual sua preferida?" casou pelo CONTEXTO com elogio e o log dizia
	 *  "(frase exata)" - mentira de log, o mesmo peco que o #40 matou. */
	@Test
	public void desempateLogaComoDesempate() {
		ControleResposta.carregaCache();
		// zera o log estatico antes: sem isso o resto da suite engana
		ControleResposta.melhorIntencao("zzqqxx");
		ControleSessao.get().setTopicoAtual("musica");

		// {sua, preferida} casa com "qual sua musica favorita" - roda o desempate
		Intencao resolvida = ControleResposta.buscaResposta("qual sua preferida");
		assertNotNull("com topico de musica a generica resolve", resolvida);

		String log = ControleResposta.getUltimoMatchLog();
		assertNotNull("desempate tem que registrar o match no log", log);
		assertTrue("log tem que dizer que veio do desempate: " + log,
				log.contains("desempate"));
		assertFalse("desempate nao pode se passar por frase exata: " + log,
				log.contains("frase exata"));
	}
}
