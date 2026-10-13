package com.JCODE.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public class KAIRA {

    @JsonProperty("nome")
    private String nome;

    @JsonProperty("versao")
    private String versao;

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getVersao() {
        return versao;
    }

    public void setVersao(String versao) {
        this.versao = versao;
    }

    public KAIRA() {
    }

    public KAIRA(String nome, String versao) {
        this.nome = nome;
        this.versao = versao;
    }

}
