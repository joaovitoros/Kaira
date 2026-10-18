package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import com.JCODE.domain.Intencao;
import com.JCODE.domain.Intencoes;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

/**
 * E2E do modo ensino (!) - o loop COMPLETO fallback -> armo -> absorvo ->
 * a frase volta a casar. E a lacuna que o MAPA marcava como ❌
 * ("fallback->resposta nao e testado ponta a ponta"):
 *
 *   - o EnsinoTurbinadoTest cobre o NUCLEO (ensinaEm com base isolada),
 *     mas comeca JA no ato de ensinar;
 *   - o ControleRespostaFallbackTest so valida o POOL DE TEXTO;
 *   - o SimuladorDeConversa registra "nao sabiu" mas nunca chama
 *     ativarAprendizado/bufferFrases/ensinaEm.
 *
 * Ninguem prova que o loop FECHA.
 */
public class EnsinoFluxoTest {

	private static final String FRASE_DESCONHECIDA = "blorf quantum zebra";

	private String bufferAnterior;

	@Before
	public void guardaBuffer() {
		bufferAnterior = FormulaResposta.bufferFrases;
		ControleResposta.carregaCache();
		ControlePalavra.carregaCache();
		ControleSinonimo.carregaCache();
		ControleSessao.reset();
	}

	/** O buffer e estatico no FormulaResposta: sem restaurar, vaza pros
	 *  outros testes da suite (mesmo cuidado do topico no DesempateContexto). */
	@After
	public void restauraBuffer() {
		FormulaResposta.bufferFrases = bufferAnterior;
	}

	/**
	 * TURNO 1 - a frase nao tem match nenhum, entao a KAIRA cai no fallback.
	 * Nesse caminho ela tem que (a) pedir ensino e (b) GUARDAR a frase no
	 * buffer, que e a ponte pro turno seguinte.
	 *
	 * Medido antes do fix: NPE em ControleFace.btnSinalizar (o
	 * ativarAprendizado roda ANTES da captura) e bufferFrases ficava null -
	 * a frase do usuario se perdia. A UI tem que ser efeito colateral, nao
	 * pre-condicao do dado.
	 */
	@Test
	public void fallbackArmaOModoEnsinoSemDependerDaUI() {
		FormulaResposta.bufferFrases = null;

		try {
			FormulaResposta.enviarResposta(null, FRASE_DESCONHECIDA);
		} catch (NullPointerException npe) {
			fail("armamento derrubou na UI antes de gravar o buffer: " + npe.getMessage());
		}

		assertEquals("a frase que falhou fica no buffer pro turno seguinte",
				FRASE_DESCONHECIDA, FormulaResposta.bufferFrases);
	}

	/**
	 * O LOOP INTEIRO: a MESMA string atravessa os tres turnos.
	 *
	 *   turno 1 - nao casa (ela nao sabe) e vai pro buffer;
	 *   turno 2 - o usuario responde e o ensino absorve via o que estava
	 *             no buffer (nao via string literal nova);
	 *   turno 3 - a MESMA frase agora casa.
	 *
	 * Base ISOLADA (mesmo cuidado do EnsinoTurbinadoTest): nunca mexe no
	 * intencoesCache estatico que os outros testes leem.
	 */
	@Test
	public void loopDoEnsinoFechaComAMesmaString() {
		Intencoes raiz = baseVazia();

		// turno 1: ela nao sabe
		assertNull("frase desconhecida nao casa antes de ensinar",
				ControleResposta.buscaResposta(raiz, FRASE_DESCONHECIDA));

		// o armamento entrega a frase pelo BUFFER - e o buffer so existe se
		// o fallback do turno 1 tiver gravado
		FormulaResposta.bufferFrases = null;
		FormulaResposta.enviarResposta(null, FRASE_DESCONHECIDA);
		String fraseArmada = FormulaResposta.bufferFrases;
		assertNotNull("o armamento tem que deixar a frase no buffer", fraseArmada);

		// turno 2: absorve o que estava no buffer
		ControleResposta.ensinaEm(raiz, fraseArmada, "uma resposta ensinada pro blorf");

		// turno 3: a MESMA string casa
		Intencao depois = ControleResposta.buscaResposta(raiz, FRASE_DESCONHECIDA);
		assertNotNull("depois de ensinada, a frase tem que casar", depois);
		assertEquals("e casa no intento certo", "auto_aprendizado", depois.getIntento());
	}

	/** Base com so a caixa de entrada: nenhum tema, nenhuma resposta pronta. */
	private Intencoes baseVazia() {
		ArrayList<Intencao> lista = new ArrayList<>();
		lista.add(new Intencao("auto_aprendizado", new ArrayList<>(), null,
				new ArrayList<>(), null, null));
		return new Intencoes(lista);
	}
}
