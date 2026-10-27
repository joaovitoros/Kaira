package com.JCODE.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.*;

public class Sessao {

    public static final int MAX_HISTORICO = 20;

    private String nomeUsuario;
    private String topicoAtual;
    private int turnos;
    private int humor = 5;
    private List<Turno> historico = new ArrayList<>();
    @JsonProperty("fatos")
    private Map<String, FatoSessao> fatos = new LinkedHashMap<>();

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

    /** #58 - a visao plana (compat): get = gosta[0], put = seta gosta. */
    @JsonIgnore
    public Map<String, String> getFatos() {
        return new AbstractMap<>() {
            @Override
            public Set<Entry<String, String>> entrySet() {
                Map<String, String> plano = new LinkedHashMap<>();
                for (Entry<String, FatoSessao> e : fatos.entrySet()) {
                    FatoSessao f = e.getValue();
                    if (f.getGosta() != null && !f.getGosta().isEmpty())
                        plano.put(e.getKey(), f.getGosta().get(0));
                }
                return plano.entrySet();
            }

            @Override
            public String get(Object chave) {
                FatoSessao f = fatos.get(chave);
                return (f == null || f.getGosta() == null || f.getGosta().isEmpty())
                        ? null : f.getGosta().get(0);
            }

            @Override
            public boolean containsKey(Object chave) {
                return fatos.containsKey(chave);
            }

            @Override
            public String put(String chave, String valor) {
                FatoSessao f = fatos.get(chave);
                if (f == null) {
                    f = new FatoSessao();
                    fatos.put(chave, f);
                }
                String antes = get(chave);
                List<String> gosta = new ArrayList<>();
                gosta.add(valor);
                f.setGosta(gosta);
                return antes;
            }

            @Override
            public boolean isEmpty() {
                return fatos.isEmpty();
            }

            @Override
            public int size() {
                return fatos.size();
            }
        };
    }

    /** #58 - o mapa com as relacoes (as rotas novas usam este). */
    @JsonIgnore
    public Map<String, FatoSessao> getFatosV2() {
        return fatos;
    }

    public void anota(Turno t) {
        historico.add(t);
        while (historico.size() > MAX_HISTORICO)
            historico.remove(0);
    }
}