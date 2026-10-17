package com.JCODE.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * #37 (C3 piloto) - uma entrada do dicionario de conhecimentos.
 *
 * termo = nome de intent da base (a chave de lookup = o valor de
 * topicoAtual); resumo = frase curta que a KAIRA fala em "conta mais";
 * texto = versao maior (pro futuro "explica melhor"); origem = fonte +
 * licenca (Wikipedia PT CC BY-SA 3.0, texto adaptado).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Conhecimento {

    @JsonProperty("termo")
    private String termo;
    @JsonProperty("resumo")
    private String resumo;
    @JsonProperty("texto")
    private String texto;
    @JsonProperty("origem")
    private String origem;

    public Conhecimento() {
    }

    public Conhecimento(String termo, String resumo, String texto, String origem) {
        this.termo = termo;
        this.resumo = resumo;
        this.texto = texto;
        this.origem = origem;
    }

    public String getTermo() {
        return termo;
    }

    public void setTermo(String termo) {
        this.termo = termo;
    }

    public String getResumo() {
        return resumo;
    }

    public void setResumo(String resumo) {
        this.resumo = resumo;
    }

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
}
