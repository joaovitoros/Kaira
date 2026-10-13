package com.JCODE.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class Sinonimo {

    @JsonProperty("palavra")
    private String palavra;
    @JsonProperty("sinonimos")
    private List<String> sinonimos;

    public String getPalavra() {
        return palavra;
    }

    public void setPalavra(String palavra) {
        this.palavra = palavra;
    }

    public List<String> getSinonimos() {
        return sinonimos;
    }

    public void setSinonimos(List<String> sinonimos) {
        this.sinonimos = sinonimos;
    }

    public Sinonimo(String palavra, List<String> sinonimos) {
        this.palavra = palavra;
        this.sinonimos = sinonimos;
    }

    public Sinonimo(){

    }
}
