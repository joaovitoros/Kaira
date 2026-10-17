package com.JCODE.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Especifica o Registro - o log de conversa em conversas.log (item opcional do
 * §6.2 do PLANO_DE_EVOLUCAO.md).
 *
 * Contrato do log:
 *  1. cada linha e "yyyy-MM-dd HH:mm:ss  <evento>" (carimbo + 2 espacos);
 *  2. linhas se ACUMULAM (APPEND) - uma chamada nunca apaga a anterior;
 *  3. o conteudo sobrevive em UTF-8 (os eventos sao frases em portugues com
 *     acento - sem isso o log de analise futura viraria lixo).
 *
 * O quarto ponto do contrato - "falha de arquivo nunca derruba o fluxo" - e
 * estrutural (try/catch dentro de linha()) e e exercitado indiretamente por
 * TODA a suíte: os hooks de decifrar/processa chamam o Registro a cada teste
 * que toca sessao/resposta, e a suíte inteira roda sem estourar.
 *
 * #36 (C2 - higiene do log): quinto ponto - o destino e configuravel
 * (-Dkaira.log.destino). A suite redireciona o proprio log e o conversas.log
 * real fica intocado, so com uso da janela - e passa a servir de insumo de
 * curadoria (fallback, aposta, temas repetidos).
 */
public class RegistroTest {

	/** #36 - salva/restaura a propriedade: teste que mexe nela nao pode vazar
	 * escrita pro conversas.log real depois de si. */
	private String destinoOriginal;

	@Before
	public void salvaDestino() {
		destinoOriginal = System.getProperty("kaira.log.destino");
	}

	@After
	public void restauraDestino() {
		if (destinoOriginal == null)
			System.clearProperty("kaira.log.destino");
		else
			System.setProperty("kaira.log.destino", destinoOriginal);
	}

	private Path arquivo() {
		return Registro.arquivo();
	}

	/** Cria o log (se preciso) e grava um evento marcado com nonce proprio. */
	@Test
	public void linhaCriaLogComCarimboDeTempo() throws Exception {
		String marcador = "registro-carimbo-" + System.nanoTime();
		Registro.linha(marcador);

		Path arq = arquivo();
		assertTrue("conversas.log nao existe em " + Caminhos.base(), Files.exists(arq));

		String texto = new String(Files.readAllBytes(arq), StandardCharsets.UTF_8);
		assertTrue("evento ausente do log: " + marcador, texto.contains(marcador));
		assertTrue("linha sem carimbo yyyy-MM-dd HH:mm:ss + 2 espacos",
				Pattern.compile("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}  " + Pattern.quote(marcador))
						.matcher(texto).find());
	}

	/** O log e acumulativo: segunda chamada nao apaga a primeira. */
	@Test
	public void linhasSeAcumulamEmOrdem() throws Exception {
		String primeiro = "registro-ordem-a-" + System.nanoTime();
		String segundo = "registro-ordem-b-" + System.nanoTime();
		Registro.linha(primeiro);
		Registro.linha(segundo);

		String texto = new String(Files.readAllBytes(arquivo()), StandardCharsets.UTF_8);
		assertTrue("primeira linha sumiu do log", texto.contains(primeiro));
		assertTrue("segunda linha ausente do log", texto.contains(segundo));
		assertTrue("APPEND inverteu a ordem das linhas",
				texto.indexOf(primeiro) < texto.indexOf(segundo));
	}

	/** Eventos em portugues com acento sobrevivem byte a byte (UTF-8). */
	@Test
	public void linhaPreservaAcentosDoEvento() throws Exception {
		String marcador = "registro-ação-café-não-" + System.nanoTime();
		Registro.linha(marcador);

		String texto = new String(Files.readAllBytes(arquivo()), StandardCharsets.UTF_8);
		assertTrue("evento com acento nao sobreviveu ao log (UTF-8 vazou)",
				texto.contains(marcador));
	}

	/**
	 * #36 (C2): a propriedade kaira.log.destino redireciona a escrita - e o
	 * motivo de a suíte nao poluir mais o conversas.log real.
	 */
	@Test
	public void propriedadeRedirecionaODestino() throws Exception {
		Path temporario = Files.createTempFile("kaira-log-teste", ".log");
		System.setProperty("kaira.log.destino", temporario.toString());

		String marcador = "registro-destino-" + System.nanoTime();
		Registro.linha(marcador);

		String texto = new String(Files.readAllBytes(temporario), StandardCharsets.UTF_8);
		assertTrue("evento nao foi pro destino redirecionado", texto.contains(marcador));
	}

	/** Sem propriedade, o destino e o conversas.log da raiz (janela real). */
	@Test
	public void semPropriedadeOCaminhoEOConversasLog() {
		System.clearProperty("kaira.log.destino");
		assertEquals(Caminhos.base().resolve("conversas.log"), Registro.arquivo());
	}
}
