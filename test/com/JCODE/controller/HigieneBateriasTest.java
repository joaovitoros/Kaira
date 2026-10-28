package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
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
 * #63 - leva de higiene das baterias: nome, genero do miss e textao no ensino.
 *
 * Sonda de 28/10 (base real isolada):
 *   janela de nome + "tudo"      -> nome="tudo" ("Prazer, tudo!")
 *   + "esquece" / "bom dia"      -> nome="bom dia"!
 *   "sou enfermeira"             -> nome="enfermeira"
 *   "qual minha serie favorita?" -> "voce ainda nao me contou SEU serie favorita"
 *   P1 + textao de 80 palavras   -> "Anotado" (vira resposta gravada)
 * Banco: max 11 palavras por exemplo / max 46 por resposta (2.475 exemplos).
 */
public class HigieneBateriasTest {

	private static final String FRASE_ENSINO = "como faco pra cadastrar a cnh no app do banco";

	private Path temp;
	private Path arquivoIntencoes;

	@Before
	public void montaBaseRealIsolada() throws Exception {
		temp = Files.createTempDirectory("kaira63");
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
		// sem nome: os testes da janela precisam dela aberta; os demais fecham.
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

	private String lerIntencoes() throws Exception {
		return Files.readString(arquivoIntencoes);
	}

	/** Abre a janela do nome (a primeira resposta pede "Como posso te chamar?"). */
	private void abreJanelaDeNome() {
		String resposta = responde("oi kaira");
		assertTrue("a janela de nome abriu", resposta.contains("chamar"));
	}

	private void fechaJanelaDeNome() {
		ControleSessao.get().setNomeUsuario("teste");
	}

	private void armaP1() {
		responde(FRASE_ENSINO);
		assertNotNull(FormulaResposta.bufferFrases);
	}

	// ================= papa: nome =================

	@Test
	public void palavraTudoNaoViraNome() {
		abreJanelaDeNome();
		responde("tudo");

		assertNull("'tudo' nao pode ser nome", ControleSessao.get().getNomeUsuario());
	}

	@Test
	public void palavraEsqueceNaoViraNome() {
		abreJanelaDeNome();
		responde("esquece");

		assertNull("'esquece' nao pode ser nome", ControleSessao.get().getNomeUsuario());
	}

	@Test
	public void saudacaoNaoViraNome() {
		abreJanelaDeNome();
		responde("bom dia");

		assertNull("'bom dia' nao pode ser nome", ControleSessao.get().getNomeUsuario());
	}

	@Test
	public void profissaoNaoViraNome() {
		responde("sou enfermeira");

		assertNull("profissao nao e' nome", ControleSessao.get().getNomeUsuario());
	}

	@Test
	public void nomeLegitimoSegue() {
		abreJanelaDeNome();
		responde("Ana");

		assertEquals("Ana", ControleSessao.get().getNomeUsuario());
	}

	// ================= genero do miss =================

	@Test
	public void generoDoMissSegueOPossessivo() {
		fechaJanelaDeNome();

		assertTrue("minha -> sua",
				responde("qual minha serie favorita?").toLowerCase()
						.contains("sua serie favorita"));
		assertTrue("meu -> seu",
				responde("qual meu signo?").toLowerCase().contains("seu signo"));
		assertTrue("minhas -> suas",
				responde("quais minhas cores favoritas?").toLowerCase()
						.contains("suas cores favoritas"));
	}

	// ================= textao no ensino =================

	@Test
	public void textaoNaoViraExemplo() throws Exception {
		fechaJanelaDeNome();
		armaP1();
		String intencoesAntes = lerIntencoes();
		String textao = "eu acredito que a vida e uma jornada cheia de desafios e que a "
				+ "gente aprende muito com cada experiencia vivida desde que estejamos "
				+ "abertos a ouvir as pessoas e a refletir sobre tudo que acontece no "
				+ "nosso dia a dia e tambem nas escolhas que a gente faz quando ninguem "
				+ "esta olhando e o tempo passa";

		String resposta = responde(textao);

		assertEquals("o textao nao pode entrar no banco",
				intencoesAntes, lerIntencoes());
		assertNotNull(resposta);
		assertTrue("recusa curada do textao",
				resposta.toLowerCase().contains("textao"));
	}

	@Test
	public void ensinoCurtoSegue() throws Exception {
		fechaJanelaDeNome();
		armaP1();
		String intencoesAntes = lerIntencoes();

		String resposta = responde("eu jogo golfe aos sabados");

		assertFalse("o ensino curto e' absorvido",
				intencoesAntes.equals(lerIntencoes()));
		assertTrue("ack de absorcao", resposta.contains("Anotado"));
	}

	@Test
	public void ensinoMedioSegue() throws Exception {
		fechaJanelaDeNome();
		armaP1();
		String intencoesAntes = lerIntencoes();
		String medio = "eu costumo acordar cedo todo dia e tomar um cafe "
				+ "antes de sair para o trabalho porque gosto da manha calma";

		responde(medio);

		assertFalse("o ensino medio (25 palavras) e' absorvido",
				intencoesAntes.equals(lerIntencoes()));
	}
}
