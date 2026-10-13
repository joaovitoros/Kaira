package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Test;

/**
 * Testa a consulta pura fraseExata (Tarefa #3).
 *
 * fraseExata devolve a frase-base com que a entrada NORMALIZADA casou, ou
 * null. Ela existe porque lerResposta/decifrar NAO servem pra avaliar:
 * chamam comandosResposta (System.exit no desligamento, Math.random,
 * ControleKaira) e ControleFace (UI). fraseExata e so o filtro, nada mais.
 *
 * Roda com CWD na raiz do projeto (carrega o KAIRA/resources/respostas.json).
 */
public class ControleRespostaFraseExataTest {

	@Before
	public void carregaCache() {
		ControleResposta.carregaCache();
	}

	@Test
	public void casaChaveSimples() {
		assertEquals("oi", ControleResposta.fraseExata("oi"));
	}

	@Test
	public void casaChaveQueEraAcentuadaNaBase() {
		// chave crua no json: "voce é legal" -> carregaCache normaliza p/ "voce e legal"
		assertEquals("voce e legal", ControleResposta.fraseExata("voce e legal"));
	}

	@Test
	public void entradaNaoRegistradaDevolveNull() {
		// parafrase: nao esta na base, entao null (gap p/ o classificador da Fase 1)
		assertNull(ControleResposta.fraseExata("e ai beleza"));
	}

	@Test
	public void naoExplodeComFraseDeDesligamento() {
		// em lerResposta isso chamaria comandosResposta -> System.exit(0).
		// fraseExata e pura: devolve a frase e pronto.
		assertEquals("desligar", ControleResposta.fraseExata("desligar"));
	}

	@Test
	public void naoConfundePrefixoComFraseInteira() {
		// "oi" existe, mas "oi seja la o que for" nao pode casar com "oi"
		assertNull(ControleResposta.fraseExata("oi seja la o que for"));
	}
}
