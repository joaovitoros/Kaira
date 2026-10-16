package com.JCODE.view;

import com.JCODE.controller.ControleFace;
import com.JCODE.controller.ControleSessao;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.Color;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;

public class Face extends JFrame {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private JPanel contentPane;
	private JTextField textFieldKairaNome;

	/**
	 * Create the frame.
	 * @param nome 
	 */
	public Face(String nome) {
		setTitle("K.A.I.R.A");
		setResizable(false);
		
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setBounds(100, 100, 580, 580);
		contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		setContentPane(contentPane);
		contentPane.setLayout(null);
		
		textFieldKairaNome = new JTextField(nome +":");
		textFieldKairaNome.setEnabled(false);
		textFieldKairaNome.setBounds(10, 11, 86, 20);
		contentPane.add(textFieldKairaNome);
		textFieldKairaNome.setColumns(10);
		
		JTextPane areaConversa = new JTextPane();
		areaConversa.setEditable(false);
		areaConversa.setBackground(Color.WHITE);

		JScrollPane scrollKaira = new JScrollPane(areaConversa);
		scrollKaira.setBounds(10, 42, 554, 255);
		contentPane.add(scrollKaira);
		
		JTextArea textAreaUsuario = new JTextArea();
		textAreaUsuario.setWrapStyleWord(true);
		textAreaUsuario.setLineWrap(true);
		textAreaUsuario.setBounds(10, 317, 554, 187);
		contentPane.add(textAreaUsuario);
		
		
		
		JButton btnEnviar = new JButton("Enviar");
		btnEnviar.setBounds(10, 515, 106, 25);
		contentPane.add(btnEnviar);

		JButton btnSinalizar = new JButton("!");
		btnSinalizar.setBounds(134, 10, 44, 23);
		contentPane.add(btnSinalizar);

		// #29 - tela de log ao vivo (instancia unica via TelaLog.abrir)
		JButton btnLog = new JButton("Log");
		btnLog.setBounds(460, 10, 104, 23);
		contentPane.add(btnLog);
		btnLog.addActionListener(e -> TelaLog.abrir());
		
		ControleFace contface = new ControleFace(areaConversa, textAreaUsuario, btnSinalizar);

		String bemVindo = ControleSessao.boasVindas();
		if (bemVindo != null)
			ControleFace.exibirResposta(bemVindo);

		addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowClosing(java.awt.event.WindowEvent e) {
				ControleSessao.gravaSessao();
			}
		});
		
		btnEnviar.addActionListener(contface);
		
		textAreaUsuario.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(java.awt.event.KeyEvent evt) {
				if (evt.getKeyCode()== KeyEvent.VK_ENTER) {
					btnEnviar.doClick();
				}
			}
		});
	}
}
