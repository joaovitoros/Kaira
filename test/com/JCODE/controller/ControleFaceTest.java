package com.JCODE.controller;

import static org.junit.Assert.assertEquals;

import javax.swing.JButton;
import javax.swing.JTextArea;

import org.junit.Before;
import org.junit.Test;

/**
 * Testa o histórico do chat (G5 do PLANO_DE_EVOLUCAO.md):
 * as respostas devem se acumular no texto, uma por linha,
 * sem sobrescrever as anteriores, e sem quebrar com null.
 */
public class ControleFaceTest {

	private ControleFace controle;

	@Before
	public void setUp() {
		// O ControleFace guarda as areas em campos static no construtor.
		JTextArea areaKaira = new JTextArea();
		JTextArea areaUsuario = new JTextArea();
		JButton botao = new JButton("!");
		controle = new ControleFace(areaKaira, areaUsuario, botao);
	}

	@Test
	public void respostaUnicaApareceUmaVez() {
		ControleFace.exibirResposta("Ola");

		assertEquals("Ola\n", areaKaira().getText());
	}

	@Test
	public void respostasAcumulamSemSobrescrever() {
		ControleFace.exibirResposta("primeira");
		ControleFace.exibirResposta("segunda");
		ControleFace.exibirResposta("terceira");

		String esperado = "primeira\nsegunda\nterceira\n";
		assertEquals(esperado, areaKaira().getText());
	}

	@Test
	public void respostaNulaNaoEscreveNadaNemQuebra() {
		ControleFace.exibirResposta("antes");

		// null = sem match ainda; nao deve poluir o chat nem lancar excecao
		ControleFace.exibirResposta(null);

		assertEquals("antes\n", areaKaira().getText());
	}

	@Test
	public void cursorVaiParaOFimParaAutoScroll() {
		ControleFace.exibirResposta("primeira");
		ControleFace.exibirResposta("segunda");

		assertEquals(areaKaira().getDocument().getLength(),
				areaKaira().getCaretPosition());
	}

	@Test
	public void respostaVaziaNaoLancaExcecao() {
		// Caso de borda: string vazia ainda gera a quebra de linha, mas nao explode
		ControleFace.exibirResposta("");
		ControleFace.exibirResposta("depois");

		assertEquals("\ndepois\n", areaKaira().getText());
	}

	// acesso a area estatica no mesmo pacote
	private JTextArea areaKaira() {
		return ControleFace.textAreaKaira;
	}
}
