package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.JCODE.domain.FatoSessao;
import com.JCODE.util.Caminhos;

/**
 * #58 - fatos da sessao v2 (o espelho do #56 para o USUARIO).
 *
 * Sonda de 27/10:
 *   "eu nao gosto de acucar"  -> GRAVAVA como gosto (gosta de=acucar) - bug
 *   "meu time nao e o gremio" -> nao grava (padrao de dimensao falta)
 *   "qual cor eu nao gosto?"  -> respondia a memoria DELA (cross-talk #56)
 *   "minha cor favorita e azul" -> cor favorita=azul (baseline)
 *
 * Regras: relacoes gosta/nao_gosta; o negativo NAO cai no positivo; a
 * pergunta em 1a pessoa e' do usuario, em 3a e' dela; nao-contradicao;
 * compatibilidade do getFatos() plano (gosta[0]).
 */
public class FatosSessaoV2Test {

	private Path temp;

	@Before
	public void montaBaseRealIsolada() throws Exception {
		temp = Files.createTempDirectory("kaira58");
		Path recursos = temp.resolve("KAIRA").resolve("resources");
		Files.createDirectories(recursos);

		for (String nome : List.of("intencoes.json", "stopwords.txt",
				"palavras.json", "grupos_sinonimos.json", "kaira.json"))
			Files.copy(Caminhos.recurso(nome), recursos.resolve(nome));

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

	private FatoSessao fato(String gosta, String... naoGosta) {
		FatoSessao f = new FatoSessao();
		if (gosta != null) {
			List<String> g = new ArrayList<>();
			g.add(gosta);
			f.setGosta(g);
		}
		for (String n : naoGosta)
			f.getNaoGosta().add(n);
		return f;
	}

	/** ACEITACAO 1: o positivo vira relacao (`gosta`); a visao plana segue. */
	@Test
	public void positivoViraRelacao() {
		responde("meu jogo favorito e minecraft");

		FatoSessao f = ControleSessao.get().getFatosV2().get("jogo favorito");
		assertNotNull("o fato existe como relacao", f);
		assertTrue("o valor esta em gosta", f.getGosta().contains("minecraft"));
		assertEquals("a visao plana (compat) devolve gosta[0]",
				"minecraft", ControleSessao.get().getFatos().get("jogo favorito"));
	}

	/**
	 * ACEITACAO 2 (o bug da sonda): "eu nao gosto de acucar" NAO pode virar
	 * gosto - vai para nao_gosta.
	 */
	@Test
	public void negativoNaoViraGosto() {
		responde("eu nao gosto de acucar");

		FatoSessao f = ControleSessao.get().getFatosV2().get("gosta de");
		assertNotNull(f);
		assertTrue("o acucar esta em nao_gosta", f.getNaoGosta().contains("acucar"));
		assertFalse("e NAO em gosta", f.getGosta().contains("acucar"));
	}

	/**
	 * ACEITACAO 3 (cross-talk): a pergunta em 1a pessoa e' do USUARIO.
	 * Hoje respondia a memoria DELA ("Uma cor que eu nao curto e cinza").
	 */
	@Test
	public void perguntaNegativaDoUsuario() {
		ControleSessao.get().getFatosV2().put("cor favorita", fato("azul", "cinza"));

		String resposta = responde("qual cor eu nao gosto?");

		assertNotNull(resposta);
		assertTrue("responde o dislike DO USUARIO", resposta.contains("cinza"));
		assertTrue("com a moldura do usuario", resposta.contains("nao e muito"));
	}

	/** ACEITACAO 4: em 3a pessoa a pergunta continua DELA (#56 preservado). */
	@Test
	public void perguntaNegativaDelaSegue() {
		String resposta = responde("qual cor voce nao gosta?");

		assertNotNull(resposta);
		assertTrue("a resposta e' uma das molduras dela",
				ControleKaira.moldurasNaoGosta("cor").stream()
						.anyMatch(m -> resposta.contains(m.replace("{v}", "cinza"))));
		assertTrue("o valor dela", resposta.contains("cinza"));
	}

	/** ACEITACAO 5: captura de dimensao negativa. */
	@Test
	public void capturaNegativaDeDimensao() {
		responde("minha cor favorita nao e azul");

		FatoSessao f = ControleSessao.get().getFatosV2().get("cor favorita");
		assertNotNull(f);
		assertTrue("o azul esta em nao_gosta", f.getNaoGosta().contains("azul"));
	}

	/** ACEITACAO 6: a captura alimenta a pergunta negativa. */
	@Test
	public void capturaNegativaAlimentaAPergunta() {
		responde("minha cor favorita nao e azul");

		String resposta = responde("qual cor eu nao gosto?");
		assertNotNull(resposta);
		assertTrue("responde o dislike capturado", resposta.contains("azul"));
	}

	/**
	 * ACEITACAO 7 (invariante): valor no lado oposto nao entra - contradicao
	 * recusada (o gosta existente vence).
	 */
	@Test
	public void naoContradicao() {
		ControleSessao.get().getFatosV2().put("cor favorita", fato("azul"));

		responde("minha cor favorita nao e azul");

		FatoSessao f = ControleSessao.get().getFatosV2().get("cor favorita");
		assertTrue("gosta intacta", f.getGosta().contains("azul"));
		assertFalse("nada entrou no nao_gosta", f.getNaoGosta().contains("azul"));
	}

	/** ACEITACAO 8: idem no generic likes ("gosto de"). */
	@Test
	public void negativoComGostoPrevioERecusado() {
		ControleSessao.get().getFatosV2().put("gosta de", fato("pizza"));

		responde("eu nao gosto de pizza");

		FatoSessao f = ControleSessao.get().getFatosV2().get("gosta de");
		assertTrue("gosta intacta", f.getGosta().contains("pizza"));
		assertFalse("nao_gosta limpo", f.getNaoGosta().contains("pizza"));
	}

	/** ACEITACAO 9: sem dislike conhecido, honesto (nunca inventa). */
	@Test
	public void semDislikeRespondeHonesto() {
		ControleSessao.get().getFatosV2().put("cor favorita", fato("azul"));

		String resposta = responde("qual cor eu nao gosto?");

		assertNotNull(resposta);
		assertTrue("honesto", resposta.contains("ainda nao me contou"));
	}

	/**
	 * ACEITACAO 10: migracao do sessao.json plano (v1) no load.
	 */
	@Test
	public void migracaoDoArquivoPlano() throws Exception {
		Path arquivo = temp.resolve("sessao.json");
		Files.writeString(arquivo,
				"{\"nomeUsuario\":\"teste\",\"fatos\":{\"time\":\"Gremio\",\"gosta de\":\"musica\"}}");
		ControleSessao.carregaSessao();

		FatoSessao time = ControleSessao.get().getFatosV2().get("time");
		assertNotNull("o fato plano virou relacao", time);
		assertTrue("o valor virou gosta", time.getGosta().contains("Gremio"));
		assertEquals("a visao plana segue", "Gremio",
				ControleSessao.get().getFatos().get("time"));
		assertNull("arquivo plano nao tem nao_gosta", time.getNaoGosta().isEmpty()
				? null : time.getNaoGosta().get(0));
	}
}
