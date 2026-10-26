package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.JCODE.util.Caminhos;

/**
 * #53 - blocklist de segredo no ensino. Rec 5 do T02; sonda de 26/10 no
 * master atual (base isolada) mediu os dois caminhos de gravacao:
 *
 *   ensino DELA (P1 + "sua CHAVE ... e VALOR")   -> kaira.json
 *     "sua senha do banco e abacaxi"  -> GRAVAVA abacaxi + ack "Anotado"
 *   ensino ORGANICO (fallback -> P1/P2 -> absorve) -> intencoes.json
 *     "meu cartao e 4111111111111111" -> GRAVAVA a frase + ack "Anotado"
 *
 * Regras da #53:
 *   (1) blocklist de termos (senha/cartao/cpf/chave pix...) + 9+ digitos;
 *   (2) ensino dela: fala inteira + par (defense in depth) - recusa curada;
 *   (3) organico: guarda no absorve() (P1 direto e P2 confirmado);
 *   (4) canonico nao-secreto segue gravando/absorvendo (nao regride).
 *
 * Isolamento: kaira.home num temp com copia da base real, caches
 * recarregados no @After - a arvore de trabalho nunca e tocada.
 */
public class EnsinoSegredoTest {

	private static final String FRASE_ENSINO = "como faco pra cadastrar a cnh no app do banco";

	private Path temp;
	private Path arquivoIntencoes;

	@Before
	public void montaBaseRealIsolada() throws Exception {
		temp = Files.createTempDirectory("kaira53");
		Path recursos = temp.resolve("KAIRA").resolve("resources");
		Files.createDirectories(recursos);

		for (String nome : List.of("intencoes.json", "stopwords.txt",
				"palavras.json", "grupos_sinonimos.json", "kaira.json"))
			Files.copy(Caminhos.recurso(nome), recursos.resolve(nome));
		arquivoIntencoes = recursos.resolve("intencoes.json");

		System.setProperty("kaira.home", temp.toString());
		Caminhos.resetar();
		recarregaTudo();
		ControleSessao.reset();
		// janela de nome FECHADA (licao do OrganicTest): usuario ja com nome
		ControleSessao.get().setNomeUsuario("teste");
		FormulaResposta.bufferFrases = null;
		FormulaResposta.respostaEnsino = null;
		ControleFace.verificarManter = false;
	}

	@After
	public void restauraOCacheRealEDepoisOTemp() throws Exception {
		FormulaResposta.bufferFrases = null;
		FormulaResposta.respostaEnsino = null;
		ControleFace.verificarManter = false;

		Caminhos.resetar();
		System.clearProperty("kaira.home");
		recarregaTudo();
		ControleSessao.reset();

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

	private void recarregaTudo() {
		ControleResposta.carregaCache();
		ControleResposta.invalidaCacheIdf();
		ControlePalavra.carregaCache();
		ControleSinonimo.carregaCache();
		ControleKaira.carregaFatos();
	}

	private String responde(String fala) {
		ControleFace.resposta = null;
		FormulaResposta.decifrar(fala);
		return ControleFace.resposta;
	}

	private String lerKaira() throws Exception {
		return Files.readString(
				temp.resolve("KAIRA").resolve("resources").resolve("kaira.json"));
	}

	private String lerIntencoes() throws Exception {
		return Files.readString(arquivoIntencoes);
	}

	/**
	 * ACEITACAO 1: senha no ensino DELA nao entra na memoria pessoal.
	 * Hoje: grava "abacaxi" no kaira.json e confirma com "Anotado".
	 */
	@Test
	public void ensinoDelaRecusaSenha() throws Exception {
		responde(FRASE_ENSINO);   // fallback arma P1
		assertNotNull("pre-condicao: o fallback arma o ensino",
				FormulaResposta.bufferFrases);

		String kairaAntes = lerKaira();
		String resposta = responde("sua senha do banco e abacaxi");

		assertEquals("nada grava no kaira.json", kairaAntes, lerKaira());
		assertFalse("o valor secreto nao aparece na memoria",
				lerKaira().contains("abacaxi"));
		assertNotNull(resposta);
		assertTrue("recusa curada (nao mente com 'Anotado')",
				resposta.contains("prefiro nao anotar"));
		assertEquals("o estado do ensino limpa", null, FormulaResposta.bufferFrases);
	}

	/**
	 * ACEITACAO 2: "chave pix" tambem e segredo - mesmo quando a extracao
	 * dos fatos joga o "pix" para fora do par (a fala inteira e checada).
	 */
	@Test
	public void ensinoDelaRecusaChavePix() throws Exception {
		responde(FRASE_ENSINO);
		assertNotNull(FormulaResposta.bufferFrases);

		String kairaAntes = lerKaira();
		String resposta = responde("sua chave pix e abacaxi");

		assertEquals("nada grava no kaira.json", kairaAntes, lerKaira());
		assertTrue("recusa curada", resposta.contains("prefiro nao anotar"));
	}

	/**
	 * ACEITACAO 3: cartao no ensino ORGANICO nao vira exemplo em
	 * intencoes.json. Hoje: absorve e confirma com "Anotado".
	 * (O buffer da cnh pontua 0.192 >= piso 0.10: absorcao direta no P1.)
	 */
	@Test
	public void organicoRecusaCartao() throws Exception {
		responde(FRASE_ENSINO);
		assertNotNull(FormulaResposta.bufferFrases);

		String intencoesAntes = lerIntencoes();
		String resposta = responde("meu cartao e 4111111111111111");

		assertEquals("nada grava no intencoes.json", intencoesAntes, lerIntencoes());
		assertNotNull(resposta);
		assertTrue("recusa curada", resposta.contains("prefiro nao anotar"));
		assertEquals("o estado do ensino limpa", null, FormulaResposta.bufferFrases);
	}

	/**
	 * ACEITACAO 4 (baseline): o ensino dela nao-secreto segue gravando com
	 * o ack de sempre (medido na #50: cor=lilas).
	 */
	@Test
	public void ensinoDelaCanonicoSegue() throws Exception {
		responde(FRASE_ENSINO);
		assertNotNull(FormulaResposta.bufferFrases);

		String resposta = responde("sua cor favorita de verdade e lilas");

		assertTrue("o fato dela grava", lerKaira().contains("lilas"));
		assertTrue("ack curado de absorcao", resposta.contains("Anotado"));
	}

	/**
	 * ACEITACAO 5 (baseline): o ensino organico nao-secreto segue
	 * absorvendo (medido na sonda: buffer cnh 0.192 >= piso).
	 */
	@Test
	public void organicoCanonicoSegue() throws Exception {
		responde(FRASE_ENSINO);
		assertNotNull(FormulaResposta.bufferFrases);

		String intencoesAntes = lerIntencoes();
		String resposta = responde("meu passarinho e o piupiu");

		assertFalse("o par nao-secreto e absorvido",
				intencoesAntes.equals(lerIntencoes()));
		assertTrue("ack curado de absorcao", resposta.contains("Anotado"));
	}
}
