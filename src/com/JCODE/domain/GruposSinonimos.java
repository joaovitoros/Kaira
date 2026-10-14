package com.JCODE.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class GruposSinonimos {

    @JsonProperty("grupos")
    List<List<String>> grupos;

    public GruposSinonimos() {
    }
    public List<List<String>> getGrupos() {
        return grupos;
    }

    public void setGrupos(List<List<String>> grupos) {
        this.grupos = grupos;
    }
}
