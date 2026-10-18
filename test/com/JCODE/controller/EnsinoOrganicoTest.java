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
 * Aprendizado organico, ponta a ponta (Ideia 3 do 12.3, out/2026) - as
 * 3 aceitacoes + a invariante da segunda opinião, dirigidas pelo
 * FormulaResposta.decifrar (o roteamento passa a viver AQUI, fora da UI;
 * lição do #45: UI nunca é pre-condicao do dado).
 *
 * O bug de hoje: o fallback arma `ControleFace.verificarManter` e o
 * MandarFrase intercepta a proxima fala ANTES de tudo - sequestro de um
 * turno: a fala seguinte vira resposta de ensino incondicionalmente, ate
 * uma troca de assunto (a troca vira "resposta"). As specs daqui provam a
 * inversao da Ideia 3: quem julga e o pipeline.
 *
 *   1. entendida -> responde normal e CANCELA o ensino (morre o sequestro);
 *   2. nao entendida + P1 + score(buffer) >= 0.10 -> ABSORVE + ack;
 *   3. nao entendida + P1 + score(buffer) < 0.10 -> PORTEIRO: retida em P2,
 *      NADA gravado, ela pede confirmação citando a frase;
 *   4. P2 + "sim" -> grava o par retido; P2 + "nao" -> descarta sem virar
 *      novo P1 - INVERIANTE: nada grava sem confirmacao explicita.
 *
 * Fixtures escolhidas por probe (ProbeBuffer, degrau final em cada uma):
 * FALLBACK = vira buffer; DIRETO = entendida. Scores medidos no dicionario
 * real: cnh=0.192 (>= piso 0.10, absorve), blorf=0.000 (< piso, portao),
 * cor favorita=1.000 (direto).
 *
 * Isolamento: kaira.home num temp com COPIA da base real (os scores
 * tem que ser os de producao), caches recarregados no @After.
 */
public class EnsinoOrganicoTest {

	private static final String BUFFER_ABSORVEL =
			"como faço pra cadastrar a cnh no app do banco";
	private static final String BUFFER_LIXO = "blorf quantum zebra";
	private static final String RESPOSTA_NAO_ENTENDIDA = "zork blorp quizzle";
	private static final String RESPOSTA_ENTENDIDA = "qual sua cor favorita";

	private Path temp;
	private Path arquivoIntencoes;
	private String bufferAnterior;
	private boolean flagAnterior;

	@Before
	public void montaBaseRealIsolada() throws Exception {
		bufferAnterior = FormulaResposta.bufferFrases;
		flagAnterior = ControleFace.verificarManter;

		temp = Files.createTempDirectory("kaira49");
		Path recursos = temp.resolve("KAIRA").resolve("resources");
		Files.createDirectories(recursos);

		// COPIA da base real ANTES de trocar a base: os scores do porteiro
		// tem que ser os medidos (0.192 / 0.000 / 1.000), nao de fixture.
		for (String nome : List.of("intencoes.json", "stopwords.txt",
				"palavras.json", "grupos_sinonimos.json"))
			Files.copy(Caminhos.recurso(nome), recursos.resolve(nome));

		arquivoIntencoes = recursos.resolve("intencoes.json");

		System.setProperty("kaira.home", temp.toString());
		Caminhos.resetar();
		recarregaTudo();
		ControleSessao.reset();
		// janela de nome FECHADA: sem isso o "Como posso te chamar?" que o
		// fallback anexa come a resposta do ensino como NOME (medido no
		// log da suíte: zork -> DEGRAU resposta-nome). Usuario ja com nome
		// e o estado normal.
		ControleSessao.get().setNomeUsuario("teste");
		FormulaResposta.bufferFrases = null;
		FormulaResposta.respostaEnsino = null;
		ControleFace.verificarManter = false;
	}

	@After
	public void restauraOCacheRealEDepoisOTemp() throws Exception {
		FormulaResposta.bufferFrases = bufferAnterior;
		FormulaResposta.respostaEnsino = null;
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
	 * ACEITACAO 3 (a que mata o bug de hoje): o usuario MUDA DE ASSUNTO
	 * logo depois do "me ensina?" - a fala e entendida, ele responde
	 * normal e o ensino e CANCELADO. Nada de sequestro: nem buffer velho
	 * pendurado, nem flag de roteamento armada.
	 *
	 * Vermelha hoje pelo motivo certo: o decifrar nao consulta nem limpa
	 * o ensino - buffer e flag continuam armados esperando o MandarFrase
	 * comer a proxima fala.
	 */
	@Test
	public void mudouDeAssuntoCancelaOEnsinoSemSequestro() throws Exception {
		String intencoesAntes = lerIntencoes();

		FormulaResposta.decifrar(BUFFER_ABSORVEL);   // turno 1: fallback arma P1
		assertNotNull("pre-condicao: a frase tem que cair no fallback",
				FormulaResposta.bufferFrases);

		FormulaResposta.decifrar(RESPOSTA_ENTENDIDA); // turno 2: entendida (1.000)

		assertEquals("fala entendida tem que cancelar o ensino pendente (sem buffer velho)",
				null, FormulaResposta.bufferFrases);
		assertFalse("e a flag de roteamento tem que desarmar (sequestro morto)",
				ControleFace.verificarManter);
		assertEquals("e nada pode ter sido gravado no caminho",
				intencoesAntes, lerIntencoes());
	}

	/**
	 * ACEITACAO 1: fallback -> resposta nao entendida -> ABSORVIDA no
	 * intento certo (gate do #33 dentro de ensinaEm) + ack curado.
	 *
	 * Vermelha hoje: sem roteamento, a resposta vira UM NOVO fallback
	 * (buffer substituido, par nunca gravado) e a bolha mostra o pool de
	 * fallback, nao o ack.
	 */
	@Test
	public void respostaNaoEntendidaEhAbsorvidaComAck() throws Exception {
		FormulaResposta.decifrar(BUFFER_ABSORVEL);        // P1
		assertNotNull("pre-condicao: fallback arma o buffer",
				FormulaResposta.bufferFrases);

		FormulaResposta.decifrar(RESPOSTA_NAO_ENTENDIDA); // 0.192 >= piso: absorve

		assertTrue("a frase tem que ter sido gravada como exemplo (par ensinado)",
				lerIntencoes().contains(Normalizador.texto(BUFFER_ABSORVEL)));
		assertEquals("o estado do ensino limpa depois de absorvir",
				null, FormulaResposta.bufferFrases);
		assertFalse("flag desarmada", ControleFace.verificarManter);
		assertNotNull("ela responde com o ack", ControleFace.resposta);
		assertTrue("ack de absorção curado: 'Anotado'",
				ControleFace.resposta.contains("Anotado"));
	}

	/**
	 * A ORDEM da receita travada em spec: usuario SEM nome (1a sessao) -
	 * o fallback pergunta "Como posso te chamar?" junto do "me ensina?",
	 * e a resposta do ensino NAO pode virar nome. O check de ensino (P1)
	 * roda ANTES da captura de nome dentro do decifrar; a janela de nome
	 * fica aberta pro turno seguinte.
	 *
	 * Vermelha hoje: respostaDeNome e o PRIMEIRO degrau do decifrar e come
	 * a resposta ("Prazer em conhecer voce, zork...") - o ensino nunca vê
	 * o turno e nada e gravado.
	 */
	@Test
	public void primeiraSessaoSemNomeNaoComeARespostaDoEnsino() throws Exception {
		ControleSessao.reset();   // nome de novo null: 1a sessao de verdade

		FormulaResposta.decifrar(BUFFER_ABSORVEL);   // fallback + pergunta de nome
		assertNotNull("pre-condicao: frase cai no fallback",
				FormulaResposta.bufferFrases);

		FormulaResposta.decifrar(RESPOSTA_NAO_ENTENDIDA);

		assertTrue("a resposta vai pro ensino, nao pro campo de nome",
				lerIntencoes().contains(Normalizador.texto(BUFFER_ABSORVEL)));
		assertEquals("e o estado do ensino limpa",
				null, FormulaResposta.bufferFrases);
		assertFalse("flag desarmada", ControleFace.verificarManter);
	}

	/**
	 * ACEITACAO 2 + INVERIANTE: score do buffer abaixo do piso -> o
	 * PORTEIRO retém a resposta em P2 - nada e gravado antes da
	 * confirmacao, e ela PERGUNTA confirmando, citando a frase.
	 *
	 * Vermelha hoje: o porteiro nao existe - a resposta vira outro
	 * fallback (buffer substituido) e a bolha e o pool generico, sem
	 * citar a frase e sem confirmar nada.
	 */
	@Test
	public void porteiroBaixoRetemSemGravarEConfirma() throws Exception {
		FormulaResposta.decifrar(BUFFER_LIXO);            // P1: score 0.000
		assertNotNull("pre-condicao: fallback arma o buffer",
				FormulaResposta.bufferFrases);
		byte[] intencoesAntes = Files.readAllBytes(arquivoIntencoes);

		FormulaResposta.decifrar(RESPOSTA_NAO_ENTENDIDA); // < piso: retida em P2

		assertTrue("INVERIANTE: nada gravado sem confirmacao",
				java.util.Arrays.equals(intencoesAntes, Files.readAllBytes(arquivoIntencoes)));
		assertEquals("o BUFFER continua o mesmo (a resposta e que fica retida)",
				BUFFER_LIXO, FormulaResposta.bufferFrases);
		assertNotNull("ela tem que estar pedindo confirmacao", ControleFace.resposta);
		assertTrue("a confirmacao cita a frase pendente",
				ControleFace.resposta.contains(BUFFER_LIXO));
	}

	/**
	 * INVERIANTE (o sim explicito): P2 + confirmacao -> o par retido
	 * finalmente grava e vem o ack.
	 *
	 * Vermelha hoje: "sim" nao passa por nenhum cenario de ensino - nada
	 * foi retido, entao nada e gravado, e a bolha nao e o ack.
	 */
	@Test
	public void confirmacaoExplicitaGravaOParRetido() throws Exception {
		FormulaResposta.decifrar(BUFFER_LIXO);            // P1
		FormulaResposta.decifrar(RESPOSTA_NAO_ENTENDIDA); // P2 (retida)
		String intencoesAntes = lerIntencoes();

		FormulaResposta.decifrar("sim");                  // confirma

		assertTrue("confirmou -> o par (buffer, resposta retida) e gravado",
				lerIntencoes().contains(Normalizador.texto(BUFFER_LIXO)));
		assertEquals("e o estado limpa",
				null, FormulaResposta.bufferFrases);
		assertNotNull("ack na bolha", ControleFace.resposta);
		assertTrue("ack de absorção curado: 'Anotado'",
				ControleFace.resposta.contains("Anotado"));
		assertFalse("flag desarmada", ControleFace.verificarManter);
	}

	/**
	 * INVERIANTE (a recusa): P2 + "nao" -> descarta o par, ack leve, e
	 * NAO vira um novo P1 silencioso (hoje a "nao" cairia no fallback e
	 * armaria ela mesma como frase a ensinar).
	 */
	@Test
	public void recusaDescartaOParSemVirarNovoP1() throws Exception {
		FormulaResposta.decifrar(BUFFER_LIXO);            // P1
		FormulaResposta.decifrar(RESPOSTA_NAO_ENTENDIDA); // P2 (retida)
		String intencoesAntes = lerIntencoes();

		FormulaResposta.decifrar("nao");                  // recusa

		assertEquals("recusou -> nada gravado",
				intencoesAntes, lerIntencoes());
		assertEquals("e o ensino nao rearma com a propria recusa",
				null, FormulaResposta.bufferFrases);
		assertNotNull("ack leve de recusa", ControleFace.resposta);
		assertTrue("ack de recusa curado: 'descartei'",
				ControleFace.resposta.contains("descartei"));
	}

	private String lerIntencoes() throws Exception {
		return new String(Files.readAllBytes(arquivoIntencoes), StandardCharsets.UTF_8);
	}
}
