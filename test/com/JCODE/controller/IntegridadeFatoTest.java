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
 * #51 - integridade do fato do usuario: pergunta nao vira fato e nao
 * sobrescreve. Achado no T02 da triagem (RELATORIO 351-357), reconfirmado
 * por sonda no master atual (26/10): "meu time e qual mesmo?" gravava
 * time="qual mesmo" por cima de Gremio porque o PADROES_FATO casa
 * declaracao em qualquer ponto da frase e o put era incondicional.
 *
 * Fixtures medidas na sonda (26/10, base real isolada):
 *   "meu time e Gremio"                    -> time=Gremio
 *   "meu time e qual mesmo?" (vazio)       -> time="qual mesmo"    (BUG)
 *   time=Gremio + mesma fala               -> time="qual mesmo"    (BUG)
 *   time=Gremio + "meu time e Corinthians" -> time=Corinthians     (BUG: sobrescreve calado)
 *   "meu jogo favorito e qual e?"          -> jogo favorito="qual e" (BUG)
 *   "meu sonho e qual mesmo?"              -> sonho="qual mesmo"   (BUG)
 *   "meu time e gremio" (caixa)            -> time="gremio"        (BUG menor: perde a caixa)
 *   "qual e o meu time?"                   -> nao grava (ja correto)
 *
 * Regras da #51:
 *   (1) fala-pergunta (com "?" ou interrogativo) nao processa fato;
 *   (2) valor-pergunta tambem nao grava;
 *   (3) fato existente com valor diferente NAO e sobrescrito (log de
 *       preservacao); mesmo valor (caixa ignorada) = no-op com o
 *       original canonico preservado.
 *
 * Isolamento: kaira.home num temp com copia da base real, caches
 * recarregados no @After - a arvore de trabalho nunca e tocada.
 */
public class IntegridadeFatoTest {

	private Path temp;

	@Before
	public void montaBaseRealIsolada() throws Exception {
		temp = Files.createTempDirectory("kaira51");
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

	/**
	 * ACEITACAO 1 (o caso do T02): pergunta vestida de declaracao nao
	 * vira fato. Hoje: grava time="qual mesmo" na sessao vazia.
	 */
	@Test
	public void perguntaDisfarcadaNaoViraFato() {
		FormulaResposta.decifrar("meu time e qual mesmo?");

		assertFalse("fala-pergunta nao pode gravar fato",
				ControleSessao.get().getFatos().containsKey("time"));
		assertNotNull("a fala continua respondendo algo (pipeline normal)",
				ControleFace.resposta);
	}

	/**
	 * ACEITACAO 2 (a destruicao do T02): com o fato ja gravado, a mesma
	 * pergunta NAO pode sobrescrever. Hoje: time vira "qual mesmo".
	 */
	@Test
	public void perguntaDisfarcadaNaoSobrescreve() {
		ControleSessao.get().getFatos().put("time", "Gremio");

		FormulaResposta.decifrar("meu time e qual mesmo?");

		assertEquals("o fato existente tem que sobreviver a pergunta",
				"Gremio", ControleSessao.get().getFatos().get("time"));
	}

	/**
	 * ACEITACAO 3 (baseline): declaracao limpa continua gravando
	 * (medido na sonda: time=Gremio).
	 */
	@Test
	public void declaracaoLimpaGrava() {
		FormulaResposta.decifrar("meu time e Gremio");

		assertEquals("Gremio", ControleSessao.get().getFatos().get("time"));
	}

	/**
	 * ACEITACAO 4: valor DIFERENTE sem pedido de correcao nao sobrescreve
	 * (a correcao vai ganhar fluxo proprio na maquina de confirmacao).
	 * Hoje: time=Gremio vira Corinthians calado.
	 */
	@Test
	public void declaracaoSemPedidoDeCorrecaoNaoSobrescreve() {
		ControleSessao.get().getFatos().put("time", "Gremio");

		FormulaResposta.decifrar("meu time e Corinthians");

		assertEquals("fato existente preservado ate existir fluxo de correcao",
				"Gremio", ControleSessao.get().getFatos().get("time"));
	}

	/**
	 * ACEITACAO 5: o padrao generico "meu X favorito e Y" tambem fica
	 * protegido. Hoje: jogo favorito=minecraft vira "qual e".
	 */
	@Test
	public void perguntaGenericaNaoSobrescreveOJogo() {
		ControleSessao.get().getFatos().put("jogo favorito", "minecraft");

		FormulaResposta.decifrar("meu jogo favorito e qual e?");

		assertEquals("minecraft",
				ControleSessao.get().getFatos().get("jogo favorito"));
	}

	/**
	 * ACEITACAO 6: idem no radar proprio do sonho. Hoje: "qual mesmo"
	 * destroi sonho=empreender.
	 */
	@Test
	public void perguntaNaoSobrescreveOSonho() {
		ControleSessao.get().getFatos().put("sonho", "empreender");

		FormulaResposta.decifrar("meu sonho e qual mesmo?");

		assertEquals("empreender", ControleSessao.get().getFatos().get("sonho"));
	}

	/**
	 * ACEITACAO 7: o MESMO fato dito de novo (caixa diferente) e no-op -
	 * o canonico sobrevive. Hoje: "Gremio" vira "gremio".
	 */
	@Test
	public void mesmoValorComOutraCaixaPreservaOCanonico() {
		ControleSessao.get().getFatos().put("time", "Gremio");

		FormulaResposta.decifrar("meu time e gremio");

		assertEquals("canonico preservado", "Gremio",
				ControleSessao.get().getFatos().get("time"));
	}

	/**
	 * ACEITACAO 8: os outros radares de fato seguem intactos (baseline
	 * medido na sonda: musica favorita e pet).
	 */
	@Test
	public void baselinesDeclarativosSeguemGravando() {
		FormulaResposta.decifrar("minha musica favorita e Legiao Urbana");
		FormulaResposta.decifrar("meu pet se chama Rex");

		assertEquals("Legiao Urbana",
				ControleSessao.get().getFatos().get("musica favorita"));
		assertEquals("Rex", ControleSessao.get().getFatos().get("pet"));
	}

	/**
	 * ACAO-GUARDA: pergunta honesta (sem forma de fato) nao grava nada -
	 * comportamento que ja funciona e nao pode regredir.
	 */
	@Test
	public void perguntaHonestaNaoGrava() {
		FormulaResposta.decifrar("qual e o meu time?");

		assertTrue("nada de fato foi gravado",
				ControleSessao.get().getFatos().isEmpty());
	}
}
