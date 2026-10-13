package com.JCODE.arquitetura;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.Test;

/**
 * Teste de ARQUITETURA (Tarefa #4 - G7). Nao testa comportamento: testa uma
 * regra de estrutura do codigo.
 *
 * Regra: nenhum arquivo .java do src/ pode conter o caminho de dado hardcoded
 * "KAIRA/resources". Tudo tem que passar por Caminhos.recurso(...).
 *
 * Por que existe: quando trocamos os 12 caminhos hardcoded, 2 foram perdidos
 * (ControleSinonimo leitura e ControleResposta escrita) e a SUITE INTEIRA
 * continuou verde - porque os testes rodam com CWD na raiz, onde caminho
 * relativo ainda resolve por acaso. Esse teste e o que pega esse tipo de
 * falha, que teste de comportamento nao ve.
 *
 * Roda com CWD na raiz do projeto (la fica o src/).
 */
public class SemCaminhoHardcodedTest {

	@Test
	public void nenhumCaminhoHardcodedDeDadosNoFonte() throws IOException {
		Path src = Paths.get("src");
		assertTrue("src/ deve existir - rode com CWD na raiz do projeto", Files.isDirectory(src));

		List<String> infratores = new ArrayList<>();

		try (Stream<Path> arquivos = Files.walk(src)) {
			arquivos.filter(p -> p.toString().endsWith(".java")).forEach(p -> {
				try {
					List<String> linhas = Files.readAllLines(p, StandardCharsets.UTF_8);
					for (int i = 0; i < linhas.size(); i++) {
						if (linhas.get(i).contains("KAIRA/resources")) {
							infratores.add(p + ":" + (i + 1));
						}
					}
				} catch (IOException e) {
					throw new RuntimeException("Falha ao ler " + p, e);
				}
			});
		}

		assertTrue(
				"caminho de dado hardcoded no fonte (use Caminhos.recurso(...)): " + infratores,
				infratores.isEmpty());
	}
}
