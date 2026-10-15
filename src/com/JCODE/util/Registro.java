package com.JCODE.util;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Log de conversa em conversas.log (raiz do projeto) - o item opcional do
 * §6.2 do PLANO_DE_EVOLUCAO.md ("conversas.log com timestamp - dados para
 * analise futura"). Uma linha por evento, com carimbo de tempo.
 *
 * REGRA: log e observabilidade, nunca logica. Se o arquivo nao puder ser
 * escrito, a falha e engolida - o chat continua funcionando igual.
 */
public final class Registro {

	private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	/** Registra um evento do fluxo (entrada, degrau, resposta, sessao...). */
	public static void linha(String evento) {
		try {
			Path arquivo = Caminhos.base().resolve("conversas.log");
			String texto = LocalDateTime.now().format(FORMATO) + "  " + evento + System.lineSeparator();
			Files.write(arquivo, texto.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
					StandardOpenOption.APPEND);
		} catch (Exception ignorada) {
			// log nunca derruba o fluxo principal
		}
	}

	private Registro() {
	}
}
