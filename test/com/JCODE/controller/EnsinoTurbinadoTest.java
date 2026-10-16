package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.JCODE.domain.Exemplo;
import com.JCODE.domain.Intencao;
import com.JCODE.domain.Intencoes;
import com.JCODE.domain.RespostaIntencao;

import org.junit.Test;

import java.util.ArrayList;

/**
 * Spec da issue #33 (PLANO §7.3 - ultimo checkbox dos Fallbacks
 * inteligentes: "modo ensino turbinado").
 *
 * Antes (#8): ensinaEm SEMPRE jogava o par (frase, resposta) no
 * auto_aprendizado, sem olhar a base - a frase ensinada nunca alimentava
 * o classificador.
 *
 * Agora: classifica ANTES de gravar. Score >= LIMIAR (0.6 - a mesma
 * regua com que a KAIRA ja responde com confianca) -> o par cai DENTRO
 * do intento vencedor; em duvida (ou match nulo) -> auto_aprendizado,
 * como sempre - a caixa de entrada continua sendo o caminho do fracasso.
 *
 * Garantias que este spec amarra:
 *  - encaixe SEM a regua do limiar jogaria frase parecida no intent errado;
 *  - a classificacao roda ANTES do invalidaCacheIdf: ordem que garante que
 *    a 2a chamada recalcula IDF/tokens com a base ja mutada (sem NPE e sem
 *    score desatualizado no ensino acumulado);
 *  - frase sem nenhum tema da base continua caindo no inbox;
 *  - origem "autor" e texto preservados - o contrato do #8
 *    (ControleRespostaAprendizadoTest) segue verde ao lado daqui, porque a
 *    base minima dele nunca atinge o limiar.
 *
 * Base ISOLADA por teste (mesmo cuidado do spec do #8): nunca mexe no
 * intencoesCache estatico que os outros testes leem.
 *
 * Efeito colateral aceito (documentado na issue): melhorIntencaoDetalhado
 * ancora ControleSessao.topico() e reescreve o ultimoMatchLog - mesmo
 * caminho do decifrar, sem disco.
 */
public class EnsinoTurbinadoTest {

	/** Base propria: dois temas com exemplos + a caixa de entrada vazia. */
	private Intencoes baseReceita() {

		ArrayList<Exemplo> exReceita = new ArrayList<>();
		exReceita.add(new Exemplo("qual a receita de bolo de cenoura", "curadoria test"));
		exReceita.add(new Exemplo("como se faz bolo de chocolate", "curadoria test"));
		exReceita.add(new Exemplo("me passa uma receita de bolo", "curadoria test"));
		ArrayList<RespostaIntencao> respReceita = new ArrayList<>();
		respReceita.add(new RespostaIntencao("3 ovos, farinha e leite", "curadoria test", 0, 10));

		ArrayList<Exemplo> exCinema = new ArrayList<>();
		exCinema.add(new Exemplo("recomenda um filme bom pra hoje", "curadoria test"));
		ArrayList<RespostaIntencao> respCinema = new ArrayList<>();
		respCinema.add(new RespostaIntencao("tem um drama coreano bom", "curadoria test", 0, 10));

		ArrayList<Intencao> lista = new ArrayList<>();
		lista.add(new Intencao("receita", exReceita, null, respReceita, null, null));
		lista.add(new Intencao("cinema", exCinema, null, respCinema, null, null));
		lista.add(new Intencao("auto_aprendizado", new ArrayList<>(), null, new ArrayList<>(), null, null));
		return new Intencoes(lista);
	}

	private Intencao porIntento(Intencoes raiz, String intento) {
		return raiz.getIntencoes().stream()
				.filter(i -> i.getIntento().equals(intento)).findFirst().orElse(null);
	}

	/**
	 * Frase IDENTICA a um exemplo da base -> score 1.0 (deterministico, nao
	 * depende de IDF): o par ensinado tem que cair em "receita", nao no
	 * inbox.
	 */
	@Test
	public void ensinoComConfiancaAltaEncaixaNoIntentoVencedor() {
		Intencoes raiz = baseReceita();

		ControleResposta.ensinaEm(raiz, "qual a receita de bolo de cenoura",
				"3 ovos, farinha, leite e 2 cenouras");

		Intencao receita = porIntento(raiz, "receita");
		Intencao inbox = porIntento(raiz, "auto_aprendizado");

		assertTrue("frase ensinada entra no intento vencedor",
				receita.getExemplos().stream()
						.anyMatch(e -> "qual a receita de bolo de cenoura".equals(e.getTexto())
								&& "autor".equals(e.getOrigem())));
		assertTrue("resposta ensinada entra no intento vencedor",
				receita.getRespostas().stream()
						.anyMatch(r -> r.getTexto().contains("2 cenouras")
								&& "autor".equals(r.getOrigem())));
		assertEquals("encaixe nao suja a caixa de entrada", 0, inbox.getExemplos().size());
	}

	/**
	 * Sem nenhum overlap com os temas -> score 0 -> abaixo do limiar ->
	 * caminho antigo (inbox). A regua existe justamente pra duvida nao
	 * contaminar intento curado.
	 */
	@Test
	public void ensinoForaDeQualquerTemaCaiNoAutoAprendizado() {
		Intencoes raiz = baseReceita();

		ControleResposta.ensinaEm(raiz, "guitarra lunar vaporosa", "uma resposta ensinada qualquer");

		Intencao inbox = porIntento(raiz, "auto_aprendizado");
		assertEquals(1, inbox.getExemplos().size());
		assertEquals("guitarra lunar vaporosa", inbox.getExemplos().get(0).getTexto());
		assertEquals(1, inbox.getRespostas().size());
		assertEquals("autor", inbox.getRespostas().get(0).getOrigem());

		assertTrue("nenhum intento curado foi tocado",
				raiz.getIntencoes().stream()
						.filter(i -> !"auto_aprendizado".equals(i.getIntento()))
						.allMatch(i -> i.getRespostas().stream()
								.noneMatch(r -> "autor".equals(r.getOrigem()))));
	}

	/**
	 * Dois ensinos em sequencia: o 1o muta a base e invalida o cache IDF; o
	 * 2o tem que RECLASSIFICAR com a base ja mutada (recalculo completo de
	 * pesos e tokens, sem NPE e sem IDF desatualizado) e cair no intento
	 * certo de cada.
	 */
	@Test
	public void ensinosAcumuladosReclassificamSemQuebrar() {
		Intencoes raiz = baseReceita();

		ControleResposta.ensinaEm(raiz, "qual a receita de bolo de cenoura", "3 ovos extras");
		ControleResposta.ensinaEm(raiz, "recomenda um filme bom pra hoje", "um drama curto");

		Intencao receita = porIntento(raiz, "receita");
		Intencao cinema = porIntento(raiz, "cinema");
		Intencao inbox = porIntento(raiz, "auto_aprendizado");

		assertEquals("1 ensino no intento certo", 1,
				receita.getExemplos().stream().filter(e -> "autor".equals(e.getOrigem())).count());
		assertEquals(1,
				receita.getRespostas().stream().filter(r -> "autor".equals(r.getOrigem())).count());
		assertEquals(1,
				cinema.getExemplos().stream().filter(e -> "autor".equals(e.getOrigem())).count());
		assertEquals(1,
				cinema.getRespostas().stream().filter(r -> "autor".equals(r.getOrigem())).count());
		assertEquals("inbox continua vazio", 0, inbox.getExemplos().size());
	}
}
