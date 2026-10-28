package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotEquals;
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
 * #60 - Ideia 2 v1: molduras sorteadas + slot.
 *
 * Sonda de 28/10: a mesma pergunta dava a MESMA frase 3x (dela, negativa e
 * do usuario). O motor e' moldura curada + {v} sorteada com anti-repeticao
 * (padrao #35) - NAO o CriarResposta (roleta antiga).
 *
 * Fixtures: o seed v0.0.3 tem 2-3 molduras por relacao; o semeador e'
 * injetavel (sempre 0) pra provar o ciclo sem aleatoriedade.
 */
public class MoldurasSorteadasTest {

	/** As chaves do seed que tem relacoes. */
	private static final List<String> CHAVES = List.of(
			"cor", "musica", "cafe", "jogo", "comida", "hobby");

	private Path temp;
	private IntUnaryOperator semeadorAntes;

	@Before
	public void montaBaseRealIsolada() throws Exception {
		temp = Files.createTempDirectory("kaira60");
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

		semeadorAntes = ControleKaira.semeadorMolduras;
		ControleKaira.semeadorMolduras = n -> 0;   // determinismo: sempre o 1o
	}

	@After
	public void restauraOCacheRealEDepoisOTemp() throws Exception {
		ControleKaira.semeadorMolduras = semeadorAntes;
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

	/**
	 * ACEITACAO 1 (contrato do seed): toda relacao tem >= 2 molduras - e' a
	 * curadoria que da a variedade.
	 */
	@Test
	public void contratoDoSeed() {
		for (String chave : CHAVES) {
			assertNotNull("fato ausente: " + chave, ControleKaira.buscaFato(chave));
			assertTrue("gosta com menos de 2 molduras: " + chave,
					ControleKaira.moldurasDe(chave).size() >= 2);
			assertTrue("nao_gosta com menos de 2 molduras: " + chave,
					ControleKaira.moldurasNaoGosta(chave).size() >= 2);
		}
	}

	/**
	 * ACEITACAO 2 (anti-repeticao): com o semeador sempre 0, duas chamadas
	 * seguidas NAO repetem a moldura (o ciclo e' 0 -> 1 -> 0...).
	 */
	@Test
	public void sorteioNaoRepeteAUltima() {
		String primeira = ControleKaira.molduraDe("cor");
		String segunda = ControleKaira.molduraDe("cor");
		String terceira = ControleKaira.molduraDe("cor");

		assertNotNull(primeira);
		assertNotEquals("a moldura repetiu na sequencia", primeira, segunda);
		assertNotEquals("a moldura repetiu na sequencia", segunda, terceira);
		assertEquals("o ciclo volta ao inicio com 2+ opcoes", primeira, terceira);
	}

	/**
	 * ACEITACAO 3: a mesma pergunta 3x mantem o VALOR e varia a moldura.
	 */
	@Test
	public void aMesmaPerguntaVariaEMantemOValor() {
		String r1 = responde("qual e a sua cor favorita?");
		String r2 = responde("qual e a sua cor favorita?");
		String r3 = responde("qual e a sua cor favorita?");

		assertTrue(r1.contains("verde"));
		assertTrue(r2.contains("verde"));
		assertTrue(r3.contains("verde"));
		assertFalse("as respostas nao podem ser todas iguais",
				r1.equals(r2) && r2.equals(r3));
	}

	/** ACEITACAO 4: a negativa tambem varia, com o valor certo. */
	@Test
	public void aNegativaTambemVaria() {
		String r1 = responde("qual cor voce nao gosta?");
		String r2 = responde("qual cor voce nao gosta?");

		assertTrue(r1.contains("cinza"));
		assertTrue(r2.contains("cinza"));
		assertFalse("a negativa nao variou", r1.equals(r2));
	}

	/**
	 * ACEITACAO 5: a moldura do usuario varia o PREFIXO e preserva o artigo
	 * do #55 (o fato do usuario tem valor consistente).
	 */
	@Test
	public void molduraDoUsuarioVariaOPrefixo() {
		ControleSessao.get().getFatos().put("jogo favorito", "minecraft");

		String r1 = responde("qual meu jogo favorito?");
		String r2 = responde("qual meu jogo favorito?");

		assertTrue("valor real", r1.contains("minecraft"));
		assertTrue("valor real", r2.contains("minecraft"));
		assertTrue("artigo do #55 preservado",
				r1.contains("o seu jogo favorito e minecraft"));
		assertFalse("o prefixo nao variou", r1.equals(r2));
	}

	/** ACEITACAO 6: toda moldura do pool e' um prefixo + o sufixo do fato. */
	@Test
	public void todasAsMoldurasDoUsuarioSaoPrefixos() {
		List<String> prefixos = ControleResposta.moldurasUsuario();

		assertTrue("pool com menos de 2 prefixos", prefixos.size() >= 2);
		for (String p : prefixos) {
			assertFalse("prefixo vazio", p.isBlank());
			assertFalse("sem virgula/pontuacao final (o sufixo vem depois)",
					p.endsWith("!") || p.endsWith("."));
		}
	}

	/**
	 * ACEITACAO 7: a NEGATIVA do usuario tambem varia o prefixo (mesmo pool,
	 * sufixo da relacao negativa).
	 */
	@Test
	public void aNegativaDoUsuarioTambemVaria() {
		ControleSessao.get().getFatosV2().put("cor favorita", fatoSessao("azul", "cinza"));

		String r1 = responde("qual cor eu nao gosto?");
		String r2 = responde("qual cor eu nao gosto?");

		assertTrue("valor do dislike", r1.contains("cinza"));
		assertTrue("valor do dislike", r2.contains("cinza"));
		assertTrue("sufixo da relacao negativa", r1.contains("nao e muito"));
		assertTrue("prefixo do pool",
				ControleResposta.moldurasUsuario().stream()
						.anyMatch(p -> r1.toLowerCase().contains(p)));
		assertFalse("o prefixo nao variou", r1.equals(r2));
	}

	private FatoSessao fatoSessao(String gosta, String... naoGosta) {
		FatoSessao f = new FatoSessao();
		List<String> g = new ArrayList<>();
		if (gosta != null)
			g.add(gosta);
		f.setGosta(g);
		for (String n : naoGosta)
			f.getNaoGosta().add(n);
		return f;
	}
}
