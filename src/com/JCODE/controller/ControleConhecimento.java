package com.JCODE.controller;

import com.JCODE.domain.Conhecimento;
import com.JCODE.domain.Conhecimentos;
import com.JCODE.util.Caminhos;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * #37 (C3 piloto) - o dicionario de conhecimentos atras do {topico}.
 *
 * conhecimentos.json (padrao do projeto: origem com fonte+licenca por item):
 * termo = nome de intent da base, que e o valor de topicoAtual; resumo =
 * frase curta que a KAIRA fala quando pedem "conta mais" sobre algo com
 * entrada; texto = versao maior (reserva pro futuro "explica melhor").
 *
 * REGRA igual ao Registro: log/pro dado nunca derruba o fluxo - arquivo
 * ausente ou topico sem entrada devolve null e o continuacao_topico cai no
 * bank atual (fallback nunca piora).
 */
public final class ControleConhecimento {

	private static ObjectMapper objectMapper = null;
	private static Map<String, Conhecimento> porTermo = null;

	/** Le (ou rele) o conhecimentos.json e indexa as entradas por termo. */
	public static synchronized void carrega() {
		porTermo = new HashMap<>();
		try {
			if (objectMapper == null)
				objectMapper = new ObjectMapper();

			FileInputStream fileInputStream = new FileInputStream(Caminhos.recurso("conhecimentos.json").toFile());
			InputStreamReader reader = new InputStreamReader(fileInputStream, StandardCharsets.UTF_8);

			Conhecimentos raiz = objectMapper.readValue(reader, Conhecimentos.class);
			if (raiz.getConhecimentos() != null)
				for (Conhecimento c : raiz.getConhecimentos())
					if (c.getTermo() != null)
						porTermo.put(c.getTermo(), c);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void garanteBase() {
		if (porTermo == null)
			carrega();
	}

	/** Resumo curto do topico, ou null quando nao tem entrada. */
	public static String resumo(String termo) {
		Conhecimento c = busca(termo);
		return c != null ? c.getResumo() : null;
	}

	/** Texto maior do topico (futuro "explica melhor"), ou null. */
	public static String texto(String termo) {
		Conhecimento c = busca(termo);
		return c != null ? c.getTexto() : null;
	}

	/** Entradas carregadas (o spec confere volume e licenca por item). */
	public static Collection<Conhecimento> todos() {
		garanteBase();
		return porTermo.values();
	}

	private static Conhecimento busca(String termo) {
		if (termo == null || termo.isBlank())
			return null;
		garanteBase();
		return porTermo.get(termo);
	}
}
