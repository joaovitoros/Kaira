package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Test;

/**
 * Testa a consulta pura fraseExata (Tarefa #3, adaptado na #7).
 *
 * fraseExata devolve o EXEMPLO com que a entrada NORMALIZADA casou, ou null.
 * No modelo antigo ela casava contra Resposta.frase (respostas.json); desde a
 * Tarefa #7 ela casa contra Exemplo.texto (intencoes.json) - mesmo papel,
 * fonte nova.
 *
 * Ela existe porque lerResposta/decifrar NAO servem pra avaliar: chamam
 * comandosResposta (System.exit no desligamento, Math.random, ControleKaira)
 * e ControleFace (UI). fraseExata e so o filtro, nada mais.
 *
 * Roda com CWD na raiz do projeto (carrega o KAIRA/resources/intencoes.json).
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
		// parafrase: nao esta como exemplo, entao null (cai na cobertura da Fase 1)
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
