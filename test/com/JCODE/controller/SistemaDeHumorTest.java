package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.JCODE.util.Caminhos;

/**
 * #23 / §7.1 - Sistema de humor (abre a Fase 3).
 *
 * O humor e um numero 0..10 na sessao (ja persistido desde a #20) que
 * escolhe a FAIXA de tom da resposta: 0-3 seca, 4-6 neutra, 7-10
 * animada (decisao do item 4 do plano: usar o humorMin/humorMax ja
 * existentes no Apendice A, sem schema novo e sem pos-processador).
 *
 * Modificadores (fala crua, como os padroes de nome/fato):
 * elogio +1, grosseria -1, frases neutras nao mexem; clamp 0..10.
 *
 * Aceite da Fase 3: elogiar 3x seguidas muda o tom das respostas de
 * forma perceptivel - o status_geral so responde da faixa animada.
 */
public class SistemaDeHumorTest {

	/** Frases de aceite: cada uma casa um padrao de modificador. */
	private static final String ELOGIO = "voce e legal";
	private static final String GROSSERIA = "voce e chata";
	private static final String NEUTRA = "qual e a capital da franca";

	/** O topico do aceite: classifica como status_geral (exemplo igual). */
	private static final String COMO_ESTA = "como esta seu humor hoje";

	/** Humor de partida em toda a suite (Sessao default). */
	private static final int HUMOR_INICIAL = 5;

	@Before
	public void zera() {
		ControleResposta.carregaCache();
		ControleSessao.reset();
	}

	@After
	public void limpa() throws Exception {
		Path sessao = Caminhos.base().resolve("sessao.json");
		if (Files.exists(sessao))
			Files.delete(sessao);
	}

	@Test
	public void elogioSobeHumorEGrosseriaBaixa() {
		ControleSessao.processa(ELOGIO);
		assertEquals("elogio devia dar +1", HUMOR_INICIAL + 1,
				ControleSessao.get().getHumor());

		ControleSessao.processa(GROSSERIA);
		assertEquals("grosseria devia dar -1", HUMOR_INICIAL,
				ControleSessao.get().getHumor());
	}

	@Test
	public void fraseNeutraNaoMudaHumor() {
		ControleSessao.processa(NEUTRA);
		assertEquals("frase neutra nao mexe no humor", HUMOR_INICIAL,
				ControleSessao.get().getHumor());
	}

	@Test
	public void humorTravaNosLimites() {
		ControleSessao.get().setHumor(10);
		ControleSessao.processa(ELOGIO);
		assertEquals("humor nao passa do teto 10", 10,
				ControleSessao.get().getHumor());

		ControleSessao.get().setHumor(0);
		ControleSessao.processa(GROSSERIA);
		assertEquals("humor nao desce do piso 0", 0,
				ControleSessao.get().getHumor());

		ControleSessao.get().setHumor(15);
		assertEquals("setter clampa em cima", 10,
				ControleSessao.get().getHumor());
		ControleSessao.get().setHumor(-3);
		assertEquals("setter clampa em baixo", 0,
				ControleSessao.get().getHumor());
	}

	/** ACEITE DA FASE 3: 3 elogios mudam o tom da mesma intencao. */
	@Test
	public void tresElogiosMudamTomDaResposta() {
		for (int i = 0; i < 3; i++)
			ControleSessao.processa(ELOGIO);
		assertEquals("3 elogios = +3", HUMOR_INICIAL + 3,
				ControleSessao.get().getHumor());

		String resposta = ControleResposta.lerResposta(COMO_ESTA);
		assertNotNull("status_geral devia classificar", resposta);
		assertTrue("humor 8 devia puxar a faixa animada (7-10): " + resposta,
				eAlguma(resposta, FAIXA_ANIMADA));
	}

	@Test
	public void humorBaixoEscolheFaixaSeca() {
		ControleSessao.get().setHumor(2);

		String resposta = ControleResposta.lerResposta(COMO_ESTA);
		assertNotNull("status_geral devia classificar", resposta);
		assertTrue("humor 2 devia puxar a faixa seca (0-3): " + resposta,
				eAlguma(resposta, FAIXA_SECA));
	}

	/** G9: o placeholder "nivel de humor em...  de 10" vira o valor real. */
	@Test
	public void statusMostraValorRealDeHumor() {
		ControleSessao.get().setHumor(8);

		String resposta = ControleResposta.lerResposta(COMO_ESTA);
		assertNotNull(resposta);
		assertTrue("linha do status devia mostrar o valor real: " + resposta,
				resposta.contains("nivel de humor em 8 de 10"));
	}

	/** Guarda: humor ja persiste pelo sessao.json (desde a #20). */
	@Test
	public void humorPersisteNaSessao() throws Exception {
		ControleSessao.get().setHumor(8);
		ControleSessao.gravaSessao();

		ControleSessao.reset();
		assertTrue(ControleSessao.carregaSessao());
		assertEquals("humor restaurado do arquivo", 8,
				ControleSessao.get().getHumor());
	}

	/**
	 * As faixas curadas da #23 (status_geral): todo texto da faixa
	 * deve conter um destes trechos - se a resposta do status num
	 * humor alto deixar de bater, o tom nao mudou de forma perceptivel
	 * e o aceite da fase caiu.
	 */
	private static final String[] FAIXA_ANIMADA = {
			"fico feliz em saber disso",
			"ainda bem que está, nossa conversa poderá ser mais divertida",
			"Tô ótima! Seu papo melhora meu dia",
			"Animada! Bora puxar o assunto?" };

	private static final String[] FAIXA_SECA = { "Creio que sim",
			"Tô de boa.", "Meia-boca, mas funcionando.",
			"Indo devagar hoje. E você?" };

	private boolean eAlguma(String resposta, String[] opcoes) {
		for (String o : opcoes)
			if (resposta.contains(o))
				return true;
		return false;
	}
}
