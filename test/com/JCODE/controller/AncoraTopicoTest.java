package com.JCODE.controller;

import static org.junit.Assert.assertEquals;

import com.JCODE.domain.Exemplo;
import com.JCODE.domain.Intencao;
import com.JCODE.domain.Intencoes;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

/**
 * Spec da issue #38 - o topico da sessao so pode ancorar com match de
 * verdade (score >= LIMIAR).
 *
 * O fato (log real de uso, 16/10): "e os filmeeees" nao casa com NADA (todos
 * os scores zerados), o argmax de zeros levava o anchor - topico virou
 * "identidade" do nada - e daqui a pouco "conte mais" (continuacao_topico
 * legitima) renderizava "{topico}" envenenado: "puxar identidade de novo".
 * A causa e o anchor incondicional do melhorIntencaoDetalhado.
 *
 * Determinismo: fixture propria (dois intents, exemplos sem overlap com a
 * frase-prova), sem tocar na base real. A frase-prova "zzz qqq" nao
 * normaliza pra nenhum token de intent nenhum - score 0.00 garantido.
 * Hoje o anchor roda mesmo no zero (red); depois da correcao so roda com
 * score >= 0.6 (green).
 *
 * O terceiro teste guarda o #27: continuacao_topico REAL (exato) continua
 * sem virar topico (e segura o pool no pedeContinuacao).
 */
public class AncoraTopicoTest {

	private String topicoOriginal;

	@Before
	public void guardaTopico() {
		topicoOriginal = ControleSessao.get().getTopicoAtual();
	}

	@After
	public void restauraTopico() {
		ControleSessao.get().setTopicoAtual(topicoOriginal);
	}

	private Intencoes baseDeFixture() {
		Intencoes raiz = new Intencoes();
		ArrayList<Intencao> lista = new ArrayList<>();
		lista.add(intentoComExemplo("musica_teste", "to ouvindo muito mpb ultimamente"));
		lista.add(intentoComExemplo("comida_teste", "vou cozinhar feijoada hoje"));
		raiz.setIntencoes(lista);
		return raiz;
	}

	private Intencao intentoComExemplo(String nome, String texto) {
		Exemplo e = new Exemplo();
		e.setTexto(texto);
		e.setOrigem("autor");
		ArrayList<Exemplo> exemplos = new ArrayList<>();
		exemplos.add(e);
		return new Intencao(nome, exemplos, null, new ArrayList<>(), null, null);
	}

	/**
	 * O bug: frase sem qualquer match (score 0.00) nao pode trocar o
	 * topico. Hoje o argmax de zeros ancora - este e o red.
	 */
	@Test
	public void fraseSemMatchNaoTrocaOTopico() {
		ControleResposta.carregaCache();
		ControleSessao.get().setTopicoAtual("filme");
		Intencoes raiz = baseDeFixture();

		ControleResposta.melhorIntencaoDetalhado(raiz, "zzz qqq");

		assertEquals("frase sem match nenhum trocou o topico da sessao",
				"filme", ControleSessao.get().getTopicoAtual());
	}

	/** Guarda: match de verdade (exato, 1.0) continua ancorando o topico. */
	@Test
	public void matchRealAncoraOTopico() {
		ControleResposta.carregaCache();
		ControleSessao.get().setTopicoAtual("filme");
		Intencoes raiz = baseDeFixture();

		ControleResposta.melhorIntencaoDetalhado(raiz, "to ouvindo muito mpb ultimamente");

		assertEquals("match exato deixou de ancorar o topico",
				"musica_teste", ControleSessao.get().getTopicoAtual());
	}

	/** Guarda #27: continuacao_topico real continua sem virar o topico. */
	@Test
	public void continuacaoRealNaoViraOTopico() {
		ControleResposta.carregaCache();
		ControleSessao.get().setTopicoAtual("filme");
		Intencoes raiz = new Intencoes();
		ArrayList<Intencao> lista = new ArrayList<>();
		lista.add(intentoComExemplo("continuacao_topico", "conta mais sobre isso"));
		raiz.setIntencoes(lista);

		ControleResposta.melhorIntencaoDetalhado(raiz, "conta mais sobre isso");

		assertEquals("continuacao_topico virou o topico (quebra o #27)",
				"filme", ControleSessao.get().getTopicoAtual());
	}
}
