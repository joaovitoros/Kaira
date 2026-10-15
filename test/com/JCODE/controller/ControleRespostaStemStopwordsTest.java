package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.JCODE.domain.Exemplo;
import com.JCODE.domain.Intencao;
import com.JCODE.domain.Intencoes;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

/**
 * Testa Secao 5.3 (stemming) + 5.4 (stopwords) no classificador
 * (Tarefa #12, issue #12), um caso por secao para ISOLAR cada metade:
 *
 *   - STEM: "gostando de musica" x exemplo "gostei de musica".
 *     Sem a 5.3 os radicais nao casam (gostando -> "gost" so com a regra
 *     -ando, gostei -> "gost" so com a regra -ei) e o score fica 1/2 = 0.5
 *     < LIMIAR -> null. Com as duas regras, 2/2 = 1.0.
 *
 *   - STOPWORD: "voce de gosta musica" x exemplo "gosta musica".
 *     Sem a 5.4 a entrada tem 4 palavras (voce e de contam) e o score fica
 *     2/4 = 0.5 < LIMIAR -> null. Com as duas stopwords fora da conta dos
 *     DOIS LADOS, 2/2 = 1.0.
 *
 *   - Entrada que vira SÓ stopword ("de que da") devolve null: o filtro
 *     nao pode virar chute.
 *
 * Fixture injetada em intencoesCache (package-private, mesmo padrao do
 * MatcherUnificadoTest); os caches REAIS de grupos e de stopwords carregam
 * no @Before. As assercoes sao por INTENCAO, nao por exemplo: mesmo que o
 * desempate de exemplo mude, a intencao tem que ser a mesma.
 *
 * Roda com CWD na raiz do projeto (stopwords.txt e grupos_sinonimos.json
 * reais). Nao chama comandosResposta (Math.random/System.exit) - puro.
 */
public class ControleRespostaStemStopwordsTest {

	/**
	 * Base fake de intents. Dois exemplos no intent "musica" (um por teste)
	 * e um distrator em "conversa" - o distrator nao pode empatar nenhum
	 * dos dois casos: "conversa" nao sobrepoem com musica.
	 */
	private Intencoes baseStemStopwords() {
		Intencoes raiz = new Intencoes();
		raiz.setIntencoes(new ArrayList<>());

		Intencao musica = new Intencao();
		musica.setIntento("musica");
		musica.setExemplos(new ArrayList<>());
		musica.getExemplos().add(new Exemplo("gostei de musica", "autor"));
		musica.getExemplos().add(new Exemplo("gosta musica", "autor"));
		musica.setRespostas(new ArrayList<>());
		raiz.getIntencoes().add(musica);

		Intencao conversa = new Intencao();
		conversa.setIntento("conversa");
		conversa.setExemplos(new ArrayList<>());
		conversa.getExemplos().add(new Exemplo("voce conversa", "autor"));
		conversa.setRespostas(new ArrayList<>());
		raiz.getIntencoes().add(conversa);

		return raiz;
	}

	@Before
	public void carregaCachesEFixture() {
		// carregaCache monta o conjunto de stopwords (Secao 5.4);
		// o indice de grupos garante que a conta nao dependa de estado
		// deixado por outro teste.
		ControleResposta.carregaCache();
		ControleSinonimo.carregaCache();
		ControleResposta.intencoesCache = baseStemStopwords();
	}

	private String intentoDe(String entrada) {
		var resultado = ControleResposta.melhorIntencao(entrada);
		assertNotNull("entrada '" + entrada + "' devia achar uma intencao", resultado);
		return resultado.values().iterator().next().getIntento();
	}

	/**
	 * Só a 5.3 resolve: sem stem, "gostando" nao casa com "gostei" e o
	 * score fica 1/2 = 0.5 (só "musica"). Com stem, os dois viram "gost"
	 * e fecha 2/2 = 1.0.
	 */
	@Test
	public void stemCruzaFlexaoDoExemplo() {
		assertEquals("musica", intentoDe("gostando de musica"));
	}

	/**
	 * Só a 5.4 resolve: sem tirar "voce" e "de" da conta, a entrada tem
	 * 4 palavras e o score fica 2/4 = 0.5. Tirando, 2/2 = 1.0.
	 */
	@Test
	public void stopwordEncolheODenominador() {
		assertEquals("musica", intentoDe("voce de gosta musica"));
	}

	/** Entrada que vira só stopword devolve null (guarda de conjunto vazio). */
	@Test
	public void entradaSoStopwordDevolveNull() {
		assertNull(ControleResposta.melhorIntencao("de que da"));
	}
}
