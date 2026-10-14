package com.JCODE.controller;

import com.JCODE.domain.GruposSinonimos;
import com.JCODE.util.Caminhos;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ControleSinonimo {

    static ObjectMapper objectMapper;

    private static Map<String, Set<String>> indice;

    private static GruposSinonimos buscaArquivoSinonimo(){

        try {
            FileInputStream fileInputStream = new FileInputStream(Caminhos.recurso("grupos_sinonimos.json").toFile());

            InputStreamReader reader = new InputStreamReader(fileInputStream, StandardCharsets.UTF_8);

            return objectMapper.readValue(reader, GruposSinonimos.class);
        }catch (Exception e){
            if(e instanceof FileNotFoundException)

                return null;
        }

        return null;
    }

    public static void carregaCache() {

        if (objectMapper == null)
            objectMapper = new ObjectMapper();

        indice = new HashMap<>();

        for(List<String> sinonimos: Objects.requireNonNull(buscaArquivoSinonimo()).getGrupos()){
            for(String palavra: sinonimos){
                indice.computeIfAbsent(palavra, k -> new HashSet<>()).addAll(sinonimos);
            }
        }
    }

    public static Boolean mesmaPalavra(String a, String b){

        if(indice == null)
            return a.equalsIgnoreCase(b);

        Set<String> vizinhos = indice.getOrDefault(a.toLowerCase(), Set.of());

        return a.equalsIgnoreCase(b) || vizinhos.contains(b.toLowerCase());
    }

}
