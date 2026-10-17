package com.JCODE.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** #37 (C3 piloto) - raiz do conhecimentos.json (padrao Intencoes). */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Conhecimentos {

    @JsonProperty("conhecimentos")
    private List<Conhecimento> conhecimentos;

    public Conhecimentos() {
    }

    public Conhecimentos(List<Conhecimento> conhecimentos) {
        this.conhecimentos = conhecimentos;
    }

    public List<Conhecimento> getConhecimentos() {
        return conhecimentos;
    }

    public void setConhecimentos(List<Conhecimento> conhecimentos) {
        this.conhecimentos = conhecimentos;
    }
}
