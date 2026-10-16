package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import com.JCODE.domain.Intencao;
import com.JCODE.domain.RespostaIntencao;

/**
 * Spec da issue #32 (BRIEFING P7.4): fatos novos na sessao.
 *
 * Hoje a sessao lembra gosta/mora/trabalha (+ nasceu em e o generico
 * "meu X favorito"); o briefing pede time de futebol, musica favorita,
 * nome do pet e sonho com ESTRUTURA IDENTICA a existente: padrao em
 * PADROES_FATO gravando chave fixa no Map de fatos + intento lembra_*
 * no banco ancorado no slot {fato:CHAVE}.
 *
 * Contratos exercitados:
 *  - captura: processa() grava time / musica favorita / pet / sonho nas
 *    chaves fixas ASCII, sem artigo inicial ("meu time e o Sao Paulo"
 *    grava "Sao Paulo" - espelho do PADROES_NOME);
 *  - dados: lembra_time/lembra_musica/lembra_pet/lembra_sonho existem
 *    com 6+ exemplos, 4+ respostas, acoes nao-nula (contrato do NPE da
 *    #27) e TODA resposta ancorada no {fato:CHAVE} proprio - sem ancora
 *    em todas, o descarte de fato pendente do #21 nunca aconteceria;
 *  - ponta a ponta: com o fato na sessao, lerResposta entrega o valor
 *    real renderizado e nunca um {fato cru na fala.
 *
 * O auto-classificacao dos novos exemplos (>= 0.99 no proprio intento)
 * e a matriz fuzzy ja sao cobertas pelo CuradoriaTemasTest - eles entram
 * na curadoria junto com os demais.
 */
public class FatosNovosTest {

	@Before
	public void prepara() {
		ControleResposta.carregaCache();
		ControleSessao.reset();
	}

	// ---------- captura (PADROES_FATO) ----------

	/** Time: variante favorito (a natural) e variante torcedor. */
	@Test
	public void capturaTimeNasDuasFrasesNaturaisSemArtigo() {
		ControleSessao.processa("meu time favorito é o São Paulo");
		assertEquals("artigo sobrando na chave time", "São Paulo",
				ControleSessao.get().getFatos().get("time"));

		ControleSessao.reset();
		ControleSessao.processa("torço pro Flamengo");
		assertEquals("variante do torcedor nao gravou time", "Flamengo",
				ControleSessao.get().getFatos().get("time"));
	}

	/** Musica favorita: a chave e ASCII mesmo o usuario falando com acento. */
	@Test
	public void capturaMusicaFavoritaNaChaveAscii() {
		ControleSessao.processa("minha música favorita é Legião Urbana");

		assertEquals("Legião Urbana",
				ControleSessao.get().getFatos().get("musica favorita"));
	}

	/** Nome do pet: "se chama" e "chama-se" cobrem as duas grafias. */
	@Test
	public void capturaNomeDoPet() {
		ControleSessao.processa("meu pet se chama Bela");
		assertEquals("Bela", ControleSessao.get().getFatos().get("pet"));

		ControleSessao.reset();
		ControleSessao.processa("meu cachorro chama-se Rex");
		assertEquals("Rex", ControleSessao.get().getFatos().get("pet"));
	}

	/** Sonho: valor guardado inteiro ("ser dentista", nao so "dentista"). */
	@Test
	public void capturaSonhoDoUsuario() {
		ControleSessao.processa("meu sonho é ser dentista");

		assertEquals("ser dentista",
				ControleSessao.get().getFatos().get("sonho"));
	}

	// ---------- dados (intencoes.json) ----------

	private Intencao base(String intento) {
		for (Intencao i : ControleResposta.getIntencoes())
			if (intento.equals(i.getIntento()))
				return i;
		return null;
	}

	/** 6+ exemplos, 4+ respostas, acoes e ancora certa em TODA resposta. */
	@Test
	public void intentsLembraNovosSeguemAEstruturaDasExistentes() {
		String[][] alvos = {
				{"lembra_time", "{fato:time}"},
				{"lembra_musica", "{fato:musica favorita}"},
				{"lembra_pet", "{fato:pet}"},
				{"lembra_sonho", "{fato:sonho}"}};

		for (String[] alvo : alvos) {
			Intencao i = base(alvo[0]);
			assertNotNull("intento sumiu da base: " + alvo[0], i);
			assertNotNull("sem acoes (NPE da #27): " + alvo[0], i.getAcoes());
			assertTrue(alvo[0] + " com menos de 6 exemplos: " + i.getExemplos().size(),
					i.getExemplos().size() >= 6);
			assertTrue(alvo[0] + " com menos de 4 respostas: " + i.getRespostas().size(),
					i.getRespostas().size() >= 4);
			for (RespostaIntencao r : i.getRespostas())
				assertTrue(alvo[0] + " resposta sem a ancora do fato: " + r.getTexto(),
						r.getTexto().contains(alvo[1]));
		}
	}

	// ---------- ponta a ponta (lerResposta) ----------

	@Test
	public void lembraTimeRenderizaNaConversa() {
		ControleSessao.processa("meu time favorito é o São Paulo");

		String resposta = ControleResposta.lerResposta("qual e o meu time mesmo");

		assertNotNull("frase devia classificar em lembra_time", resposta);
		assertTrue("fato nao renderizou: " + resposta, resposta.contains("São Paulo"));
		assertFalse("token cru na entrega: " + resposta, resposta.contains("{fato"));
	}

	@Test
	public void lembraMusicaRenderizaNaConversa() {
		ControleSessao.processa("minha música favorita é Legião Urbana");

		String resposta = ControleResposta.lerResposta("qual e a minha musica favorita mesmo");

		assertNotNull("frase devia classificar em lembra_musica", resposta);
		assertTrue("fato nao renderizou: " + resposta, resposta.contains("Legião Urbana"));
		assertFalse("token cru na entrega: " + resposta, resposta.contains("{fato"));
	}

	@Test
	public void lembraPetRenderizaNaConversa() {
		ControleSessao.processa("meu pet se chama Bela");

		String resposta = ControleResposta.lerResposta("voce lembra o nome do meu pet");

		assertNotNull("frase devia classificar em lembra_pet", resposta);
		assertTrue("fato nao renderizou: " + resposta, resposta.contains("Bela"));
		assertFalse("token cru na entrega: " + resposta, resposta.contains("{fato"));
	}

	@Test
	public void lembraSonhoRenderizaNaConversa() {
		ControleSessao.processa("meu sonho é ser dentista");

		String resposta = ControleResposta.lerResposta("voce lembra do meu sonho");

		assertNotNull("frase devia classificar em lembra_sonho", resposta);
		assertTrue("fato nao renderizou: " + resposta, resposta.contains("ser dentista"));
		assertFalse("token cru na entrega: " + resposta, resposta.contains("{fato"));
	}
}
