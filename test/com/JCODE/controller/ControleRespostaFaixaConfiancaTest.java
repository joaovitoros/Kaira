package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.JCODE.domain.Exemplo;
import com.JCODE.domain.Intencao;
import com.JCODE.domain.Intencoes;
import com.JCODE.domain.MatchIntencao;
import com.JCODE.domain.RespostaIntencao;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

/**
 * Faixas de confianca (issue #16, Secao 5.2 fim): o limiar unico vira tres
 * faixas - ALTA >= LIMIAR (0.6) responde direto, MEDIUM [0.3, 0.6) aposta e
 * confirma, BAIXA < 0.3 cai no fallback. A aposta e o degrau NOVO: hoje a
 * media morre junto com a baixa no "Nao sei como responder a isso".
 *
 * Fixture: a mesma de 6 exemplos do ControleRespostaPesosIdfTest - "quero"
 * em todos (idf 0), "saudar" em 2 (log 3), "musica" em 1 (log 6). Diferenca
 * pro #14: TODAS as intencoes nascem com acoes = lista vazia (o
 * comandosResposta chama getAcoes().contains e NPEia com null) e a SAUDACAO
 * ganha exatamente UMA resposta ("bom dia") - 1 resposta faz o
 * escolheMultiRespostas ser deterministico (indice 0 sempre).
 *
 * Os numeros da faixa com a entrada "quero saudar xyz" = {quero, saud, xyz}:
 *   pesos: quero 0 + saud log3 + xyz ausente (pesoAusente log 6)
 *   "quero saudar" casa quero+saud -> log3 / (log3 + log6) = 0.38 -> MEDIA
 *   tudo mais casa so "quero" (peso 0) -> score 0
 * Entrada "quero ouvir musica": casa tudo -> 1.0 -> ALTA.
 * Entrada "zxqw plmok": nada casa -> 0.0 -> BAIXA.
 *
 * Roda com CWD na raiz do projeto (stopwords e grupos REAIS via caches).
 * A casca respostaAposta(String) usa o cache REAL: por isso o teste dela
 * usa entrada so de stopword - os tokens vaziam ANTES de olhar os dados,
 * entao o resultado nao depende do intencoes.json local.
 */
public class ControleRespostaFaixaConfiancaTest {

	/** 6 exemplos: "quero" em todos, "saudar" em 2, "musica" em 1. */
	private Intencoes baseSeisExemplos() {
		Intencoes raiz = new Intencoes();
		raiz.setIntencoes(new ArrayList<>());

		Intencao saudacao = intencao("saudacao", "quero saudar", "quero saudar bom dia");
		saudacao.getRespostas().add(new RespostaIntencao("bom dia", "autor", 0, 10));

		raiz.getIntencoes().add(saudacao);
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
		i.setAcoes(new ArrayList<>());
		return i;
	}

	@Before
	public void carregaCaches() {
		ControleSinonimo.carregaCache();
		ControleResposta.carregaCache();
	}

	/**
	 * O coracao da issue: melhorIntencaoDetalhado NAO tem portao de limiar -
	 * devolve a melhor intencao mesmo em 0.38. A frase e o score vem juntos
	 * (MatchIntencao), e o valor tem que ser EXATAMENTE log3/(log3+log6):
	 * a conta e so soma e divisao de doubles, deterministica bit a bit.
	 */
	@Test
	public void detalhadoDevolveAMelhorIntencaoSemPortaoDeLimiar() {
		MatchIntencao m = ControleResposta.melhorIntencaoDetalhado(baseSeisExemplos(), "quero saudar xyz");

		assertNotNull("sem portao, tem que devolver a melhor ate ser media", m);
		assertEquals("a palavra rara 'saudar' ancora a saudacao", "saudacao", m.getIntencao().getIntento());
		assertEquals("frase-exemplo vencedora e a mais curta (desempate por resto)",
				"quero saudar", m.getFrase());
		assertEquals("score = log3 / (log3 + log6) = 0.38 (faixa media)",
				Math.log(3) / (Math.log(3) + Math.log(6)), m.getScore(), 0.0);
	}

	/**
	 * A rede de seguridad do refactor: melhorIntencao continua com o portao
	 * de ALTA (0.6). A media 0.38 NAO pode vazar pro caminho de hoje - quem
	 * classifica alto continuam sendo so os scores >= 0.6.
	 */
	@Test
	public void melhorIntencaoContinuaComPortaoDeAlta() {
		assertNull("media nao pode vazar pro caminho alto",
				ControleResposta.melhorIntencao(baseSeisExemplos(), "quero saudar xyz"));
	}

	/**
	 * ALTA: entrada que casa tudo (score 1.0) nao aposta - responde direto
	 * pelo lerResposta (buscaResposta -> comandosResposta). respostaAposta
	 * devolve null e pronto.
	 */
	@Test
	public void faixaAltaNaoAposta() {
		Intencoes raiz = baseSeisExemplos();

		MatchIntencao m = ControleResposta.melhorIntencaoDetalhado(raiz, "quero ouvir musica");
		assertNotNull("com score 1.0 o detalhado obviamente devolve match", m);
		assertEquals("casa tudo -> 1.0", 1.0, m.getScore(), 0.0);

		assertNull("alta responde direto (lerResposta), aposta nao",
				ControleResposta.respostaAposta(raiz, "quero ouvir musica"));
	}

	/**
	 * BAIXA: nada casa (score 0.0). O detalhado devolve o pior match (0.0,
	 * sem portao), mas respostaAposta nao aposta - fallback de hoje.
	 */
	@Test
	public void faixaBaixaCaiNoFallback() {
		Intencoes raiz = baseSeisExemplos();

		MatchIntencao m = ControleResposta.melhorIntencaoDetalhado(raiz, "zxqw plmok");
		assertNotNull("sem portao devolve ate a pior (score 0.0)", m);
		assertEquals("nada casou -> score 0.0", 0.0, m.getScore(), 0.0);

		assertNull("baixa e papel do fallback (aprende), nao da aposta",
				ControleResposta.respostaAposta(raiz, "zxqw plmok"));
	}

	/**
	 * MEDIUM de verdade: a aposta vem com a resposta REAL da intencao +
	 * a confirmacao. A string tem que casar CHARACTER A CHARACTER,
	 * inclusive o acento do "voce" - e o espaco antes do "Foi".
	 */
	@Test
	public void faixaMediaApontaEConfirma() {
		String aposta = ControleResposta.respostaAposta(baseSeisExemplos(), "quero saudar xyz");

		assertNotNull("media tem que apostar", aposta);
		assertEquals("bom dia Foi isso que você quis dizer?", aposta);
	}

	/**
	 * Os dois "null" contratados do detalhado: base sem exemplos (loop nem
	 * roda) e entrada que vira tokens vazios (tudo stopword). A ultima
	 * assercao e a casca publica respostaAposta(String) - ela carrega o
	 * cache REAL, mas os tokens vaziam antes de tocar nos dados, entao e
	 * deterministica mesmo com intencoes.json sujo de aprendizado local.
	 */
	@Test
	public void semTokensOuSemExemplosDetalhadoENulo() {
		Intencoes vazia = new Intencoes();
		vazia.setIntencoes(new ArrayList<>());
		assertNull("base sem exemplos -> null",
				ControleResposta.melhorIntencaoDetalhado(vazia, "quero saudar"));

		assertNull("entrada so de stopword -> tokens vazios -> null",
				ControleResposta.melhorIntencaoDetalhado(baseSeisExemplos(), "de a o"));

		assertNull("casca com cache real e idem",
				ControleResposta.respostaAposta("de a o"));
	}
}
