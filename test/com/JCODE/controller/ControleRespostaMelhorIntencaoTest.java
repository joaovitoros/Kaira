package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.JCODE.domain.Intencao;

import org.junit.Before;
import org.junit.Test;

/**
 * Testa o classificador de intents melhorIntencao (Tarefa #7, §5.1).
 *
 * Substitui o BuscaRespostaTest da era respostas.json. melhorIntencao devolve
 * a Intencao vencedora (score = maior cobertura sobre os exemplos da
 * intenção, >= LIMIAR) ou null. É pura: não chama comandosResposta (nem
 * System.exit nem Math.random), então dá pra medir headless.
 *
 * Dois casos daqui são os que FECHAM OS GAPS do eval:
 *   - "e ai beleza"  -> cumprimentar    (antes: null; sem sobreposição c/ "oi")
 *   - "qual seu jogo favorito" -> jogo_favorito (antes: dependia do desempate
 *     entre "do Mario" e "do Sonic"; a intenção resolve sem depender de ordem)
 *
 * Roda com CWD na raiz do projeto (carrega o KAIRA/resources/intencoes.json).
 */
public class ControleRespostaMelhorIntencaoTest {

	@Before
	public void carregaCache() {
		ControleResposta.carregaCache();
	}

	private String intentoDe(String entrada) {
		Intencao i = ControleResposta.melhorIntencao(entrada);
		assertNotNull("entrada '" + entrada + "' devia achar uma intencao", i);
		return i.getIntento();
	}

	/** Caso exato simples classifica na intenção certa. */
	@Test
	public void cumprimentoSimples() {
		assertEquals("cumprimentar", intentoDe("oi"));
	}

	/** Frase não-exata que a cobertura fecha. */
	@Test
	public void perguntaDeGostoNaoExata() {
		// "qual a sua cor preferida" tambem existe como exemplo; garante que a
		// pergunta de preferencia cai em gostos_ia e nao num intento generico.
		assertEquals("gostos_ia", intentoDe("qual a sua cor preferida"));
	}

	/**
	 * GAP 1 (fecha aqui): "e ai beleza" tem zero sobreposição lexica com "oi".
	 * Só a camada de intents resolve - o exemplo "e ai tudo beleza" do intento
	 * cumprimentar dá cobertura 3/3 e leva o caso.
	 */
	@Test
	public void eAiBelezaFechaOGap() {
		assertEquals("cumprimentar", intentoDe("e ai beleza"));
	}

	/**
	 * GAP 2 (fecha aqui): "qual seu jogo favorito" empatava em cobertura entre
	 * "do Mario" e "do Sonic"; pela intenção o caso resolve sem depender do
	 * desempate por ordem de arquivo.
	 */
	@Test
	public void jogoFavoritoFechaOGap() {
		assertEquals("jogo_favorito", intentoDe("qual seu jogo favorito"));
	}

	/** Entrada sem relação com nada cai no fallback honesto (null), não num chute. */
	@Test
	public void entradaSemNadaDevolveNull() {
		assertNull(ControleResposta.melhorIntencao("zxqw plmok"));
	}

	/** lerResposta tem que repassar o null sem estourar NPE no caminho seguro. */
	@Test
	public void lerRespostaSemMatchDevolveNullSemExplodir() {
		assertNull(ControleResposta.lerResposta("zxqw plmok"));
	}
}
