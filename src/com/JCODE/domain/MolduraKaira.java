package com.JCODE.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

/** #56 - a moldura de um fato por relacao (gosta/nao_gosta, com {v}). */
public class MolduraKaira {

    @JsonProperty("gosta")
    private String gosta;

    @JsonProperty("nao_gosta")
    private String naoGosta;

    public String getGosta() { return gosta; }
    public void setGosta(String gosta) { this.gosta = gosta; }

    public String getNaoGosta() { return naoGosta; }
    public void setNaoGosta(String naoGosta) { this.naoGosta = naoGosta; }
}