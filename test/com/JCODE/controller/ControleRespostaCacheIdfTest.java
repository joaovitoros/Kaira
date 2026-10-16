package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

import com.JCODE.domain.Exemplo;
import com.JCODE.domain.Intencao;
import com.JCODE.domain.Intencoes;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Map;

/**
 * #26 (briefing P7.2) - Cache de pesosIdf e de tokens de exemplos.
 *
 * O classificador recalculava o IDF varrendo TODOS os exemplos (e
 * re-stemmizando cada um) a cada mensagem - O(N^2) com a base da
 * consultoria. O contrato:
 *
 * - mesma base (mesmo objeto Intencoes) devolve o MESMO mapa, imutavel;
 * - a base so muda no carregaCache (instancia nova, rebuild automatico
 *   por identidade) ou no ensinaEm (mutacao - precisa de invalidaCacheIdf);
 * - bases diferentes nunca se misturam (ida e volta);
 * - os VALORES do IDF nao mudam (spec ControleRespostaPesosIdfTest
 *   continua verde).
 *
 * Contratos novos: ControleResposta.invalidaCacheIdf() e a identidade
 * do mapa devolvido por pesosIdf.
 */
public class ControleRespostaCacheIdfTest {

	/** Mesma fixture de 6 exemplos da spec da issue #14. */
	private Intencoes baseSeisExemplos() {
		Intencoes raiz = new Intencoes();
		raiz.setIntencoes(new ArrayList<>());

		raiz.getIntencoes().add(intencao("saudacao", "quero saudar", "quero saudar bom dia"));
		raiz.getIntencoes().add(intencao("musica", "quero ouvir musica"));
		raiz.getIntencoes().add(intencao("tempo", "quero tempo hoje"));
		raiz.getIntencoes().add(intencao("comida", "quero comer comida"));
		raiz.getIntencoes().add(intencao("jogo", "quero jogar jogo"));

		return raiz;
	}

	private Intencao intencao(String nome, String... frases) {
		Intencao i = new Intencao();
		i.setIntento(nome);
		i.setExemplos(new ArrayList<>());
		for (String f : frases)
			i.getExemplos().add(new Exemplo(f, "autor"));
		i.setRespostas(new ArrayList<>());
		return i;
	}

	@Before
	public void carregaCaches() {
		ControleSinonimo.carregaCache();
		ControleResposta.carregaCache();
	}

	/** RED aqui: hoje cada chamada monta um HashMap novo. */
	@Test
	public void mesmaBaseDevolveOMesmoMapa() {
		Intencoes raiz = baseSeisExemplos();

		Map<String, Double> primeira = ControleResposta.pesosIdf(raiz);
		Map<String, Double> segunda = ControleResposta.pesosIdf(raiz);

		assertSame("a mesma base devolve o mesmo mapa (cache)", primeira, segunda);
		// os valores seguem o contrato da issue #14
		assertEquals("quero em todos os exemplos -> 0", 0.0, segunda.get("quero"), 1e-9);
		assertEquals("df 2 de 6 -> log 3", Math.log(3), segunda.get("saud"), 1e-9);
		assertEquals("df 1 de 6 -> log 6", Math.log(6), segunda.get("musica"), 1e-9);
	}

	/** Mutacao em runtime (ensino) precisa ser invalidada. */
	@Test
	public void invalidaCacheIdfAtualizaOsPesos() {
		Intencoes raiz = baseSeisExemplos();
		ControleResposta.pesosIdf(raiz); // aquece o cache

		// ensinaEm muta a base: df de "musica" vai de 1 para 2 (total 7)
		raiz.getIntencoes().get(1).getExemplos().add(new Exemplo("outra musica", "autor"));
		ControleResposta.invalidaCacheIdf();

		Map<String, Double> depois = ControleResposta.pesosIdf(raiz);
		assertEquals("df 2 de 7 -> log(7/2)", Math.log(7.0 / 2), depois.get("musica"), 1e-9);
		assertEquals("quero em 6 de 7 agora (o exemplo novo nao o tem) -> log(7/6)",
				Math.log(7.0 / 6), depois.get("quero"), 1e-9);
	}

	/** Guarda: ida e volta entre bases nunca contamina. */
	@Test
	public void basesDiferentesNaoSeMisturam() {
		Intencoes a = baseSeisExemplos();
		Intencoes b = new Intencoes();
		b.setIntencoes(new ArrayList<>());
		b.getIntencoes().add(intencao("musica", "quero musica"));

		Map<String, Double> pesosA = ControleResposta.pesosIdf(a);
		Map<String, Double> pesosB = ControleResposta.pesosIdf(b);

		assertNotSame("bases diferentes nao compartilham mapa", pesosA, pesosB);
		assertEquals("volta pro mapa da base A", Math.log(3), pesosA.get("saud"), 1e-9);
		assertEquals("base B: 1 exemplo -> idf 0", 0.0, pesosB.get("quero"), 1e-9);

		// cache de slot unico (uma base por vez, como no fluxo real):
		// trocar de base reconstrui - o contrato e os VALORES continuarem
		// certos na ida e volta, nao a identidade do mapa
		Map<String, Double> volta = ControleResposta.pesosIdf(a);
		assertEquals("ida e volta: pesos da base A de novo", Math.log(3), volta.get("saud"), 1e-9);
		assertEquals("ida e volta: quero volta a 0 (6 de 6)", 0.0, volta.get("quero"), 1e-9);
	}

	/** Guarda: o classificador (caminho inteiro) nao se contamina. */
	@Test
	public void classificadorNaoSeContaminaEntreBases() {
		Intencoes a = baseSeisExemplos();
		Intencoes b = new Intencoes();
		b.setIntencoes(new ArrayList<>());
		b.getIntencoes().add(intencao("musica", "quero musica"));

		ControleResposta.pesosIdf(a); // aquece com a base A

		Map<String, Intencao> r = ControleResposta.melhorIntencao(b, "quero musica");
		assertNotNull("classificador na base B devia achar algo", r);
		assertEquals("musica", r.values().iterator().next().getIntento());
	}
}
