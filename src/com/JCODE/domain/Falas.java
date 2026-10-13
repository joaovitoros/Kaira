package com.JCODE.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class Falas {

    @JsonProperty("falas")
    private List<Resposta> falas;

    public List<Resposta> getFalas() {
        return falas;
    }

    public void setFalas(List<Resposta> falas) {
        this.falas = falas;
    }

    public Falas(List<Resposta> falas) {
        this.falas = falas;
    }

    public Falas() {
    }
}
