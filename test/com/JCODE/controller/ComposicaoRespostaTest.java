package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntUnaryOperator;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.JCODE.domain.FatoSessao;
import com.JCODE.util.Caminhos;

/**
 * #61 - Ideia 2 v2: a composicao da resposta (abertura + fato + followup).
 *
 * Baseline (28/10): a resposta de memoria e' moldura + slot (o followup do
 * #25 a cada 3 turnos, colado no fim). A v2 embrulha: abertura curada
 * (nunca pergunta) + o fato capitalizado; a inclusao e' sorteada e a
 * abertura tem anti-repeticao. Misses/honestos NAO compoem.
 */
public class ComposicaoRespostaTest {

	private Path temp;
	private IntUnaryOperator semeadorAberturaAntes;
	private IntUnaryOperator semeadorMoldurasAntes;

	@Before
	public void montaBaseRealIsolada() throws Exception {
		temp = Files.createTempDirectory("kaira61");
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

		semeadorAberturaAntes = ControleResposta.semeadorAbertura;
		semeadorMoldurasAntes = ControleKaira.semeadorMolduras;
		ControleResposta.semeadorAbertura = n -> 0;   // sempre compoe
		ControleKaira.semeadorMolduras = n -> 0;
	}

	@After
	public void restauraOCacheRealEDepoisOTemp() throws Exception {
		ControleResposta.semeadorAbertura = semeadorAberturaAntes;
		ControleKaira.semeadorMolduras = semeadorMoldurasAntes;
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

	/** O prefixo de abertura presente na resposta, ou null. */
	private String aberturaDe(String resposta) {
		for (String a : ControleResposta.aberturas())
			if (resposta.startsWith(a))
				return a;
		return null;
	}

	/** ACEITACAO 1: o pool e' limpo - >= 3 aberturas e NENHUMA com "?". */
	@Test
	public void poolLimpo() {
		List<String> aberturas = ControleResposta.aberturas();

		assertTrue("pool com menos de 3 aberturas", aberturas.size() >= 3);
		for (String a : aberturas) {
			assertFalse("abertura vazia", a.isBlank());
			assertFalse("abertura com pergunta (metralhadora): " + a,
					a.contains("?"));
		}
	}

	/** ACEITACAO 2: com a abertura ligada, a resposta compoe (e mantem o valor). */
	@Test
	public void aAberturaCompoe() {
		String resposta = responde("qual e a sua cor favorita?");

		assertNotNull(resposta);
		assertNotNull("nao compôs com a abertura", aberturaDe(resposta));
		assertTrue("o valor segue", resposta.contains("verde"));
	}

	/** ACEITACAO 3: com a abertura desligada, a resposta e' a do v1. */
	@Test
	public void semAbertura() {
		ControleResposta.semeadorAbertura = n -> 1;   // nunca compoe

		String resposta = responde("qual e a sua cor favorita?");

		assertNotNull(resposta);
		assertEquals("nao devia ter abertura", null, aberturaDe(resposta));
		assertTrue("o valor segue", resposta.contains("verde"));
	}

	/** ACEITACAO 4: a abertura nao repete a ultima. */
	@Test
	public void antiRepeticaoDaAbertura() {
		String a1 = aberturaDe(responde("qual e a sua cor favorita?"));
		String a2 = aberturaDe(responde("qual e a sua cor favorita?"));

		assertNotNull(a1);
		assertNotNull(a2);
		assertFalse("a abertura repetiu", a1.equals(a2));
	}

	/** ACEITACAO 5: a negativa dela tambem compoe. */
	@Test
	public void aNegativaCompoe() {
		String resposta = responde("qual cor voce nao gosta?");

		assertNotNull(aberturaDe(resposta));
		assertTrue("o valor negativo", resposta.contains("cinza"));
	}

	/** ACEITACAO 6: a resposta do usuario compoe (com o artigo do #55). */
	@Test
	public void aDoUsuarioCompoe() {
		ControleSessao.get().getFatosV2().put("jogo favorito", fatoSessao("minecraft"));

		String resposta = responde("qual meu jogo favorito?");

		assertNotNull(aberturaDe(resposta));
		assertTrue("o valor real", resposta.contains("minecraft"));
		assertTrue("o artigo do #55", resposta.contains("o seu jogo favorito"));
	}

	/**
	 * ACEITACAO 7 (invariante): no maximo 1 "?" por resposta - a abertura
	 * nunca pergunta e o followup do #25 e' um so.
	 */
	@Test
	public void noMaximoUmaPergunta() {
		for (int i = 0; i < 4; i++) {
			String resposta = responde("qual e a sua cor favorita?");
			long perguntas = resposta.chars().filter(c -> c == '?').count();
			assertTrue("mais de uma pergunta na resposta: " + resposta,
					perguntas <= 1);
		}
	}

	private FatoSessao fatoSessao(String gosta) {
		FatoSessao f = new FatoSessao();
		List<String> g = new ArrayList<>();
		g.add(gosta);
		f.setGosta(g);
		return f;
	}
}
