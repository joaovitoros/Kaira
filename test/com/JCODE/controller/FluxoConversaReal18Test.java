package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.JCODE.util.Caminhos;
import com.JCODE.util.Normalizador;

/**
 * Conversa REAL de 18/10/2026 transcrita do conversas.log - o mesmo papel
 * que o FluxoConversaRealTest fez com a conversa de 17/10: o que aconteceu
 * com o autor vira guarda contra regressao.
 *
 * O log de 18/10 e o primeiro em que o aprendizado organico (#49) rodou em
 * uso real. O loop do elefente (3 turnos: fallback -> absorvido -> responde)
 * ja aconteceu DE VERDADE e virou dado curado: o par subiu pro intento
 * `curiosidade` com o erro de digitacao do autor corrigido ("lembrar"), e o
 * auto_aprendizado voltou a zero (guarda IntencoesCuradoNaoDegradadoTest).
 * Por isso o replay aqui usa um par NOVO, medido no dicionario real:
 *
 *   - pergunta "voce sabia que tubaroes dormem em movimento" = 0.250
 *     (janela do ensino: >= 0.10 o porteiro absorve, < 0.30 nao vai
 *     pra aposta e chega no fallback);
 *   - resposta "eles nunca param de nadar nem dormindo" = 0.252
 *     (nao entendida pela cascata -> vira a resposta do ensino).
 *
 * Atencao: as frases sao medidas, nao escolhidas a dedo - candidatas
 * naturais tipo "os tubaroes precisam nadar pra respirar" marcaram 0.325
 * e a aposta sequestraria o turno (a regua e a mesma de sempre).
 *
 * Isolamento: igual ao EnsinoOrganicoTest - kaira.home num temp com COPIA
 * da base real (os scores tem que ser os medidos).
 */
public class FluxoConversaReal18Test {

	private static final String PERGUNTA_NOVA =
			"voce sabia que tubaroes dormem em movimento";
	private static final String RESPOSTA_NOVA =
			"eles nunca param de nadar nem dormindo";
	private static final String PERGUNTA_ELEFANTE =
			"voce sabia que elefentes tem boa memoria";

	private Path temp;
	private Path arquivoIntencoes;
	private String bufferAnterior;
	private String respostaEnsinoAnterior;
	private boolean flagAnterior;

	@Before
	public void montaBaseRealIsolada() throws Exception {
		bufferAnterior = FormulaResposta.bufferFrases;
		respostaEnsinoAnterior = FormulaResposta.respostaEnsino;
		flagAnterior = ControleFace.verificarManter;

		temp = Files.createTempDirectory("kaira50");
		Path recursos = temp.resolve("KAIRA").resolve("resources");
		Files.createDirectories(recursos);

		for (String nome : List.of("intencoes.json", "stopwords.txt",
				"palavras.json", "grupos_sinonimos.json"))
			Files.copy(Caminhos.recurso(nome), recursos.resolve(nome));

		arquivoIntencoes = recursos.resolve("intencoes.json");

		System.setProperty("kaira.home", temp.toString());
		Caminhos.resetar();
		recarregaTudo();
		ControleSessao.reset();
		// sessao como a do log: usuario ja com nome, sem pergunta pendurada
		ControleSessao.get().setNomeUsuario("teste");
		FormulaResposta.bufferFrases = null;
		FormulaResposta.respostaEnsino = null;
		ControleFace.verificarManter = false;
	}

	@After
	public void restauraOCacheRealEDepoisOTemp() throws Exception {
		FormulaResposta.bufferFrases = bufferAnterior;
		FormulaResposta.respostaEnsino = respostaEnsinoAnterior;
		ControleFace.verificarManter = flagAnterior;

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
	}

	/**
	 * O loop de 3 turnos do log, com um par novo medido: ela nao sabe,
	 * o porteiro absorve a resposta na hora, e a MESMA pergunta volta a
	 * casar com a resposta ensinada. O mecanismo do uso real, congelado.
	 */
	@Test
	public void loopDoEnsinoFechaComFrasesNovas() throws Exception {
		// turno 1: ela nao sabe e arma o ensino (como no log, 15:02:47)
		FormulaResposta.decifrar(PERGUNTA_NOVA);

		assertNotNull("turno 1: a frase cai no fallback e arma o buffer",
				FormulaResposta.bufferFrases);
		assertNotNull("turno 1: fallback responde", ControleFace.resposta);
		assertTrue("turno 1: o fallback pede ensino ('me ensina')",
				ControleFace.resposta.contains("me ensina"));

		// turno 2: a resposta do autor - 0.252, nao entendida, vira ensino
		FormulaResposta.decifrar(RESPOSTA_NOVA);

		assertTrue("turno 2: a PERGUNTA entra na base como exemplo",
				lerIntencoes().contains(Normalizador.texto(PERGUNTA_NOVA)));
		assertTrue("turno 2: a RESPOSTA entra na base (o conteudo ensinado)",
				lerIntencoes().contains(Normalizador.texto(RESPOSTA_NOVA)));
		assertEquals("turno 2: o estado do ensino limpa",
				null, FormulaResposta.bufferFrases);
		assertFalse("turno 2: flag desarmada", ControleFace.verificarManter);
		assertNotNull("turno 2: ack na bolha", ControleFace.resposta);
		assertTrue("turno 2: ack de absorcao curado ('Anotado')",
				ControleFace.resposta.contains("Anotado"));

		// turno 3: a MESMA pergunta agora casa e ela responde
		FormulaResposta.decifrar(PERGUNTA_NOVA);

		assertNotNull("turno 3: ela responde", ControleFace.resposta);
		assertTrue("turno 3: a resposta e a que foi ensinada ('eles' fica -",
				ControleFace.resposta.contains("eles"));
		assertFalse("turno 3: ela nao pede ensino de novo - o loop fechou",
				ControleFace.resposta.contains("me ensina"));
		assertEquals("turno 3: o topico vira o intento do ensino",
				"auto_aprendizado", ControleSessao.get().getTopicoAtual());
	}

	/**
	 * O elefante do log virou DADO CURADO: a curadoria de #49 subiu o par
	 * pro intento `curiosidade` (com o "lebrar" corrigido pra "lembrar") e
	 * zerou a caixa de entrada. Aqui se prova o resultado: a pergunta do
	 * log agora casa direto (frase exata, score 1.000) e ela responde como
	 * curiosidade, sem pedir ensino.
	 */
	@Test
	public void elefanteDoLogVirouDadoCurado() {
		FormulaResposta.decifrar(PERGUNTA_ELEFANTE);

		assertEquals("a pergunta do log casa no curiosidade curado",
				"curiosidade", ControleSessao.get().getTopicoAtual());
		assertNotNull("ela responde", ControleFace.resposta);
		assertFalse("responde com confianca, sem pedir ensino",
				ControleFace.resposta.contains("me ensina"));
	}

	/**
	 * A recuperacao de topico do mesmo log: depois de levar resposta fora
	 * do assunto ("qual voce gosta mais?" casou em gostos_ia), o pedido
	 * EXPLICITO do autor ("estou falando de musica...") resolve - match
	 * 0.66 direto, topico volta a musica. O que nao resolve sozinho (a
	 * mudanca brusca sem o pedido explicito) e o #41, adiado.
	 */
	@Test
	public void pedidoExplicitoRecuperaOTopicoDoLog() {
		FormulaResposta.decifrar("estou falando de musica, qual e sua preferida?");

		assertEquals("o topico volta para musica quando ele e explicito",
				"musica", ControleSessao.get().getTopicoAtual());
		assertNotNull("ela responde", ControleFace.resposta);
		assertFalse("casou direto: nao vai pra confirmacao de aposta",
				ControleFace.resposta.contains("Foi isso"));
	}

	private String lerIntencoes() throws Exception {
		return new String(Files.readAllBytes(arquivoIntencoes), StandardCharsets.UTF_8);
	}
}
