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
 * #62 - G6: o comando "Executar" neutralizado.
 *
 * Codigo antigo (lido em 28/10): Runtime.exec(fraseComando[1]) sem
 * confirmacao, so a primeira palavra do caminho e JOptionPane modal no
 * erro. A v1 remove a execucao e responde a recusa curada - o comando
 * nao e mais uma acao.
 */
public class ComandoSeguroTest {

	private Path temp;

	@Before
	public void montaBaseRealIsolada() throws Exception {
		temp = Files.createTempDirectory("kaira62");
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

	/** ACEITACAO 1: o comando nao executa - a recusa curada responde. */
	@Test
	public void oComandoRecusa() {
		String resposta = responde("executar notepad");

		assertNotNull(resposta);
		assertTrue("recusa curada", resposta.contains("nao faco"));
		assertFalse("nada de execucao", resposta.contains("Executando o programa"));
	}

	/** ACEITACAO 2: caminho completo (com espacos) nao quebra - recusa igual. */
	@Test
	public void caminhoCompletoNaoQuebra() {
		String resposta = responde("executar C:\\Windows\\System32\\notepad.exe");

		assertNotNull(resposta);
		assertTrue("recusa curada", resposta.contains("nao faco"));
	}

	/** ACEITACAO 3: "executar" sozinho nao estoura. */
	@Test
	public void comandoSozinhoNaoEstoura() {
		String resposta = responde("executar");

		assertNotNull(resposta);
		assertTrue("recusa curada", resposta.contains("nao faco"));
	}

	/** ACEITACAO 4: caixa alta tambem e' reconhecida. */
	@Test
	public void caixaAltaReconhecida() {
		assertTrue(ControleComandos.validaComandos("Executar notepad"));
	}

	/**
	 * ACEITACAO 5: "executar" no MEIO da frase nao vira comando (o gatilho
	 * e' o inicio) - a frase segue o pipeline normal.
	 */
	@Test
	public void noMeioNaoViraComando() {
		String resposta = responde("como executar isso?");

		assertNotNull(resposta);
		assertFalse("nao e o comando", resposta.contains("nao faco"));
	}

	/** ACEITACAO 6: null/branco seguros (a UI nunca e' pre-condicao). */
	@Test
	public void nulosSeguros() {
		assertFalse(ControleComandos.validaComandos(null));
		assertFalse(ControleComandos.validaComandos(""));
		assertFalse(ControleComandos.validaComandos("   "));
	}
}
