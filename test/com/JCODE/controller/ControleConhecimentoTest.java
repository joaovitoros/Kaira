package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.JCODE.domain.Conhecimento;
import com.JCODE.domain.Intencao;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;

/**
 * Spec da issue #37 (C3 piloto - o dicionario de conhecimentos atras do
 * {topico}, §12.1 do plano: a ideia de dicionario do usuario).
 *
 * O fato: o P2 (#27/#28) ja faz "conta mais" cair em continuacao_topico e
 * usar {topico}, mas as respostas do bank so DEVOLVEM a palavra - nao tem
 * substancia atras. Agora conhecimentos.json (padrao intencoes.json, origem
 * com fonte+licenca por item) fornece resumo/texto por termo = nome de
 * intent, e o continuacao_topico fala a substancia quando tem entrada.
 *
 * Guardas do spec: contrato de volume/licenca (>= 15 entradas completas,
 * origem citando a fonte, todo termo casando com intent REAL da base - o
 * lookup e por topicoAtual = nome do intent), null seguro (arquivo ausente
 * ou topico desconhecido nunca derruba o fluxo) e o encaixe no
 * comandosResposta (fixture continuacao_topico devolve o resumo do topico
 * ancorado; sem entrada, segue o bank).
 */
public class ControleConhecimentoTest {

	@Before
	public void carregaBases() {
		ControleConhecimento.carrega();
		ControleResposta.carregaCache();
	}

	/** Contrato das entradas: completas, licenciadas e mapeadas em intents reais. */
	@Test
	public void pilotoTemEntradasCompletasEMapeadas() {
		assertTrue("menos de 15 entradas no dicionario piloto", ControleConhecimento.todos().size() >= 15);

		for (Conhecimento c : ControleConhecimento.todos()) {
			assertNotNull("entrada sem termo", c.getTermo());
			assertTrue("resumo vazio em " + c.getTermo(), c.getResumo() != null && !c.getResumo().isBlank());
			assertTrue("texto vazio em " + c.getTermo(), c.getTexto() != null && !c.getTexto().isBlank());
			assertTrue("origem sem fonte em " + c.getTermo(),
					c.getOrigem() != null && c.getOrigem().contains("Wikipédia PT"));
			assertTrue("origem sem licenca em " + c.getTermo(), c.getOrigem().contains("CC BY-SA"));
			assertTrue("termo nao e intent real da base: " + c.getTermo() + " (lookup quebra)",
					ControleResposta.getIntencoes().stream()
							.anyMatch(i -> c.getTermo().equals(i.getIntento())));
		}
	}

	/** Topico conhecido devolve resumo e texto. */
	@Test
	public void topicoConhecidoDevolveResumoETexto() {
		String resumo = ControleConhecimento.resumo("trabalho_usuario");
		assertNotNull("trabalho_usuario deveria ter entrada no piloto", resumo);
		assertTrue("resumo curto demais (e pra falar em voz alta)", resumo.length() > 30);
		assertNotNull("texto (futuro explica melhor) ausente", ControleConhecimento.texto("trabalho_usuario"));
	}

	/** Null seguro: desconhecido/branco/nulo nunca derruba o fluxo. */
	@Test
	public void topicoDesconhecidoDevolveNull() {
		assertNull("intent inexistente nao pode ter entrada", ControleConhecimento.resumo("intencao_inexistente"));
		assertNull("topico nulo devolve null", ControleConhecimento.resumo(null));
		assertNull("topico em branco devolve null", ControleConhecimento.resumo("  "));
	}

	/** O encaixe: continuacao_topico fala o resumo do topico ancorado. */
	@Test
	public void continuacaoTopicoFalaASubstanciaDoTopico() {
		String topicoAntes = ControleSessao.get().getTopicoAtual();
		try {
			ControleSessao.get().setTopicoAtual("trabalho_usuario");
			Intencao ct = new Intencao("continuacao_topico", new ArrayList<>(), null, new ArrayList<>(), null, null);

			String falada = ControleResposta.comandosResposta(ct);

			assertEquals("encaixe nao devolveu o resumo do dicionario",
					ControleConhecimento.resumo("trabalho_usuario"), falada);
		} finally {
			ControleSessao.get().setTopicoAtual(topicoAntes);
		}
	}

	/** Sem entrada pro topico, o encaixe desiste e deixa o bank rolar. */
	@Test
	public void topicoSemEntradaCaiNoBank() {
		String topicoAntes = ControleSessao.get().getTopicoAtual();
		try {
			ControleSessao.get().setTopicoAtual("intencao_inexistente");
			Intencao ct = new Intencao("continuacao_topico", new ArrayList<>(), null, new ArrayList<>(), null, null);

			// pool vazio (fixture) - o bank real tem as frases; aqui o ponto e
			// que o encaixe nao devolve resumo inexistente nem estoura
			String falada = ControleResposta.comandosResposta(ct);
			assertNull("sem entrada, o encaixe nao pode devolver nada proprio", falada);
		} finally {
			ControleSessao.get().setTopicoAtual(topicoAntes);
		}
	}
}
