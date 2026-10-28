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
import java.util.function.IntUnaryOperator;
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

    // #60 - sorteio das molduras com anti-repeticao (padrao #35)
    static IntUnaryOperator semeadorMolduras = n -> (int) (Math.random() * n);
    private static final Map<String, String> ultimaMoldura = new HashMap<>();

    private static String sorteiaMoldura(String chave, List<String> opcoes) {
        List<String> livres = new ArrayList<>(opcoes);
        String ultima = ultimaMoldura.get(chave);
        if (ultima != null && livres.size() > 1)
            livres.remove(ultima);
        if (livres.isEmpty())
            livres = new ArrayList<>(opcoes);
        String escolhida = livres.get(semeadorMolduras.applyAsInt(livres.size()));
        ultimaMoldura.put(chave, escolhida);
        return escolhida;
    }

    public static String molduraDe(String chave) {
        if (cacheMolduras == null)
            carregaFatos();
        MolduraKaira m = chave == null ? null : cacheMolduras.get(chave);
        if (m == null || m.getGosta() == null || m.getGosta().isEmpty())
            return null;
        return sorteiaMoldura(chave + ":gosta", m.getGosta());
    }

    public static String molduraNaoGosta(String chave) {
        if (cacheMolduras == null)
            carregaFatos();
        MolduraKaira m = chave == null ? null : cacheMolduras.get(chave);
        if (m == null || m.getNaoGosta() == null || m.getNaoGosta().isEmpty())
            return null;
        return sorteiaMoldura(chave + ":nao_gosta", m.getNaoGosta());
    }

    /** #60 - as listas (o spec confere contrato e pertencimento). */
    public static List<String> moldurasDe(String chave) {
        if (cacheMolduras == null)
            carregaFatos();
        MolduraKaira m = chave == null ? null : cacheMolduras.get(chave);
        return m == null || m.getGosta() == null ? List.of() : m.getGosta();
    }

    public static List<String> moldurasNaoGosta(String chave) {
        if (cacheMolduras == null)
            carregaFatos();
        MolduraKaira m = chave == null ? null : cacheMolduras.get(chave);
        return m == null || m.getNaoGosta() == null ? List.of() : m.getNaoGosta();
    }

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

        // #60 - v0.0.2 ({"gosta": "texto"}) -> v0.0.3 ({"gosta": ["texto"]})
        List<String> emTransicao = new ArrayList<>();
        molduras.fieldNames().forEachRemaining(n -> {
            ObjectNode m = (ObjectNode) molduras.get(n);
            if ((m.has("gosta") && m.get("gosta").isTextual())
                    || (m.has("nao_gosta") && m.get("nao_gosta").isTextual()))
                emTransicao.add(n);
        });
        for (String n : emTransicao) {
            ObjectNode m = (ObjectNode) molduras.get(n);
            if (m.has("gosta") && m.get("gosta").isTextual()) {
                String t = m.get("gosta").asText();
                m.putArray("gosta").add(t);
            }
            if (m.has("nao_gosta") && m.get("nao_gosta").isTextual()) {
                String t = m.get("nao_gosta").asText();
                m.putArray("nao_gosta").add(t);
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

    /** #50/#59 - grava o fato dela na relacao indicada: substitui a lista
     *  daquela relacao, preserva a outra, com as guardas de segredo e de
     *  nao-contradicao. */
    public static boolean gravaFato(String chave, String valor, boolean gosta) {
        if (chave == null || chave.isBlank() || valor == null || valor.isBlank())
            return false;
        if (ehSegredo(chave + " " + valor)) {
            Registro.linha("SESSAO    : fato " + chave + " ignorado (segredo)");
            return false;
        }
        if (contemNoLadoOposto(chave, valor, gosta)) {
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
        List<String> alvo = gosta ? fato.getGosta() : fato.getNaoGosta();
        alvo.clear();
        alvo.add(valor);

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

    /** #50 - compat: escreve na relacao positiva. */
    public static boolean gravaFato(String chave, String valor) {
        return gravaFato(chave, valor, true);
    }

    /** #59 - compat: escreve na relacao negativa. */
    public static boolean gravaNaoGosta(String chave, String valor) {
        return gravaFato(chave, valor, false);
    }


    /** #50/#59 - a forma de ensino dela: "sua CHAVE ... (nao) e VALOR".
     *  Devolve {chave, valor, relacao} com relacao "gosta"/"nao_gosta". */
    public static String[] extraiFatoDela(String falaNormalizada) {
        if (falaNormalizada == null)
            return null;
        // #59 - a NEGATIVA vem primeiro (o padrao positivo engole o "nao")
        Matcher negativo = Pattern.compile(
                        "\\b(?:sua|seu)\\s+([\\p{L}]+)\\s*(?:preferid[oa]|favorit[oa])?[\\p{L}\\s]*?"
                                + "\\b(?:nao|nunca)\\s+(?:é|eh|e)\\s+([\\p{L}][\\p{L}\\s]*)\\s*$")
                .matcher(falaNormalizada);
        if (negativo.find())
            return new String[] { negativo.group(1).trim(), negativo.group(2).trim(), "nao_gosta" };
        Matcher m = Pattern.compile(
                        "\\b(?:sua|seu)\\s+([\\p{L}]+)\\s*(?:preferid[oa]|favorit[oa])?[\\p{L}\\s]*?"
                                + "\\b(?:e|eh)\\s+([\\p{L}][\\p{L}\\s]*)\\s*$")
                .matcher(falaNormalizada);
        if (!m.find())
            return null;
        return new String[] { m.group(1).trim(), m.group(2).trim(), "gosta" };
    }

    /** #56 - o valor ja esta na relacao OPOSTA? (ela nao se contradiz) */
    public static boolean contemNoLadoOposto(String chave, String valor) {
        return contemNoLadoOposto(chave, valor, true);
    }

    /** #59 - o valor esta na relacao OPOSTA a que se quer escrever? */
    public static boolean contemNoLadoOposto(String chave, String valor, boolean gosta) {
        if (cacheFatos == null)
            carregaFatos();
        FatoKaira fato = chave == null ? null : cacheFatos.get(chave);
        if (fato == null || valor == null)
            return false;
        List<String> oposta = gosta ? fato.getNaoGosta() : fato.getGosta();
        if (oposta == null)
            return false;
        String alvo = compara(valor);
        for (String v : oposta)
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
