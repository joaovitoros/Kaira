package com.JCODE.view;

import com.JCODE.util.Registro;
import com.JCODE.util.Tema;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * #29 - tela de log ao vivo: o conversas.log crescendo em tempo real.
 *
 * Le o rodape por offset de bytes a cada 1s (Timer do Swing) e so
 * decodifica ate o ultimo \n do pedaco novo - nunca corta um char
 * UTF-8 no meio (0x0A so existe como fim de linha). Carga inicial com
 * as ultimas 2000 linhas: o arquivo tem dezenas de milhares.
 * Contrato Registro: nunca derruba o app.
 */
public class TelaLog extends JFrame {

	private static TelaLog aberta;

	private final JTextArea area = new JTextArea();
	private final JCheckBox autoScroll = new JCheckBox("Auto-scroll", true);
	private final Timer timer;
	private long offset;

	/** Instancia unica: abrir de novo so traz a janela pra frente. */
	public static void abrir() {
		if (aberta == null || !aberta.isDisplayable())
			aberta = new TelaLog();
		aberta.setVisible(true);
		aberta.toFront();
	}

	private TelaLog() {
		setTitle("K.A.I.R.A - log ao vivo");
		setBounds(150, 150, 780, 540);
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

		JPanel painel = new JPanel(new BorderLayout(5, 5));
		painel.setBorder(new EmptyBorder(5, 5, 5, 5));
		painel.setBackground(Tema.FUNDO);

		// #31: log no preto esverdeado, texto verde claro (mesma paleta da Face)
		area.setEditable(false);
		area.setBackground(Tema.PROFUNDO);
		area.setForeground(Tema.TEXTO);
		area.setSelectionColor(Tema.VERDE_ESCURO);
		area.setSelectedTextColor(Tema.TEXTO);
		JScrollPane rolagem = new JScrollPane(area);
		rolagem.setBorder(BorderFactory.createLineBorder(Tema.VERDE_ESCURO));
		rolagem.getViewport().setBackground(Tema.PROFUNDO);
		painel.add(rolagem, BorderLayout.CENTER);

		JButton limpar = new JButton("Limpar visualizacao");
		JButton recarregar = new JButton("Recarregar");
		temaBotao(limpar);
		temaBotao(recarregar);
		JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
		barra.setBackground(Tema.FUNDO);
		autoScroll.setOpaque(true);
		autoScroll.setBackground(Tema.FUNDO);
		autoScroll.setForeground(Tema.NEON);
		barra.add(autoScroll);
		barra.add(limpar);
		barra.add(recarregar);
		painel.add(barra, BorderLayout.SOUTH);
		setContentPane(painel);

		carregaInicial();

		timer = new Timer(1000, e -> atualiza());
		timer.start();

		// limpar = so a visao (e segura o auto-scroll pra inspecionar);
		// o arquivo continua crescendo daqui
		limpar.addActionListener(e -> {
			area.setText("");
			autoScroll.setSelected(false);
		});
		recarregar.addActionListener(e -> carregaInicial());

		addWindowListener(new WindowAdapter() {
			@Override
			public void windowClosing(WindowEvent e) {
				timer.stop();
			}
		});
	}

	/** Mesma forcacao de botao flat da Face: LAF nativo ignora setBackground. */
	private static void temaBotao(JButton botao) {
		botao.setContentAreaFilled(false);
		botao.setOpaque(true);
		botao.setBackground(Tema.CAMPO);
		botao.setForeground(Tema.NEON);
		botao.setBorder(BorderFactory.createLineBorder(Tema.VERDE_ESCURO));
		botao.setFocusPainted(false);
	}

	private Path arquivo() {
		// #36 - fonte unica: mesma regra do Registro (janela real = conversas.log)
		return Registro.arquivo();
	}

	/** Ultimas 2000 linhas; offset = fim do arquivo em bytes. */
	private void carregaInicial() {
		try {
			byte[] bytes = Files.readAllBytes(arquivo());
			String texto = new String(bytes, StandardCharsets.UTF_8);
			int corte = ultimasLinhas(texto, 2000);
			area.setText(texto.substring(corte));
			area.setCaretPosition(area.getDocument().getLength());
			offset = bytes.length;
		} catch (Exception e) {
			area.setText("(conversas.log ainda nao existe)");
			offset = 0;
		}
	}

	/** Indice do char onde comecam as ultimas `quantas` linhas. */
	private static int ultimasLinhas(String texto, int quantas) {
		int vistos = 0;
		for (int i = texto.length() - 1; i >= 0; i--) {
			if (texto.charAt(i) == '\n' && ++vistos == quantas)
				return i + 1;
		}
		return 0;
	}

	private void atualiza() {
		try {
			byte[] bytes = Files.readAllBytes(arquivo());
			if (bytes.length < offset) { // arquivo recriado do zero
				carregaInicial();
				return;
			}
			if (bytes.length == offset)
				return;

			// do offset ate o ultimo \n do pedaco novo: corte em
			// borda de byte segura pra UTF-8
			int fim = bytes.length - 1;
			while (fim >= offset && bytes[fim] != '\n')
				fim--;
			if (fim < offset)
				return; // linha ainda incompleta, espera o proximo tick

			String novo = new String(bytes, (int) offset, fim + 1 - (int) offset,
					StandardCharsets.UTF_8);
			area.append(novo);
			offset = fim + 1L;
			if (autoScroll.isSelected())
				area.setCaretPosition(area.getDocument().getLength());
		} catch (Exception ignorada) {
			// mesma regra do Registro: o log nunca derruba o app
		}
	}
}
