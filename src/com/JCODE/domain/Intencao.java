package com.JCODE.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class Intencao {

    @JsonProperty("intento")
    private String intento;
    @JsonProperty("exemplos")
    private List<Exemplo> exemplos;
    @JsonProperty("palavrasChave")
    private List<String> palavrasChave;
    @JsonProperty("respostas")
    private List<RespostaIntencao> respostas;
    @JsonProperty("followups")
    private List<String> followups;
    @JsonProperty("acoes")
    private List<String> acoes;

    public String getIntento() {
        return intento;
    }

    public void setIntento(String intento) {
        this.intento = intento;
    }

    public List<Exemplo> getExemplos() {
        return exemplos;
    }

    public void setExemplos(List<Exemplo> exemplos) {
        this.exemplos = exemplos;
    }

    public List<String> getPalavrasChave() {
        return palavrasChave;
    }

    public void setPalavrasChave(List<String> palavrasChave) {
        this.palavrasChave = palavrasChave;
    }

    public List<RespostaIntencao> getRespostas() {
        return respostas;
    }

    public void setRespostas(List<RespostaIntencao> respostas) {
        this.respostas = respostas;
    }

    public List<String> getFollowups() {
        return followups;
    }

    public void setFollowups(List<String> followups) {
        this.followups = followups;
    }

    public List<String> getAcoes() {
        return acoes;
    }

    public void setAcoes(List<String> acoes) {
        this.acoes = acoes;
    }

    public Intencao() {
    }

    public Intencao(String intento, List<Exemplo> exemplos, List<String> palavrasChave, List<RespostaIntencao> respostas, List<String> followups, List<String> acoes) {
        this.intento = intento;
        this.exemplos = exemplos;
        this.palavrasChave = palavrasChave;
        this.respostas = respostas;
        this.followups = followups;
        this.acoes = acoes;
    }

}
