package com.JCODE.controller;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import javax.swing.JButton;
import javax.swing.JTextArea;
import javax.swing.JTextPane;

import org.junit.Before;
import org.junit.Test;

/**
 * Testa a apresentacao do chat (bolhas) mantendo o contrato antigo do
 * G5: as mensagens se ACUMULAM (um balao por mensagem), null nao polui,
 * cursor vai pro fim, string vazia nao explode. Novo: cada um no seu
 * lado - usuario a direita, KAIRA a esquerda.
 *
 * O HTML do documento e a fonte da verdade do que aparece. O HTML
 * serializado pode usar aspas simples OU duplas, entao as assercoes
 * normalizam aspas antes de olhar atributo.
 */
public class ControleFaceTest {

	private ControleFace controle;

	@Before
	public void setUp() {
		// O ControleFace guarda as areas em campos static no construtor.
		JTextPane areaConversa = new JTextPane();
		JTextArea areaUsuario = new JTextArea();
		JButton botao = new JButton("!");
		controle = new ControleFace(areaConversa, areaUsuario, botao);
	}

	@Test
	public void respostaUnicaApareceUmaVez() {
		ControleFace.exibirResposta("Ola");

		assertTrue("a resposta tem que estar no chat", fonte().contains("Ola"));
		assertEquals("um balao so", 1, baloes());
	}

	@Test
	public void respostasAcumulamSemSobrescrever() {
		ControleFace.exibirResposta("primeira");
		ControleFace.exibirResposta("segunda");
		ControleFace.exibirResposta("terceira");

		String mostrada = fonte();
		assertTrue(mostrada.contains("primeira"));
		assertTrue(mostrada.contains("segunda"));
		assertTrue(mostrada.contains("terceira"));
		assertEquals("tres baloes", 3, baloes());
	}

	@Test
	public void respostaNulaNaoEscreveNadaNemQuebra() {
		// null = sem match ainda; nao deve poluir o chat nem lancar excecao
		ControleFace.exibirResposta("antes");
		ControleFace.exibirResposta(null);

		assertEquals("antes sem poluir", 1, baloes());
	}

	@Test
	public void cursorVaiParaOFimParaAutoScroll() {
		ControleFace.exibirResposta("primeira");
		ControleFace.exibirResposta("segunda");

		assertEquals(areaConversa().getDocument().getLength(),
				areaConversa().getCaretPosition());
	}

	@Test
	public void respostaVaziaNaoLancaExcecao() {
		// Caso de borda: string vazia ainda gera um balao vazio, mas nao explode
		ControleFace.exibirResposta("");
		ControleFace.exibirResposta("depois");

		assertTrue(fonte().contains("depois"));
	}

	/**
	 * O pedido visual: conversa de chat de verdade - fala do usuario a
	 * direita (align='right'), resposta da KAIRA depois e a esquerda.
	 */
	@Test
	public void kairaFalaNaEsquerdaEUsuarioNaDireita() {
		ControleFace.exibirFraseUsuario("oi kaira");
		ControleFace.exibirResposta("ola, tudo bem?");

		String mostrada = aspasSimples(fonte());
		int direita = mostrada.indexOf("align='right'");
		int esquerda = mostrada.indexOf("align='left'");
		assertTrue("fala do usuario e o primeiro balao, a direita", direita >= 0);
		assertTrue("resposta da KAIRA vem depois, a esquerda", esquerda > direita);
		assertTrue(mostrada.contains("oi kaira"));
		assertTrue(mostrada.contains("ola, tudo bem?"));
	}

	@Test
	public void fraseDoUsuarioVaziaOuNulaNaoViraBalao() {
		ControleFace.exibirFraseUsuario("   ");
		ControleFace.exibirFraseUsuario(null);
		ControleFace.exibirResposta("depois");

		assertEquals("so o balao da resposta", 1, baloes());
	}

	/**
	 * §6.2: separador visual ENTRE turnos - a regua sai DEPOIS da resposta
	 * da KAIRA (fim de turno), nunca depois da fala so do usuario e nunca
	 * em resposta nula (turno nao fechado).
	 */
	@Test
	public void turnoFechaComSeparadorDepoisDaResposta() {
		ControleFace.exibirFraseUsuario("oi");
		ControleFace.exibirResposta("ola, tudo bem?");

		String html = fonte().toLowerCase();
		int regua = html.indexOf("<hr");
		assertTrue("turno sem separador visual: " + fonte(), regua >= 0);
		assertTrue("regua tem que vir depois da resposta da KAIRA",
				regua > html.indexOf("ola, tudo bem?"));
	}

	@Test
	public void umSeparadorPorTurno() {
		ControleFace.exibirFraseUsuario("um");
		ControleFace.exibirResposta("resposta um");
		ControleFace.exibirFraseUsuario("dois");
		ControleFace.exibirResposta("resposta dois");

		assertEquals("uma regua por turno fechado", 2, separadores());
	}

	@Test
	public void respostaNulaNaoFechaTurnoComRegua() {
		ControleFace.exibirResposta(null);

		assertEquals("resposta nula nao pode soltar regua", 0, separadores());
	}

	// acesso a area estatica no mesmo pacote
	private JTextPane areaConversa() {
		return ControleFace.areaConversa;
	}

	private String fonte() {
		return areaConversa().getText();
	}

	/** Balao = tabela de largura total; o HTML serializado pode vir com " ou '. */
	private int baloes() {
		String fonte = aspasSimples(fonte());
		int total = 0;
		int i = fonte.indexOf("width='100%'");
		while (i >= 0) {
			total++;
			i = fonte.indexOf("width='100%'", i + 1);
		}
		return total;
	}

	/** Regua de fim de turno (§6.2) - tag <hr> em qualquer caixa. */
	private int separadores() {
		String html = fonte().toLowerCase();
		int total = 0;
		int i = html.indexOf("<hr");
		while (i >= 0) {
			total++;
			i = html.indexOf("<hr", i + 1);
		}
		return total;
	}

	private String aspasSimples(String html) {
		return html.replace("\"", "'");
	}
}
