package com.JCODE.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class Sinonimos {

    @JsonProperty("sinonimos")
    List<Sinonimo> sinonimos;

    public List<Sinonimo> getSinonimos() {
        return sinonimos;
    }

    public void setSinonimos(List<Sinonimo> sinonimos) {
        this.sinonimos = sinonimos;
    }

    public Sinonimos(List<Sinonimo> sinonimos) {
        this.sinonimos = sinonimos;
    }

    public Sinonimos() {
    }
}
