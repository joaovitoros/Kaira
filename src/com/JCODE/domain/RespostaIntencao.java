package com.JCODE.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public class RespostaIntencao {

    @JsonProperty("texto")
    private String texto;
    @JsonProperty("origem")
    private String origem;
    @JsonProperty("humorMin")
    private int humorMin;
    @JsonProperty("humorMax")
    private int humorMax;

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

    public int getHumorMin() {
        return humorMin;
    }

    public void setHumorMin(int humorMin) {
        this.humorMin = humorMin;
    }

    public int getHumorMax() {
        return humorMax;
    }

    public void setHumorMax(int humorMax) {
        this.humorMax = humorMax;
    }

    public RespostaIntencao() {
    }

    public RespostaIntencao(String texto, String origem, int humorMin, int humorMax) {
        this.texto = texto;
        this.origem = origem;
        this.humorMin = humorMin;
        this.humorMax = humorMax;
    }
}
