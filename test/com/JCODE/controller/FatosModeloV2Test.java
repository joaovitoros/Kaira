package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
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
 * #56 - modelo de fatos v2 (dela): relacoes gosta/nao_gosta.
 *
 * Sonda de 26/10 (base real isolada): o kaira.json era plano
 * ("fatos": {"cor": "verde"}, versao 0.0.1) e a pergunta negativa nao
 * existia - "qual cor voce nao gosta?" respondia besteira ("conversar,
 * ate nos conhecermos bem..."), idem "qual musica voce nao curte?".
 *
 * Schema v2:
 *   fatos:    "cor": { "gosta": ["verde"], "nao_gosta": ["cinza"] }
 *   molduras: "cor": { "gosta": "A minha cor preferida e {v}!",
 *                      "nao_gosta": "Uma cor que eu nao curto e {v}..." }
 *
 * Regras da #56:
 *   (1) migracao no load: arquivo plano v0.0.1 vira v2 em memoria
 *       ("cor":"verde" -> gosta ["verde"]);
 *   (2) a positiva continua igual (gosta[0] + moldura de gosta);
 *   (3) a NEGATIVA ("qual cor voce nao gosta?") le nao_gosta + moldura
 *       de nao_gosta; vazia -> honesto;
 *   (4) o ensino grava em gosta (substitui) e preserva nao_gosta;
 *   (5) o arquivo salvo depois de gravar ja sai v2 (versao 0.0.2).
 */
public class FatosModeloV2Test {

	private static final String FRASE_ENSINO = "como faco pra cadastrar a cnh no app do banco";

	private Path temp;

	@Before
	public void montaBaseRealIsolada() throws Exception {
		temp = Files.createTempDirectory("kaira56");
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

	private String lerKaira() throws Exception {
		return Files.readString(
				temp.resolve("KAIRA").resolve("resources").resolve("kaira.json"));
	}

	/**
	 * ACEITACAO 1: arquivo plano (v0.0.1) migra no load - "cor":"verde" vira
	 * gosta ["verde"], moldura plana continua valendo, nao_gosta fica vazio.
	 */
	@Test
	public void migracaoDoArquivoPlano() throws Exception {
		Path kaira = temp.resolve("KAIRA").resolve("resources").resolve("kaira.json");
		Files.writeString(kaira, "{\"nome\":\"K.A.I.R.A\",\"versao\":\"0.0.1\","
				+ "\"fatos\":{\"cor\":\"verde\"},"
				+ "\"molduras\":{\"cor\":\"A minha cor preferida e {v}!\"}}");
		ControleKaira.carregaFatos();

		assertEquals("o valor plano virou gosta", "verde", ControleKaira.buscaFato("cor"));
		assertNull("arquivo plano nao tem nao_gosta", ControleKaira.buscaNaoGosta("cor"));
		assertNotNull("a moldura plana continua valendo", ControleKaira.molduraDe("cor"));
	}

	/**
	 * ACEITACAO 2 (baseline): a pergunta positiva segue na relacao gosta
	 * com a moldura de sempre (seed v2: cor=verde).
	 */
	@Test
	public void positivaSegueDaRelacaoGosta() {
		String resposta = responde("qual e a sua cor favorita?");

		assertNotNull(resposta);
		assertTrue("resposta da memoria", resposta.contains("verde"));
	}

	/**
	 * ACEITACAO 3: a pergunta NEGATIVA le nao_gosta com a moldura da relacao.
	 * Seed v2: cor.nao_gosta = ["cinza"]. Hoje: besteira ("conversar...").
	 */
	@Test
	public void negativaRespondeDaRelacaoNaoGosta() {
		String resposta = responde("qual cor voce nao gosta?");

		assertNotNull(resposta);
		assertTrue("o valor de nao_gosta", resposta.contains("cinza"));
		assertTrue("a resposta e' uma das molduras da relacao negativa",
				ControleKaira.moldurasNaoGosta("cor").stream()
						.anyMatch(m -> resposta.contains(m.replace("{v}", "cinza"))));
	}

	/** Negativa sem valor na lista: honesto, nunca inventa. */
	@Test
	public void negativaVaziaRespondeHonesto() {
		String resposta = responde("qual musica voce nao curte?");

		assertNotNull(resposta);
		assertTrue("honesto quando a lista esta vazia",
				resposta.contains("Ainda nao tenho"));
	}

	/**
	 * ACEITACAO 4: o ensino grava na relacao GOSTA (substitui) e preserva a
	 * nao_gosta; a proxima pergunta responde o valor gravado.
	 */
	@Test
	public void ensinoGravaNaRelacaoGosta() {
		responde(FRASE_ENSINO);   // fallback arma P1
		assertNotNull(FormulaResposta.bufferFrases);

		responde("sua cor e lilas");

		assertEquals("o ensino gravou em gosta", "lilas", ControleKaira.buscaFato("cor"));
		assertEquals("a nao_gosta sobrevive", "cinza", ControleKaira.buscaNaoGosta("cor"));
		assertTrue("a proxima pergunta responde o gravado",
				responde("qual e a sua cor favorita?").contains("lilas"));
	}

	/**
	 * ACEITACAO 5: depois de gravar, o arquivo sai no schema v2 (versao
	 * 0.0.2, relacoes gosta/nao_gosta).
	 */
	@Test
	public void arquivoSalvoEmV2() throws Exception {
		responde(FRASE_ENSINO);
		responde("sua cor e lilas");

		String kaira = lerKaira();
		assertTrue("chave de relacao gosta no arquivo", kaira.contains("\"gosta\""));
		assertTrue("chave de relacao nao_gosta no arquivo", kaira.contains("\"nao_gosta\""));
		assertTrue("versao nova no arquivo", kaira.contains("0.0.2"));
	}

	/**
	 * ACEITACAO 6 (invariante de nao-contradicao): um valor que ja esta na
	 * relacao OPOSTA nao entra - ela nao se contradiz. Seed: cor.nao_gosta =
	 * [cinza]; o ensino "sua cor e cinza" (lado gosta) e' recusado.
	 */
	@Test
	public void contradicaoNoEnsinoNaoGrava() {
		responde(FRASE_ENSINO);   // fallback arma P1
		assertNotNull(FormulaResposta.bufferFrases);

		String resposta = responde("sua cor e cinza");

		assertEquals("a relacao gosta fica intacta", "verde", ControleKaira.buscaFato("cor"));
		assertEquals("a nao_gosta fica intacta", "cinza", ControleKaira.buscaNaoGosta("cor"));
		assertNotNull(resposta);
		assertTrue("ack proprio da contradicao", resposta.contains("contradizer"));
	}

	/**
	 * ACEITACAO 7 (dado sujo): valor nos DOIS lados no arquivo - no load a
	 * relacao positiva vence e o valor sai da negativa.
	 */
	@Test
	public void dedupNoLoad() throws Exception {
		Path kaira = temp.resolve("KAIRA").resolve("resources").resolve("kaira.json");
		Files.writeString(kaira, "{\"nome\":\"K.A.I.R.A\",\"versao\":\"0.0.2\",\"fatos\":{"
				+ "\"cor\":{\"gosta\":[\"verde\"],\"nao_gosta\":[\"verde\",\"cinza\"]}},"
				+ "\"molduras\":{\"cor\":{\"gosta\":\"A minha cor preferida e {v}!\","
				+ "\"nao_gosta\":\"Uma cor que eu nao curto e {v}.\"}}}");
		ControleKaira.carregaFatos();

		assertEquals("a positiva vence", "verde", ControleKaira.buscaFato("cor"));
		assertEquals("o verde saiu da negativa", "cinza", ControleKaira.buscaNaoGosta("cor"));
	}
}
