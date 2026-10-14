package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.JCODE.domain.Exemplo;
import com.JCODE.domain.Intencao;
import com.JCODE.domain.Intencoes;
import com.JCODE.domain.RespostaIntencao;

import org.junit.Test;

import java.util.ArrayList;

/**
 * Testa o aprendizado (Tarefa #8, issue #8): ensinar uma frase+resposta nova.
 *
 * O modelo antigo (chamadaFrase da era respostas.json) gravava a frase num
 * Resposta solto e regravava respostas.json via falasCache - que agora e sempre
 * null, transformando o ensino num no-op silencioso. O novo modelo manda o que
 * o usuario ensina para o intent auto_aprendizado (caixa de entrada), como
 * Exemplo + RespostaIntencao, e persiste em intencoes.json.
 *
 * Este arquivo testa a parte PURA: a mutacao em memoria do conjunto de
 * intencoes. O lado do disco (chamadaFrase gravando intencoes.json) e coberto
 * indiretamente pelo CarregaCacheTest (que le o arquivo real) - aqui a gente
 * nao toca em disco de proposito, pra nao sujar o dado curado do repo a cada
 * execucao da suite.
 *
 * A decisao central que estes testes amarram: o que se ensina vira Exemplo
 * (origem "autor") + RespostaIntencao (origem "autor", humorMin 0, humorMax 10)
 * DENTRO do intent auto_aprendizado.
 */
public class ControleRespostaAprendizadoTest {

	/**
	 * Constroi um Intencoes de teste com um intent auto_aprendizado vazio (e um
	 * cumprimentar qualquer, pra garantir que o ensino nao vaza pra outro intent).
	 */
	private Intencoes baseDeTeste() {
		Intencoes raiz = new Intencoes();
		raiz.setIntencoes(new ArrayList<>());

		Intencao cumprimentar = new Intencao();
		cumprimentar.setIntento("cumprimentar");
		cumprimentar.setExemplos(new ArrayList<>());
		cumprimentar.setRespostas(new ArrayList<>());
		raiz.getIntencoes().add(cumprimentar);

		Intencao aprendizado = new Intencao();
		aprendizado.setIntento("auto_aprendizado");
		aprendizado.setExemplos(new ArrayList<>());
		aprendizado.setRespostas(new ArrayList<>());
		raiz.getIntencoes().add(aprendizado);

		return raiz;
	}

	private Intencao autoAprendizado(Intencoes raiz) {
		return raiz.getIntencoes().stream()
				.filter(i -> "auto_aprendizado".equals(i.getIntento()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("intent auto_aprendizado ausente na base de teste"));
	}

	/**
	 * O lado puro do ensino: recebe o conjunto de intencoes e muta o intent
	 * auto_aprendizado, adicionando a frase ensinada como Exemplo e a resposta
	 * como RespostaIntencao. Nao toca em disco.
	 */
	@Test
	public void ensinoAdicionaExemploNoAutoAprendizado() {
		Intencoes raiz = baseDeTeste();
		int exemplosAntes = autoAprendizado(raiz).getExemplos().size();

		ControleResposta.ensinaEm(raiz, "qual a capital da Bólgria?", "Varna");

		assertEquals("devia ter adicionado exatamente um exemplo",
				exemplosAntes + 1, autoAprendizado(raiz).getExemplos().size());

		boolean achou = autoAprendizado(raiz).getExemplos().stream()
				.anyMatch(e -> "qual a capital da Bólgria?".equals(e.getTexto()));
		assertTrue("a frase ensinada devia estar no auto_aprendizado como Exemplo", achou);
	}

	/** A resposta ensinada vira RespostaIntencao no mesmo intent (pool p/ sorteio). */
	@Test
	public void ensinoAdicionaRespostaNoAutoAprendizado() {
		Intencoes raiz = baseDeTeste();

		ControleResposta.ensinaEm(raiz, "qual a capital da Bólgria?", "Varna");

		RespostaIntencao ensinada = autoAprendizado(raiz).getRespostas().stream()
				.filter(r -> "Varna".equals(r.getTexto()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("a resposta ensinada devia estar no pool do auto_aprendizado"));

		assertNotNull(ensinada);
	}

	/** A resposta ensinada nasce com origem "autor" (eh a marca do que foi ensinado). */
	@Test
	public void respostaEnsinadaTemOrigemAutor() {
		Intencoes raiz = baseDeTeste();

		ControleResposta.ensinaEm(raiz, "qual a capital da Bólgria?", "Varna");

		RespostaIntencao ensinada = autoAprendizado(raiz).getRespostas().stream()
				.filter(r -> "Varna".equals(r.getTexto()))
				.findFirst()
				.orElseThrow();

		assertEquals("origem da resposta ensinada devia ser 'autor'", "autor", ensinada.getOrigem());
	}

	/** A frase ensinada (Exemplo) tambem nasce com origem "autor". */
	@Test
	public void exemploEnsinadoTemOrigemAutor() {
		Intencoes raiz = baseDeTeste();

		ControleResposta.ensinaEm(raiz, "qual a capital da Bólgria?", "Varna");

		Exemplo ensinado = autoAprendizado(raiz).getExemplos().stream()
				.filter(e -> "qual a capital da Bólgria?".equals(e.getTexto()))
				.findFirst()
				.orElseThrow();

		assertEquals("origem do exemplo ensinado devia ser 'autor'", "autor", ensinado.getOrigem());
	}

	/**
	 * O texto ensinado guarda o ORIGINAL (com acento/caixa). Nao pode ser
	 * normalizado no cache - eh o que impede a degradacao do arquivo curado
	 * (o bug da Tarefa #2, revivido na gravacao do intencoes.json).
	 */
	@Test
	public void ensinoPreservaTextoOriginal() {
		String fraseComAcento = "Você é o robô favorito?";
		String respostaComAcento = "Talvez você?";
		Intencoes raiz = baseDeTeste();

		ControleResposta.ensinaEm(raiz, fraseComAcento, respostaComAcento);

		Exemplo ensinado = autoAprendizado(raiz).getExemplos().stream()
				.filter(e -> fraseComAcento.equals(e.getTexto()))
				.findFirst()
				.orElseThrow();

		// O acento/caixa tem que sobreviver intactos: normalizar aqui seria o
		// bug da Tarefa #2 renascendo na gravacao do intencoes.json.
		assertEquals("o exemplo ensinado devia guardar o texto ORIGINAL, sem normalizar",
				fraseComAcento, ensinado.getTexto());

		RespostaIntencao ensinada = autoAprendizado(raiz).getRespostas().stream()
				.filter(r -> respostaComAcento.equals(r.getTexto()))
				.findFirst()
				.orElseThrow();

		assertEquals("a resposta ensinada tambem devia manter o texto ORIGINAL",
				respostaComAcento, ensinada.getTexto());
	}

	/** Faixa de humor padrao de resposta ensinada: 0..10 (humor cheio disponivel). */
	@Test
	public void respostaEnsinadaTemFaixaDeHumorPadrao() {
		Intencoes raiz = baseDeTeste();

		ControleResposta.ensinaEm(raiz, "qual a capital da Bólgria?", "Varna");

		RespostaIntencao ensinada = autoAprendizado(raiz).getRespostas().stream()
				.filter(r -> "Varna".equals(r.getTexto()))
				.findFirst()
				.orElseThrow();

		assertEquals("humorMin padrao devia ser 0", 0, ensinada.getHumorMin());
		assertEquals("humorMax padrao devia ser 10", 10, ensinada.getHumorMax());
	}

	/**
	 * O ensino NAO vaza pra outro intent: o intent cumprimentar da base de teste
	 * nao pode ganhar exemplo nem resposta.
	 */
	@Test
	public void ensinoNaoVazaParaOutroIntent() {
		Intencoes raiz = baseDeTeste();
		Intencao cumprimentar = raiz.getIntencoes().stream()
				.filter(i -> "cumprimentar".equals(i.getIntento()))
				.findFirst().orElseThrow();
		int exemplosAntes = cumprimentar.getExemplos().size();
		int respostasAntes = cumprimentar.getRespostas().size();

		ControleResposta.ensinaEm(raiz, "qual a capital da Bólgria?", "Varna");

		assertEquals("cumprimentar nao devia ganhar exemplo", exemplosAntes, cumprimentar.getExemplos().size());
		assertEquals("cumprimentar nao devia ganhar resposta", respostasAntes, cumprimentar.getRespostas().size());
	}

	/**
	 * Duas ensinadas empilham no mesmo intent auto_aprendizado (a caixa de
	 * entrada acumula, nao substitui).
	 */
	@Test
	public void ensinosAcumulamNoMesmoIntent() {
		Intencoes raiz = baseDeTeste();

		ControleResposta.ensinaEm(raiz, "qual a capital da Bólgria?", "Varna");
		ControleResposta.ensinaEm(raiz, "quem é o rei do heavy metal?", "Rob Halford");

		assertEquals("dois ensinos deviam acumular dois exemplos",
				2, autoAprendizado(raiz).getExemplos().size());
		assertEquals("dois ensinos deviam acumular duas respostas",
				2, autoAprendizado(raiz).getRespostas().size());
	}
}
