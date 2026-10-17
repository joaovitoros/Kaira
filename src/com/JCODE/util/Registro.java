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
 *
 * #36 (C2 - higiene do log): o destino e configuravel por -Dkaira.log.destino.
 * A janela real usa conversas.log; a suite de teste redireciona pra um arquivo
 * proprio, porque o log so serve pra curadoria se for SO uso real (o que caiu
 * em fallback/aposta, temas que repetem, DEGRAU a DEGRAU).
 */
public final class Registro {

	private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	/** #36 (C2) - arquivo efetivo do log: janela real usa conversas.log; a
	 * suite roda com -Dkaira.log.destino=<caminho> pra nao poluir. */
	public static Path arquivo() {
		String destino = System.getProperty("kaira.log.destino");
		if (destino != null && !destino.isBlank())
			return Path.of(destino);
		return Caminhos.base().resolve("conversas.log");
	}

	/** Registra um evento do fluxo (entrada, degrau, resposta, sessao...). */
	public static void linha(String evento) {
		try {
			Path alvo = arquivo();
			String texto = LocalDateTime.now().format(FORMATO) + "  " + evento + System.lineSeparator();
			Files.write(alvo, texto.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE,
					StandardOpenOption.APPEND);
		} catch (Exception ignorada) {
			// log nunca derruba o fluxo principal
		}
	}

	private Registro() {
	}
}
