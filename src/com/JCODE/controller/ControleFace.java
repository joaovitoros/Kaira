package com.JCODE.controller;

import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JTextArea;

public class ControleFace implements ActionListener{
	static JTextArea textAreaKaira;
	static JTextArea textAreaUsuario;
	static JButton btnSinalizar;
	static String resposta;
	static String falaUsuario;
	static boolean verificarManter;
	static boolean verificarCriarResp;
	public ControleFace(JTextArea textAreaKaira1, JTextArea textAreaUsuario1, JButton btnSinalizar) {
		ControleFace.textAreaKaira = textAreaKaira1;
		ControleFace.textAreaUsuario = textAreaUsuario1;
		ControleFace.btnSinalizar = btnSinalizar;
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		String cmd = e.getActionCommand();
		
		if(cmd.equalsIgnoreCase("Enviar")){
			MandarFrase();
			exibirResposta(resposta);
		}

	}

	/**
	 * Acrescenta a resposta ao histórico do chat, sem sobrescrever as anteriores.
	 * A resposta nula (ainda sem match) e ignorada para nao poluir a conversa.
	 */
	static void exibirResposta(String texto) {
		if(texto==null)
			return;
		textAreaKaira.append(texto + "\n");
		textAreaKaira.setCaretPosition(textAreaKaira.getDocument().getLength());
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
