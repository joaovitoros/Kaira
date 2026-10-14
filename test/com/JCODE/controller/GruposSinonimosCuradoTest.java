package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.JCODE.domain.GruposSinonimos;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Guardas de qualidade de DADO sobre o grupos_sinonimos.json curado (Tarefa
 * #9, issue #9) - o mesmo espirito do IntencoesCuradoNaoDegradadoTest: o
 * arquivo e o dado curado do repo, e o pipeline de curadoria (Wiktionary pt
 * via kaikki.org, CC BY-SA 4.0) garante propriedades que o dado bruto nao
 * garante. Se um dia o arquivo for regenerado, estas guardas pegam regressao
 * de curadoria.
 */
public class GruposSinonimosCuradoTest {

	private JsonNode arquivoCurado() throws Exception {
		byte[] bytes = Files.readAllBytes(Paths.get("KAIRA", "resources", "grupos_sinonimos.json"));
		return new ObjectMapper().readTree(bytes);
	}

	private List<List<String>> gruposCarregados() throws Exception {
		GruposSinonimos dominio = new ObjectMapper().readValue(
				Paths.get("KAIRA", "resources", "grupos_sinonimos.json").toFile(), GruposSinonimos.class);
		return dominio.getGrupos();
	}

	/** CC BY-SA exige credito: o campo fonte fica gravado no arquivo. */
	@Test
	public void fonteDeAtribuicaoPresente() throws Exception {
		String fonte = arquivoCurado().get("fonte").asText();
		assertFalse("o campo fonte devia existir e nao ser vazio (CC BY-SA exige credito)",
				fonte == null || fonte.isEmpty());
	}

	/** O domain carrega o arquivo ignorando o campo extra "fonte". */
	@Test
	public void domainCarregaIgnorandoFonte() throws Exception {
		List<List<String>> grupos = gruposCarregados();
		assertNotNull("getGrupos() devia devolver a lista", grupos);
		assertFalse("a lista de grupos devia ter itens", grupos.isEmpty());
	}

	/** Grupos de 2 a 8 membros (a curadoria corta os gigantes de lixo). */
	@Test
	public void gruposTemDe2a8Membros() throws Exception {
		for (List<String> grupo : gruposCarregados()) {
			assertTrue("grupo devia ter de 2 a 8 membros: " + grupo,
					grupo.size() >= 2 && grupo.size() <= 8);
		}
	}

	/** Membro e palavra unica (sem espaco) e nao vazia. */
	@Test
	public void membrosSaoPalavrasUnicas() throws Exception {
		for (List<String> grupo : gruposCarregados()) {
			for (String membro : grupo) {
				assertFalse("membro vazio no grupo " + grupo,
						membro == null || membro.isEmpty());
				assertFalse("membro com espaco devia ter ficado de fora: '" + membro + "'",
						membro.contains(" "));
			}
		}
	}

	/** Sem grupo repetido (dedupe pos-colapso do pipeline). */
	@Test
	public void semGruposDuplicados() throws Exception {
		List<List<String>> grupos = gruposCarregados();
		Set<List<String>> unicos = new HashSet<>(grupos);

		assertEquals("grupos duplicados voltaram (regressao do dedupe)",
				grupos.size(), unicos.size());
	}

	/** Sem membro repetido dentro do grupo. */
	@Test
	public void semMembroDuplicadoNoGrupo() throws Exception {
		for (List<String> grupo : gruposCarregados()) {
			assertEquals("membro duplicado no grupo " + grupo,
					grupo.size(), new HashSet<>(grupo).size());
		}
	}

	/**
	 * Fixture dos outros testes: os grupos de que os testes de sinonimo
	 * dependem existem mesmo no arquivo (verificados contra o arquivo real,
	 * nao assumidos).
	 */
	@Test
	public void gruposQueOsTestesDependemExistem() throws Exception {
		Set<Set<String>> conjuntos = new HashSet<>();
		for (List<String> grupo : gruposCarregados()) {
			conjuntos.add(new HashSet<>(grupo));
		}

		assertTrue("grupo [voce, tu] sumiu do arquivo",
				conjuntos.contains(Set.of("voce", "tu")));
		assertTrue("grupo [obrigado, gracias, valeu] sumiu do arquivo",
				conjuntos.contains(Set.of("obrigado", "gracias", "valeu")));
		assertTrue("grupo [dizer, falar] sumiu do arquivo",
				conjuntos.contains(Set.of("dizer", "falar")));
		assertTrue("grupo [falar, comunicar] sumiu do arquivo",
				conjuntos.contains(Set.of("falar", "comunicar")));
	}
}
