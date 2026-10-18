package com.JCODE.controller;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.JCODE.util.Caminhos;

/**
 * O palavras.json faltando nao pode deixar o bot sem correcao (issue #47).
 *
 * O buraco (diagnosticado medindo, antes do fix): buscaArquivoPalavra() num
 * FileNotFoundException fazia `return null` -> carregaCache() guardava null no
 * cache -> a primeira chamada de existsPalavras()/correcaoPalavra() estourava
 * NPE. Apagar o arquivo (ou clonar o repo sem ele, ou 1a instalacao) derrubava
 * a correcao inteira.
 *
 * O fluxo novo, a pedido do autor: "no return null do if(FileNotFound), ele
 * tem que criar o arquivo lendo a base de intents e pegando as palavras" - ou
 * seja: gera o dicionario das fontes (base de intents + sinonimos, se a fonte
 * entrar), GRAVA o palavras.json e devolve a lista. Commitado tem que ser
 * igual ao regenerado.
 *
 * Isolamento: sysprop kaira.home aponta pra um dir temporario com uma base de
 * intents MINIMA (schema do arquivo real: intento + exemplos[].texto) e o
 * grupos_sinonimos.json de fixture. O palavras.json la NAO existe - esse e o
 * cenario. O @After limpa o sysprop e recarrega o cache REAL do repo, entao a
 * suite inteira continua vendo o dicionario do autor.
 */
public class PalavrasRegeneraAoFaltarTest {

	private Path temp;

	@Before
	public void montaBaseMinimaSemPalavras() throws Exception {
		temp = Files.createTempDirectory("kaira47");
		Path recursos = temp.resolve("KAIRA").resolve("resources");
		Files.createDirectories(recursos);

		Files.write(recursos.resolve("intencoes.json"), (
				"{\"intencoes\": ["
						+ "{\"intento\": \"filmes\", \"exemplos\": ["
						+ "{\"texto\": \"qual seu filme favorito\"},"
						+ "{\"texto\": \"gosto muito de filme de acao\"}]},"
						+ "{\"intento\": \"cumprimentar\", \"exemplos\": ["
						+ "{\"texto\": \"oi tudo bem\"}]}"
						+ "]}").getBytes(StandardCharsets.UTF_8));

		Files.write(recursos.resolve("grupos_sinonimos.json"), (
				"{\"fonte\": \"fixture do PalavrasRegeneraAoFaltarTest\","
						+ "\"grupos\": [[\"filme\", \"pelicula\"]]}").getBytes(StandardCharsets.UTF_8));

		// o cenario: palavras.json NAO e criado - ele tem que faltar
		System.setProperty("kaira.home", temp.toString());
		Caminhos.resetar();
	}

	@After
	public void restauraOCacheRealEDepoisOTemp() throws Exception {
		Caminhos.resetar();
		System.clearProperty("kaira.home");
		ControlePalavra.carregaCache();

		if (temp != null && Files.exists(temp)) {
			try (Stream<Path> caminhos = Files.walk(temp)) {
				caminhos.sorted(Comparator.reverseOrder()).forEach(p -> {
					try {
						Files.delete(p);
					} catch (Exception ignorada) {
					}
				});
			}
		}
	}

	/** O cache sai montado da base, nao null (era o NPE de antes). */
	@Test
	public void arquivoFaltandoMontaOCacheDaBase() {
		ControlePalavra.carregaCache();

		assertNotNull("arquivo ausente nao pode deixar o palavrasCache null",
				ControlePalavra.palavrasCache);
		assertTrue("cache regenerado tem que cobrir a base ('filme' vem do fixture de intents)",
				ControlePalavra.existsPalavras("filme"));
		assertTrue("o cache regenerado tem que ter tamanho (nao uma lista capenga)",
				ControlePalavra.palavrasCache.size() > 0);
	}

	/** E o arquivo tem que passar a EXISTIR - o fluxo cria o que faltava. */
	@Test
	public void fluxoCriaOArquivoQueFaltava() throws Exception {
		ControlePalavra.carregaCache();

		assertTrue("o fluxo tem que gravar o palavras.json que faltava",
				Files.exists(temp.resolve("KAIRA").resolve("resources").resolve("palavras.json")));
	}
}
