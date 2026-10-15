package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.JCODE.util.Normalizador;

import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Guardas de DADO sobre o stopwords.txt (Tarefa #12, issue #12, Secao 5.4)
 * - o mesmo espirito do GruposSinonimosCuradoTest: o arquivo e dado curado
 * do repo (lista do plano normalizada + "vc"), e o carregamento garante a
 * forma canonica que o filtro do classificador espera.
 *
 * Roda com CWD na raiz do projeto (le KAIRA/resources/stopwords.txt).
 */
public class StopwordsCuradoTest {

	/** Mesmo padrao dos outros testes: o carregaCache monta o conjunto. */
	@Before
	public void carregaCache() {
		ControleResposta.carregaCache();
	}

	private static Path arquivo() {
		return Paths.get("KAIRA", "resources", "stopwords.txt");
	}

	/** Linhas de palavra: ignora vazias e comentarios "#". */
	private static List<String> linhasDoArquivo() throws Exception {
		List<String> out = new ArrayList<>();
		for (String linha : Files.readAllLines(arquivo(), StandardCharsets.UTF_8)) {
			String t = linha.trim();
			if (t.isEmpty() || t.startsWith("#"))
				continue;
			out.add(t);
		}
		return out;
	}

	/** O arquivo existe e tem a lista (sem ele o filtro vira silencio). */
	@Test
	public void arquivoExisteNaoVazio() throws Exception {
		assertTrue("stopwords.txt devia existir", Files.exists(arquivo()));
		assertFalse("stopwords.txt devia ter linhas de palavra", linhasDoArquivo().isEmpty());
	}

	/**
	 * Toda linha ja vem em forma de texto(): lowercase, sem acento, uma
	 * palavra so (sem espaco), sem repetir - contrato do formato.
	 */
	@Test
	public void linhasJaNormalizadasESemDuplicata() throws Exception {
		Set<String> vistos = new HashSet<>();
		for (String linha : linhasDoArquivo()) {
			assertEquals("linha devia estar em forma texto(): " + linha,
					Normalizador.texto(linha), linha);
			assertFalse("linha com espaco: " + linha, linha.contains(" "));
			assertTrue("linha repetida: " + linha, vistos.add(linha));
		}
	}

	/** Palavras-chave da lista do plano + o aditivo "vc". */
	@Test
	public void contemPalavrasChaveDoPlano() throws Exception {
		List<String> linhas = linhasDoArquivo();
		for (String esperada : new String[] { "a", "de", "que", "como",
				"voce", "nao", "quando", "qual", "vc" }) {
			assertTrue("stopword chave faltando: " + esperada, linhas.contains(esperada));
		}
	}

	/** Palavra de conteudo nao pode ser stopword (senao a conta perde sinal). */
	@Test
	public void naoContemPalavraDeConteudo() throws Exception {
		List<String> linhas = linhasDoArquivo();
		for (String proibida : new String[] { "musica", "gosta", "falar", "jogo" }) {
			assertFalse("palavra de conteudo nao devia ser stopword: " + proibida,
					linhas.contains(proibida));
		}
	}

	/**
	 * O conjunto carregado fica em forma canonica pos-stem: cada elemento e
	 * igual ao proprio stem, casando com o pipeline tokensDe (stem -> filtro).
	 */
	@Test
	public void conjuntoCarregadoEmFormaCanonica() {
		assertNotNull("ControleResposta.stopwords devia ter carregado",
				ControleResposta.stopwords);
		assertTrue("stopwords devia conter 'de'", ControleResposta.stopwords.contains("de"));
		assertTrue("stopwords devia conter 'vc'", ControleResposta.stopwords.contains("vc"));
		for (String w : ControleResposta.stopwords) {
			assertEquals("stopword carregada devia ser pos-stem (canonica): " + w,
					w, Normalizador.stem(w));
		}
	}
}
