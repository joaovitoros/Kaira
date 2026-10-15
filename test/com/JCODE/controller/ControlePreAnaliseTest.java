package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.JCODE.domain.Exemplo;
import com.JCODE.domain.FrameSlot;
import com.JCODE.domain.Intencao;
import com.JCODE.domain.Intencoes;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Pre-analise frame + slot (issue #18, Secao 5.7): decompor a frase em
 * frame (molde - "qual X favorito") e slot (termo de conteudo - "carro").
 *
 * As 4 regras da secao:
 *  - marcadores sao frase-modelo (regex de 2+ palavras), nao palavra solta;
 *  - o mecanismo e o do §5.4: stopwords/isso/rubricas fora isola o slot;
 *  - roda ANTES mas como SINAL: no melhorIntencaoDetalhado o frame e o
 *    desempate de 3o nivel (score empatado + resto empatado) - nunca portao;
 *  - fallback informado: "Ainda nao sei sobre {slot}" no pool da #17.
 *
 * A tabela e curada (4 familias): preferencia = "qual .* favorit",
 * gosto = "gosta de", opiniao = "acha d", cumprimento = "bom dia|boa
 * tarde|boa noite". Frames novos = linhas novas na tabela.
 *
 * Roda com CWD na raiz do projeto (stopwords REAIS via cache).
 * preAnalise nao toca a base de intencoes - e puro.
 */
public class ControlePreAnaliseTest {

	@Before
	public void carregaCaches() {
		ControleSinonimo.carregaCache();
		ControleResposta.carregaCache();
	}

	/**
	 * O coracao da secao: mesmo frame, slots diferentes. O slot sai na
	 * forma CRUA do usuario (nao o stem) e sem as rubricas do frame - por
	 * isso "carro" e nao "seu carro favorito". Cumprimento nao tem slot
	 * de conteudo: "" e o contrato.
	 */
	@Test
	public void preAnaliseSeparaFrameDeSlot() {
		FrameSlot preferencia = ControlePreAnalise.preAnalise("qual seu carro favorito");
		assertEquals("qual X favorito = frame preferencia", "preferencia", preferencia.getFrame());
		assertEquals("o que sobra depois das rubricas e o slot", "carro", preferencia.getSlot());

		FrameSlot gosto = ControlePreAnalise.preAnalise("voce gosta de musica");
		assertEquals("gosto", gosto.getFrame());
		assertEquals("musica", gosto.getSlot());

		FrameSlot cumprimento = ControlePreAnalise.preAnalise("bom dia");
		assertEquals("cumprimento", cumprimento.getFrame());
		assertEquals("cumprimento nao tem slot de conteudo", "", cumprimento.getSlot());
	}

	/**
	 * Fallback informado funciona MESMO sem frame conhecido: "quem e o
	 * buffy" nao casa frame nenhum, mas as stopwords/artigos caem sozinhos
	 * e o slot "buffy" sobrevive - e e o que a aposta do §7.3 quer.
	 */
	@Test
	public void slotFuncionaSemFrame() {
		FrameSlot a = ControlePreAnalise.preAnalise("quem é o buffy");
		assertNull("frase fora da tabela curada nao tem frame", a.getFrame());
		assertEquals("artigo e verbo de ligacao caem, conteudo fica", "buffy", a.getSlot());
	}

	/**
	 * A sobrecarga com slot renderiza os templates "Ainda nao sei sobre
	 * {slot}..." - mesma alma da #17 (honesto, curioso, ensin, ?) e a
	 * mesma anti-repeticao. Sem slot ("" ou null), cai no pool generico
	 * da #17 intacto.
	 */
	@Test
	public void fallbackComSlotPersonalizadoEGenericoSemSlot() {
		String a = ControleResposta.respostaFallback("carro");
		String b = ControleResposta.respostaFallback("carro");
		List<String> generico = ControleResposta.frasesFallback();

		assertTrue("fallback cita o slot: " + a, a.contains("sobre carro"));
		assertTrue("fallback convida ao ensino: " + a, a.contains("ensin"));
		assertTrue("fallback e curioso: " + a, a.endsWith("?"));
		assertNotEquals("anti-repeticao vale pra slot tambem", a, b);

		String semSlot = ControleResposta.respostaFallback("");
		assertTrue("slot vazio cai no pool generico da #17", generico.contains(semSlot));
	}

	/**
	 * O SINAL no ranking: duas intencoes com score 0.5 empatado e resto 1
	 * empatado - o empate se resolve pelo frame. Entrada "qual seu carro
	 * favorito" (frame preferencia) empata colega ("seu carro e novo",
	 * sem frame) com preferencia_jogo ("qual seu jogo favorito", mesmo
	 * frame) e o desempate leva pra PREFERENCIA_JOGO. Sem o 3o nivel, o
	 * vencedor seria a primeira na ordem (colega).
	 */
	@Test
	public void frameDesempataCoberturaEmpatada() {
		Intencoes raiz = new Intencoes();
		raiz.setIntencoes(new ArrayList<>());
		raiz.getIntencoes().add(intencao("colega", "seu carro e novo"));
		raiz.getIntencoes().add(intencao("preferencia_jogo", "qual seu jogo favorito"));

		FrameSlot analise = ControlePreAnalise.preAnalise("qual seu carro favorito");
		assertEquals("a entrada e de preferencia", "preferencia", analise.getFrame());

		var m = ControleResposta.melhorIntencaoDetalhado(raiz, "qual seu carro favorito");
		assertNotNull("empate tem que devolver alguem", m);
		assertEquals("o frame da entrada casa com o exemplo de preferencia_jogo",
				"preferencia_jogo", m.getIntencao().getIntento());
	}

	/** Fixture: duas intencoes que empatam em score e resto. */
	private Intencao intencao(String nome, String... frases) {
		Intencao i = new Intencao();
		i.setIntento(nome);
		i.setExemplos(new ArrayList<>());
		for (String f : frases)
			i.getExemplos().add(new Exemplo(f, "autor"));
		i.setRespostas(new ArrayList<>());
		i.setAcoes(new ArrayList<>());
		return i;
	}
}
