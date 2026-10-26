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
 * Fatia "explica melhor" do C3 (#37): o dicionario tem `resumo` (falado no
 * "conta mais") e `texto` (versao maior). Sonda de 26/10 (base real isolada,
 * topico armado) mediu que o roteamento ja existe na base:
 *
 *   "conta mais sobre isso"      -> continuacao_topico 1.00 (exata)
 *   "me explica melhor"         -> continuacao_topico 1.00 (frase do banco)
 *   "explica melhor sobre isso"  -> continuacao_topico 0.66
 *   "conta com mais detalhes"    -> continuacao_topico 1.00 (exata)
 *
 * Mas o encaixe sempre fala o RESUMO - o TEXTO nunca sai. Regra:
 *   - "conta mais"          -> resumo (baseline #37 preservado)
 *   - "explica/detalha/..." -> texto (fallback resumo -> banco)
 *   - topico sem entrada    -> banco (nunca quebra)
 *   - a flag vive SO o turno (reset no processa, como o #28).
 */
public class ExplicaMelhorTest {

	private static final String TOPICO = "trabalho_usuario";

	private Path temp;

	@Before
	public void montaBaseRealIsolada() throws Exception {
		temp = Files.createTempDirectory("kaira54");
		Path recursos = temp.resolve("KAIRA").resolve("resources");
		Files.createDirectories(recursos);

		for (String nome : List.of("intencoes.json", "stopwords.txt",
				"palavras.json", "grupos_sinonimos.json", "kaira.json",
				"conhecimentos.json"))
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
		ControleConhecimento.carrega();
	}

	private String respondeComTopico(String topico, String fala) {
		ControleSessao.reset();
		ControleSessao.get().setNomeUsuario("teste");
		FormulaResposta.bufferFrases = null;
		FormulaResposta.respostaEnsino = null;
		ControleFace.verificarManter = false;
		ControleSessao.get().setTopicoAtual(topico);

		ControleFace.resposta = null;
		FormulaResposta.decifrar(fala);
		return ControleFace.resposta;
	}

	/**
	 * BASELINE (#37): "conta mais" continua falando o RESUMO.
	 */
	@Test
	public void contaMaisSegueNoResumo() {
		String resposta = respondeComTopico(TOPICO, "conta mais sobre isso");

		assertEquals("conta mais fala o resumo do dicionario",
				ControleConhecimento.resumo(TOPICO), resposta);
	}

	/**
	 * ACEITACAO 1: "me explica melhor" fala o TEXTO (a versao maior).
	 * Hoje: fala o resumo, igual ao "conta mais".
	 */
	@Test
	public void explicaMelhorFalaOTexto() {
		String resposta = respondeComTopico(TOPICO, "me explica melhor");

		assertEquals("explica melhor fala o texto do dicionario",
				ControleConhecimento.texto(TOPICO), resposta);
	}

	/** "conta com mais detalhes" tambem pede profundidade (texto). */
	@Test
	public void comMaisDetalhesFalaOTexto() {
		assertEquals("detalhes pedem o texto",
				ControleConhecimento.texto(TOPICO),
				respondeComTopico(TOPICO, "conta com mais detalhes"));
	}

	/** Outro topico com entrada: musica. */
	@Test
	public void explicaMelhorComOutroTopico() {
		assertEquals("texto da musica",
				ControleConhecimento.texto("musica"),
				respondeComTopico("musica", "me explica melhor"));
	}

	/**
	 * Topico sem entrada: o encaixe desiste e o banco responde (nunca
	 * quebra, nunca inventa).
	 */
	@Test
	public void topicoSemEntradaCaiNoBanco() {
		assertNull("piada nao tem entrada no dicionario",
				ControleConhecimento.texto("piada"));

		String resposta = respondeComTopico("piada", "me explica melhor");

		assertNotNull("sem entrada, o banco responde", resposta);
		assertTrue("nao inventa substancia de dicionario",
				!resposta.isBlank());
	}

	/**
	 * A flag vive SO o turno (limpeza no processa, como o #28): depois de um
	 * "explica melhor", o proximo "conta mais" volta pro resumo - SEM reset
	 * de sessao entre as falas (o processa e' quem limpa, a cada decifrar).
	 */
	@Test
	public void explicacaoNaoVazaProTurnoSeguinte() {
		ControleSessao.reset();
		ControleSessao.get().setNomeUsuario("teste");
		FormulaResposta.bufferFrases = null;
		FormulaResposta.respostaEnsino = null;
		ControleFace.verificarManter = false;
		ControleSessao.get().setTopicoAtual(TOPICO);

		ControleFace.resposta = null;
		FormulaResposta.decifrar("me explica melhor");
		assertEquals("explica melhor -> texto",
				ControleConhecimento.texto(TOPICO), ControleFace.resposta);

		// sem reset de sessao: quem limpa a flag e' o processa() do turno novo
		ControleFace.resposta = null;
		FormulaResposta.decifrar("conta mais sobre isso");
		assertEquals("o turno seguinte volta pro resumo",
				ControleConhecimento.resumo(TOPICO), ControleFace.resposta);
	}
}
