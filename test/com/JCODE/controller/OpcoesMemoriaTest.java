package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
 * #57 - o fluxo de opcoes da memoria dela (a parte interativa da Ideia 1).
 *
 * Sonda de 26/10 (base real isolada):
 *   "qual e a sua serie favorita?" -> matcher responde besteira (sem estado)
 *   "qual e a sua banda favorita?" -> aposta sem sentido
 *   "qual e a sua cor favorita?"   -> hit da memoria (baseline)
 *   "qual e a sua senha do banco?" -> fallback + arma o ensino (secreto!)
 *
 * Fluxo: miss DELA -> convite ("Me diz quais sao as opcoes?") + arma a chave;
 * a proxima fala e' a LISTA (2+ itens curtos com virgula/e/ou) -> sorteia um,
 * grava em gosta e confirma. Nao-lista -> rejeita e mantem; 2a falha/comando
 * cancela. Item do lado oposto (#56) sai do pool. Segredo (#53) nao convida.
 */
public class OpcoesMemoriaTest {

	private Path temp;
	private Path arquivoKaira;
	private Path arquivoIntencoes;

	@Before
	public void montaBaseRealIsolada() throws Exception {
		temp = Files.createTempDirectory("kaira57");
		Path recursos = temp.resolve("KAIRA").resolve("resources");
		Files.createDirectories(recursos);

		for (String nome : List.of("intencoes.json", "stopwords.txt",
				"palavras.json", "grupos_sinonimos.json", "kaira.json"))
			Files.copy(Caminhos.recurso(nome), recursos.resolve(nome));
		arquivoKaira = recursos.resolve("kaira.json");
		arquivoIntencoes = recursos.resolve("intencoes.json");

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

	private String lerKaira() throws Exception {
		return Files.readString(arquivoKaira);
	}

	private String lerIntencoes() throws Exception {
		return Files.readString(arquivoIntencoes);
	}

	/**
	 * ACEITACAO 1: a pergunta dela sem memoria vira CONVITE e arma a chave.
	 * Hoje: o matcher responde besteira ("serie leve pra recomendar...").
	 */
	@Test
	public void missDelaConvidaEOpcoes() {
		String resposta = responde("qual e a sua serie favorita?");

		assertNotNull(resposta);
		assertTrue("o convite das opcoes", resposta.contains("quais sao as opcoes"));
		assertEquals("a chave fica armada esperando a lista",
				"serie", ControleSessao.getChaveOpcoes());
	}

	/**
	 * ACEITACAO 2: a lista de 3 e' sorteada, gravada e a proxima pergunta
	 * responde o valor escolhido.
	 */
	@Test
	public void listaDeTresSorteiaEGrava() throws Exception {
		responde("qual e a sua serie favorita?");   // arma
		String resposta = responde("dark, breaking bad, lost");

		assertNotNull(resposta);
		String kaira = lerKaira();
		boolean algum = kaira.contains("dark")
				|| kaira.contains("breaking bad") || kaira.contains("lost");
		assertTrue("um dos itens foi gravado", algum);
		assertNull("o estado foi consumido", ControleSessao.getChaveOpcoes());

		String pergunta = responde("qual e a sua serie favorita?");
		assertTrue("a proxima pergunta responde o gravado",
				pergunta.contains("dark") || pergunta.contains("breaking bad")
						|| pergunta.contains("lost"));
	}

	/** ACEITACAO 3: lista de 2 com "e" tambem vale. */
	@Test
	public void listaComE() throws Exception {
		responde("qual e a sua serie favorita?");
		String resposta = responde("dark e breaking bad");

		assertNotNull(resposta);
		String kaira = lerKaira();
		assertTrue(kaira.contains("dark") || kaira.contains("breaking bad"));
		assertNull(ControleSessao.getChaveOpcoes());
	}

	/**
	 * ACEITACAO 4 (invariante #56): item que ja esta em nao_gosta sai do pool
	 * antes do sorteio. Fixture: cor.nao_gosta = [cinza]; a lista e'
	 * "cinza, roxo" -> so roxo pode ser escolhido.
	 */
	@Test
	public void contradicaoSaiDoPool() throws Exception {
		ControleSessao.armaOpcoes("cor");
		responde("cinza, roxo");

		assertEquals("o roxo foi gravado (o cinza nao podia)",
				"roxo", ControleKaira.buscaFato("cor"));
		assertEquals("a nao_gosta intacta", "cinza", ControleKaira.buscaNaoGosta("cor"));
	}

	/**
	 * ACEITACAO 5: nao-lista ("abobrinha") e' rejeitada, o estado fica armado
	 * e uma segunda tentativa de lista funciona.
	 */
	@Test
	public void rejeicaoMantemEEstatado() throws Exception {
		responde("qual e a sua serie favorita?");
		String rejeicao = responde("abobrinha");

		assertTrue("explica a forma da lista", rejeicao.contains("lista"));
		assertEquals("o estado continua armado", "serie", ControleSessao.getChaveOpcoes());

		String segunda = responde("dark, lost");
		assertNotNull(segunda);
		assertNull("a segunda tentativa valida consome", ControleSessao.getChaveOpcoes());
	}

	/** ACEITACAO 6: na segunda falha o estado e' cancelado. */
	@Test
	public void segundaFalhaCancela() {
		ControleSessao.armaOpcoes("serie");

		responde("abobrinha");
		String segunda = responde("nao sei");

		assertNotNull(segunda);
		assertNull("o estado foi cancelado", ControleSessao.getChaveOpcoes());
	}

	/**
	 * ACEITACAO 7 (precedencia): o fluxo de opcoes vence a pergunta pendente
	 * (#41) - a lista vira memoria, nao absorcao.
	 */
	@Test
	public void opcoesVencemAPerguntaPendente() throws Exception {
		ControleSessao.armaPergunta("trabalho_usuario", "E amanha, mais leve ou mais puxado?");
		ControleSessao.armaOpcoes("serie");
		String intencoesAntes = lerIntencoes();

		responde("dark, lost");

		assertEquals("nada absorvido no intencoes.json",
				intencoesAntes, lerIntencoes());
		String kaira = lerKaira();
		assertTrue("a serie foi gravada na memoria",
				kaira.contains("dark") || kaira.contains("lost"));
		assertNull("opcoes consumidas", ControleSessao.getChaveOpcoes());
	}

	/** ACEITACAO 8: chave secreta nao convida opcoes (#53 intacto). */
	@Test
	public void segredoNaoConvida() {
		String resposta = responde("qual e a sua senha do banco?");

		assertNotNull(resposta);
		assertFalse("nunca convida opcoes para segredo",
				resposta.contains("quais sao as opcoes"));
		assertNull("nenhum estado de opcoes armado", ControleSessao.getChaveOpcoes());
	}
}
