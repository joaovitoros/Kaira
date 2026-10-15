package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.Set;

/**
 * Testa a geracao de respostas com sinonimos (Tarefa #13, issue #13): o
 * CriarResposta sorteia, pra cada palavra, um vizinho do grupo de sinonimos
 * (ou mantem a palavra) e remonta a frase.
 *
 * O metodo usa Random, entao nao da pra assertar a saida exata - os testes
 * amarram INVARIANTES, com o conjunto de vizinhos ESTATICO (verificado
 * contra o grupos_sinonimos.json real, nao calculado em tempo de teste -
 * calculado em tempo de teste o teste viraria tautologia e nao pegaria
 * stem vazado pelo indice da Secao 5.3/5.4):
 *
 *   - bairro de "dizer": contar, declinar, dita, dizer, fala, falar,
 *     narrar, referir, relatar (9 palavras)
 *   - bairro de "voce": amece, tu, voce (3 palavras)
 *
 * Se a curadoria dos grupos mudar um dia, estes conjuntos sao atualizados
 * aqui - mesmo espirito do MatcherUnificadoTest (par verificado a mao).
 *
 * FormulaResposta nao e coberto aqui: criacaoLeituraRespostas tem
 * Math.random e ControleFace (UI) - caminho so de app real.
 *
 * Roda com CWD na raiz do projeto (indice de grupos REAL via carregaCache).
 */
public class ControleRespostaCriarRespostaTest {

	/** Bairros estaticos verificados contra o arquivo real de grupos. */
	private static final Set<String> GRUPO_DIZER = Set.of(
			"contar", "declinar", "dita", "dizer", "fala", "falar",
			"narrar", "referir", "relatar");

	private static final Set<String> GRUPO_VOCE = Set.of("amece", "tu", "voce");

	/** O indice de grupos e REAL; a base de intents nao entra aqui. */
	@Before
	public void carregaCacheDeSinonimos() {
		ControleSinonimo.carregaCache();
	}

	/** Resposta nula (entrada desconhecida) passa direto - sem explodir. */
	@Test
	public void nuloDevolveNull() {
		assertNull(ControleResposta.CriarResposta(null));
	}

	/** Palavra sem grupo volta igual - o gerador nao chuta palavra de fora. */
	@Test
	public void palavraSemGrupoFicaIgual() {
		assertEquals("zxqw plmok", ControleResposta.CriarResposta("zxqw plmok"));
	}

	/**
	 * O gerador so troca por vizinho do grupo CRU. 30 corridas em "dizer":
	 * toda saida pertence ao bairro verificado. Se o indice da Secao
	 * 5.3/5.4 vazar stems (buscaGrupoSinonimo lendo o indice stemmado),
	 * sai "diz"/"cont"/"fal" e este teste grita.
	 */
	@Test
	public void substituicaoSoDeVizinhoDoGrupo() {
		for (int i = 0; i < 30; i++) {
			String saida = ControleResposta.CriarResposta("dizer");
			assertTrue("saida fora do grupo de 'dizer': " + saida,
					GRUPO_DIZER.contains(saida));
		}
	}

	/**
	 * Em 30 corridas, pelo menos UMA troca acontece de verdade (o proprio
	 * grupo contem a palavra, entao 1/9 das corridas sortea a mesma - a
	 * chance de 30 seguidas nao trocarem e (1/9)^30, zero practical).
	 */
	@Test
	public void algumaTrocaAcontece() {
		boolean trocou = false;
		for (int i = 0; i < 30; i++) {
			if (!"dizer".equals(ControleResposta.CriarResposta("dizer"))) {
				trocou = true;
				break;
			}
		}
		assertTrue("em 30 corridas nenhuma troca aconteceu - feature morta", trocou);
	}

	/** Grupo pequeno tambem respeita o bairro: {amece, tu, voce}. */
	@Test
	public void substituicaoNoGrupoPequenoDeVoce() {
		for (int i = 0; i < 30; i++) {
			String saida = ControleResposta.CriarResposta("voce");
			assertTrue("saida fora do grupo de 'voce': " + saida,
					GRUPO_VOCE.contains(saida));
		}
	}

	/** Entrada de 3 palavras sai com 3 - o gerador nao come nem inventa. */
	@Test
	public void contagemDePalavrasPreservada() {
		for (int i = 0; i < 10; i++) {
			String saida = ControleResposta.CriarResposta("dizer bom dia");
			assertNotNull(saida);
			assertEquals("contagem de palavras mudou: " + saida,
					3, saida.split(" ").length);
		}
	}

	/**
	 * buscaGrupoSinonimo devolve o bairro CRU exato de "dizer" - o assert
	 * exato (nao so contem) tambem pega stem vazado pelo indice da
	 * Secao 5.3/5.4.
	 */
	@Test
	public void buscaGrupoSinonimoDevolveGrupoCru() {
		assertEquals("buscaGrupoSinonimo devia devolver o grupo cru exato",
				GRUPO_DIZER, ControleSinonimo.buscaGrupoSinonimo("dizer"));
	}

	/** Palavra sem grupo devolve set vazio (nao null, nao chute). */
	@Test
	public void buscaGrupoSinonimoSemGrupoDevolveVazio() {
		Set<String> vizinhos = ControleSinonimo.buscaGrupoSinonimo("zxqw");
		assertNotNull(vizinhos);
		assertTrue("palavra sem grupo devia devolver set vazio", vizinhos.isEmpty());
	}
}
