package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.JCODE.domain.Exemplo;
import com.JCODE.domain.Intencao;
import com.JCODE.domain.Intencoes;
import com.JCODE.domain.MatchIntencao;
import com.JCODE.domain.Sessao;
import com.JCODE.domain.Turno;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

/**
 * Sessao de conversa em memoria (issue #19, Secao 6.1) - abre a Fase 2:
 * a KAIRA lembra quem e o usuario, do que ta falando e o que ja foi dito.
 *
 * As 3 regras desta issue:
 *  - detectar NOME por regex curada ("meu nome e X", "me chamo X", "sou o X",
 *    "pode me chamar de X") e perguntar "Como posso te chamar?" uma unica vez;
 *  - detectar FATOS ("eu gosto de minecraft" -> fatos["gosta de"]="minecraft");
 *  - TOPICO guardado a cada intencao classificada (hook no
 *    melhorIntencaoDetalhado) - null nunca limpa a ultima significativa.
 * Persistencia (sessao.json) e a issue #20.
 *
 * Cada teste comeca com reset() - Sessao e estado static compartilhado.
 * Os tests de topico usam o matcher REAL com fixture propria (como o #18).
 */
public class ControleSessaoTest {

	@Before
	public void limpaSessao() {
		ControleSessao.reset();
	}

	/**
	 * Os 4 padroes do plano, um por processo (cada um precisa de sessao
	 * nova pra provar o que foi capturado). A grafia vem CRUA do usuario -
	 * "Joao" sem acento seria outra pessoa.
	 */
	@Test
	public void detectaNomeNosQuatroPadroes() {
		ControleSessao.processa("meu nome é João");
		assertEquals("João", ControleSessao.get().getNomeUsuario());

		ControleSessao.reset();
		ControleSessao.processa("me chamo Ana");
		assertEquals("Ana", ControleSessao.get().getNomeUsuario());

		ControleSessao.reset();
		ControleSessao.processa("sou o Pedro");
		assertEquals("Pedro", ControleSessao.get().getNomeUsuario());

		ControleSessao.reset();
		ControleSessao.processa("pode me chamar de Zé");
		assertEquals("Zé", ControleSessao.get().getNomeUsuario());
	}

	/**
	 * A pergunta "Como posso te chamar?" e de UMA vez so (consome) e so
	 * enquanto nao ha nome: ja com nome extraido, nunca pergunta.
	 */
	@Test
	public void perguntouDeNomeUmaSoVezENaoComNomeJaSalvo() {
		String primeira = ControleSessao.perguntaDeNome();
		assertEquals("Como posso te chamar?", primeira);
		assertNull("consome - segunda chamada nao repete", ControleSessao.perguntaDeNome());

		ControleSessao.reset();
		ControleSessao.processa("me chamo Ana");
		assertNull("quem ja tem nome nao ouve a pergunta", ControleSessao.perguntaDeNome());
	}

	/**
	 * Fato da v1: "gosto de" -> valor depois do padrao, sem pontuacao final.
	 * Frase sem o padrao nao inventa fato nenhum.
	 */
	@Test
	public void detectaFatoGostaDe() {
		ControleSessao.processa("eu gosto de minecraft");
		assertEquals("minecraft", ControleSessao.get().getFatos().get("gosta de"));

		ControleSessao.reset();
		ControleSessao.processa("bom dia");
		assertTrue("frase sem padrao de fato deixa o mapa vazio",
				ControleSessao.get().getFatos().isEmpty());
	}

	/**
	 * Tópico = última intenção classificada, via hook no matcher real
	 * (melhorIntencaoDetalhado chama ControleSessao.topico). Match null
	 * (entrada sem tokens) não limpa o tópico anterior.
	 */
	@Test
	public void topicoACadaIntencaoClassificada() {
		Intencoes raiz = new Intencoes();
		raiz.setIntencoes(new ArrayList<>());
		raiz.getIntencoes().add(intencao("musica_favorita", "musica"));

		// o hook roda DENTRO do detalhado: chamar e ja ter topico
		MatchIntencao m = ControleResposta.melhorIntencaoDetalhado(raiz, "musica");
		assertNotNull("match tem que existir", m);
		assertEquals("musica_favorita", ControleSessao.get().getTopicoAtual());

		// entrada vazia devolve null e NAO limpa a ultima intencao significativa
		assertNull(ControleResposta.melhorIntencaoDetalhado(raiz, ""));
		assertEquals("null nao limpa o topico", "musica_favorita",
				ControleSessao.get().getTopicoAtual());
	}

	/**
	 * Historico corta nas ultimas 20 (sobram as RECENTES - as 5 primeiras
	 * somem); turnos conta TROCAS (1 por fala do usuario, nao por turno da
	 * KAIRA); vazio/null nao contam (o comando chama enviarResposta com "").
	 */
	@Test
	public void historicoLimitaA20ETurnosContamTrocas() {
		ControleSessao.processa(null);
		ControleSessao.processa("");
		assertEquals("vazio nao conta turno", 0, ControleSessao.get().getTurnos());

		for (int i = 1; i <= 25; i++)
			ControleSessao.processa("mensagem " + i);

		Sessao s = ControleSessao.get();
		assertEquals(25, s.getTurnos());
		assertEquals("historico corta em " + Sessao.MAX_HISTORICO, Sessao.MAX_HISTORICO,
				s.getHistorico().size());
		assertEquals("sobram as recentes: a 1ª sobrevivente e a 6ª",
				"mensagem 6", s.getHistorico().get(0).getTexto());
		assertEquals("usuario", s.getHistorico().get(0).getAutor());

		ControleSessao.anotaResposta("ok, anotado");
		Turno kaira = s.getHistorico().get(s.getHistorico().size() - 1);
		assertEquals("kaira", kaira.getAutor());
		assertEquals("ok, anotado", kaira.getTexto());
		assertEquals("turno da KAIRA nao conta troca", 25, s.getTurnos());
	}

	/**
	 * Expansão curada de fatos (out/2026): mora/trabalha/nasceu + a regra
	 * de DOIS grupos "meu X favorito é Y" — a chave vem do texto do
	 * usuário ("jogo favorito"), a tabela não precisa adivinhar.
	 */
	@Test
	public void detectaFatosNovos() {
		ControleSessao.processa("eu moro em São Paulo");
		assertEquals("São Paulo", ControleSessao.get().getFatos().get("mora em"));

		ControleSessao.reset();
		ControleSessao.processa("meu jogo favorito é minecraft");
		assertEquals("minecraft", ControleSessao.get().getFatos().get("jogo favorito"));

		ControleSessao.reset();
		ControleSessao.processa("eu trabalho com programação");
		assertEquals("programação", ControleSessao.get().getFatos().get("trabalha com"));

		ControleSessao.reset();
		ControleSessao.processa("nasci em Belo Horizonte");
		assertEquals("Belo Horizonte", ControleSessao.get().getFatos().get("nasceu em"));
	}

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
