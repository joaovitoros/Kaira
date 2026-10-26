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
 * #52 - triagem v2: variantes naturais da porta DELA, misses honestos e o
 * PADROES_FATO feminino. Mapa do T02 (RELATORIO) medido na sonda de 26/10:
 *
 *   variantes DA PORTA DELA (todas MISS hoje - roleta/limitation_ia responde):
 *     "quais sao os seus jogos favoritos?"      -> plural
 *     "qual e a sua cor favorita de verdade?"   -> filler
 *     "qual e a sua cor favorita, hein?"        -> filler
 *     "me conta qual e a sua cor favorita"      -> interrogativo nao-inicial
 *     "e a sua musica favorita, qual e?"        -> interrogativo no fim
 *     "qual e a sua comida preferida mesmo?"    -> filler
 *     "sua cor favorita e qual?"                -> ordem invertida
 *
 *   miss DELA com fato do USUARIO (o vazamento do "seu time"):
 *     user time=Gremio + "qual seu time?" -> hoje "voce torce pro Gremio!"
 *     esperado: espelho honesto ("...mas o seu time eu guardei: Gremio!")
 *
 *   miss USUARIO sem fato (hoje "Gosta do que?"):
 *     "qual minha cor favorita?" -> "Voce ainda nao me contou sua cor favorita!"
 *
 *   PADROES_FATO feminino (hoje nao grava):
 *     "minha comida favorita e lasanha" / "minha cor favorita e azul"
 *
 * Isolamento: kaira.home num temp com copia da base real, caches
 * recarregados no @After - a arvore de trabalho nunca e tocada.
 */
public class TriagemVariacoesTest {

	private Path temp;

	@Before
	public void montaBaseRealIsolada() throws Exception {
		temp = Files.createTempDirectory("kaira52");
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

	// ================= A) variantes da porta DELA =================

	/** Plural: "jogos" tem que achar a chave "jogo" na memoria dela. */
	@Test
	public void variantePluralRespondeAMemoria() {
		assertTrue("plural achou o jogo dela (Quake)",
				responde("quais sao os seus jogos favoritos?").contains("Quake"));
	}

	/** Filler "de verdade": a chave continua "cor" (nao "verdade"). */
	@Test
	public void varianteComFillerDeVerdade() {
		assertTrue("filler nao mata a porta",
				responde("qual e a sua cor favorita de verdade?").contains("verde"));
	}

	/** Filler ", hein?". */
	@Test
	public void varianteComVirgulaEHein() {
		assertTrue("virgula e hein nao matam a porta",
				responde("qual e a sua cor favorita, hein?").contains("verde"));
	}

	/** Interrogativo no meio ("me conta qual e a sua cor favorita"). */
	@Test
	public void varianteComInterrogativoNaoInicial() {
		assertTrue("interrogativo nao precisa abrir a frase",
				responde("me conta qual e a sua cor favorita").contains("verde"));
	}

	/** Interrogativo no fim ("e a sua musica favorita, qual e?"). */
	@Test
	public void varianteComInterrogativoNoFim() {
		assertTrue("interrogativo no fim tambem conta",
				responde("e a sua musica favorita, qual e?").contains("synthwave"));
	}

	/** "preferida mesmo?" - outro sufixo (preferid) + filler. */
	@Test
	public void variantePreferidaComMesmo() {
		assertTrue("preferida + mesmo acham a comida dela",
				responde("qual e a sua comida preferida mesmo?").contains("pao de queijo"));
	}

	/** Ordem invertida ("sua cor favorita e qual?"). */
	@Test
	public void varianteOrdemInvertida() {
		assertTrue("ordem invertida acha a cor dela",
				responde("sua cor favorita e qual?").contains("verde"));
	}

	// ================= B) misses =================

	/**
	 * O vazamento do T02: pergunta sobre ela, chave que ela nao tem, mas o
	 * USUARIO tem o mesmo fato. Resposta espelho - nunca o pipeline lendo
	 * o "seu" como sendo do usuario ("voce torce pro Gremio!").
	 */
	@Test
	public void missDelaRespondeEspelhoDoFatoDoUsuario() {
		ControleSessao.get().getFatos().put("time", "Gremio");

		String resposta = responde("qual seu time?");

		assertNotNull(resposta);
		assertTrue("espelho traz o fato guardado", resposta.contains("Gremio"));
		assertTrue("marca de espelho honesto", resposta.contains("guardei"));
		assertFalse("nunca o vazamento do pipeline (lembra_time)",
				resposta.contains("torce"));
	}

	/** Miss da porta USUARIO: honesto sobre nunca ter contado. */
	@Test
	public void missUsuarioRespondeHonesto() {
		String resposta = responde("qual meu signo?");

		assertNotNull(resposta);
		assertTrue("declara a fonte (nunca contou)",
				resposta.contains("ainda nao me contou"));
		assertTrue("nomeia o que falta", resposta.contains("signo"));
	}

	/** Miss da porta USUARIO sem nada conhecido: nunca inventa valor. */
	@Test
	public void missUsuarioNaoInventaValor() {
		String resposta = responde("qual minha cor favorita?");

		assertNotNull(resposta);
		assertTrue("declara a fonte", resposta.contains("ainda nao me contou"));
		assertTrue("nomeia a chave da pergunta", resposta.contains("cor favorita"));
		assertFalse("nao inventa o valor da memoria dela", resposta.contains("verde"));
	}

	// ================= C) PADROES_FATO feminino =================

	/** "minha comida favorita e lasanha" grava na sessao (hoje nao grava). */
	@Test
	public void fatoFemininoGrava() {
		responde("minha comida favorita e lasanha");

		assertEquals("lasanha", ControleSessao.get().getFatos().get("comida favorita"));
	}

	/** O fato feminino gravado responde pela moldura do usuario. */
	@Test
	public void fatoFemininoResponde() {
		responde("minha cor favorita e azul");

		String resposta = responde("qual minha cor favorita?");
		assertNotNull(resposta);
		assertTrue("responde o valor que ele contou", resposta.contains("azul"));
		assertTrue("pela moldura honesta", resposta.contains("pelo que voce me contou"));
	}

	// ================= D) sanidade (nao pode regredir) =================

	/** A canonica da #50 segue (memoria dela + moldura). */
	@Test
	public void canonicaDelaSegue() {
		assertTrue(responde("qual e o seu hobby?").contains("catalogar"));
	}

	/** A canonica do usuario segue (fato + moldura honesta). */
	@Test
	public void canonicaUsuarioSegue() {
		ControleSessao.get().getFatos().put("jogo favorito", "minecraft");

		assertTrue(responde("qual meu jogo favorito?").contains("minecraft"));
	}

	/** Declarativa continua FORA das portas (invariante da #50). */
	@Test
	public void declarativaNaoIntercepta() {
		String resposta = responde("sua cor favorita de verdade e lilas");

		assertNotNull(resposta);
		assertFalse("declarativa nao responde como memoria",
				resposta.contains("lilas"));
	}
}
