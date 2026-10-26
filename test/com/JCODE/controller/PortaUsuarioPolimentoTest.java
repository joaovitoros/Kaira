package com.JCODE.controller;

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
 * #55 - polimento da porta USUARIO. Sonda de 26/10 (base real isolada):
 *
 *   fatos cor favorita=azul   + "qual minha cor favorita?"
 *     -> "pelo que voce me contou, O SEU cor favorita e azul!"   (genero errado)
 *   fatos comida favorita=lasanha + "qual minha comida favorita?"
 *     -> "... O SEU comida favorita e lasanha!"                  (genero errado)
 *   nomeUsuario=Karin + "qual meu nome?"
 *     -> "Voce ainda nao me contou seu nome!"                    (honesto FALSO)
 *   nomeUsuario=null  + "qual meu nome?" -> honesto (correto)
 *
 * Regras da #55:
 *   (1) o artigo da moldura sai do genero da chave: "favorita"/"preferida"
 *       -> "a sua"; demais -> "o seu";
 *   (2) "qual meu nome?" com nomeUsuario preenchido responde o nome -
 *       nunca o honesto falso;
 *   (3) masculinas e nome desconhecido seguem como hoje (guardas).
 */
public class PortaUsuarioPolimentoTest {

	private Path temp;

	@Before
	public void montaBaseRealIsolada() throws Exception {
		temp = Files.createTempDirectory("kaira55");
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

	/** Moldura FEMININA: cor favorita -> "a sua cor favorita e azul!". */
	@Test
	public void molduraFemininaCorreta() {
		ControleSessao.get().getFatos().put("cor favorita", "azul");

		String resposta = responde("qual minha cor favorita?");

		assertNotNull(resposta);
		assertTrue("artigo feminino correto",
				resposta.contains("a sua cor favorita e azul"));
	}

	/** Moldura FEMININA: comida favorita. */
	@Test
	public void molduraFemininaComida() {
		ControleSessao.get().getFatos().put("comida favorita", "lasanha");

		assertTrue("artigo feminino correto",
				responde("qual minha comida favorita?")
						.contains("a sua comida favorita e lasanha"));
	}

	/** Moldura MASCULINA segue: jogo favorito. */
	@Test
	public void molduraMasculinaSegue() {
		ControleSessao.get().getFatos().put("jogo favorito", "minecraft");

		assertTrue("artigo masculino preservado",
				responde("qual meu jogo favorito?")
						.contains("o seu jogo favorito e minecraft"));
	}

	/** Moldura MASCULINA simples segue: time. */
	@Test
	public void molduraMasculinaSimplesSegue() {
		ControleSessao.get().getFatos().put("time", "Gremio");

		assertTrue("segue como hoje",
				responde("qual meu time?").contains("o seu time e Gremio"));
	}

	/**
	 * ACEITACAO: nome conhecido responde o nome (hoje: honesto falso).
	 */
	@Test
	public void nomeConhecidoResponde() {
		ControleSessao.get().setNomeUsuario("Karin");

		String resposta = responde("qual meu nome?");

		assertNotNull(resposta);
		assertTrue("diz o nome que ela sabe", resposta.contains("Karin"));
		assertFalse("nunca o honesto falso",
				resposta.contains("ainda nao me contou"));
	}

	/** Sem nome conhecido, o honesto continua (guard). */
	@Test
	public void nomeDesconhecidoSegueHonesto() {
		ControleSessao.get().setNomeUsuario(null);

		String resposta = responde("qual meu nome?");

		assertNotNull(resposta);
		assertTrue("honesto quando nao sabe",
				resposta.contains("ainda nao me contou"));
	}
}
