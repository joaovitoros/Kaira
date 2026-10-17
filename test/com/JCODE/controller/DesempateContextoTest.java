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
}
