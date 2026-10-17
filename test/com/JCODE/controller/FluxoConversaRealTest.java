package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.JCODE.domain.Intencao;
import com.JCODE.util.Normalizador;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Fluxo de conversa REAL, transcrito do conversas.log de 17/10 17:10-17:12
 * (ao vivo, pos-#41/#42/#43). Nao e fixture inventada: e a sequencia que o
 * usuario digitou e que o log registrou, turno a turno.
 *
 * O que so um FLUXO valida - todo teste unitario chama reset() e enxerga um
 * turno so, entao nenhuma suite existente cobria isto:
 *
 *   1. a MESMA frase generica resolve pra intents DIFERENTES conforme o
 *      topico da conversa: "qual seu preferido?" deu filme em 17:11:03 e
 *      musica em 17:11:52 - e o topico so chega la por causa dos turnos
 *      ANTERIORES. E a propriedade inteira do #41;
 *   2. frase exata ancora o topico pro proximo turno (#41) - sem isso a
 *      generica do turno seguinte nao tem contexto e cai no ruido;
 *   3. o desempate casa genero (seu~sua, favorita~favorito) (#42);
 *   4. o log diz o caminho que escolheu a resposta - "frase exata" x
 *      "desempate por topico" (#43).
 *
 * Reproducao do bug original (17/10 16:18:27): com topico musica, "qual o
 * seu preferido?" foi pra gostos_ia com 1.00. Neste fluxo, o turno 7 e o MESMO
 * caso - e agora resolve pra musica.
 */
public class FluxoConversaRealTest {

	@Before
	public void prepara() {
		ControleResposta.carregaCache();
		// O app carrega estes tres no ControleCache.carregaCacheGeral() no
		// startup; sem os dois ultimos o teste roda numa configuacao que nao e
		// a de producao. O sinonimo e o caso em que isso decide o desfecho:
		// no turno 17:12:20 "favorita" so casa com "favorito" VIA SINONIMO
		// (2/3 = 0.61, acima do limiar); sem o indice cai no
		// equalsIgnoreCase, so "seu" casa (1/3) e o match vira null - que e
		// exatamente o que aconteceu na 1a execucao desta spec.
		ControlePalavra.carregaCache();
		ControleSinonimo.carregaCache();
		ControleSessao.reset();
	}

	/** A sessao e estatica: sem limpar, o fluxo vaza pro proximo teste. */
	@After
	public void limpa() {
		ControleSessao.reset();
	}

	/**
	 * O mesmo caminho do FormulaResposta.decifrar ate o classificador:
	 * processa com o texto CRU (nome/fato/humor sao detectados la), tiraNome,
	 * normaliza, buscaResposta. Os degraus de comando/aposta/resposta-nome
	 * ficam de fora porque nao sao match.
	 */
	private Intencao turno(String fala) {
		ControleSessao.processa(fala);
		String limpa = ControleSessao.tiraNome(fala);
		return ControleResposta.buscaResposta(Normalizador.texto(limpa));
	}

	/** O MATCH do log: o caminho que resolveu o turno (frase exata x desempate). */
	private String match() {
		String log = ControleResposta.getUltimoMatchLog();
		assertNotNull("todo turno com match registra o log", log);
		return log;
	}

	@Test
	public void fluxoCompletoDoLogDe17DeOutubro() {

		// 17:10:48 - "oi" casa por frase exata e abre a janela do nome
		Intencao i = turno("oi");
		assertEquals("cumprimento abre a conversa", "cumprimentar", i.getIntento());
		assertTrue("match por frase exata: " + match(), match().contains("frase exata"));
		assertNotNull("o cumprimento abre a pergunta de nome",
				ControleSessao.perguntaDeNome());

		// 17:10:51 - o nome pelado so entra dentro da janela da pergunta, e a
		// resposta-nome e degrau proprio (nem passa pelo classificador)
		ControleSessao.processa("joao");
		assertEquals("nome capturado na janela da pergunta",
				"joao", ControleSessao.get().getNomeUsuario());
		assertNotNull("resposta-nome nao passa pelo classificador",
				ControleSessao.respostaDeNome("joao"));

		// 17:10:57 - frase exata de filme; o ponto e ela ANCORAR o topico
		i = turno("voce gosta de filmes?");
		assertEquals("filme por frase exata", "filme", i.getIntento());
		assertTrue("match por frase exata: " + match(), match().contains("frase exata"));
		assertEquals("frase exata ancora o topico pro proximo turno",
				"filme", ControleSessao.get().getTopicoAtual());

		// 17:11:03 - generica sem palavra de assunto: o topico anterior decide
		i = turno("qual seu preferido?");
		assertEquals("generica herda o topico filme", "filme", i.getIntento());
		assertTrue("resolveu pelo desempate, nao pelo ruido: " + match(),
				match().contains("desempate"));
		assertEquals("o desempate mantem o topico", "filme",
				ControleSessao.get().getTopicoAtual());

		// 17:11:17 - traz a palavra "musica", entao NAO e generica: o
		// classificador decide e o topico troca de verdade
		i = turno("e voce gosta de musica?");
		assertEquals("com palavra de assunto o classificador decide",
				"gostos_ia", i.getIntento());
		assertTrue("match do classificador tem a frase vencedora: " + match(),
				match().contains("frase=\"voce gosta de musica\""));
		assertEquals("troca de assunto legitima", "gostos_ia",
				ControleSessao.get().getTopicoAtual());

		// 17:11:26 - frase exata de musica volta a ancorar o topico
		i = turno("qual sua musica favorita?");
		assertEquals("musica por frase exata", "musica", i.getIntento());
		assertEquals("frase exata ancora o topico", "musica",
				ControleSessao.get().getTopicoAtual());

		// 17:11:52 - a MESMA frase generica do turno 3, agora com topico
		// musica, resolve pra OUTRO intento. E o caso exato do bug ao vivo
		// (16:18:27) que ia pra gostos_ia - aqui o desempate casa genero e
		// sinonimo (entrada {seu, preferido} x exemplo {sua, musica, favorita})
		i = turno("qual seu preferido?");
		assertEquals("a mesma frase, com topico musica, resolve pra musica",
				"musica", i.getIntento());
		assertTrue("resolveu pelo desempate: " + match(),
				match().contains("desempate"));
		assertEquals("o topico segue sendo musica", "musica",
				ControleSessao.get().getTopicoAtual());

		// 17:12:09 - voltando pro assunto: tem palavra de conteudo, o
		// classificador troca o topico sem depender do desempate
		i = turno("e os filmes, qual voce gosta?");
		assertEquals("volta pro filme pelo classificador", "filme", i.getIntento());
		assertEquals("topico troca de volta", "filme",
				ControleSessao.get().getTopicoAtual());

		// 17:12:20 - erro de digitação ("fimle"): o classificador direto nao
		// passa do limiar e o degrau de correcao entra; o match vem do texto
		// corrigido e ainda assim resolve pro topico certo
		String falaComErro = "qual seu fimle favorita?";
		ControleSessao.processa(falaComErro);
		String normalizada = Normalizador.texto(ControleSessao.tiraNome(falaComErro));
		assertNotNull("correcao de palavra devolve resposta",
				ControleCorrecao.corrigirPalavras(normalizada));
		assertTrue("match vem do texto corrigido: " + match(),
				match().contains("intento=filme"));
		assertEquals("correcao leva pro topico certo", "filme",
				ControleSessao.get().getTopicoAtual());

		// O MESMO turno de cima, mas sem o erro de digitacao. E o contraponto
		// que mostra que o degrau de correcao so e NECESSARIO quando ha erro:
		// aqui "filme" esta certo, entao o classificador casa de primeira
		// (3/3: seu + filme + favorita~favorito via sinonimo) e o match e
		// direto - nao passa pela correcao nem pelo desempate.
		i = turno("qual seu filme favorita?");
		assertEquals("sem erro, casa de primeira no intento certo",
				"filme", i.getIntento());
		String limpo = match();
		assertTrue("match direto pelo classificador: " + limpo,
				limpo.contains("intento=filme score=1.00"));
		assertTrue("nao foi nem correcao nem desempate: " + limpo,
				!limpo.contains("frase exata") && !limpo.contains("desempate"));
		assertEquals("topico continua filme", "filme",
				ControleSessao.get().getTopicoAtual());

		// 17:12:40 - generica so com variantes de genero (sua, favorita): o
		// desempate casa pelo topico sem nenhuma igualdade direta de token
		i = turno("e qual sua favorita?");
		assertEquals("generica de genero herda o topico", "filme", i.getIntento());
		assertTrue("resolveu pelo desempate: " + match(),
				match().contains("desempate"));
	}
}
