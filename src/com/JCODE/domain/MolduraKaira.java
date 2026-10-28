package com.JCODE.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/** #56 - a moldura de um fato por relacao (gosta/nao_gosta, com {v}). */
public class MolduraKaira {

    @JsonProperty("gosta")
    private List<String> gosta = new ArrayList<>();

    @JsonProperty("nao_gosta")
    private List<String> naoGosta = new ArrayList<>();

    public List<String> getGosta() { return gosta; }
    public void setGosta(List<String> gosta) { this.gosta = gosta; }

    public List<String> getNaoGosta() { return naoGosta; }
    public void setNaoGosta(List<String> naoGosta) { this.naoGosta = naoGosta; }
}