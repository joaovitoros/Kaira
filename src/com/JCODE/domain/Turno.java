package com.JCODE.domain;

public class Turno {

    private String autor;
    private String texto;

    /** Construtor vazio so pro Jackson (leitura do sessao.json). */
    public Turno() {
    }

    public Turno(String autor, String texto) {
        this.autor = autor;
        this.texto = texto;
    }

    public String getAutor() {
        return autor;
    }

    public String getTexto() {
        return texto;
    }
}