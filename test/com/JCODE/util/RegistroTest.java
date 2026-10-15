package com.JCODE.util;

import static org.junit.Assert.assertTrue;

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
 */
public class RegistroTest {

	private Path arquivo() {
		return Caminhos.base().resolve("conversas.log");
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
}
