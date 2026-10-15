package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.JCODE.domain.Exemplo;
import com.JCODE.domain.Intencao;
import com.JCODE.domain.Intencoes;
import com.JCODE.domain.MatchIntencao;
import com.JCODE.domain.RespostaIntencao;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Guarda a CURADORIA de temas do intencoes.json (levas de out/2026).
 *
 * O avaliador congelado (14/14) protege as REGRAS antigas; este spec protege
 * os DADOS NOVOS sem tocar na régua:
 *
 *  1. contrato de volume - todo intento curado tem 6+ exemplos e 4+ respostas
 *     com origem marcada e humor dentro de 0..10;
 *  2. a matriz de AUTO-CLASSIFICACAO - todo exemplo curado tem que voltar pro
 *     MESMO intento pelo classificador real (e se um exemplo ambíguo capotar
 *     pra outro intento, a suíte fica vermelha e o dado é consertado);
 *  3. casos de comportamento fora da base - frases NAO-exatas que tem que cair
 *     no tema certo (o matching fuzzy de verdade, nao so igualdade literal).
 */
public class CuradoriaTemasTest {

	@Before
	public void carregaCacheReal() {
		ControleResposta.carregaCache();
	}

	/** Intentos curados = os que nasceram com origem "curadoria out/2026". */
	private List<Intencao> curados() {
		List<Intencao> fora = new ArrayList<>();
		for (Intencao i : ControleResposta.getIntencoes()) {
			if (i.getExemplos() == null || i.getExemplos().isEmpty())
				continue;
			Exemplo primeiro = i.getExemplos().get(0);
			if (primeiro.getOrigem() != null && primeiro.getOrigem().contains("curadoria"))
				fora.add(i);
		}
		return fora;
	}

	/** A raiz COMPLETA da base - o classificador enxerga tudo, como no app. */
	private Intencoes raizToda() {
		Intencoes raiz = new Intencoes();
		raiz.setIntencoes(new ArrayList<>(ControleResposta.getIntencoes()));
		return raiz;
	}

	/** 1. Contrato de volume e integridade dos dados curados. */
	@Test
	public void contratoDeVolumeEDadosCurados() {
		List<Intencao> lista = curados();
		assertTrue("nenhum intento curado encontrado (filtro de origem quebrou)", lista.size() >= 11);

		for (Intencao i : lista) {
			assertTrue(i.getIntento() + ": curado com menos de 6 exemplos (" + i.getExemplos().size() + ")",
					i.getExemplos().size() >= 6);
			assertTrue(i.getIntento() + ": curado com menos de 4 respostas (" + i.getRespostas().size() + ")",
					i.getRespostas().size() >= 4);

			for (Exemplo e : i.getExemplos())
				assertTrue(i.getIntento() + ": exemplo sem origem de curadoria: " + e.getTexto(),
						e.getOrigem() != null && e.getOrigem().contains("curadoria"));

			for (RespostaIntencao r : i.getRespostas()) {
				assertTrue(i.getIntento() + ": resposta sem origem de curadoria: " + r.getTexto(),
						r.getOrigem() != null && r.getOrigem().contains("curadoria"));
				assertTrue(i.getIntento() + ": humorMin fora de 0..10", r.getHumorMin() >= 0 && r.getHumorMin() <= 10);
				assertTrue(i.getIntento() + ": humorMax fora de 0..10", r.getHumorMax() >= 0 && r.getHumorMax() <= 10);
				assertTrue(i.getIntento() + ": humorMin > humorMax", r.getHumorMin() <= r.getHumorMax());
			}
		}
	}

	/**
	 * 2. A matriz: cada exemplo curado classifica no PROPRIO intento.
	 *
	 * Aqui mora o risco real da curadoria: frase de um tema que, pelo matching,
	 * empata ou perde pra outro intento da base. Com a base inteira na raiz,
	 * isso aparece como teste vermelho - dado, não lógica, é o que se conserta.
	 */
	@Test
	public void todoExemploCuradoClassificaNoProprioIntento() {
		Intencoes raiz = raizToda();
		int verificados = 0;

		for (Intencao i : curados()) {
			for (Exemplo e : i.getExemplos()) {
				MatchIntencao m = ControleResposta.melhorIntencaoDetalhado(raiz, e.getTexto());
				assertNotNull(i.getIntento() + ": classificador devolveu nulo pro proprio exemplo: " + e.getTexto(), m);
				assertEquals("exemplo curado capotou pra outro intento: \"" + e.getTexto() + "\"",
						i.getIntento(), m.getIntencao().getIntento());
				assertTrue("exemplo proprio com score baixo demais (" + m.getScore() + "): " + e.getTexto(),
						m.getScore() >= 0.99);
				verificados++;
			}
		}
		assertTrue("matriz vazia - nenhum exemplo curado verificado", verificados >= 66);
	}

	/**
	 * 3. Comportamento fora da base: frases que NAO estao no arquivo, mas o
	 * fuzzy tem que jogar no tema certo. Cobre o caminho que o app usa de
	 * verdade (usuario nunca digita o exemplo literal).
	 */
	@Test
	public void frasesNaoExatasCaemNoTemaCerto() {
		Intencoes raiz = raizToda();
		String[][] casos = {
				{ "recomenda um filme bom pra hoje", "filme" },
				{ "boa série essa, hein", "serie" },
				{ "vem cá, me conta uma curiosidade", "curiosidade" },
				{ "quero ouvir uma musica boa agora", "musica" },
				{ "voce acompanha o futebol brasileiro?", "esporte" },
				{ "vou viajar em dezembro", "viagem" },
		};

		for (String[] caso : casos) {
			MatchIntencao m = ControleResposta.melhorIntencaoDetalhado(raiz, caso[0]);
			assertNotNull("frase sem classificacao: " + caso[0], m);
			assertEquals("frase caiu no tema errado: \"" + caso[0] + "\"",
					caso[1], m.getIntencao().getIntento());
		}
	}
}
