package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.JCODE.domain.Exemplo;
import com.JCODE.domain.Intencao;
import com.JCODE.domain.RespostaIntencao;
import com.JCODE.util.Normalizador;

import org.junit.Before;
import org.junit.Test;

import java.util.List;

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

	/** Carga básica: o arquivo tem que ter virado objetos de verdade. */
	@Test
	public void cacheCarregadoNaoVazio() {
		assertNotNull("getIntencoes() devia devolver a lista carregada", intencoes());
		assertFalse("getIntencoes() devia ter itens", intencoes().isEmpty());
	}

	/**
	 * A CHAVE de match (Exemplo.texto) deve chegar normalizada - e normalizar
	 * de novo não muda nada (idempotente). É o equivalente, no modelo novo,
	 * do fraseEhNormalizadaAoCarregar da Tarefa #2.
	 */
	@Test
	public void exemplosChegamNormalizados() {
		for (Intencao i : intencoes()) {
			for (Exemplo e : i.getExemplos()) {
				String texto = e.getTexto();
				assertNotNull("Exemplo.texto nao pode ser null", texto);
				assertEquals(
						"Exemplo.texto devia estar normalizado: " + texto,
						Normalizador.texto(texto), texto);
			}
		}
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
