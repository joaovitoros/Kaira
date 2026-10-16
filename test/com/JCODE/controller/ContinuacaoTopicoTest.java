package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.JCODE.domain.Intencao;
import com.JCODE.domain.RespostaIntencao;
import com.JCODE.util.Normalizador;

import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Map;

/**
 * #27 (briefing P2 / §12.2) - Continuação de assunto.
 *
 * O usuario pede pra continuar ("fala mais disso") e hoje cai em
 * mudar_assunto / convite_conversa / cotidiano - o contrario do que
 * ele quer. A feature tem duas metades:
 *
 * - DADOS: intencao `continuacao_topico` com os literais de
 *   continuacao (frases de 1-2 tokens precisam do literal, regra do
 *   briefing);
 * - CODIGO: o hook de topico (melhorIntencaoDetalhado) gravava
 *   `topicoAtual` incondicionalmente - continuar o assunto NAO pode
 *   virar o novo topico, senao o slot {topico} renderiza
 *   "continuacao_topico" na propria resposta de continuacao.
 *
 * Guardas de colisao: "falar de outra coisa" e cia continuam em
 * mudar_assunto (os exemplos das duas intents nao podem dividir
 * token-set - briefing regra 6).
 */
public class ContinuacaoTopicoTest {

	@Before
	public void zera() {
		ControleSessao.reset();
		ControleResposta.carregaCache();
	}

	private void assertIntencao(String frase, String esperado) {
		Map<String, Intencao> r = ControleResposta.melhorIntencao(Normalizador.texto(frase));
		assertNotNull("frase devia classificar: " + frase, r);
		assertEquals(frase + " -> " + esperado, esperado,
				r.values().iterator().next().getIntento());
	}

	/** RED: a intencao continuacao_topico nao existe na base ainda. */
	@Test
	public void frasesDeContinuacaoCaemNaIntencaoCerta() {
		assertIntencao("fala mais disso", "continuacao_topico");
		assertIntencao("conta mais sobre isso", "continuacao_topico");
		assertIntencao("e depois o que aconteceu", "continuacao_topico");
		assertIntencao("me explica melhor essa parte", "continuacao_topico");
	}

	/**
	 * #28 - literais de pedido que vazaram no log: "conta mais" caia em
	 * piada (17:27:14) e "me fale mais" em convite_conversa (17:28:14).
	 * Com entrada de token unico, o literal com resto 0 vence o
	 * desempate - as frases antigas de continuacao tem resto >= 1.
	 */
	@Test
	public void literaisDePedidoVazadosEntramNaContinuacao() {
		assertIntencao("conta mais", "continuacao_topico");
		assertIntencao("me fale mais", "continuacao_topico");
	}

	/** Guarda: o que E de mudar assunto nao migra. */
	@Test
	public void mudarAssuntoNaoPerdeAsFrasesDele() {
		assertIntencao("falar de outra coisa", "mudar_assunto");
		assertIntencao("muda de assunto", "mudar_assunto");
		assertIntencao("cansei desse tema", "mudar_assunto");
	}

	/** RED: o hook de topico sobrescrevia com "continuacao_topico". */
	@Test
	public void continuarNaoSobrescreveOTopico() {
		ControleSessao.topico("filme");

		ControleResposta.melhorIntencao(Normalizador.texto("fala mais disso"));

		assertEquals("o topico continua sendo o assunto de verdade",
				"filme", ControleSessao.get().getTopicoAtual());
	}

	/**
	 * CONTRATO DE ESQUEMA (hotfix pos-#27): toda intencao da base real
	 * nasce com acoes - o comandosResposta chama getAcoes().contains e
	 * NPEia com null (perigo ja documentado no fixture do
	 * ControleRespostaFaixaConfiancaTest). A continuacao_topico entrou
	 * no #27 sem o campo e derrubou o app ao vivo no "fala mais disso".
	 */
	@Test
	public void baseNaoTemIntentoSemAcoes() {
		for (Intencao i : ControleResposta.getIntencoes())
			assertNotNull("intencao da base sem acoes: " + i.getIntento(), i.getAcoes());
	}

	/**
	 * Hotfix #27b - a voz dela (rastreado no conversas.log): toda
	 * resposta de continuacao tem que ANCORAR no topico ({topico}) e
	 * falar com a voz dela, em vez de devolver a bola ("Conta, tou
	 * ouvindo!", "Vai!", "continua!") - o usuario pede pra ELA falar
	 * mais e ela fazia o contrario. Followups idem: pergunta ESPECIFICA
	 * sobre o topico, nao "me conta mais".
	 */
	@Test
	public void continuacaoFalaDoTopicoENaoDevolveABola() {
		Intencao c = null;
		for (Intencao i : ControleResposta.getIntencoes())
			if ("continuacao_topico".equals(i.getIntento())) {
				c = i;
				break;
			}
		assertNotNull("continuacao_topico sumiu da base", c);

		for (RespostaIntencao r : c.getRespostas())
			assertTrue("resposta de continuacao sem {topico} (devolve a bola): " + r.getTexto(),
					r.getTexto().contains("{topico}"));

		assertNotNull("continuacao_topico sem followups", c.getFollowups());
		for (String f : c.getFollowups())
			assertTrue("followup de continuacao sem {topico}: " + f, f.contains("{topico}"));
	}

	/**
	 * #28 (B) - o usuario entregou o turno pra ela falar: o pool
	 * generico do §7.2 ("E voce, o que andou fazendo hoje?") nao pode
	 * ser colado no turno de continuacao (log 17:28:48). RED ate o B.
	 */
	@Test
	public void pedidoDeContinuacaoSeguraOPoolGenerico() {
		ControleSessao.processa("tudo tranquilo por aqui");
		ControleSessao.processa("entao me conta mais");
		ControleSessao.processa("legal legal");

		// classifica continuacao: o hook marca o turno como de pedido
		ControleResposta.melhorIntencao(Normalizador.texto("fala mais disso"));

		assertNull("pool generico segurado no turno de continuacao",
				ControleSessao.perguntaInteresse());
	}

	/**
	 * #28 (B guarda) - a fonte 1 (followups do topicoAtual) PASSA no
	 * turno de continuacao: fechar com pergunta SOBRE o tema e
	 * continuacao, nao anulacao.
	 */
	@Test
	public void pedidoDeContinuacaoAindaFechaComFollowupDoTopico() {
		List<String> doBanco = ControleResposta.getIntencoes().stream()
				.filter(i -> "filme".equals(i.getIntento()))
				.findFirst().get().getFollowups();
		assertFalse("pre-condicao: filme tem followups no banco", doBanco.isEmpty());

		ControleSessao.topico("filme");
		ControleSessao.processa("tudo tranquilo por aqui");
		ControleSessao.processa("entao me conta mais");
		ControleSessao.processa("legal legal");
		ControleResposta.melhorIntencao(Normalizador.texto("fala mais disso"));

		String pergunta = ControleSessao.perguntaInteresse();
		assertNotNull("fonte 1 deve passar no turno de continuacao", pergunta);
		assertTrue("vem dos followups do topico: " + pergunta, doBanco.contains(pergunta));
	}

	/**
	 * #28 (B) - a flag do pedido morre no processa: o ciclo seguinte
	 * volta a ouvir o §7.2 normalmente (senao a iniciativa morria
	 * depois do primeiro pedido de continuacao).
	 */
	@Test
	public void pedidoDeContinuacaoNaoCalaOSeguinte() {
		ControleSessao.processa("tudo tranquilo por aqui");
		ControleSessao.processa("entao me conta mais");
		ControleSessao.processa("legal legal");
		ControleResposta.melhorIntencao(Normalizador.texto("fala mais disso"));
		assertNull("1o ciclo: pedido segura o pool", ControleSessao.perguntaInteresse());

		ControleSessao.processa("continua");
		ControleSessao.processa("hm entendi");
		ControleSessao.processa("boa");
		assertNotNull("2o ciclo: flag morreu no processa, volta a perguntar",
				ControleSessao.perguntaInteresse());
	}

	/** RED de novo: {topico} renderizaria o nome da propria continuacao. */
	@Test
	public void slotTopicoRenderizaOTemaAnterior() {
		ControleSessao.topico("filme");

		ControleResposta.melhorIntencao(Normalizador.texto("fala mais disso"));

		assertEquals("Conta mais sobre filme",
				ControleResposta.preencheSlots("Conta mais sobre {topico}"));
	}
}
