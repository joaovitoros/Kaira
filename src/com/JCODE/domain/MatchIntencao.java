package com.JCODE.domain;

public class MatchIntencao {

    private String frase;
    private Intencao intencao;
    private double score;

    public MatchIntencao(String frase, Intencao intencao, double score) {
        this.frase = frase;
        this.intencao = intencao;
        this.score = score;
    }

    public String getFrase() {
        return frase;
    }

    public Intencao getIntencao() {
        return intencao;
    }

    public double getScore() {
        return score;
    }
}