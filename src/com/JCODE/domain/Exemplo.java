package com.JCODE.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Exemplo {

    @JsonProperty("texto")
    private String texto;
    @JsonProperty("origem")
    private String origem;


    public String getTexto() {
        return texto;
    }

    public void setTexto(String texto) {
        this.texto = texto;
    }

    public String getOrigem() {
        return origem;
    }

    public void setOrigem(String origem) {
        this.origem = origem;
    }

    public Exemplo() {
    }

    public Exemplo(String texto, String origem) {
        this.texto = texto;
        this.origem = origem;
    }
}
