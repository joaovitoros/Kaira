package com.JCODE.controller;

import com.JCODE.domain.FatoKaira;
import com.JCODE.domain.KAIRA;
import com.JCODE.domain.MolduraKaira;
import com.JCODE.util.Caminhos;
import com.JCODE.util.Registro;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ControleKaira {

    static ObjectMapper objectMapper;

    static KAIRA kairaCache;

    // #50 - memoria pessoal dela: os fatos do kaira.json + as molduras que os vestem
    private static Map<String, FatoKaira> cacheFatos = null;
    private static Map<String, MolduraKaira> cacheMolduras = null;

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

        if (objectMapper == null)
            objectMapper = new ObjectMapper();

        // #56 - migracao no load: o formato plano (v0.0.1) vira v2 em memoria
        JsonNode raiz = objectMapper.readTree(
                new InputStreamReader(new FileInputStream(Caminhos.recurso("kaira.json").toFile()),
                        StandardCharsets.UTF_8));

        ObjectNode fatos = (ObjectNode) raiz.get("fatos");
        if (fatos != null) {
            List<String> planas = new ArrayList<>();
            fatos.fieldNames().forEachRemaining(n -> {
                if (fatos.get(n).isTextual())
                    planas.add(n);
            });
            for (String n : planas) {
                ObjectNode fato = objectMapper.createObjectNode();
                fato.putArray("gosta").add(fatos.get(n).asText());
                fatos.set(n, fato);
            }
        }

        ObjectNode molduras = (ObjectNode) raiz.get("molduras");
        if (molduras != null) {
            List<String> planas = new ArrayList<>();
            molduras.fieldNames().forEachRemaining(n -> {
                if (molduras.get(n).isTextual())
                    planas.add(n);
            });
            for (String n : planas) {
                ObjectNode m = objectMapper.createObjectNode();
                m.put("gosta", molduras.get(n).asText());
                molduras.set(n, m);
            }
        }

        KAIRA kaira = objectMapper.treeToValue(raiz, KAIRA.class);

        // #56 - dado sujo: valor nos dois lados - a relacao positiva vence
        if (kaira.getFatos() != null)
            for (FatoKaira fato : kaira.getFatos().values()) {
                if (fato.getGosta() == null || fato.getNaoGosta() == null)
                    continue;
                List<String> gostaNorm = new ArrayList<>();
                for (String v : fato.getGosta())
                    gostaNorm.add(compara(v));
                List<String> limpa = new ArrayList<>();
                for (String v : fato.getNaoGosta())
                    if (!gostaNorm.contains(compara(v)))
                        limpa.add(v);
                fato.setNaoGosta(limpa);
            }

        kairaCache = kaira;

        return kaira;
    }

    public static KAIRA criarCacheKAIRA(){

        criarCaminho();

        if(objectMapper==null)
            objectMapper = new ObjectMapper();

        KAIRA kaira = new KAIRA();

        kaira.setNome("K.A.I.R.A");
        kaira.setVersao("0.0.2");

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
            kairaCache = null;
        }
        cacheFatos = (kairaCache != null && kairaCache.getFatos() != null)
                ? kairaCache.getFatos() : new HashMap<>();
        cacheMolduras = (kairaCache != null && kairaCache.getMolduras() != null)
                ? kairaCache.getMolduras() : new HashMap<>();
    }

    public static String buscaFato(String chave) {
        if (cacheFatos == null)
            carregaFatos();
        FatoKaira fato = chave == null ? null : cacheFatos.get(chave);
        return (fato == null || fato.getGosta() == null || fato.getGosta().isEmpty())
                ? null : fato.getGosta().get(0);
    }

    /** #56 - a relacao negativa do fato (o que ela nao gosta). */
    public static String buscaNaoGosta(String chave) {
        if (cacheFatos == null)
            carregaFatos();
        FatoKaira fato = chave == null ? null : cacheFatos.get(chave);
        return (fato == null || fato.getNaoGosta() == null || fato.getNaoGosta().isEmpty())
                ? null : fato.getNaoGosta().get(0);
    }

    /** #56 - as chaves que a memoria dela conhece (a porta negativa varre). */
    public static java.util.Set<String> chavesConhecidas() {
        if (cacheFatos == null)
            carregaFatos();
        return cacheFatos.keySet();
    }

    public static String molduraDe(String chave) {
        if (cacheMolduras == null)
            carregaFatos();
        MolduraKaira m = chave == null ? null : cacheMolduras.get(chave);
        return m == null ? null : m.getGosta();
    }

    public static String molduraNaoGosta(String chave) {
        if (cacheMolduras == null)
            carregaFatos();
        MolduraKaira m = chave == null ? null : cacheMolduras.get(chave);
        return m == null ? null : m.getNaoGosta();
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
        if (contemNoLadoOposto(chave, valor)) {
            Registro.linha("SESSAO    : fato " + chave + " recusado (contradicao)");
            return false;
        }
        if (cacheFatos == null)
            carregaFatos();
        FatoKaira fato = cacheFatos.get(chave);
        if (fato == null) {
            fato = new FatoKaira();
            cacheFatos.put(chave, fato);
        }
        // #56 - o ensino substitui a relacao GOSTA e preserva a nao_gosta
        List<String> gosta = new ArrayList<>();
        gosta.add(valor);
        fato.setGosta(gosta);

        kairaCache.setFatos(cacheFatos);
        kairaCache.setVersao("0.0.2");

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

    /** #56 - o valor ja esta na relacao OPOSTA? (ela nao se contradiz) */
    public static boolean contemNoLadoOposto(String chave, String valor) {
        if (cacheFatos == null)
            carregaFatos();
        FatoKaira fato = chave == null ? null : cacheFatos.get(chave);
        if (fato == null || valor == null)
            return false;
        String alvo = compara(valor);
        if (fato.getNaoGosta() != null)
            for (String v : fato.getNaoGosta())
                if (alvo.equals(compara(v)))
                    return true;
        return false;
    }

    /** #56 - comparacao por valor (minusculas, sem acento). */
    private static String compara(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").trim().toLowerCase(java.util.Locale.ROOT);
    }

}
