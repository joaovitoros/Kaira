package com.JCODE.controller;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Test;

import java.util.Arrays;

/**
 * A correcao de palavra NAO pode depender da ordem do arquivo.
 *
 * correcaoPalavra guarda o melhor candidato com "<" estrito: em EMPATE de
 * distancia vence o PRIMEIRO da lista. Como a distancia de Levenshtein cobra
 * 2 numa troca de letras vizinhas, "fimle" empatava a distancia 2 com
 * fale/fim/fique/fome - e o bot falava de "filme" so porque o arquivo atual,
 * por acidente historico (o +filme da issue #42), tem o filme antes dos
 * outros. A lista alfabetica (que uma regeneracao produziria) corrigia pra
 * "fale".
 *
 * Isso e uma mina: o fluxo de regeneracao do palavras.json (arquivo faltando
 * -> gera da base de intents) produziria outra ordem e a correcao mudaria de
 * resposta sem ninguem perceber. A propriedade que tem que valer e uma so:
 * MESMA lista de candidatos, QUALQUER ordem, MESMO resultado.
 *
 * Caso real: FluxoConversaRealTest - log de 17/10, o usuario digitou "fimle"
 * e a resposta do bot tem que ser sobre "filme".
 */
public class CorrecaoOrdemArquivoTest {

	@After
	public void restauraVocabularioReal() {
		ControlePalavra.carregaCache();
	}

	/** Controle: a ordem que o arquivo atual tem por acidente historico. */
	@Test
	public void ordemDoArquivoAtualDaFilme() {
		ControlePalavra.palavrasCache = Arrays.asList("filme", "fale", "fim", "fique", "fome");
		assertEquals("'fimle' com o 'filme' na frente -> 'filme'",
				"filme", ControlePalavra.correcaoPalavra("fimle"));
	}

	/** A MESMA lista na ordem alfabetica (o que a regeneracao geraria) tem que dar igual. */
	@Test
	public void ordemAlfabeticaDaFilme() {
		ControlePalavra.palavrasCache = Arrays.asList("fale", "filme", "fim", "fique", "fome");
		assertEquals("'fimle' com a lista gerada -> 'filme' (mesmo resultado, ordem nao decide)",
				"filme", ControlePalavra.correcaoPalavra("fimle"));
	}
}
