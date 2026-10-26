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
 * #41 - a maquina de contexto: a pergunta pendente dela (o simetrico do #48).
 *
 * Sonda de 26/10 (base real isolada):
 *   - o followup sai e e' esquecido (sem estado);
 *   - "kkk" como resposta ao followup -> "me ensina?" e ARMA o ensino (o
 *     sequestro simetrico que esta issue mata);
 *   - scores medidos das respostas: "ta corrido" 0.688 (cotidiano),
 *     "sei la" 0.506 (semana_dias), "kkk" 0.000, "fui no mercado" 1.000.
 *
 * Regras aprovadas (comentario de escopo da reabertura + hibrido do autor):
 *   a. comando/nome/confirmacao de aposta -> responde normal, cancela com ack;
 *   b. fala em forma de PERGUNTA -> cancela, nada grava (o pipeline responde);
 *   c. score >= LIMIAR (0.6) -> absorve no intento do match ("intento certo");
 *   d. piso (0.10) <= score < 0.6 -> absorve na familia que perguntou
 *      (ou auto_aprendizado quando o followup veio do pool generico);
 *   e. score < piso -> P2 do #48 (retencao + confirmacao; nada grava sem "sim").
 *
 * O par absorvido: exemplo = a resposta do usuario, resposta = o texto do
 * followup dela ("ta corrido" -> "E amanha, mais leve ou mais puxado?").
 *
 * Isolamento: kaira.home num temp com copia da base real; caches
 * recarregados no @After - a arvore de trabalho nunca e' tocada.
 */
public class MaquinaContextoTest {

	private static final String TOPICO = "trabalho_usuario";
	private static final String FOLLOWUP = "E amanha, mais leve ou mais puxado?";

	private Path temp;
	private Path arquivoIntencoes;

	@Before
	public void montaBaseRealIsolada() throws Exception {
		temp = Files.createTempDirectory("kaira41");
		Path recursos = temp.resolve("KAIRA").resolve("resources");
		Files.createDirectories(recursos);

		for (String nome : List.of("intencoes.json", "stopwords.txt",
				"palavras.json", "grupos_sinonimos.json", "kaira.json"))
			Files.copy(Caminhos.recurso(nome), recursos.resolve(nome));
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

	private String lerIntencoes() throws Exception {
		return Files.readString(arquivoIntencoes);
	}

	private boolean intentoTemExemplo(String intento, String texto) {
		return ControleResposta.getIntencoes().stream()
				.filter(i -> intento.equals(i.getIntento()))
				.findFirst()
				.map(i -> i.getExemplos().stream()
						.anyMatch(e -> texto.equals(e.getTexto())))
				.orElse(false);
	}

	/**
	 * ACEITACAO 1: quando a resposta sai COM followup, a pergunta fica
	 * pendente (familia + texto). Hoje: entregue e esquecida.
	 */
	@Test
	public void followupArmaAPerguntaPendente() {
		ControleSessao.get().setTopicoAtual(TOPICO);

		// 3 turnos entendidos disparam a iniciativa (TURNOS_SEM_PERGUNTA = 3)
		responde("muito puxado");
		responde("muito puxado");
		responde("muito puxado");

		assertNotNull("o followup armou a pergunta pendente",
				ControleSessao.getPerguntaPendente());
		assertEquals("a familia que perguntou fica registrada",
				TOPICO, ControleSessao.getFamiliaPergunta());
	}

	/**
	 * REGRA B: fala em forma de pergunta (outro assunto) cancela a pendente
	 * com ack transparente - e NADA grava.
	 */
	@Test
	public void perguntaDeOutroAssuntoCancelaComAck() throws Exception {
		ControleSessao.armaPergunta(TOPICO, FOLLOWUP);
		String intencoesAntes = lerIntencoes();

		String resposta = responde("qual e a capital da franca?");

		assertNotNull(resposta);
		assertTrue("ack transparente do cancelamento",
				resposta.contains("deixa pra"));
		assertNull("a pendente foi limpa",
				ControleSessao.getPerguntaPendente());
		assertEquals("nada grava no cancelamento", intencoesAntes, lerIntencoes());
	}

	/**
	 * REGRA D: resposta vaga na banda da aposta (0.3-0.6) absorve na
	 * FAMILIA que perguntou. Fixture medida: "sei la" = 0.506.
	 */
	@Test
	public void respostaVagaAbsorveNaFamilia() throws Exception {
		ControleSessao.armaPergunta(TOPICO, FOLLOWUP);

		String resposta = responde("sei la");

		assertNotNull(resposta);
		assertTrue("ack curado de absorcao", resposta.contains("Anotado"));
		assertTrue("a resposta virou exemplo da familia",
				intentoTemExemplo(TOPICO, "sei la"));
		assertNull("a pendente foi consumida",
				ControleSessao.getPerguntaPendente());
	}

	/**
	 * REGRA A/C: fala com match confiante (>= 0.6) e' entendida pelo pipeline -
	 * ele responde normal e a pendente cancela com ack. NADA absorve.
	 * Fixture: "ta corrido" = 0.688 -> cotidiano (o pipeline sabe responder).
	 * (O replay de 26/10 provou que absorver essa faixa engolia conversa
	 * normal: BOM 380->298 e GERAL 5,2->4,8.)
	 */
	@Test
	public void respostaConfianteCancelaEPipelineResponde() throws Exception {
		ControleSessao.armaPergunta(TOPICO, FOLLOWUP);
		String intencoesAntes = lerIntencoes();

		String resposta = responde("ta corrido");

		assertNotNull(resposta);
		assertTrue("ack transparente do cancelamento",
				resposta.contains("deixa pra"));
		assertNull("a pendente foi limpa",
				ControleSessao.getPerguntaPendente());
		assertEquals("nada absorve na faixa entendida",
				intencoesAntes, lerIntencoes());
		assertFalse("nao virou exemplo de intento nenhum",
				intentoTemExemplo("cotidiano", "ta corrido"));
	}

	/**
	 * REGRA E: fala vazia (0.0) retem o par no P2 do #48 - nada grava sem
	 * confirmacao. Hoje: "me ensina?" e arma o ensino (o sequestro).
	 */
	@Test
	public void falaVaziaRetemNoP2() throws Exception {
		ControleSessao.armaPergunta(TOPICO, FOLLOWUP);
		String intencoesAntes = lerIntencoes();

		String resposta = responde("kkk");

		assertNotNull(resposta);
		assertTrue("pede confirmacao (P2)", resposta.contains("me confirma"));
		assertEquals("nada grava antes do sim", intencoesAntes, lerIntencoes());
		assertNotNull("o par fica retido no estado do P2",
				FormulaResposta.respostaEnsino);
		assertNull("a pendente foi consumida",
				ControleSessao.getPerguntaPendente());

		// o "sim" confirma e absorve o par retido
		String confirmacao = responde("sim");
		assertTrue("ack de absorcao no sim",
				confirmacao.contains("Anotado"));
		assertFalse("a base mudou depois do sim",
				intencoesAntes.equals(lerIntencoes()));
	}

	/** Sem pergunta pendente, o comportamento de hoje segue (guard). */
	@Test
	public void semPendenteNadaMuda() {
		String resposta = responde("kkk");

		assertNotNull(resposta);
		assertTrue("fallback de sempre arma o ensino",
				resposta.contains("me ensina"));
		assertNotNull("estado do ensino intocado",
				FormulaResposta.bufferFrases);
	}
}
