package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.JCODE.domain.Exemplo;
import com.JCODE.domain.Intencao;
import com.JCODE.domain.Intencoes;
import com.JCODE.domain.MatchIntencao;
import com.JCODE.domain.RespostaIntencao;
import com.JCODE.util.Normalizador;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Guarda a CURADORIA DA CONSULTORIA out/2026 (expansao da base para conversa
 * diaria: 33 intenções novas + expansão das fracas, ~1100 exemplos, ~390
 * respostas, followups da Fase 3).
 *
 * O CuradoriaTemasTest protege o que nasceu "curadoria out/2026" - mas só os
 * intents cujo PRIMEIRO exemplo tem essa origem, e a matriz dele só olha os
 * exemplos desses intents. A consultoria também mexeu em intents ANTIGOS
 * (humor_usuario, piada, sugestao...), cuja matriz ninguém cobre. Este spec
 * fecha o buraco pelo critério de proveniência: tudo que carrega
 * 'curadoria consultoria out/2026' no origem:
 *
 *  1. contrato de volume - intento NOVO da consultoria (primeiro exemplo com
 *     origem consultoria) nasce com 6+ exemplos, 4+ respostas e 2+ followups
 *     (alimento da iniciativa própria, §7.2);
 *  2. a matriz de AUTO-CLASSIFICACAO estendida - TODO exemplo da consultoria
 *     (mesmo os que entraram em intento antigo) tem que voltar pro MESMO
 *     intento pelo classificador real, com score >= 0.99;
 *  3. integridade - nenhum exemplo da consultoria repete texto dentro da base
 *     (o merge rejeita, mas o arquivo é o que vale).
 *
 * Roda com CWD na raiz do projeto (mesma regra do CuradoriaTemasTest).
 */
public class CuradoriaConsultoriaTest {

	@Before
	public void carregaCacheReal() {
		ControleResposta.carregaCache();
	}

	/** Marcas de proveniência das levas feitas pela curadoria (aceitas na guarda). */
	private static final String[] LEVAS = {
			"curadoria consultoria out/2026",
			"curadoria dialogo out/2026",
			"curadoria uso-real out/2026",
			"curadoria rodada-2 out/2026"};

	private boolean ehDeLeva(String origem) {
		if (origem == null)
			return false;
		for (String leva : LEVAS)
			if (origem.contains(leva))
				return true;
		return false;
	}

	private boolean ehDaConsultoria(Exemplo e) {
		return ehDeLeva(e.getOrigem());
	}

	/** Exemplos da consultoria, agrupados por intento (inclusive intents antigos). */
	private List<Intencao> intentsComConteudoDaConsultoria() {
		List<Intencao> fora = new ArrayList<>();
		for (Intencao i : ControleResposta.getIntencoes())
			if (i.getExemplos().stream().anyMatch(this::ehDaConsultoria))
				fora.add(i);
		return fora;
	}

	/** Intentos NOVOS da consultoria: o primeiro exemplo nasceu dela. */
	private List<Intencao> intentsNovosDaConsultoria() {
		List<Intencao> fora = new ArrayList<>();
		for (Intencao i : ControleResposta.getIntencoes()) {
			if (i.getExemplos().isEmpty())
				continue;
			Exemplo primeiro = i.getExemplos().get(0);
			if (ehDaConsultoria(primeiro))
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

	/**
	 * 1. Contrato de volume dos intentos novos (a meta mínima da consultoria:
	 * nascer maior que o piso da curadoria anterior, com followups preenchidos
	 * porque a Fase 3 vai consumir).
	 */
	@Test
	public void intentosNovosNasceramGrandes() {
		List<Intencao> novos = intentsNovosDaConsultoria();
		assertTrue("nenhum intento novo da consultoria (filtro de origem quebrou)",
				novos.size() >= 30);

		for (Intencao i : novos) {
			assertTrue(i.getIntento() + ": novo com menos de 6 exemplos (" + i.getExemplos().size() + ")",
					i.getExemplos().size() >= 6);
			assertTrue(i.getIntento() + ": novo com menos de 4 respostas (" + i.getRespostas().size() + ")",
					i.getRespostas().size() >= 4);
			assertTrue(i.getIntento() + ": novo sem followups (Fase 3 depende disso)",
					i.getFollowups() != null && i.getFollowups().size() >= 2);

			for (Exemplo e : i.getExemplos())
				assertTrue(i.getIntento() + ": exemplo de intento novo fora das levas: " + e.getTexto(),
						ehDaConsultoria(e));
			for (RespostaIntencao r : i.getRespostas()) {
				assertTrue(i.getIntento() + ": resposta de intento novo fora das levas: " + r.getTexto(),
						ehDeLeva(r.getOrigem()));
				assertTrue(i.getIntento() + ": humorMin fora de 0..10", r.getHumorMin() >= 0 && r.getHumorMin() <= 10);
				assertTrue(i.getIntento() + ": humorMax fora de 0..10", r.getHumorMax() >= 0 && r.getHumorMax() <= 10);
				assertTrue(i.getIntento() + ": humorMin > humorMax", r.getHumorMin() <= r.getHumorMax());
			}
		}
	}

	/**
	 * 2. A matriz estendida: TODO exemplo da consultoria classifica no PROPRIO
	 * intento - inclusive os apensados em intents antigos (humor_usuario,
	 * piada...), que a matriz do CuradoriaTemasTest não alcança.
	 */
	@Test
	public void todoExemploDaConsultoriaClassificaNoProprioIntento() {
		Intencoes raiz = raizToda();
		int verificados = 0;

		for (Intencao i : intentsComConteudoDaConsultoria()) {
			for (Exemplo e : i.getExemplos()) {
				if (!ehDaConsultoria(e))
					continue;

				MatchIntencao m = ControleResposta.melhorIntencaoDetalhado(raiz, e.getTexto());
				assertNotNull(i.getIntento() + ": classificador devolveu nulo pro proprio exemplo: " + e.getTexto(), m);
				assertEquals("exemplo da consultoria capotou pra outro intento: \"" + e.getTexto() + "\"",
						i.getIntento(), m.getIntencao().getIntento());
				assertTrue("exemplo proprio com score baixo demais (" + m.getScore() + "): " + e.getTexto(),
						m.getScore() >= 0.99);
				verificados++;
			}
		}
		assertTrue("matriz da consultoria vazia - filtro de origem quebrou", verificados >= 1000);
	}

	/**
	 * 3. Integridade: nenhum texto de exemplo da consultoria aparece duas vezes
	 * na base (o merge recusa duplicata, mas o arquivo em disco é a prova).
	 */
	@Test
	public void nenhumExemploDaConsultoriaRepeteNaBase() {
		Set<String> textosConsultoria = new HashSet<>();
		int total = 0;

		for (Intencao i : ControleResposta.getIntencoes()) {
			for (Exemplo e : i.getExemplos()) {
				if (!ehDaConsultoria(e))
					continue;
				total++;
				assertTrue("exemplo da consultoria duplicado na base: \"" + e.getTexto() + "\"",
						textosConsultoria.add(e.getTexto()));
			}
		}
		assertTrue("consultoria sumiu da base (menos de 1000 exemplos: " + total + ")", total >= 1000);
	}

	/**
	 * 4. Literais de match exato (origem "autor"): frases só-stopword ("e voce",
	 * "e vc") não têm token nenhum - o classificador devolve null DE PROPÓSITO
	 * e elas só existem para o `fraseExata` (que roda ANTES do classificador no
	 * app). A matriz acima pula essas por construção; este spec é a guarda delas:
	 * cada literal "autor" da base tem que ser encontrado pelo match exato.
	 */
	@Test
	public void literaisDeMatchExatoResolvemPelaFraseExata() {
		int verificados = 0;
		for (Intencao i : ControleResposta.getIntencoes()) {
			for (Exemplo e : i.getExemplos()) {
				if (!"autor".equals(e.getOrigem()))
					continue;
				String exata = ControleResposta.fraseExata(Normalizador.texto(e.getTexto()));
				assertEquals("literal de match exato não resolve (intento " + i.getIntento() + "): " + e.getTexto(),
						e.getTexto(), exata);
				verificados++;
			}
		}
		assertTrue("nenhum literal de match exato na base (esperava os da P1: 'e voce'/'e vc')",
				verificados >= 2);
	}
}
