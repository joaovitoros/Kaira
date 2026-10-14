package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.JCODE.domain.Exemplo;
import com.JCODE.domain.Intencao;
import com.JCODE.domain.RespostaIntencao;
import com.JCODE.util.Normalizador;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Teste de INTEGRACAO do carregamento do cache (Tarefa #7, migração p/
 * intencoes.json, §5.1).
 *
 * Substitui o CarregaCacheTest da era respostas.json. O NormalizadorTest
 * cobre o Normalizador isolado (unidade); este cobre o WIRING: a hora em que
 * carregaCache() lê intencoes.json e aplica o normalizador nos campos CERTOS.
 *
 * A lição que sobrevive da Tarefa #2 e vale de novo aqui: normalizar a chave
 * DE MATCH (Exemplo.texto) e o que garante o acerto; normalizar o que é
 * EXIBIDO (RespostaIntencao.texto) é o que quebra. Bug desse tipo passa
 * ilesos em teste de unidade e só aparece na integração - por isso este
 * arquivo existe.
 *
 * Roda contra o KAIRA/resources/intencoes.json REAL (caminho relativo),
 * entao o CWD tem que ser a raiz do projeto.
 */
public class ControleRespostaCarregaCacheTest {

	@Before
	public void carregaCacheReal() {
		ControleResposta.carregaCache();
	}

	private List<Intencao> intencoes() {
		return ControleResposta.getIntencoes();
	}

	private Intencao intencao(String nome) {
		return intencoes().stream()
				.filter(i -> nome.equals(i.getIntento()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("intencao '" + nome + "' nao encontrada no cache"));
	}

	/** Le o intencoes.json curado direto do disco (sem passar pelo cache). */
	private JsonNode arquivoCurado() throws Exception {
		byte[] bytes = java.nio.file.Files.readAllBytes(
				java.nio.file.Paths.get("KAIRA", "resources", "intencoes.json"));
		return new ObjectMapper().readTree(bytes);
	}

	/** Carga básica: o arquivo tem que ter virado objetos de verdade. */
	@Test
	public void cacheCarregadoNaoVazio() {
		assertNotNull("getIntencoes() devia devolver a lista carregada", intencoes());
		assertFalse("getIntencoes() devia ter itens", intencoes().isEmpty());
	}

	/**
	 * A CHAVE de match (Exemplo.texto) deve chegar com o TEXTO ORIGINAL do
	 * arquivo - sem ser normalizado na carga (Tarefa #8, issue #8).
	 *
	 * O intencoes.json curado tem exemplos acentuados ("Bom falar com você").
	 * Se o carregaCache mutasse o texto no lugar (toLowerCase + sem acento),
	 * este teste falharia: o acento sumiria do cache. Guardar o original e o
	 * que impede a gravacao do aprendizado de degradar o arquivo curado em
	 * minusculo/sem acento - o bug da Tarefa #2 revivido na gravacao.
	 *
	 * A comparacao (fraseExata/melhorIntencao) normaliza NA HORA; ver
	 * ControleRespostaFraseExataTest pra garantir que o match continua casando.
	 */
	@Test
	public void exemplosGuardamTextoOriginal() throws Exception {
		// Le o arquivo curado direto e confere que NENHUM Exemplo foi alterado
		// pela carga. Se a normalizacao da Tarefa #2 ainda rodasse aqui (toLowerCase
		// + sem acento no lugar), exemplos com caixa/algo diferente da forma
		// normalizada seriam mutados - e este teste pegaria.
		//
		// A propriedade: para todo Exemplo, o texto carregado deve ser igual ao
		// texto gravado no arquivo. O carregaCache nao pode reescrever nada.
		JsonNode raiz = arquivoCurado();
		Set<String> textosNoArquivo = new HashSet<>();
		for (JsonNode intento : raiz.get("intencoes")) {
			for (JsonNode ex : intento.get("exemplos")) {
				textosNoArquivo.add(ex.get("texto").asText());
			}
		}
		assertFalse("arquivo curado sem exemplos (fixture quebrada)", textosNoArquivo.isEmpty());

		Set<String> textosNoCache = new HashSet<>();
		for (Intencao i : intencoes()) {
			for (Exemplo e : i.getExemplos()) {
				textosNoCache.add(e.getTexto());
			}
		}

		assertEquals(
				"a carga alterou os exemplos (normalizou no lugar - remova a mutacao do carregaCache)",
				textosNoArquivo, textosNoCache);
	}

	/** O texto EXIBIDO (RespostaIntencao.texto) deve manter acento e caixa alta. */
	@Test
	public void respostasExibidasMantemAcento() {
		Intencao cumprimentar = intencao("cumprimentar");

		boolean temAcentuada = cumprimentar.getRespostas().stream()
				.anyMatch(r -> "Bom falar com você".equals(r.getTexto()));

		assertTrue(
				"resposta exibida perdeu o acento (normalizaram o campo errado)",
				temAcentuada);
	}

	/** Origem tem que sobreviver à carga (é ela que garante a atribuição de licença). */
	@Test
	public void exemplosCarregamOrigem() {
		for (Intencao i : intencoes()) {
			for (Exemplo e : i.getExemplos()) {
				assertNotNull("Exemplo.origem nao pode ser null (intencao " + i.getIntento() + ")", e.getOrigem());
				assertFalse("Exemplo.origem nao pode ser vazia", e.getOrigem().trim().isEmpty());
			}
		}
	}

	/** Cada pool de respostas deve ter origem e faixa de humor válida (0..10). */
	@Test
	public void respostasDoPoolCarregamOrigemEFaixaDeHumor() {
		for (Intencao i : intencoes()) {
			for (RespostaIntencao r : i.getRespostas()) {
				assertNotNull("RespostaIntencao.origem nao pode ser null (intencao " + i.getIntento() + ")", r.getOrigem());
				assertTrue("humorMin deve ficar em 0..10, veio " + r.getHumorMin(),
						r.getHumorMin() >= 0 && r.getHumorMin() <= 10);
				assertTrue("humorMax deve ficar em 0..10, veio " + r.getHumorMax(),
						r.getHumorMax() >= 0 && r.getHumorMax() <= 10);
				assertTrue("humorMin (" + r.getHumorMin() + ") nao pode exceder humorMax (" + r.getHumorMax() + ")",
						r.getHumorMin() <= r.getHumorMax());
			}
		}
	}

	/** Cenario ponta a ponta: digitado com acento e pontuacao, casa com a base. */
	@Test
	public void entradaAcentuadaCasaComABase() {
		String entrada = Normalizador.texto("Você é legal?");

		String resposta = ControleResposta.lerResposta(entrada);

		assertNotNull(
				"entrada '" + entrada + "' devia casar com a base (frase normalizada)",
				resposta);
	}
}
