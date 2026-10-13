package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.Test;

import com.JCODE.domain.Resposta;

/**
 * Testa o lookup com classificador buscaResposta (Tarefa #6).
 *
 * buscaResposta devolve o objeto Resposta com que a entrada NORMALIZADA casou
 * (exato -> classificador), ou null. E pura: NAO chama comandosResposta, entao
 * nao ha System.exit/Math.random - da pra medir headless. Existe porque o chat
 * (lerResposta) precisa do Resposta (nao so da frase) pra gerar a resposta, e
 * agora ele tem que achar com o classificador, nao so com match exato.
 *
 * Roda com CWD na raiz do projeto (carrega KAIRA/resources/respostas.json).
 */
public class ControleRespostaBuscaRespostaTest {

	@Before
	public void carregaCache() {
		ControleResposta.carregaCache();
	}

	@Test
	public void exatoDevolveOResposta() {
		Resposta r = ControleResposta.buscaResposta("oi");
		assertNotNull("'oi' existe na base, tinha que achar", r);
		assertEquals("oi", r.getFrase());
	}

	@Test
	public void classificadorFechaBomDia() {
		// "bom dia" nao e frase exata; o classificador acha "bom dia como voce esta"
		Resposta r = ControleResposta.buscaResposta("bom dia");
		assertNotNull("'bom dia' tinha que resolver via classificador", r);
		assertEquals("bom dia como voce esta", r.getFrase());
	}

	@Test
	public void classificadorFechaComoVcEsta() {
		// desempata "como esta seu humor hoje" (mesma cobertura) pelo menor excedente
		Resposta r = ControleResposta.buscaResposta("como vc esta");
		assertNotNull("'como vc esta' tinha que resolver via classificador", r);
		assertEquals("como voce esta", r.getFrase());
	}

	@Test
	public void fraseSemSobreposicaoDevolveNull() {
		// "e ai beleza" nao tem nenhuma palavra em comum com "oi" -> nada acima do limiar
		assertNull(ControleResposta.buscaResposta("e ai beleza"));
	}

	@Test
	public void lerRespostaSemMatchDevolveNullSemExplodir() {
		// buscaResposta devolve null quando nada casa; lerResposta tem que repassar
		// null, NAO NPE. Caminho seguro: como nao achou, nao chega no comandosResposta.
		assertNull(ControleResposta.lerResposta("e ai beleza"));
	}
}
