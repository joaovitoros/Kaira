package com.JCODE.view;

import com.JCODE.controller.ControleFace;
import com.JCODE.controller.ControleSessao;
import com.JCODE.util.Caminhos;
import com.JCODE.util.Registro;
import com.JCODE.util.Tema;

import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.Image;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class Face extends JFrame {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private JPanel contentPane;
	private JTextField textFieldKairaNome;
	private JLabel lblAvatar;
	private ImageIcon avatarConversa;
	private ImageIcon avatarIdle;
	private ImageIcon avatarBaixa;
	private ImageIcon avatarAlta;
	private Timer timerIdle;

	/** Milissemundos sem interacao ate o avatar trocar pra imagem ociosa. */
	private static final int IDLE_MS = 30_000;
	/** Lado maximo do avatar escalonado (cabe no canto sem cobrir o campo). */
	private static final int AVATAR_MAX = 88;

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
		contentPane.setBackground(Tema.FUNDO);
		
		textFieldKairaNome = new JTextField(nome +":");
		textFieldKairaNome.setEnabled(false);
		textFieldKairaNome.setBounds(10, 11, 86, 20);
		contentPane.add(textFieldKairaNome);
		textFieldKairaNome.setColumns(10);
		textFieldKairaNome.setBackground(Tema.CAMPO);
		textFieldKairaNome.setForeground(Tema.NEON);
		textFieldKairaNome.setDisabledTextColor(Tema.NEON_APAGADO);
		textFieldKairaNome.setBorder(BorderFactory.createLineBorder(Tema.VERDE_ESCURO));
		
		JTextPane areaConversa = new JTextPane();
		areaConversa.setEditable(false);
		// #31: o HTML do chat pinta por cima; FUNDO/TEXTO valem onde o HTML nao chega
		areaConversa.setBackground(Tema.FUNDO);
		areaConversa.setForeground(Tema.TEXTO);

		JScrollPane scrollKaira = new JScrollPane(areaConversa);
		scrollKaira.setBorder(BorderFactory.createLineBorder(Tema.VERDE_ESCURO));
		scrollKaira.getViewport().setBackground(Tema.FUNDO);
		scrollKaira.setBounds(10, 42, 554, 255);
		contentPane.add(scrollKaira);
		
		JTextArea textAreaUsuario = new JTextArea();
		textAreaUsuario.setWrapStyleWord(true);
		textAreaUsuario.setLineWrap(true);
		// #30: 554 -> 456; a faixa da direita (474..562) fica pro avatar
		textAreaUsuario.setBounds(10, 317, 456, 187);
		contentPane.add(textAreaUsuario);
		textAreaUsuario.setBackground(Tema.CAMPO);
		textAreaUsuario.setForeground(Tema.TEXTO);
		textAreaUsuario.setCaretColor(Tema.NEON);
		textAreaUsuario.setSelectionColor(Tema.VERDE_ESCURO);
		textAreaUsuario.setSelectedTextColor(Tema.TEXTO);
		textAreaUsuario.setBorder(BorderFactory.createLineBorder(Tema.VERDE_ESCURO));

		lblAvatar = new JLabel();
		lblAvatar.setBounds(474, 450, 88, 88);
		lblAvatar.setHorizontalAlignment(SwingConstants.CENTER);
		lblAvatar.setVerticalAlignment(SwingConstants.CENTER);
		contentPane.add(lblAvatar);

		avatarConversa = carregaAvatar("kaira_conversa.png");
		avatarIdle = carregaAvatar("kaira_idle.png");
		// #34 - variantes por faixa de humor; ausentes caem na conversa
		avatarBaixa = carregaAvatar("kaira_baixa.png");
		avatarAlta = carregaAvatar("kaira_alta.png");
		if (avatarConversa != null)
			lblAvatar.setIcon(avatarConversa);

		// ocioso apos 30s sem interacao; qualquer acao do usuario volta pro estado conversando
		timerIdle = new Timer(IDLE_MS, e -> {
			if (avatarIdle != null)
				lblAvatar.setIcon(avatarIdle);
		});
		timerIdle.setRepeats(false);
		timerIdle.start();

		JButton btnEnviar = new JButton("Enviar");
		btnEnviar.setBounds(10, 515, 106, 25);
		contentPane.add(btnEnviar);
		temaBotao(btnEnviar);

		JButton btnSinalizar = new JButton("!");
		btnSinalizar.setBounds(134, 10, 44, 23);
		contentPane.add(btnSinalizar);
		temaBotao(btnSinalizar);
		// desarmado; o ControleFace troca pra NEON (manter) e vermelho (criar)
		btnSinalizar.setForeground(Tema.NEON_APAGADO);

		// #29 - tela de log ao vivo (instancia unica via TelaLog.abrir)
		JButton btnLog = new JButton("Log");
		btnLog.setBounds(460, 10, 104, 23);
		contentPane.add(btnLog);
		temaBotao(btnLog);
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
		btnEnviar.addActionListener(e -> reativaConversa());

		textAreaUsuario.addKeyListener(new KeyAdapter() {
			@Override
			public void keyPressed(java.awt.event.KeyEvent evt) {
				reativaConversa();
				if (evt.getKeyCode()== KeyEvent.VK_ENTER) {
					btnEnviar.doClick();
				}
			}
		});
	}

	/**
	 * Botao chato do LAF nativo ignora setBackground; o trio abaixo força
	 * o fundo escuro do tema: sem fill nativo, opaco e borda verde escura.
	 */
	private static void temaBotao(JButton botao) {
		botao.setContentAreaFilled(false);
		botao.setOpaque(true);
		botao.setBackground(Tema.CAMPO);
		botao.setForeground(Tema.NEON);
		botao.setBorder(BorderFactory.createLineBorder(Tema.VERDE_ESCURO));
		botao.setFocusPainted(false);
	}

	/**
	 * Qualquer interacao devolve o avatar ao estado ativo escolhendo a
	 * expressao pela faixa de humor da sessao (#34) e reinicia o relogio
	 * da ociosidade.
	 */
	private void reativaConversa() {
		ImageIcon icone = avatarPorHumor();
		if (icone != null)
			lblAvatar.setIcon(icone);
		if (timerIdle != null)
			timerIdle.restart();
	}

	/**
	 * #34 - 0..3 cara fechada, 4..6 a conversa (padrao, e onde a janela
	 * abre), 7..10 animada. Variante ausente cai na conversa: nunca zera
	 * o icone por falta de arquivo.
	 */
	private ImageIcon avatarPorHumor() {
		int humor = ControleSessao.get().getHumor();
		if (humor <= 3 && avatarBaixa != null)
			return avatarBaixa;
		if (humor >= 7 && avatarAlta != null)
			return avatarAlta;
		return avatarConversa;
	}

	/**
	 * Carrega um PNG do diretorio de avatar (sempre via Caminhos.recurso)
	 * escalado pra caber no canto. Qualquer problema (arquivo ausente, I/O,
	 * formato) vira null + linha no log: a janela abre normalmente so sem o
	 * avatar - nunca quebra a tela.
	 */
	private static ImageIcon carregaAvatar(String nome) {
		try {
			File arquivo = Caminhos.recurso("avatar/" + nome).toFile();
			if (!arquivo.exists()) {
				Registro.linha("AVATAR    : ausente " + arquivo.getPath() + " - seguindo sem");
				return null;
			}
			BufferedImage imagem = ImageIO.read(arquivo);
			if (imagem == null) {
				Registro.linha("AVATAR    : ilegivel " + arquivo.getPath() + " - seguindo sem");
				return null;
			}
			double escala = Math.min((double) AVATAR_MAX / imagem.getWidth(),
					(double) AVATAR_MAX / imagem.getHeight());
			int largura = Math.max(1, (int) Math.round(imagem.getWidth() * escala));
			int altura = Math.max(1, (int) Math.round(imagem.getHeight() * escala));
			Image redimencionada = imagem.getScaledInstance(largura, altura, Image.SCALE_SMOOTH);
			Registro.linha("AVATAR    : carregado " + nome + " ("
					+ imagem.getWidth() + "x" + imagem.getHeight()
					+ " -> " + largura + "x" + altura + ")");
			return new ImageIcon(redimencionada);
		} catch (IOException e) {
			Registro.linha("AVATAR    : falha ao carregar " + nome + " (" + e.getMessage() + ")");
			return null;
		}
	}
}
