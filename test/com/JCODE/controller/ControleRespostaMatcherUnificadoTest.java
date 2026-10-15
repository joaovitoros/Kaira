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
 * Testa a UNIFICACAO do classificador (Tarefa #10, issue #10, Secao 5.2): o
 * melhorIntencao passa a ser o UNICO classificador de cobertura, e o matcher
 * real do app (buscaResposta) passa a casar por GRUPOS em vez de contains puro.
 *
 * Motivo: existiam DOIS classificadores quase identicos no ControleResposta -
 * o melhorFrasesPorSimilaridade (contains puro, sem sinonimo, usado pelo
 * buscaResposta e pelo resolver que o Avaliador mede) e o melhorIntencao (com
 * grupos, da Tarefa #9, mas ORFAO: nenhum caminho do app passava por ele). O
 * ganho da §5.5 so valia no isolamento.
 *
 * A conta que este teste amarra (achada no grupos_sinonimos.json REAL):
 *   entrada  "voce gosta como falar"
 *   exemplo  "voce gosta de conversar"
 * SEM grupos (contains puro): casa so "voce","gosta" = 2/4 = 0.500 < LIMIAR 0.6
 *                             -> buscaResposta devolve null (falha)
 * COM grupos: como~de e falar~conversar fecham 4/4 = 1.000 >= 0.6
 *             -> buscaResposta devolve a Intencao
 * E e exatamente essa a diferenciacao que a unificacao tem que trazer pro
 * caminho do app. Se alguem religar o matcher sem sinonimo, "como falar" deixa
 * de casar e o teste_gruposCruzaNoBuscaRespostaDoApp grita.
 *
 * buscaResposta e o ponto de teste certo: e publico, puro, nao toca UI nem
 * comandosResposta (que tem Math.random e System.exit). O lerResposta por baixo
 * chamaria comandosResposta - por isso testamos o buscaResposta.
 *
 * Roda com CWD na raiz do projeto (carrega KAIRA/resources/grupos_sinonimos.json
 * via ControleSinonimo - o indice de grupos e REAL; so a base de intents e fake).
 */
public class ControleRespostaMatcherUnificadoTest {

	/**
	 * Base fake de intents. Um so intent, com o exemplo onde os GRUPOS mudam o
	 * resultado: "como falar" so casa com "de conversar" pelo grupos_sinonimos.json.
	 *
	 * intencoesCache e static package-private (mesmo package com.JCODE.controller),
	 * entao o @Before injeta esta base direto - sem disco, sem intencoes.json real.
	 */
	private Intencoes baseUnificada() {
		Intencoes raiz = new Intencoes();
		raiz.setIntencoes(new ArrayList<>());

		Intencao convite = new Intencao();
		convite.setIntento("convite_conversa");
		convite.setExemplos(new ArrayList<>());
		convite.getExemplos().add(new Exemplo("voce gosta de conversar", "autor"));
		convite.setRespostas(new ArrayList<>());
		raiz.getIntencoes().add(convite);

		return raiz;
	}

	/**
	 * igual ao da Tarefa #9: o melhorIntencao e puro e nao monta o indice de
	 * sinonimos. Quem monta e o setup, lendo o grupos_sinonimos.json REAL.
	 * Sem isto o indice fica null, mesmaPalavra vira igualdade pura e
	 * como~de / falar~conversar nao casam.
	 */
	@Before
	public void montaIndiceDeSinonimos() {
		ControleSinonimo.carregaCache();
		ControleResposta.intencoesCache = baseUnificada();
	}

	/**
	 * O ganho da §5.2 no CAMINHO DO APP: "voce gosta como falar" nao casa com o
	 * exemplo "voce gosta de conversar" por igualdade (como!=de,
	 * falar!=conversar) - cobertura so 2/4 = 0.5, abaixo do LIMIAR. Mas casa
	 * pelos GRUPOS (como~de, falar~conversar), fechando 4/4 = 1.0.
	 *
	 * Se o buscaResposta ainda usar contains puro (sem grupo), ele devolve null
	 * e este teste falha - e e exatamente isso que ele protege.
	 */
	@Test
	public void grupoCruzaNoBuscaRespostaDoApp() {
		Intencao achada = ControleResposta.buscaResposta("voce gosta como falar");

		assertNotNull("voce gosta como falar devia casar via grupo (como~de, falar~conversar) no buscaResposta", achada);
		assertEquals("devia cair no intent de conversa", "convite_conversa", achada.getIntento());
	}

	/**
	 * O match exato continua funcionando depois da unificacao: entrada igual ao
	 * exemplo casa na hora, sem passar pela cobertura.
	 */
	@Test
	public void matchExatoContinuaFuncionando() {
		Intencao achada = ControleResposta.buscaResposta("voce gosta de conversar");

		assertNotNull("match exato tem que continuar casando", achada);
		assertEquals("convite_conversa", achada.getIntento());
	}

	/**
	 * Entrada sem relacao com nenhum exemplo continua caindo no fallback honesto
	 * (null) - a unificacao nao pode virar chute. "zxqw plmok" nao tem sinonimo
	 * em nenhum grupo.
	 */
	@Test
	public void entradaSemNadaContinuaNull() {
		assertNull(ControleResposta.buscaResposta("zxqw plmok xyzw"));
	}

	/**
	 * melhorIntencao devolve a MESMA Intencao que o buscaResposta - os dois tem
	 * que ser o mesmo classificador depois da unificacao (o buscaResposta passa
	 * a delegar no melhorIntencao em vez do melhorFrasesPorSimilaridade).
	 */
	@Test
	public void buscaRespostaEOMesmoClassificadorDoMelhorIntencao() {
		Intencao viaBusca = ControleResposta.buscaResposta("voce gosta como falar");
		Map<String, Intencao> viaMelhor = ControleResposta.melhorIntencao("voce gosta como falar");

		assertNotNull(viaBusca);
		assertNotNull(viaMelhor);
		assertEquals("buscaResposta e melhorIntencao devem apontar pra mesma intencao",
				viaMelhor.values().iterator().next().getIntento(), viaBusca.getIntento());
	}
}
