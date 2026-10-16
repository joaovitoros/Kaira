package com.JCODE.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Sessao {

    public static final int MAX_HISTORICO = 20;

    private String nomeUsuario;
    private String topicoAtual;
    private int turnos;
    private int humor = 5;
    private List<Turno> historico = new ArrayList<>();
    private Map<String, String> fatos = new LinkedHashMap<>();

    public String getNomeUsuario() {
        return nomeUsuario;
    }

    public void setNomeUsuario(String nomeUsuario) {
        this.nomeUsuario = nomeUsuario;
    }

    public String getTopicoAtual() {
        return topicoAtual;
    }

    public void setTopicoAtual(String topicoAtual) {
        this.topicoAtual = topicoAtual;
    }

    public int getTurnos() {
        return turnos;
    }

    public void setTurnos(int turnos) {
        this.turnos = turnos;
    }

    public int getHumor() {
        return humor;
    }

    public void setHumor(int humor) {
        if (humor < 0)
            humor = 0;
        if (humor > 10)
            humor = 10;
        this.humor = humor;
    }

    public List<Turno> getHistorico() {
        return historico;
    }

    public Map<String, String> getFatos() {
        return fatos;
    }

    public void anota(Turno t) {
        historico.add(t);
        while (historico.size() > MAX_HISTORICO)
            historico.remove(0);
    }
}