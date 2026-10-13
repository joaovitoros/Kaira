package com.JCODE.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class Resposta {

    @JsonProperty("frase")
    private String frase;
    @JsonProperty("respostas")
    private List<String> respostas;
    @JsonProperty("assunto")
    private String assunto;

    public String getFrase() {
        return frase;
    }

    public void setFrase(String frase) {
        this.frase = frase;
    }

    public List<String> getRespostas() {
        return respostas;
    }

    public void setRespostas(List<String> respostas) {
        this.respostas = respostas;
    }

    public String getAssunto() {
        return assunto;
    }

    public void setAssunto(String assunto) {
        this.assunto = assunto;
    }

    public Resposta(String frase, List<String> respostas, String tipo) {
        this.frase = frase;
        this.respostas = respostas;
        this.assunto = tipo;
    }

    public Resposta() {
    }
}
