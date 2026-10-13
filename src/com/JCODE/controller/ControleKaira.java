package com.JCODE.controller;

import com.JCODE.domain.KAIRA;
import com.JCODE.util.Caminhos;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class ControleKaira {

    static ObjectMapper objectMapper;

    static KAIRA kairaCache;

    public static KAIRA carregaCacheKAIRA() throws IOException {

        if(objectMapper == null)
            objectMapper = new ObjectMapper();

        FileInputStream fileInputStream = new FileInputStream(Caminhos.recurso("kaira.json").toFile());

        InputStreamReader reader = new InputStreamReader(fileInputStream, StandardCharsets.UTF_8);

        KAIRA kaira = objectMapper.readValue(reader, KAIRA.class);

        kairaCache = kaira;

        return kaira;
    }

    public static KAIRA criarCacheKAIRA(){

        criarCaminho();

        if(objectMapper==null)
            objectMapper = new ObjectMapper();

        KAIRA kaira = new KAIRA();

        kaira.setNome("K.A.I.R.A");
        kaira.setVersao("0.0.1");

        try (FileWriter fileWriter = new FileWriter(Caminhos.recurso("kaira.json").toFile(), StandardCharsets.UTF_8)) {

            String jsonInicial = objectMapper.writeValueAsString(kaira);

            fileWriter.write(jsonInicial);
            System.out.println("Arquivo JSON escrito com sucesso! " +jsonInicial);
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null;
    }

    public static KAIRA buscaDadosGerais(){
        if(kairaCache ==null) {
            try {
                kairaCache = carregaCacheKAIRA();
            }catch (Exception e){
                e.printStackTrace();
            }
        }

        return kairaCache;
    }

    public static void criarCaminho(){
        try {
            Files.createDirectories(Caminhos.base().resolve("KAIRA").resolve("resources"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
