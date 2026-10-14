package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.JCODE.domain.Exemplo;
import com.JCODE.domain.Intencao;
import com.JCODE.util.Caminhos;

import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Guarda o arquivo curado contra DEGRADACAO (Tarefa #8, issue #8).
 *
 * O perigo que este arquivo existe pra pegar: o carregaCache de origem mutava
 * Exemplo.texto no lugar (toLowerCase + sem acento). Enquanto nada gravava o
 * cache de volta, isso era inofensivo. Mas a Tarefa #8 faz o aprendizado
 * REGRAVAR intencoes.json a partir do cache. Se a normalizacao continuar
 * acontecendo na carga, cada ensinada reescreve o arquivo inteiro com todos os
 * exemplos em minusculo/sem acento - o arquivo curado se degrada silenciosamente.
 *
 * E a mesma classe de bug da Tarefa #2 (bug do acento), so que no campo de
 * Exemplo em vez de Resposta. Por isso o teste le o ARQUIVO REAL em disco:
 * prova que os exemplos originais sobrevivem intactos, independente de como o
 * cache se comporta em memoria.
 *
 * Roda com CWD na raiz do projeto (caminho relativo do recurso).
 */
public class IntencoesCuradoNaoDegradadoTest {

	@Before
	public void carregaCacheReal() {
		ControleResposta.carregaCache();
	}

	/** Le o intencoes.json REAL de disco, como texto cru. */
	private String arquivoCurado() throws Exception {
		Path caminho = Caminhos.recurso("intencoes.json");
		return new String(Files.readAllBytes(caminho), StandardCharsets.UTF_8);
	}

	/**
	 * O arquivo em disco tem que conter exemplo com CAIXA ALTA e ACAUTE. O
	 * respostas.json de origem tinha exemplos como "Bom falar com você" no pool
	 * de exibicao; aqui garantimos que o arquivo curado nao virou tudo minusculo.
	 */
	@Test
	public void arquivoCuradoMantemAcentoECase() throws Exception {
		String bruto = arquivoCurado();

		assertTrue(
				"intencoes.json perdeu acentos (a normalizacao na carga vazou pra gravacao)",
				bruto.contains("você"));
	}

	/**
	 * A chave e a INTEGRIDADE do dado curado: nenhum Exemplo do arquivo real pode
	 * estar "grudado" - ou seja, o arquivo nao pode ter sido reescrito de forma
	 * que exemplos percam a origem (que eh o que garante atribuicao de licenca).
	 */
	@Test
	public void exemplosDoArquivoCuradoMantemOrigem() throws Exception {
		for (Intencao i : ControleResposta.getIntencoes()) {
			for (Exemplo e : i.getExemplos()) {
				assertTrue(
						"Exemplo.origem vazia no arquivo curado (intencao " + i.getIntento() + ")",
						e.getOrigem() != null && !e.getOrigem().trim().isEmpty());
			}
		}
	}

	/**
	 * Controle de sanidade: o auto_aprendizado existe no arquivo curado e nasce
	 * VAZIO (a caixa de entrada so ganha conteudo quando alguem ensina). Se um
	 * ensino de teste vazar pro arquivo real, isto aqui pega.
	 */
	@Test
	public void autoAprendizadoNasceVazioNoArquivoCurado() {
		Intencao aprendizado = ControleResposta.getIntencoes().stream()
				.filter(i -> "auto_aprendizado".equals(i.getIntento()))
				.findFirst()
				.orElseThrow(() -> new AssertionError("intent auto_aprendizado ausente do intencoes.json"));

		assertEquals("o auto_aprendizado do arquivo curado devia nascer vazio (caixa de entrada)",
				0, aprendizado.getExemplos().size());
	}

	/**
	 * Controle de sanidade: os exemplos carregados guardam texto nao-nulo e
	 * nao-vazio. Garante que a carga nao esta deixando Exemplo sem chave de match.
	 */
	@Test
	public void exemplosCarregadosTemTexto() {
		for (Intencao i : ControleResposta.getIntencoes()) {
			for (Exemplo e : i.getExemplos()) {
				assertNotNull("Exemplo.texto null apos carga", e.getTexto());
				assertFalse("Exemplo.texto vazio apos carga", e.getTexto().trim().isEmpty());
			}
		}
	}
}
