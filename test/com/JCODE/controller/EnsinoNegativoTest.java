package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.JCODE.util.Caminhos;

/**
 * #59 - captura NEGATIVA do ensino dela (o fecho da Ideia 1).
 *
 * Sonda de 27/10 (P1 armado): "sua cor nao e roxo" GRAVAVA gosta=roxo (o
 * "nao" engolido pela extracao positiva); "sua cor nao e verde" confirmava
 * "Anotado" (falso); o positivo e o segredo ja estavam certos.
 *
 * Regras: a extracao detecta a negativa primeiro e devolve a relacao;
 * gravaNaoGosta substitui nao_gosta e preserva gosta; a nao-contradicao
 * recusa com o ack proprio; o ack de absorcao e' o de sempre.
 */
public class EnsinoNegativoTest {

	private static final String FRASE_ENSINO = "como faco pra cadastrar a cnh no app do banco";

	private Path temp;

	@Before
	public void montaBaseRealIsolada() throws Exception {
		temp = Files.createTempDirectory("kaira59");
		Path recursos = temp.resolve("KAIRA").resolve("resources");
		Files.createDirectories(recursos);

		for (String nome : List.of("intencoes.json", "stopwords.txt",
				"palavras.json", "grupos_sinonimos.json", "kaira.json"))
			Files.copy(Caminhos.recurso(nome), recursos.resolve(nome));

		System.setProperty("kaira.home", temp.toString());
		Caminhos.resetar();
		recarregaTudo();
		ControleSessao.reset();
		// janela de nome FECHADA (licao do OrganicTest): usuario ja com nome
		ControleSessao.get().setNomeUsuario("teste");
		FormulaResposta.bufferFrases = null;
		FormulaResposta.respostaEnsino = null;
		ControleFace.verificarManter = false;
	}

	@After
	public void restauraOCacheRealEDepoisOTemp() throws Exception {
		FormulaResposta.bufferFrases = null;
		FormulaResposta.respostaEnsino = null;
		ControleFace.verificarManter = false;

		Caminhos.resetar();
		System.clearProperty("kaira.home");
		recarregaTudo();
		ControleSessao.reset();

		if (temp != null && Files.exists(temp)) {
			try (Stream<Path> caminhos = Files.walk(temp)) {
				caminhos.sorted(Comparator.reverseOrder()).forEach(p -> {
					try {
						Files.delete(p);
					} catch (Exception ignorada) {
					}
				});
			}
		}
	}

	private void recarregaTudo() {
		ControleResposta.carregaCache();
		ControleResposta.invalidaCacheIdf();
		ControlePalavra.carregaCache();
		ControleSinonimo.carregaCache();
		ControleKaira.carregaFatos();
	}

	private String responde(String fala) {
		ControleFace.resposta = null;
		FormulaResposta.decifrar(fala);
		return ControleFace.resposta;
	}

	private void armaP1() {
		responde(FRASE_ENSINO);
		assertNotNull("pre-condicao: o fallback arma o ensino",
				FormulaResposta.bufferFrases);
	}

	/**
	 * ACEITACAO 1 (o bug da sonda): o negativo grava em nao_gosta, NAO em
	 * gosta - e o ack e' o de absorcao de sempre.
	 */
	@Test
	public void negativoGravaNaRelacaoCerta() {
		armaP1();

		String resposta = responde("sua cor nao e roxo");

		assertTrue("o roxo esta em nao_gosta", "roxo".equals(ControleKaira.buscaNaoGosta("cor")));
		assertEquals("a relacao gosta fica intacta (verde)", "verde", ControleKaira.buscaFato("cor"));
		assertTrue("ack curado de absorcao", resposta.contains("Anotado"));
	}

	/** ACEITACAO 2: a pergunta negativa dela responde o capturado. */
	@Test
	public void aPerguntaNegativaRespondeOCapturado() {
		armaP1();
		responde("sua cor nao e roxo");

		String resposta = responde("qual cor voce nao gosta?");

		assertNotNull(resposta);
		assertTrue("responde o dislike capturado", resposta.contains("roxo"));
		assertTrue("a resposta e' uma das molduras da relacao negativa",
				ControleKaira.moldurasNaoGosta("cor").stream()
						.anyMatch(m -> resposta.contains(m.replace("{v}", "roxo"))));
	}

	/**
	 * ACEITACAO 3 (nao-contradicao): dizer que nao gosta de um valor que ja
	 * esta em gosta e' recusado com o ack proprio.
	 */
	@Test
	public void contradicaoERecusada() {
		armaP1();

		String resposta = responde("sua cor nao e verde");

		assertEquals("gosta intacta", "verde", ControleKaira.buscaFato("cor"));
		assertFalse("verde NAO entra na negativa", "verde".equals(ControleKaira.buscaNaoGosta("cor")));
		assertTrue("ack de contradicao", resposta.contains("contradizer"));
	}

	/** ACEITACAO 4: repetir o mesmo negativo e' idempotente. */
	@Test
	public void negativoRepetidoEIdempotente() {
		armaP1();
		responde("sua cor nao e cinza");   // cinza ja esta no seed

		assertEquals("segue cinza", "cinza", ControleKaira.buscaNaoGosta("cor"));
		assertEquals("gosta intacta", "verde", ControleKaira.buscaFato("cor"));
	}

	/** ACEITACAO 5: o positivo segue gravando em gosta (regressao). */
	@Test
	public void positivoSegue() {
		armaP1();

		String resposta = responde("sua cor e lilas");

		assertEquals("gravou em gosta", "lilas", ControleKaira.buscaFato("cor"));
		assertTrue("ack de absorcao", resposta.contains("Anotado"));
	}

	/** ACEITACAO 6: o segredo tambem e' recusado na forma negativa. */
	@Test
	public void segredoNegativoERecusado() {
		armaP1();

		String resposta = responde("sua senha nao e abacaxi");

		assertTrue("ack de segredo", resposta.contains("prefiro nao anotar"));
		assertFalse("nada gravou", "abacaxi".equals(ControleKaira.buscaNaoGosta("senha")));
	}
}
