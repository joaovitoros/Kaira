package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.JCODE.domain.Intencao;
import com.JCODE.util.Normalizador;

import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;

/**
 * Spec da issue #39 - a aposta ("Foi isso que voce quis dizer?") vira
 * contexto pendente do proximo turno:
 *
 *   - confirmacao explicita -> entrega a resposta REAL do candidato e ancora;
 *   - continuacao ("conte mais") -> segue o candidato proposto;
 *   - match de outro assunto -> descarta a aposta;
 *   - frase qualquer -> nao entrega nem gasta a pendencia.
 *
 * Determinismo: base real pras entregas (filme e musica tem intento e
 * entrada no dicionario do #37); fixture so pro encaixe de continuacao.
 */
public class ApostaPendenteTest {

	@After
	public void limpa() {
		ControleSessao.reset();
		ControleSessao.consomeApostaPendente();
	}

	private Intencao continuacaoFixture() {
		return new Intencao("continuacao_topico", new ArrayList<>(), null,
				new ArrayList<>(), null, null);
	}

	/** Confirmacao entrega o candidato, ancora o topico e consome a fila. */
	@Test
	public void confirmacaoEntregaOCandidatoEAnkoraOTopico() {
		ControleResposta.carregaCache();
		ControleSessao.get().setTopicoAtual("musica");
		ControleSessao.apostaPendente("filme");

		String entregue = ControleResposta.entregaApostaPendente(Normalizador.texto("isso"));

		assertNotNull("confirmacao tem que entregar a resposta real do candidato", entregue);
		assertEquals("confirmar ancora o candidato como topico",
				"filme", ControleSessao.get().getTopicoAtual());
		assertNull("consumiu: a pendencia sai da fila",
				ControleSessao.getApostaPendente());
		assertNull("segunda confirmacao nao tem mais o que entregar",
				ControleResposta.entregaApostaPendente(Normalizador.texto("isso")));
	}

	/** Continuacao logo apos a aposta segue o candidato, nao o topico antigo. */
	@Test
	public void continuacaoSegueOApostaPendente() {
		ControleResposta.carregaCache();
		ControleSessao.get().setTopicoAtual("musica");
		ControleSessao.apostaPendente("filme");

		String falado = ControleResposta.comandosResposta(continuacaoFixture());

		assertEquals("continuacao tem que seguir o candidato da aposta",
				ControleConhecimento.resumo("filme"), falado);
		assertEquals("a continuacao confirma o candidato como topico",
				"filme", ControleSessao.get().getTopicoAtual());
		assertNull("consumiu a pendencia", ControleSessao.getApostaPendente());

		// pendencia consumida: a proxima continuacao volta pro topico atual
		ControleSessao.get().setTopicoAtual("musica");
		assertEquals("sem pendencia a continuacao usa o topico atual (#37)",
				ControleConhecimento.resumo("musica"),
				ControleResposta.comandosResposta(continuacaoFixture()));
	}

	/** Match de outro assunto descarta a aposta velha. */
	@Test
	public void matchDeOutroAssuntoDescartaAAposta() {
		ControleResposta.carregaCache();
		ControleSessao.apostaPendente("filme");

		// "quem e voce" = identidade 1.00 na base real: match de verdade
		ControleResposta.melhorIntencao(Normalizador.texto("quem e voce"));

		assertNull("assunto novo com match de verdade descarta a aposta",
				ControleSessao.getApostaPendente());
	}

	/** Frase qualquer nao entrega nem gasta a pendencia. */
	@Test
	public void fraseQualquerNaoEntregaNemGasta() {
		ControleResposta.carregaCache();
		ControleSessao.apostaPendente("filme");

		assertNull("so confirmacao explicita entrega",
				ControleResposta.entregaApostaPendente(Normalizador.texto("conte mais sobre filme")));
		assertEquals("frase qualquer nao gasta a pendencia",
				"filme", ControleSessao.getApostaPendente());
	}
}
