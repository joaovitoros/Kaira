package com.JCODE.controller;

import com.JCODE.domain.KAIRA;
import com.JCODE.util.Caminhos;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ControleKaira {

    static ObjectMapper objectMapper;

    static KAIRA kairaCache;

    // #50 - memoria pessoal dela: os fatos do kaira.json + as molduras que os vestem
    private static Map<String, String> cacheFatos = null;
    private static Map<String, String> cacheMolduras = null;

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

    public static void carregaFatos() {
        try {
            kairaCache = carregaCacheKAIRA();
        } catch (Exception e) {
            kairaCache = null;   // arquivo ausente: fatos vazios, sem crash
        }
        cacheFatos = (kairaCache != null && kairaCache.getFatos() != null)
                ? kairaCache.getFatos() : new HashMap<>();
        cacheMolduras = (kairaCache != null && kairaCache.getMolduras() != null)
                ? kairaCache.getMolduras() : new HashMap<>();
    }

    public static String buscaFato(String chave) {
        if (cacheFatos == null)
            carregaFatos();
        return chave == null ? null : cacheFatos.get(chave);
    }

    public static String molduraDe(String chave) {
        if (cacheMolduras == null)
            carregaFatos();
        return chave == null ? null : cacheMolduras.get(chave);
    }

    /** #50 - grava o fato dela: cache + arquivo (mesma tatica do ensinaEm no intencoes.json). */
    public static void gravaFato(String chave, String valor) {
        if (chave == null || chave.isBlank() || valor == null || valor.isBlank())
            return;
        if (cacheFatos == null)
            carregaFatos();
        cacheFatos.put(chave, valor);

        kairaCache.setFatos(cacheFatos);

        try (FileWriter fileWriter = new FileWriter(
                Caminhos.recurso("kaira.json").toFile(), StandardCharsets.UTF_8)) {

            String json = objectMapper.writeValueAsString(kairaCache);

            fileWriter.write(json);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** #50 - a forma de ensino dela: "sua CHAVE (preferida|favorito) ... e VALOR". */
    public static String[] extraiFatoDela(String falaNormalizada) {
        if (falaNormalizada == null)
            return null;
        Matcher m = Pattern.compile(
                        "\\b(?:sua|seu)\\s+([\\p{L}]+)\\s*(?:preferid[oa]|favorit[oa])?[\\p{L}\\s]*?\\b(?:e|eh)\\s+([\\p{L}][\\p{L}\\s]*)\\s*$")
                .matcher(falaNormalizada);
        if (!m.find())
            return null;
        return new String[] { m.group(1).trim(), m.group(2).trim() };
    }

}
