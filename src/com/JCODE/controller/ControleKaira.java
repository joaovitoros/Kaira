package com.JCODE.controller;

import com.JCODE.domain.KAIRA;
import com.JCODE.util.Caminhos;
import com.JCODE.util.Registro;
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

    // #53 - blocklist de segredo: senha/cartao/cpf/chave pix nao entram na memoria
    private static final String SEGREDOS = "(?i).*\\b(?:senha|password|cartao|cartão|cpf|cnpj"
            + "|cvv|token|pix|pin|agencia|agência|conta\\s+bancaria|conta\\s+bancária"
            + "|conta\\s+corrente|codigo\\s+de\\s+seguranca|código\\s+de\\s+segurança)\\b.*";

    public static boolean ehSegredo(String texto) {
        if (texto == null)
            return false;
        if (texto.matches(SEGREDOS))
            return true;
        // CPF/cartao (com ou sem pontuacao): 9+ digitos seguidos
        return texto.replaceAll("\\D", "").length() >= 9;
    }

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

    /** #50 - grava o fato dela (cache + arquivo, mesma tatica do ensinaEm).
     *  #53 - devolve false quando o par tem cara de segredo (nada e gravado). */
    public static boolean gravaFato(String chave, String valor) {
        if (chave == null || chave.isBlank() || valor == null || valor.isBlank())
            return false;
        if (ehSegredo(chave + " " + valor)) {
            Registro.linha("SESSAO    : fato " + chave + " ignorado (segredo)");
            return false;
        }
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
        return true;
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
