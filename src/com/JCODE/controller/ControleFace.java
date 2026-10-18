package com.JCODE.controller;

import com.JCODE.util.Tema;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;

import javax.swing.JButton;
import javax.swing.JTextArea;
import javax.swing.JTextPane;
import javax.swing.text.BadLocationException;
import javax.swing.text.html.HTMLEditorKit;

public class ControleFace implements ActionListener{
	static JTextPane areaConversa;
	static JTextArea textAreaUsuario;
	static JButton btnSinalizar;
	static String resposta;
	static String falaUsuario;
	static boolean verificarManter;
	static boolean verificarCriarResp;
	public ControleFace(JTextPane areaConversa1, JTextArea textAreaUsuario1, JButton btnSinalizar) {
		// setContentType troca o editor por HTML - e o que permite balao de chat
		areaConversa1.setContentType("text/html");
		// #31: tema preto e verde no proprio documento HTML (fundo, texto, regua)
		javax.swing.text.html.StyleSheet css =
				((javax.swing.text.html.HTMLDocument) areaConversa1.getDocument()).getStyleSheet();
		css.addRule("body { background-color: " + Tema.hex(Tema.FUNDO)
				+ "; color: " + Tema.hex(Tema.TEXTO) + "; }");
		css.addRule("hr { color: " + Tema.hex(Tema.VERDE_ESCURO) + "; }");
		ControleFace.areaConversa = areaConversa1;
		ControleFace.textAreaUsuario = textAreaUsuario1;
		ControleFace.btnSinalizar = btnSinalizar;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		String cmd = e.getActionCommand();

		if(cmd.equalsIgnoreCase("Enviar")){
			MandarFrase();
			exibirFraseUsuario(falaUsuario);
			exibirResposta(resposta);
		}

	}

	/**
	 * Acrescenta a resposta ao chat como balao da KAIRA (esquerda), sem
	 * sobrescrever as anteriores. A resposta nula (ainda sem match) e
	 * ignorada para nao poluir a conversa.
	 */
	public static void exibirResposta(String texto) {
		if(texto==null)
			return;
		balao(false, texto);
		fimDeTurno();   // §6.2: separador entre turnos
	}

	/**
	 * Balao do usuario (direita). Nao e logica nova: e a apresentacao da
	 * fala que ja foi mandada - o texto sai do mesmo falaUsuario de sempre.
	 */
	static void exibirFraseUsuario(String texto) {
		if(texto==null || texto.trim().isEmpty())
			return;
		balao(true, texto);
	}

	/**
	 * Cada mensagem vira uma tabela de largura total: usuario a direita
	 * (verde), KAIRA a esquerda (cinza) - conversa de chat de verdade.
	 * Escapar o texto e obrigatorio: < e & quebrariam o HTML do balao.
	 */
	private static void balao(boolean doUsuario, String texto) {
		// #31: baloes do tema - usuario em verde escuro, KAIRA em superficie escura
		String cor = Tema.hex(doUsuario ? Tema.BALAO_USUARIO : Tema.BALAO_KAIRA);
		String fala = "<font color='" + Tema.hex(Tema.TEXTO) + "'>" + escapa(texto) + "</font>";
		String celula = doUsuario
				? "<td align='right' bgcolor='" + cor + "'>" + fala + "</td>"
				: "<td align='left' bgcolor='" + cor + "'>" + fala + "</td>";
		String html = "<table width='100%' cellspacing='2' cellpadding='5'><tr>"
				+ celula + "<td width='6'></td></tr></table>";
		HTMLEditorKit kit = (HTMLEditorKit) areaConversa.getEditorKit();
		javax.swing.text.html.HTMLDocument doc =
				(javax.swing.text.html.HTMLDocument) areaConversa.getDocument();
		try {
			kit.insertHTML(doc, doc.getLength(), html, 0, 0, null);
		} catch (BadLocationException | IOException e) {
			// apresentacao nunca derruba o chat
			e.printStackTrace();
		}
		areaConversa.setCaretPosition(doc.getLength());
	}

	/**
	 * §6.2: regua visual de fim de turno - depois da resposta da KAIRA.
	 * O cursor vai pro fim junto (auto-scroll por turno).
	 */
	private static void fimDeTurno() {
		HTMLEditorKit kit = (HTMLEditorKit) areaConversa.getEditorKit();
		javax.swing.text.html.HTMLDocument doc =
				(javax.swing.text.html.HTMLDocument) areaConversa.getDocument();
		try {
			kit.insertHTML(doc, doc.getLength(), "<hr>", 0, 0, null);
		} catch (BadLocationException | IOException e) {
			// apresentacao nunca derruba o chat
			e.printStackTrace();
		}
		areaConversa.setCaretPosition(doc.getLength());
	}

	private static String escapa(String texto) {
		return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	public static void MandarFrase(){
		falaUsuario = textAreaUsuario.getText();
		textAreaUsuario.setText("");
		// #49 - o roteamento saiu daqui (morre o sequestro do turno): o
		// pipeline julga. A flag vira so brilho visual do botao.
		mudarNaoVerificar();
		try {
			FormulaResposta.decifrar(falaUsuario);
		} catch (Exception e1) {
			e1.printStackTrace();
		}
	}

	public static void recebeResp(String frase){
		resposta = frase;
	}

	public static void mudarVerificar(){
		// #45 - estado PRIMEIRO: verificarManter e o que muda o roteamento
		// do MandarFrase (turno seguinte vira manterResposta em vez de
		// decifrar). O botao e so aviso visual. A ordem antiga - botao
		// antes - derrubava em tela nao montada e PERDIA a frase do
		// usuario, porque enviarResposta chamava isto antes de gravar o
		// bufferFrases. Ponto: a UI nunca pode ser pre-condicao do dado.
		verificarManter=true;
		if (btnSinalizar != null)
			btnSinalizar.setForeground(Tema.NEON);
	}

	public static void mudarCriar(){
		btnSinalizar.setForeground(Color.RED);
		verificarCriarResp=true;
	}

	// #49 - desarma o ensino na UI (o brilho do botao acompanha o estado);
	// o roteamento nao mora mais aqui - quem julga e o pipeline
	public static void mudarNaoVerificar() {
		verificarManter = false;
		if (btnSinalizar != null)
			btnSinalizar.setForeground(Tema.NEON_APAGADO);
	}

}
