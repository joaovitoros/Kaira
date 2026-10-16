package com.JCODE.util;

import java.awt.Color;

/**
 * #31 - paleta KAIRA: preto e verde neon, a identidade visual das telas.
 *
 * Fonte unica do tema: Face, TelaLog e ControleFace importam daqui. Mudou
 * uma cor, muda o app inteiro - nenhum hex solto no fonte (alem deste).
 * Cores sao RGB puro; hex() entrega "#rrggbb" pro HTML dos baloes.
 */
public final class Tema {

	/** Preto esverdeado - fundo geral das janelas. */
	public static final Color FUNDO = new Color(0x0A0E0C);

	/** Um degrau acima do fundo - campos de texto e botoes. */
	public static final Color CAMPO = new Color(0x121813);

	/** Quase preto - miolo da conversa e do log. */
	public static final Color PROFUNDO = new Color(0x050705);

	/** Verde neon - destaque, rotulo dos botoes, ``!`` armado (manter). */
	public static final Color NEON = new Color(0x39FF6C);

	/** Verde apagado - ``!`` desarmado (estado normal do botao). */
	public static final Color NEON_APAGADO = new Color(0x7F9384);

	/** Verde claro - texto corrido sobre o preto. */
	public static final Color TEXTO = new Color(0xB7FFC6);

	/** Verde escuro - bordas, selecao de texto e regua de fim de turno. */
	public static final Color VERDE_ESCURO = new Color(0x14532D);

	/** Balao do usuario (direita) - verde escuro vivo. */
	public static final Color BALAO_USUARIO = new Color(0x0F5132);

	/** Balao da KAIRA (esquerda) - superficie escura. */
	public static final Color BALAO_KAIRA = new Color(0x151B16);

	/** "#rrggbb" pras atributos bgcolor/color do HTML do chat. */
	public static String hex(Color cor) {
		return String.format("#%06X", cor.getRGB() & 0xFFFFFF);
	}

	private Tema() {
	}
}
