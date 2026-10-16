package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.Before;
import org.junit.Test;

/**
 * §7.2 - Iniciativa própria: a KAIRA pergunta de interesse sozinha.
 *
 * A cada 3 turnos do usuário sem ela ter feito pergunta, a resposta do
 * turno ganha uma pergunta de interesse anexada:
 *
 * - fonte 1: os `followups` da intenção atual (tópico da sessão) - o
 *   banco ja tem 195 followups em 74 intencoes;
 * - fonte 2: pool generico proprio (frases do plano, ex. "E voce, o
 *   que andou fazendo hoje?"), com slot {nome} em algumas;
 * - anti-repeticao: nunca a mesma pergunta duas vezes seguidas;
 * - a pergunta de interesse zera o contador, e a pergunta de nome
 *   tambem (a KAIRA ja perguntou algo no turno).
 *
 * Contrato novo: ControleSessao.perguntaInteresse().
 */
public class IniciativaPropriaTest {

	@Before
	public void zera() {
		ControleSessao.reset();
	}

	/** Turnos 1 e 2 ficam quietos; a iniciativa espera o 3o turno. */
	@Test
	public void naoPerguntaAntesDoTerceiroTurno() {
		ControleSessao.processa("tudo tranquilo por aqui");
		assertNull("1o turno nao interrompe", ControleSessao.perguntaInteresse());

		ControleSessao.processa("entao me conta mais");
		assertNull("2o turno tambem nao", ControleSessao.perguntaInteresse());
	}

	/** ACEITE: no 3o turno, pergunta de interesse (e termina em ?). */
	@Test
	public void perguntaNoTerceiroTurno() {
		ControleSessao.processa("tudo tranquilo por aqui");
		ControleSessao.processa("entao me conta mais");
		ControleSessao.processa("legal legal");

		String pergunta = ControleSessao.perguntaInteresse();
		assertNotNull("3o turno devia ter pergunta de interesse", pergunta);
		assertTrue("pergunta de interesse termina em ? : " + pergunta,
				pergunta.endsWith("?"));
	}

	/** Depois de perguntar, o contador recomeca. */
	@Test
	public void zeraContadorAoPerguntar() {
		ControleSessao.processa("tudo tranquilo por aqui");
		ControleSessao.processa("entao me conta mais");
		ControleSessao.processa("legal legal");
		assertNotNull(ControleSessao.perguntaInteresse());

		ControleSessao.processa("continua");
		assertNull("turno seguinte volta a ficar quieto",
				ControleSessao.perguntaInteresse());
	}

	/** Se a KAIRA ja perguntou o nome no turno, nao soma pergunta. */
	@Test
	public void perguntaDeNomeTambemZeraOContador() {
		ControleSessao.processa("tudo tranquilo por aqui");
		ControleSessao.processa("entao me conta mais");
		ControleSessao.processa("legal legal");

		assertEquals("Como posso te chamar?", ControleSessao.perguntaDeNome());
		assertNull("turno ja tem pergunta do nome",
				ControleSessao.perguntaInteresse());
	}

	/** Anti-repeticao: duas iniciativas seguidas nunca a mesma frase. */
	@Test
	public void naoRepeteDuasVezesSeguidas() {
		ControleSessao.processa("tudo tranquilo por aqui");
		ControleSessao.processa("entao me conta mais");
		ControleSessao.processa("legal legal");
		String primeira = ControleSessao.perguntaInteresse();

		ControleSessao.processa("continua");
		ControleSessao.processa("hm entendi");
		ControleSessao.processa("boa");
		String segunda = ControleSessao.perguntaInteresse();

		assertNotNull(primeira);
		assertNotNull(segunda);
		assertFalse("nao repetiu a mesma pergunta: " + primeira,
				primeira.equals(segunda));
	}

	/** Com topico atual que tem followups no banco, usa o followup. */
	@Test
	public void usaFollowupsDaIntencaoAtual() {
		List<String> doBanco = ControleResposta.getIntencoes().stream()
				.filter(i -> "filme".equals(i.getIntento()))
				.findFirst().get().getFollowups();
		assertFalse("pre-condicao: filme tem followups no banco",
				doBanco.isEmpty());

		ControleSessao.topico("filme");
		ControleSessao.processa("tudo tranquilo por aqui");
		ControleSessao.processa("entao me conta mais");
		ControleSessao.processa("legal legal");

		String pergunta = ControleSessao.perguntaInteresse();
		assertTrue("pergunta vem dos followups do topico: " + pergunta,
				doBanco.contains(pergunta));
	}
}
