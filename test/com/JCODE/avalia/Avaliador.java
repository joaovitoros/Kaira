package com.JCODE.avalia;

import com.JCODE.controller.ControleResposta;
import com.JCODE.util.Normalizador;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Harness de avaliacao do KAIRA (Tarefa #3) - a regua de medição.
 *
 * NAO e um teste: e infra de teste. Fica em test/ de proposito, nao vai pro
 * app. Existe porque lerResposta/decifrar nao servem pra medir (System.exit,
 * Math.random, UI) - o Avaliador usa a consulta pura resolver (exato ->
 * classificador), que nao tem efeito colateral.
 *
 * Roda cada entrada do dataset por Normalizador.texto() -> resolver() e
 * compara com o esperado. Dois tiers:
 *
 *   - REGRESSAO (fraseEsperada != null): tem que casar hoje.
 *   - GAP     (fraseEsperada == null + alvoFase1): nao resolve hoje; e o
 *     placar que o classificador da Fase 1 (§5.2) precisa baixar. (Na Tarefa
 *     #5 o classificador fecha 2 gaps - eles viraram regressao la em cima.)
 *
 * Uso (CWD na raiz do projeto):
 *   java -cp "out/test;externallibs/*" com.JCODE.avalia.Avaliador
 */
public class Avaliador {

	/** Um caso do dataset. ignoreUnknown: permite campos de doc sem quebrar. */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Caso {
		@JsonProperty("entrada")
		public String entrada;
		/** Frase-base que a entrada DEVE casar. null = caso de gap. */
		@JsonProperty("fraseEsperada")
		public String fraseEsperada;
		/** Frase ideal que o classificador da Fase 1 deve alcancar (só documenta o gap). */
		@JsonProperty("alvoFase1")
		public String alvoFase1;
		@JsonProperty("nota")
		public String nota;
	}

	/** Raiz do dataset. ignoreUnknown: o dataset pode ter campos de doc (ex.: _doc). */
	@JsonIgnoreProperties(ignoreUnknown = true)
	public static class Dataset {
		@JsonProperty("casos")
		public List<Caso> casos = new ArrayList<>();
	}

	/** O resultado da corrida - e o que o AvaliadorTest asserta. */
	public static class Resultado {
		public int casosRegressao;
		public int acertosRegressao;
		public List<String> falhasRegressao = new ArrayList<>();
		public int casosGap;
		public List<String> detalhesGap = new ArrayList<>();
	}

	public static Resultado avaliar(String caminhoDataset) {
		Resultado r = new Resultado();

		Dataset dataset;
		try (InputStreamReader reader = new InputStreamReader(
				new FileInputStream(new File(caminhoDataset)), StandardCharsets.UTF_8)) {
			// UTF-8 explicito: o dataset tem acentos (Voce, musica...)
			dataset = new ObjectMapper().readValue(reader, Dataset.class);
		} catch (Exception e) {
			throw new RuntimeException("Falha ao carregar o dataset: " + caminhoDataset, e);
		}

		for (Caso c : dataset.casos) {
			// quem normaliza e o avaliador (no app, quem normaliza e o decifrar).
			// resolver recebe a entrada JA normalizada e faz exato -> classificador.
			String resolvida = ControleResposta.resolver(Normalizador.texto(c.entrada));

			if (c.fraseEsperada != null) {
				r.casosRegressao++;
				if (c.fraseEsperada.equals(resolvida)) {
					r.acertosRegressao++;
				} else {
					r.falhasRegressao.add("\"" + c.entrada + "\" -> esperado \"" + c.fraseEsperada
							+ "\", veio " + (resolvida == null ? "null" : "\"" + resolvida + "\""));
				}
			} else {
				r.casosGap++;
				r.detalhesGap.add("\"" + c.entrada + "\" -> alvo Fase 1: \""
						+ (c.alvoFase1 == null ? "?" : c.alvoFase1) + "\""
						+ (c.nota == null ? "" : "  (" + c.nota + ")"));
			}
		}

		return r;
	}

	public static void main(String[] args) {
		String caminho = args.length > 0 ? args[0] : "test/resources/avaliacao.json";
		Resultado r = avaliar(caminho);

		double acuracia = r.casosRegressao == 0 ? 0.0
				: (100.0 * r.acertosRegressao / r.casosRegressao);

		System.out.println();
		System.out.println("=== Avaliacao KAIRA ===");
		System.out.println("Dataset: " + caminho);
		System.out.println("Total de casos: " + (r.casosRegressao + r.casosGap));
		System.out.println();
		System.out.printf("REGRESSAO (tem que casar hoje): %d/%d  (%.1f%%)%n",
				r.acertosRegressao, r.casosRegressao, acuracia);
		System.out.println("GAPS (alvo do classificador - Fase 1): " + r.casosGap);
		System.out.println();

		if (!r.falhasRegressao.isEmpty()) {
			System.out.println("!! Falhas de regressao:");
			r.falhasRegressao.forEach(f -> System.out.println("   - " + f));
			System.out.println();
		}

		if (!r.detalhesGap.isEmpty()) {
			System.out.println("Gaps ainda nao resolvidos (o que a Fase 1 tem que ganhar):");
			r.detalhesGap.forEach(g -> System.out.println("   - " + g));
			System.out.println();
		}
	}
}
