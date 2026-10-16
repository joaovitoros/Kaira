package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.JCODE.util.Caminhos;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Spec da issue #20 (§6.1): persistência da sessão entre execuções.
 *
 * O critério de aceite da §6.1: fechar e reabrir → KAIRA cumprimenta pelo
 * nome e retoma o tópico. O arquivo de runtime é sessao.json na pasta de
 * dados (raiz, ao lado do conversas.log) e fica no .gitignore.
 *
 * Contratos exercitados (ControleSessao):
 *  - gravaSessao()      → escreve o estado em UTF-8 no arquivo da base;
 *  - carregaSessao()    → boolean: true restaurou, false sessão nova;
 *  - boasVindas()       → String com "Bem-vindo de volta", nome e tópico;
 *                         null quando não há nome persistido;
 *  - arquivo corrompido/ausente → abertura continua limpa (nunca quebra).
 *
 * Humor (5, default) só ganha teste de rodízio quando a §7 criar setter.
 */
public class ControleSessaoPersistenciaTest {

	private Path arquivo() {
		return Caminhos.base().resolve("sessao.json");
	}

	@Before
	public void limpaAntes() throws Exception {
		ControleSessao.reset();
		Files.deleteIfExists(arquivo());
	}

	@After
	public void limpaDepois() throws Exception {
		Files.deleteIfExists(arquivo());
		ControleSessao.reset();
	}

	/** O estado aprendido sai pro arquivo de dados, em UTF-8 (João != Joao). */
	@Test
	public void gravaSessaoCriaArquivoComAcentos() {
		ControleSessao.processa("meu nome é João");
		ControleSessao.topico("musica");
		ControleSessao.gravaSessao();

		assertTrue("sessao.json não foi criado em " + Caminhos.base(), Files.exists(arquivo()));
		String bruto = new String(Files.readAllBytes(arquivo()), StandardCharsets.UTF_8);
		assertTrue("nome com acento não sobreviveu ao arquivo: " + bruto, bruto.contains("João"));
	}

	/**
	 * Reabrir = mesmo estado: nome, tópico, turnos, histórico (turnos de
	 * usuário e da KAIRA) e o mapa de fatos, tudo intacto.
	 */
	@Test
	public void carregaSessaoRestauraEstadoCompleto() throws Exception {
		ControleSessao.processa("meu nome é João");
		ControleSessao.processa("eu gosto de música");
		ControleSessao.processa("eu moro em São Paulo");
		ControleSessao.processa("como foi seu dia");
		ControleSessao.anotaResposta("dia tranquilo, e o seu?");
		ControleSessao.topico("cotidiano");
		int turnosAntes = ControleSessao.get().getTurnos();
		ControleSessao.gravaSessao();

		ControleSessao.reset(); // simula fechar e reabrir
		assertTrue("carregaSessao devia dizer que restaurou de arquivo",
				ControleSessao.carregaSessao());

		assertEquals("nome restaurado", "João", ControleSessao.get().getNomeUsuario());
		assertEquals("tópico restaurado", "cotidiano", ControleSessao.get().getTopicoAtual());
		assertEquals("turnos restaurados", turnosAntes, ControleSessao.get().getTurnos());
		assertEquals("fato 'gosta de'", "música", ControleSessao.get().getFatos().get("gosta de"));
		assertEquals("fato 'mora em'", "São Paulo", ControleSessao.get().getFatos().get("mora em"));
		assertEquals("histórico restaurado (usuário + KAIRA)", 5,
				ControleSessao.get().getHistorico().size());
		assertEquals("primeiro turno é do usuário", "usuario",
				ControleSessao.get().getHistorico().get(0).getAutor());
	}

	/** O critério da §6.1: "Bem-vindo de volta, João!" + retoma o tópico. */
	@Test
	public void boasVindasCumprimentaERetomaTopico() throws Exception {
		ControleSessao.processa("meu nome é João");
		ControleSessao.topico("musica");
		ControleSessao.gravaSessao();

		ControleSessao.reset();
		ControleSessao.carregaSessao();

		String bemVindo = ControleSessao.boasVindas();
		assertNotNull("reabrir com nome conhecido devia gerar boas-vindas", bemVindo);
		assertTrue("faltou a fórmula de volta: " + bemVindo, bemVindo.contains("Bem-vindo de volta"));
		assertTrue("faltou o nome: " + bemVindo, bemVindo.contains("João"));
		assertTrue("faltou retomar o tópico: " + bemVindo, bemVindo.contains("musica"));
	}

	/** Sem nome não há boas-vindas personalizada (primeira visita não repete nada). */
	@Test
	public void boasVindasNulaSemNomePersistido() {
		assertNull("sessão sem nome não tem boas-vindas", ControleSessao.boasVindas());
	}

	/** Primeira execução (sem arquivo): abre limpo, sem erro e sem saudação. */
	@Test
	public void primeiraExecucaoAbreLimpo() {
		assertFalse("sem arquivo devia ser sessão nova", ControleSessao.carregaSessao());
		assertNull("sessão nova sem nome", ControleSessao.get().getNomeUsuario());
		assertEquals("sessão nova zerada", 0, ControleSessao.get().getTurnos());
		assertNull(ControleSessao.boasVindas());
	}

	/** Arquivo corrompido não derruba a abertura: vira sessão nova. */
	@Test
	public void arquivoCorrompidoNaoDerrubaAbertura() throws Exception {
		Files.write(arquivo(), "{isso nao e json".getBytes(StandardCharsets.UTF_8));

		assertFalse("arquivo corrompido devia virar sessão nova, não explodir",
				ControleSessao.carregaSessao());
		assertNull("sessão nova mesmo com arquivo quebrado", ControleSessao.get().getNomeUsuario());
		assertEquals(0, ControleSessao.get().getTurnos());
	}
}
