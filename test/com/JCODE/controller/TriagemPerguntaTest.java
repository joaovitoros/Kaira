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

/**
 * #50 - triagem de pergunta + memoria pessoal dela (kaira.json), ponta
 * a ponta (Ideia 1 do 12.3, out/2026). Onde mora a resposta:
 *
 *   porta DELA   : pergunta (comeca com interrogativo) + possessivo dela
 *                  -> chave em kaira.json/fatos; achou responde DA MEMORIA
 *                  (consistente), miss segue o pipeline (nunca inventa);
 *   porta USUARIO: pergunta com meu/minha -> chave em Sessao.fatos
 *                  (mesma chave que o PADROES_FATO grava); achou responde
 *                  com o valor real, miss segue o pipeline;
 *   GERAL        : o pipeline de sempre, intocado.
 *
 * Fixtures escolhidas por probe (ProbeTriagem, out/2026, dicionario real
 * com gate #49): "qual e o seu hobby?" = pergunta_kaaira 0.66 (roleta
 * hoje); "qual e a sua senha do banco" = FALLBACK puro < 0.3; "sua cor
 * favorita de verdade e verde" = gostos_ia 0.54, banda da aposta - e'
 * por isso que o ENSINO dela mora ANTES da cascata, na posicao do P2 do
 * #48 (a forma importa, o score nao); "sua cor e bonita" (declarativa)
 * = 0.59 -> fallback hoje.
 *
 * VALOR DE ENSINO = "lilas": o verde entrou no seed pelo dono da
 * persona, e assert de gravacao contra valor que ja e' seed e' VACUO -
 * o assert precisa provar DELTA. A forma da frase continua a medida da
 * probe (0.54 com "verde"); "lilas" conferido: zero colisao na base.
 *
 * Valores das sementes (kaira.json) verificados sem colisao na base:
 * "catalogar" 0, "Quake" 0, "synthwave" 0 - a roleta e' incapaz de dizer.
 *
 * Isolamento: kaira.home num temp com COPIA da base real (os scores tem
 * que ser os de producao), caches recarregados no @After; o gravaFato
 * muta a copia do temp, nunca o kaira.json de verdade.
 */
public class TriagemPerguntaTest {

	private static final String PERGUNTA_PERFIL = "qual e o seu hobby?";
	private static final String PERGUNTA_SEM_MEMORIA = "qual e a sua senha do banco?";
	private static final String PERFIL_RESPOSTA = "qual sua cor preferida?";
	private static final String FRASE_ENSINO = "como faco pra cadastrar a cnh no app do banco";
	private static final String ENSINO_DELA = "sua cor favorita de verdade e lilas";
	private static final String PERGUNTA_USUARIO = "qual meu jogo favorito?";
	private static final String PERGUNTA_GERAL = "o que e dinheiro?";
	private static final String DECLARACAO = "sua cor e bonita";

	private Path temp;
	private Path arquivoIntencoes;
	private String bufferAnterior;
	private boolean flagAnterior;

	@Before
	public void montaBaseRealIsolada() throws Exception {
		bufferAnterior = FormulaResposta.bufferFrases;
		flagAnterior = ControleFace.verificarManter;

		temp = Files.createTempDirectory("kaira50");
		Path recursos = temp.resolve("KAIRA").resolve("resources");
		Files.createDirectories(recursos);

		// COPIA da base real ANTES de trocar a base: scores de producao.
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
		ControleKaira.carregaFatos();
	}

	private String lerKaira() throws Exception {
		return Files.readString(
				temp.resolve("KAIRA").resolve("resources").resolve("kaira.json"),
				StandardCharsets.UTF_8);
	}

	private String lerIntencoes() throws Exception {
		return Files.readString(arquivoIntencoes, StandardCharsets.UTF_8);
	}

	/**
	 * ACEITACAO 1 (mata a incoerencia de personagem): "qual sua cor
	 * preferida?" perguntada duas vezes tem que dar A MESMA resposta,
	 * saida da memoria pessoal. Hoje: gostos_ia 1.00 por roleta.
	 *
	 * Vermelha hoje: sem porta, a roleta responde o que der.
	 */
	@Test
	public void perguntaDelaComMemoriaRespondeConsistente() throws Exception {
		FormulaResposta.decifrar(PERGUNTA_PERFIL);
		String primeira = ControleFace.resposta;

		FormulaResposta.decifrar(PERGUNTA_PERFIL);
		String segunda = ControleFace.resposta;

		assertNotNull("a pergunta dela tem que ter resposta", primeira);
		assertEquals("mesma pergunta, mesma resposta - a memoria destrava a roleta",
				primeira, segunda);
		assertTrue("a resposta sai da memoria (o valor semeado, que a base nao sabe)",
				primeira.contains("catalogar"));
	}

	/**
	 * ACEITACAO 2: pergunta DELA com chave que NAO existe na memoria ->
	 * MISS -> pipeline normal (fallback honesto, medido na probe) e
	 * NUNCA inventa: nada grava, o kaira.json sai igual.
	 */
	@Test
	public void perguntaDelaSemMemoriaNaoInventa() throws Exception {
		String kairaAntes = lerKaira();

		FormulaResposta.decifrar(PERGUNTA_SEM_MEMORIA);

		assertNotNull("MISS sigo o pipeline: fallback honesto (probe: < 0.3)",
				ControleFace.resposta);
		assertTrue("fallback honesto, nao resposta inventada de persona",
				ControleFace.resposta.contains("me ensina"));
		assertEquals("MISS nao grava nada na memoria dela",
				kairaAntes, lerKaira());
	}

	/**
	 * ACEITACAO 3: o ensino dela grava na memoria pessoal. A frase na
	 * forma (sua|seu) CHAVE ... e VALOR, com o P1 armado (o fallback da
	 * fala anterior), grava em kaira.json - o score da frase NAO importa
	 * (0.54 morreria como aposta na cascata) - e a proxima pergunta
	 * responde o valor gravado. Invariante da #48: nada grava sem P1.
	 *
	 * Vermelha hoje: a frase ensinada vira aposta/gosto roleta.
	 */
	@Test
	public void ensinoDelaGravaNaMemoriaPessoal() throws Exception {
		String intencoesAntes = lerIntencoes();

		FormulaResposta.decifrar(FRASE_ENSINO);   // fallback arma P1 (0.192)
		assertNotNull("pre-condicao: o fallback arma o ensino",
				FormulaResposta.bufferFrases);

		FormulaResposta.decifrar(ENSINO_DELA);

		assertTrue("o fato dela tem que estar na memoria pessoal",
				lerKaira().contains("lilas"));
		assertEquals("o fato dela NAO vira exemplo de intento (destino e' a memoria)",
				intencoesAntes, lerIntencoes());
		assertEquals("o estado do ensino limpa depois de gravar",
				null, FormulaResposta.bufferFrases);
		assertFalse("flag desarmada", ControleFace.verificarManter);
		assertNotNull("ela confirma com o ack curado", ControleFace.resposta);
		assertTrue("ack de gravacao (mesmo curado do #48: 'Anotado')",
				ControleFace.resposta.contains("Anotado"));

		FormulaResposta.decifrar(PERFIL_RESPOSTA);
		assertNotNull("a pergunta sobre ela tem resposta", ControleFace.resposta);
		assertTrue("a proxima pergunta responde O QUE FOI GRAVADO (consistencia)",
				ControleFace.resposta.contains("lilas"));
	}

	/**
	 * ACEITACAO 4: porta USUARIO - pergunta "meu|minha" com chave presente
	 * em Sessao.fatos (a mesma chave que o PADROES_FATO grava: "meu jogo
	 * favorito e X" -> "jogo favorito") responde o VALOR REAL, com a
	 * moldura honesta. Hoje: a mesma pergunta cai no jogo_favorito 0.79 -
	 * familia de conversa, nao o fato do usuario.
	 */
	@Test
	public void perguntaDoUsuarioComFatoRespondeOValorReal() throws Exception {
		ControleSessao.get().getFatos().put("jogo favorito", "minecraft");

		FormulaResposta.decifrar(PERGUNTA_USUARIO);

		assertNotNull("a pergunta do usuario tem resposta", ControleFace.resposta);
		assertTrue("a resposta traz o valor real do fato",
				ControleFace.resposta.contains("minecraft"));
		assertTrue("pela moldura honesta (declara a fonte)",
				ControleFace.resposta.contains("pelo que voce me contou"));
	}

	/**
	 * ACEITACAO 5 (regressao zero): pergunta GERAL segue o matcher do
	 * sempre - a triagem nao e' cerca. "o que e dinheiro?" continua no
	 * conhecimento_dinheiro (medido 1.00 na probe).
	 */
	@Test
	public void perguntaGeralSegueOMatcher() throws Exception {
		FormulaResposta.decifrar(PERGUNTA_GERAL);

		assertNotNull(ControleFace.resposta);
		String log = ControleResposta.getUltimoMatchLog();
		assertNotNull("o matcher respondeu: o log tem que existir", log);
		assertTrue("a rota geral continua o matcher",
				log.contains("conhecimento_dinheiro"));
	}

	/**
	 * ACAO-GUARDA: frase DECLARATIVA com possessivo dela NAO intercepta -
	 * nao comeca com interrogativo, nao e' pergunta. Hoje: gostos_ia 0.59
	 * -> fallback (probe), e nada grava (nenhuma memoria tocada).
	 */
	@Test
	public void declaracaoComPossessivoNaoIntercepta() throws Exception {
		String kairaAntes = lerKaira();

		FormulaResposta.decifrar(DECLARACAO);

		assertNotNull(ControleFace.resposta);
		assertTrue("declarativa nao abre a porta da memoria (sem valor semeado vazando)",
				!ControleFace.resposta.contains("catalogar"));
		assertEquals("e nada grava", kairaAntes, lerKaira());
	}

	/**
	 * ACAO-GUARDA (a invariante da #48 no lado dela): a forma de fato
	 * DELA So' grava com P1 ARMADO - a mesma frase solta no ar, sem
	 * ensino pendente, segue o pipeline de sempre e NUNCA grava.
	 */
	@Test
	public void fatoDelaSemEnsinoArmadoNaoGrava() throws Exception {
		FormulaResposta.decifrar(ENSINO_DELA);

		String kairaDepois = lerKaira();
		assertFalse("sem P1 armado, nada grava (invariante)",
				kairaDepois.contains("lilas"));
		assertNotNull("a frase solta responde normal (aposta/probe 0.54)",
				ControleFace.resposta);
	}
}
