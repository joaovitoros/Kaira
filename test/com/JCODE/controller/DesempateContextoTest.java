package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.JCODE.domain.Exemplo;
import com.JCODE.domain.Intencao;
import com.JCODE.domain.Intencoes;
import com.JCODE.util.Normalizador;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

/**
 * Spec da issue #41 (§5.1) - desempate por contexto: quando a frase nao tem
 * NENHUMA palavra especifica (so tokens genericos), o topico atual decide.
 *
 * O fato (log real de uso, 17/10): no meio do papo de filme, "e qual seu
 * preferido?" -> tokens [qual, seu, prefer] -> gostos_ia vencia com 1.00 no
 * exemplo "seu passatempo preferido" (match exato de token generico). O
 * certo: filme (exemplo "qual seu filme favorito").
 *
 * Determinismo: fixture propria (sem depender da base) + um teste de
 * reproducao real-base (o caso de uso real), topico guardado/restaurado.
 */
public class DesempateContextoTest {

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
		lista.add(intentoComExemplo("gostos_ia_teste", "seu passatempo preferido"));
		lista.add(intentoComExemplo("filme_teste", "qual seu filme favorito"));
		lista.add(intentoComExemplo("musica_teste", "to ouvindo muito mpb ultimamente"));
		raiz.setIntencoes(lista);
		return raiz;
	}

	private Intencao intentoComExemplo(String nome, String texto) {
		Exemplo e = new Exemplo(texto, "autor");
		ArrayList<Exemplo> exemplos = new ArrayList<>();
		exemplos.add(e);
		return new Intencao(nome, exemplos, null, new ArrayList<>(), null, null);
	}

	/** Frase so com genericos, SEM topico na sessao, cai no vencedor normal. */
	@Test
	public void fraseGenericaSemTopicoCaiNoVencedorNormal() {
		ControleResposta.carregaCache();
		ControleSessao.get().setTopicoAtual(null);

		Intencao vencida = ControleResposta.buscaResposta(baseDeFixture(), "qual seu preferido");

		assertEquals("sem topico o token generico decide como sempre",
				"gostos_ia_teste", vencida.getIntento());
	}

	/** Com topico na sessao, a frase so de genericos segue o contexto. */
	@Test
	public void fraseGenericaComTopicoSegueOContexto() {
		ControleResposta.carregaCache();
		ControleSessao.get().setTopicoAtual("filme_teste");

		Intencao vencida = ControleResposta.buscaResposta(baseDeFixture(), "qual seu preferido");

		assertEquals("frase so com genericos tem que seguir o topico atual",
				"filme_teste", vencida.getIntento());
	}

	/** Frase com palavra especifica ignora o contexto: o token conteudo manda. */
	@Test
	public void fraseComPalavraEspecificaIgnoraOContexto() {
		ControleResposta.carregaCache();
		ControleSessao.get().setTopicoAtual("filme_teste");

		Intencao vencida = ControleResposta.buscaResposta(baseDeFixture(),
				"to ouvindo muito mpb ultimamente");

		assertEquals("palavra especifica nao pode ser sequestrada pelo contexto",
				"musica_teste", vencida.getIntento());
	}

	/** Reproducao do caso real (log 17/10): base real, topico de filme. */
	@Test
	public void reproducaoRealQualSeuPreferido() {
		ControleResposta.carregaCache();
		ControleSessao.get().setTopicoAtual(null);

		assertEquals("sem topico o caso real continua em gostos_ia (guarda)",
				"gostos_ia",
				ControleResposta.buscaResposta("qual seu preferido").getIntento());

		ControleSessao.get().setTopicoAtual("filme");
		assertEquals("com topico de filme o genericos segue o assunto",
				"filme",
				ControleResposta.buscaResposta("qual seu preferido").getIntento());
	}

	/** Reproducao ao vivo (17/10): a frase exata tem que ancorar o topico.
	 *  "voce gosta de filmes?" casa por frase exata e SAIA sem gravar topico;
	 *  a proxima generica ("qual seu preferido?") ficava sem contexto e o
	 *  gostos_ia sequestrava o assunto com 1.00 em "seu passatempo preferido".
	 *
	 *  O anchor mora no buscaResposta (caminho do app), nao no fraseExata
	 *  (caminho so do avaliador). */
	@Test
	public void fraseExataAncoraTopicoProProximoTurno() {
		ControleResposta.carregaCache();
		ControleSessao.get().setTopicoAtual(null);

		Intencao exata = ControleResposta.buscaResposta(Normalizador.texto("voce gosta de filmes?"));
		assertEquals("frase exata de filme", "filme", exata.getIntento());
		assertEquals("frase exata tem que ancorar o topico",
				"filme", ControleSessao.get().getTopicoAtual());

		assertEquals("generica segue o topico que a frase exata ancorou",
				"filme", ControleResposta.buscaResposta("qual seu preferido").getIntento());
	}

	/** Reproducao ao vivo (17/10 16:18): topico musica + generica com genero/
	 *  sinonimo diferente do exemplo. A base tem SO UM exemplo de preferencia
	 *  em musica - "qual sua musica favorita" -> {sua, musica, favorita}; a
	 *  entrada "qual seu preferido" -> {seu, preferido}. Sem genero (seu~sua) e
	 *  sinonimo (preferido~favorito) no desempate, nenhum token casa -> null ->
	 *  gostos_ia sequestra com 1.00 em "seu passatempo preferido". */
	@Test
	public void desempateCasaGeneroESinonimoNaBaseReal() {
		ControleResposta.carregaCache();
		ControleSessao.get().setTopicoAtual("musica");

		assertEquals("genero supletivo (seu~sua) tem que casar no desempate",
				"musica",
				ControleResposta.buscaResposta("qual seu preferido").getIntento());

		ControleSessao.get().setTopicoAtual("musica");
		assertEquals("sinonimo+genero (favorito~favorita) tem que casar no desempate",
				"musica",
				ControleResposta.buscaResposta("qual seu favorito").getIntento());
	}

	/** Fixture isolada da base: genero/sinonimo e o UNICO caminho de match
	 *  (a entrada nao compartilha token nenhum com o exemplo por igualdade). */
	@Test
	public void desempateCasaGeneroSemIgualdadeDireta() {
		ControleResposta.carregaCache();
		ControleSessao.get().setTopicoAtual("carro_teste");

		Intencao vencida = ControleResposta.buscaResposta(baseDeGenero(), "qual seu preferido");

		assertNotNull("frase so de genericos tem que resolver pelo topico", vencida);
		assertEquals("genero/sinonimo no desempate", "carro_teste", vencida.getIntento());
	}

	private Intencoes baseDeGenero() {
		Intencoes raiz = new Intencoes();
		ArrayList<Intencao> lista = new ArrayList<>();
		lista.add(intentoComExemplo("carro_teste", "qual sua cor de carro favorita"));
		lista.add(intentoComExemplo("outro_teste", "seu colega chegou cedo"));
		raiz.setIntencoes(lista);
		return raiz;
	}

	/** §5.2 (borda restante) - topico SEM nenhum exemplo de favorito/
	 *  preferido. O desempate nao tem o que casar e devolve null; o
	 *  classificador entra e vence (aqui gostos_ia_teste), MAS nao pode
	 *  HERDAR o topico do usuario. A pergunta generica e sobre o assunto
	 *  dele, entao o assunto tem que sobreviver.
	 *
	 *  Antes do fix: o ancoraTopico gravava o vencedor do classificador e
	 *  APAGAVA o topico do usuario - a conversa virava sobre gostos e o
	 *  tema sumia (sequestro, o mesmo peco do #41 por outro caminho). */
	@Test
	public void genericaSemExemploDeFavoritoNaoSequestraOTopico() {
		ControleResposta.carregaCache();
		ControleSessao.get().setTopicoAtual("historia_teste");

		Intencao vencida = ControleResposta.buscaResposta(baseSemFavorito(), "qual seu preferido");

		assertNotNull("a pergunta generica ainda tem match", vencida);
		assertEquals("o topico do usuario sobrevive a generica sem favorito",
				"historia_teste", ControleSessao.get().getTopicoAtual());
	}

	/** A MESMA borda na BASE REAL. Medido: 82 dos 98 intents nao tem nenhum
	 *  exemplo de favorito/preferido; historia e clima estao entre eles e
	 *  existem de verdade. Com o fix, os dois sobrevivem a pergunta
	 *  generica - sem o fix, os dois viravam gostos_ia. */
	@Test
	public void bordaRealTopicoSemFavoritoNaoSequestrado() {
		ControleResposta.carregaCache();

		for (String topico : new String[] { "historia", "clima" }) {
			ControleSessao.get().setTopicoAtual(topico);
			ControleResposta.buscaResposta(Normalizador.texto("qual seu preferido"));
			assertEquals("topico " + topico + " sobrevive a generica sem favorito",
					topico, ControleSessao.get().getTopicoAtual());
		}
	}

	private Intencoes baseSemFavorito() {
		Intencoes raiz = new Intencoes();
		ArrayList<Intencao> lista = new ArrayList<>();
		// o topico do usuario: exemplo SEM nenhuma variante de favorito, entao
		// o desempate nao tem o que casar com {seu, preferido}
		lista.add(intentoComExemplo("historia_teste", "eu adoro historia antiga"));
		lista.add(intentoComExemplo("gostos_ia_teste", "seu passatempo preferido"));
		raiz.setIntencoes(lista);
		return raiz;
	}
}
