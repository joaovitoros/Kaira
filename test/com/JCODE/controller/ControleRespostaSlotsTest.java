package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.time.LocalTime;

/**
 * Spec da issue #21 (§6.3): slots nas respostas + uso dos fatos.
 *
 * O critério de aceite que fecha: "você lembra o que eu falei que gosto?"
 * → resposta correta baseada em fatos. O JSON guarda o slot CRUD e a
 * renderização acontece na entrega (nunca o valor pronto no arquivo).
 *
 * Contratos exercitados (ControleResposta):
 *  - preencheSlots(String)   → {nome}/{topico}/{hora_do_dia}/{fato:CHAVE}
 *                              renderizados; texto sem slot volta igual;
 *                              null vira null;
 *  - fatosCompletos(String)  → true se não há {fato:} ou se todos existem;
 *  - comandosResposta (via   → resposta com {fato:} só sai se o fato
 *    lerResposta)                existir; sem fato, resposta genérica
 *                              sem token pendente (caminho do fallback).
 *
 * Defaults de sessão vazia (decisão de design da #21):
 *  - {nome}        → "amigo"
 *  - {topico}      → "tudo um pouco"
 *  - {hora_do_dia} → "bom dia" antes das 12h, senão "boa tarde"
 *    (regra literal do plano; 2 estados)
 *
 * O render em FormulaResposta.enviarResposta (belt-and-suspenders dos
 * caminhos de correção) não tem comportamento observável hoje - nenhum
 * texto de fallback tem slot §6.3 - então fica fora da spec.
 */
public class ControleRespostaSlotsTest {

	@Before
	public void prepara() {
		ControleResposta.carregaCache();
		ControleSessao.reset();
	}

	/** Slots viram valores reais; nada de "{" cru na entrega. */
	@Test
	public void preencheSlotsSubstituiNomeTopicoEHora() {
		ControleSessao.get().setNomeUsuario("João");
		ControleSessao.get().setTopicoAtual("musica");

		String saida = ControleResposta.preencheSlots(
				"{hora_do_dia}, {nome}! A gente tava falando sobre {topico}.");

		String esperado = LocalTime.now().getHour() < 12 ? "bom dia" : "boa tarde";
		assertFalse("slot cru na saída: " + saida, saida.contains("{"));
		assertTrue("faltou a hora do dia (" + esperado + "): " + saida, saida.contains(esperado));
		assertTrue("faltou o nome: " + saida, saida.contains("João"));
		assertTrue("faltou o tópico: " + saida, saida.contains("musica"));
	}

	/** Sessão vazia (primeira visita) usa os defaults, nunca slot cru. */
	@Test
	public void preencheSlotsComPadroesNaSessaoVazia() {
		String saida = ControleResposta.preencheSlots(
				"{hora_do_dia}, {nome}! Tava falando sobre {topico}.");

		String esperado = LocalTime.now().getHour() < 12 ? "bom dia" : "boa tarde";
		assertFalse("slot cru com sessão vazia: " + saida, saida.contains("{"));
		assertTrue("default do nome: " + saida, saida.contains("amigo"));
		assertTrue("default do tópico: " + saida, saida.contains("tudo um pouco"));
		assertTrue("hora do dia: " + saida, saida.contains(esperado));
	}

	/** Texto sem slot volta idêntico (as respostas de sempre não mudam). */
	@Test
	public void preencheSlotsPreservaTextoSemSlots() {
		String puro = "Ainda não sei falar disso... me ensina? O que você quis dizer?";
		assertEquals(puro, ControleResposta.preencheSlots(puro));
		assertNull(ControleResposta.preencheSlots(null));
	}

	/** fatosCompletos: sem token = completo; com token, só com o fato na sessão. */
	@Test
	public void fatosCompletosSoVerdadeiroQuandoFatoExiste() {
		assertFalse("fato ausente não pode passar como completo",
				ControleResposta.fatosCompletos("Você me disse que gosta de {fato:gosta de}, né?"));
		assertTrue("texto sem slot de fato é sempre completo",
				ControleResposta.fatosCompletos("Resposta simples, sem fato nenhum"));

		ControleSessao.get().getFatos().put("gosta de", "minecraft");
		assertTrue("fato presente tem que renderizar",
				ControleResposta.fatosCompletos("Você me disse que gosta de {fato:gosta de}, né?"));
	}

	/**
	 * Critério de aceite da §6.3: com o fato aprendido, a resposta sai com
	 * o valor real e sem token. Frase já normalizada (sem acento), como o
	 * decifrar entrega.
	 */
	@Test
	public void respostaLembradaSoSaiComFatoPresente() {
		ControleSessao.get().getFatos().put("gosta de", "minecraft");

		String resposta = ControleResposta.lerResposta("voce lembra do que eu gosto");

		assertNotNull("frase devia classificar na base (novo intento lembra_gosta)", resposta);
		assertTrue("fato não foi renderizado: " + resposta, resposta.contains("minecraft"));
		assertFalse("token de fato cru na entrega: " + resposta, resposta.contains("{fato"));
	}

	/** Sem o fato aprendido: resposta genérica sem token e sem fato inventado. */
	@Test
	public void semFatoNaSessaoCaiNaRespostaGenericaSemToken() {
		String resposta = ControleResposta.lerResposta("voce lembra do que eu gosto");

		assertNotNull("sem fato ainda precisa responder (caminho genérico)", resposta);
		assertFalse("token pendente sem fato: " + resposta, resposta.contains("{fato"));
		assertFalse("fato inventado: " + resposta, resposta.contains("minecraft"));
	}
}
