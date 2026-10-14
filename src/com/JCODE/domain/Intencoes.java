package com.JCODE.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Intencoes {

    @JsonProperty("intencoes")
    private List<Intencao> intencoes;

    public List<Intencao> getIntencoes() {
        return intencoes;
    }

    public void setIntencoes(List<Intencao> intencoes) {
        this.intencoes = intencoes;
    }

    public Intencoes() {
    }

    public Intencoes(List<Intencao> intencoes) {
        this.intencoes = intencoes;
    }
}
