package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.JCODE.domain.Intencao;
import com.JCODE.domain.RespostaIntencao;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntUnaryOperator;

/**
 * Spec da issue #35 (briefing §6-C1 - anti-repeticao de resposta).
 *
 * O fato: escolheMultiRespostas era Math.random puro - resposta de intencao
 * podia repetir IMEDIATAMENTE (a mesma frase 2x seguidas). Anti-repeticao
 * existia so no ultimoFallback (#17) e no ultimoInteresse (pergunta de
 * interesse). Agora as ultimas N=2 frases entregues POR INTENTO saem do
 * sorteio; se a lista filtrada esvaziar, cai no sorteio normal (nunca fica
 * sem resposta - pool de 1 continua funcionando).
 *
 * Determinismo: o sorteio passa por ControleResposta.semeador (gancho
 * injetavel). Com o semeador fixo em 0 a faltade anti-repeticao fica
 * PROVADA: o mesmo indice vence sempre, as 3 frases sao iguais (red).
 * Apos a correcao cada volta exclui as 2 anteriores - 3 frases distintas,
 * sempre, independente de aleatoriedade (green estavel, sem flake).
 *
 * Fixture isolada por teste (nenhum toque no intencoesCache real); o
 * semeador volta ao padrao no @After pra nao contaminar os outros testes.
 */
public class AntiRepeticaoTurnoTest {

	private IntUnaryOperator semeadorOriginal;

	@Before
	public void guardaSemeador() {
		semeadorOriginal = ControleResposta.semeador;
	}

	@After
	public void restauraSemeador() {
		ControleResposta.semeador = semeadorOriginal;
	}

	private Intencao intentoComRespostas(String nome, String... textos) {
		ArrayList<RespostaIntencao> respostas = new ArrayList<>();
		for (String t : textos)
			respostas.add(new RespostaIntencao(t, "curadoria test", 0, 10));
		return new Intencao(nome, new ArrayList<>(), null, respostas, null, null);
	}

	/**
	 * O nucleo do C1: 3 turnos seguidos no mesmo intento nunca entregam a
	 * mesma frase duas vezes. Com semeador em 0 (sempre o primeiro), o
	 * codigo antigo devolve a MESMA frase 3x - este teste e o red dele.
	 */
	@Test
	public void tresTurnosSeguidosNoMesmoIntentoNuncaRepetem() {
		ControleResposta.semeador = n -> 0;
		Intencao musica = intentoComRespostas("musica_teste",
				"frase A", "frase B", "frase C", "frase D");
		List<String> pool = List.of("frase A", "frase B", "frase C", "frase D");

		String r1 = ControleResposta.comandosResposta(musica);
		String r2 = ControleResposta.comandosResposta(musica);
		String r3 = ControleResposta.comandosResposta(musica);

		assertNotNull("resposta nunca nula", r1);
		assertNotNull("resposta nunca nula", r2);
		assertNotNull("resposta nunca nula", r3);
		assertTrue("r1 vem do pool", pool.contains(r1));
		assertTrue("r2 vem do pool", pool.contains(r2));
		assertTrue("r3 vem do pool", pool.contains(r3));
		assertEquals("turno 1 e 2 nao repetem", false, r1.equals(r2));
		assertEquals("turno 2 e 3 nao repetem", false, r2.equals(r3));
		assertEquals("turno 1 e 3 nao repetem", false, r1.equals(r3));
	}

	/**
	 * Guarda do fallback: pool de 1 (33 intents de conversa diaria tem
	 * exatamente 1 resposta na faixa seca) - a exclusao esvazia, volta pro
	 * sorteio normal e NUNCA devolve nulo.
	 */
	@Test
	public void poolDeUmaFraseNuncaFicaNulo() {
		ControleResposta.semeador = n -> 0;
		Intencao unica = intentoComRespostas("unico_teste", "unica frase");

		String a = ControleResposta.comandosResposta(unica);
		String b = ControleResposta.comandosResposta(unica);

		assertNotNull("nunca nulo com pool de 1", a);
		assertEquals("unica frase devolvida sempre", "unica frase", a);
		assertEquals("2o turno tambem entrega", "unica frase", b);
	}
}
