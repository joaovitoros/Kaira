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
import java.util.Map;

/**
 * Testa o classificador por GRUPOS (Tarefa #9, issue #9, Secao 5.5): a
 * intersecao da cobertura do melhorIntencao passa a casar palavra-a-palavra
 * pelo grupo de sinonimos (mesmaPalavra), dos dois lados - entrada e exemplo.
 *
 * Igual ao split da task #8 (chamadaFrase/ensinaEm): o nucleo puro e
 * melhorIntencao(Intencoes, String), testavel sem disco. O lado do cache
 * (melhorIntencao(String), lazy-load) delega nele.
 *
 * A conta que este teste amarra: a entrada "tu gosta de falar" contra o
 * exemplo "voce gosta de dizer" faz 4/4 = 1.0 COM grupos (tu~voce, falar~dizer)
 * e cruza o LIMIAR 0.6. SEM grupos fica 2/4 = 0.5 - abaixo do LIMIAR, devia
 * dar null. E esse o ganho real do Secao 5.5: sinonimos deixam de ser roleta
 * e viram cobertura.
 *
 * O distrator ("gosta de mario") existe pra provar que o casamento nao vaza:
 * ele cobre 2/4 = 0.5 da entrada e nao pode levar.
 */
public class ControleRespostaSinonimoGruposTest {

	private Intencoes baseDeTeste() {
		Intencoes raiz = new Intencoes();
		raiz.setIntencoes(new ArrayList<>());

		Intencao teste = new Intencao();
		teste.setIntento("teste_grupo");
		teste.setExemplos(new ArrayList<>());
		teste.getExemplos().add(new Exemplo("voce gosta de dizer", "autor"));
		teste.setRespostas(new ArrayList<>());
		raiz.getIntencoes().add(teste);

		Intencao distrator = new Intencao();
		distrator.setIntento("teste_outro");
		distrator.setExemplos(new ArrayList<>());
		distrator.getExemplos().add(new Exemplo("gosta de mario", "autor"));
		distrator.setRespostas(new ArrayList<>());
		raiz.getIntencoes().add(distrator);

		return raiz;
	}

	/**
	 * O melhorIntencao(Intencoes, String) e PURO de proposito (split da task #8):
	 * nao toca disco, entao nao monta o indice de sinonimos. Quem monta e o setup
	 * do teste, lendo o grupos_sinonimos.json real via ControleSinonimo.carregaCache().
	 * Sem isto o indice fica null, mesmaPalavra vira igualdade pura e o tu~voce nao casa.
	 */
	@Before
	public void montaIndiceDeSinonimos() {
		ControleSinonimo.carregaCache();
	}

	/**
	 * O ganho do Secao 5.5: entrada com sinonimos cruza o LIMIAR que sem
	 * grupos nao cruzava (sem grupo: 2/4 = 0.5 < 0.6; com grupos: 4/4 = 1.0).
	 */
	@Test
	public void sinonimoCruzaLimiarQueSemGrupoNaoCruzava() {
		Map<String, Intencao> resultado = ControleResposta.melhorIntencao(baseDeTeste(), "tu gosta de falar");

		assertNotNull("tu gosta de falar devia achar o intent via grupos (tu~voce, falar~dizer)", resultado);
		assertEquals("devia ser o intent do exemplo com sinonimos", "teste_grupo",
				resultado.values().iterator().next().getIntento());
	}

	/**
	 * O grupo nao afrouxa o LIMIAR: entrada sem par suficiente (1/3 = 0.33)
	 * continua sem intent.
	 */
	@Test
	public void semParSuficienteContinuaSemIntent() {
		Map<String, Intencao> resultado = ControleResposta.melhorIntencao(baseDeTeste(), "zzqqxx qqzz gosta");

		assertNull("cobertura 1/3 devia ficar abaixo do LIMIAR mesmo com grupos", resultado);
	}

	/**
	 * fraseExata NAO entra grupo (decisao da issue #9): o match exato continua
	 * string == string. "tu esta ai" nao casa exato com o exemplo real "voce
	 * esta ai" do intencoes.json - quem quiser casar tem que passar pela
	 * cobertura.
	 *
	 * O primeiro assert e a validade do fixture: o exemplo "voce esta ai"
	 * existe mesmo no arquivo curado (se um dia sumir, este teste grita).
	 */
	@Test
	public void fraseExataNaoUsaGrupo() {
		assertEquals("voce esta ai", ControleResposta.fraseExata("voce esta ai"));
		assertNull("fraseExata devia continuar estrito: tu != voce no match exato",
				ControleResposta.fraseExata("tu esta ai"));
	}
}
