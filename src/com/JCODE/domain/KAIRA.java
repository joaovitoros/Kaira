package com.JCODE.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class KAIRA {

    @JsonProperty("nome")
    private String nome;

    @JsonProperty("versao")
    private String versao;

    @JsonProperty("fatos")
    private Map<String, String> fatos;

    @JsonProperty("molduras")
    private Map<String, String> molduras;

    public Map<String, String> getFatos() { return fatos; }
    public void setFatos(Map<String, String> fatos) { this.fatos = fatos; }
    public Map<String, String> getMolduras() { return molduras; }
    public void setMolduras(Map<String, String> molduras) { this.molduras = molduras; }

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
