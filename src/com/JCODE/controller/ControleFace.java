package com.JCODE.controller;

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
	static void exibirResposta(String texto) {
		if(texto==null)
			return;
		balao(false, texto);
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
		String cor = doUsuario ? "#d1e7dd" : "#e2e3e5";
		String celula = doUsuario
				? "<td align='right' bgcolor='" + cor + "'>" + escapa(texto) + "</td>"
				: "<td align='left' bgcolor='" + cor + "'>" + escapa(texto) + "</td>";
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

	private static String escapa(String texto) {
		return texto.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	public static void MandarFrase(){
		falaUsuario = textAreaUsuario.getText();
		textAreaUsuario.setText("");
		if(verificarManter){
			verificarManter=false;
			btnSinalizar.setForeground(Color.BLACK);
			FormulaResposta.manterResposta(falaUsuario.trim());
		}else if(verificarCriarResp){
//			verificarCriarResp=false;
//			btnSinalizar.setForeground(Color.BLACK);
//			FormulaResposta.manterCriacao(falaUsuario.trim());
		}else{
			try {
				FormulaResposta.decifrar(falaUsuario);
			} catch (Exception e1) {
				e1.printStackTrace();
			}
		}
	}

	public static void recebeResp(String frase){
		resposta = frase;
	}

	public static void mudarVerificar(){
		btnSinalizar.setForeground(Color.GREEN);
		verificarManter=true;
	}

	public static void mudarCriar(){
		btnSinalizar.setForeground(Color.RED);
		verificarCriarResp=true;
	}

}
