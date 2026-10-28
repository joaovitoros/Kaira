package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.JCODE.domain.FatoSessao;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

/**
 * #68 - Declaracao de fato do usuario ("eu gosto de musica", "minha cor
 * favorita e verde", "meu time e o flamengo").
 *
 * Medido na sonda de 28/10: a triagem de fatos GRAVA (v2: gosta/nao_gosta),
 * mas so RESPONDE `ehPergunta` - a declaracao cai no classificador, que casa
 * o `lembra_*` ("voce lembra da musica que eu gosto?") e responde "me ensina?".
 * A negativa grava certo no v2 (o v1 e que fica vazio).
 *
 * A porta (#68) confirma o registro com followup (2 molduras por familia,
 * anti-repeticao) e nao ancora topico. Guardas: valor-pessoa ("gosto de voce")
 * nao vira fato, fala longa/adversativa/interrogativa nao confirma.
 */
public class DeclaracaoFatoTest {

	@Before
	public void zera() {
		ControleSessao.reset();
		ControleResposta.carregaCache();
		ControlePalavra.carregaCache();
		ControleSinonimo.carregaCache();
	}

	private String responde(String fala) {
		ControleSessao.get().setNomeUsuario("teste");
		FormulaResposta.bufferFrases = null;
		FormulaResposta.respostaEnsino = null;
		ControleFace.resposta = null;
		FormulaResposta.decifrar(fala);
		return ControleFace.resposta;
	}

	private FatoSessao fato(String chave) {
		return ControleSessao.get().getFatosV2().get(chave);
	}

	/** A resposta e uma das molduras do pool, com os slots preenchidos. */
	private boolean veioDoPool(java.util.List<String> pool, String resposta, String chave, String valor, String artigo) {
		for (String moldura : pool) {
			String esperada = moldura.replace("{v}", valor)
					.replace("{chave}", chave).replace("{artigo}", artigo);
			if (resposta.equals(esperada))
				return true;
		}
		return false;
	}

	/** "eu gosto de X" confirma o registro - nao cai no lembra_*. */
	@Test
	public void gostoDeMusicaConfirmaSemMeEnsina() {
		String r = responde("eu gosto de musica");
		assertNotNull(r);
		assertFalse("classificador respondeu por cima: " + r, r.contains("me ensina"));
		assertTrue("nao confirmou o fato: " + r,
				veioDoPool(ControleResposta.confirmacoesVerbo(), r, "gosta de", "musica", "seu"));
		assertNotNull("o fato nao foi gravado", fato("gosta de"));
		assertEquals(List.of("musica"), fato("gosta de").getGosta());
	}

	/** A negativa confirma com o sinal certo e grava em nao_gosta. */
	@Test
	public void naoGostoConfirmaNegativo() {
		String r = responde("eu nao gosto de musica");
		assertTrue("sinal errado: " + r,
				veioDoPool(ControleResposta.confirmacoesNaoGosta(), r, "gosta de", "musica", "seu"));
		assertNotNull("o fato negativo nao foi gravado", fato("gosta de"));
		assertEquals(List.of("musica"), fato("gosta de").getNaoGosta());
	}

	/** Atributo feminino usa "sua" (o genero do #55). */
	@Test
	public void favoritaUsaArtigoFeminino() {
		String r = responde("minha cor favorita e verde");
		assertTrue("genero errado: " + r,
				veioDoPool(ControleResposta.confirmacoesAtributo(), r, "cor favorita", "verde", "sua"));
		assertFalse("genero errado: " + r, r.contains("seu cor favorita"));
	}

	/** Atributo masculino usa "seu". */
	@Test
	public void favoritoUsaArtigoMasculino() {
		String r = responde("meu jogo favorito e minecraft");
		assertTrue(r, veioDoPool(ControleResposta.confirmacoesAtributo(), r, "jogo favorito", "minecraft", "seu"));
	}

	/** Elogio nao vira fato: "gosto de voce" nao grava nem confirma. */
	@Test
	public void gostoDeVoceNaoGravaNemConfirma() {
		String r = responde("gosto de voce viu");
		assertFalse("elogio virou confirmacao: " + r, r.toLowerCase().contains("anotei"));
		assertNull("valor-pessoa nao pode virar fato", fato("gosta de"));
	}

	/** Declaracao longa/ambigua segue o classificador (nao confirma). */
	@Test
	public void declaracaoLongaNaoConfirma() {
		String r = responde("eu gosto bastante de mpb e indie porque me acalma");
		assertFalse("fala longa demais pra confirmar: " + r, r.toLowerCase().contains("anotei"));
	}

	/** A confirmacao nao ancora topico. */
	@Test
	public void confirmacaoNaoAncoraTopico() {
		ControleSessao.get().setTopicoAtual("filme");
		responde("eu gosto de musica");
		assertEquals("a confirmacao nao pode trocar o topico", "filme",
				ControleSessao.get().getTopicoAtual());
	}
}
