package com.JCODE.controller;

import com.JCODE.domain.Sinonimo;
import com.JCODE.domain.Sinonimos;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public class ControleSinonimo {

    static ObjectMapper objectMapper;

    static List<Sinonimo> sinonimosCache;

    private static Sinonimos buscaArquivoSinonimo(){

        try {
            FileInputStream fileInputStream = new FileInputStream("KAIRA/resources/sinonimos.json");

            InputStreamReader reader = new InputStreamReader(fileInputStream, StandardCharsets.UTF_8);

            return objectMapper.readValue(reader, Sinonimos.class);
        }catch (Exception e){
            if(e instanceof FileNotFoundException)

                return null;
        }

        return null;
    }

    public static void carregaCache() {

        if (objectMapper == null)
            objectMapper = new ObjectMapper();

        sinonimosCache = buscaArquivoSinonimo().getSinonimos();
    }

    public static void criarCacheSinonimo() {

        try (FileWriter fileWriter = new FileWriter("KAIRA/resources/sinonimos.json", StandardCharsets.UTF_8)) {

            fileWriter.write(cache);
        } catch (IOException e) {
            e.printStackTrace();
        }

        carregaCache();

        List<String> listaSinonimos = sinonimosCache.stream().map(Sinonimo::getPalavra).collect(Collectors.toList());

        ControlePalavra.gravaPalavraEmLista(listaSinonimos);

    }

    public static String procuraSinonimo(String palavra){

        String[] retorno = new String[1];

        sinonimosCache.stream().filter(sinonimo -> sinonimo.getPalavra().equalsIgnoreCase(palavra))
                .findFirst().ifPresentOrElse(sinonimo -> {
            retorno[0] = escolheSinonimo(sinonimo.getSinonimos());
        }, () ->{ retorno[0] = palavra;
        });

        if(retorno[0].equalsIgnoreCase(palavra)) {
            String palavraCorrigida = ControlePalavra.correcaoPalavra(palavra);

            sinonimosCache.stream().filter(sinonimo -> sinonimo.getPalavra().equalsIgnoreCase(palavraCorrigida))
                    .findFirst().ifPresent(sinonimo ->
                retorno[0] = escolheSinonimo(sinonimo.getSinonimos()));
        }

        return retorno[0];

    }

    private static String escolheSinonimo(List<String> sinonimos){

        if (sinonimos.isEmpty())
            return null;

        int qtdSinonimos = sinonimos.size();

        int escolha = (int) (Math.random() * (qtdSinonimos));

        return sinonimos.get(escolha);
    }

    private final static String cache = "{\"sinonimos\":[\n" +
            "  {\n" +
            "    \"palavra\": \"ia\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"voce\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"hallo\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"oi\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"agora\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"hoje\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"maneira\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"legal\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"chateado\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"triste\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"bonito\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"belo\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"falaria\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"diria\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"ok\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"bem\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"seu\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"sua\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"modelo\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"versao\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"algum\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"alguma\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"conto\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"história\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"recente\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"atual\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"tenho\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"possuo\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"enquanto\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"durante\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"ali\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"lá\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"radiante\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"brilhante\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"demais\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"muitas\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"claro\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"definido\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"exito\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"sucesso\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"pegando\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"coletando\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"começando\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"iniciando\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"agradeço!\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"obrigada!\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"segundo\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"como\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"favorita\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"preferida\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"ótima\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"boa\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"finalizar\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"desligar\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"tu\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"voce\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"infeliz\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"triste\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"conhece\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"sabe\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"uma\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"alguma\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"bastante\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"muito\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"retorna\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"volta\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"correto\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"certo\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"andar\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"caminhar\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"manipular\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"usar\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"ele\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"dele\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"compreendo\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"entendo\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"fale\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"diga\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"retorno\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"volto\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"conversar\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"falar\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"dados\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"informaçoes\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"possuo\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"tenho\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"enxergar\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"ver\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"breve\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"logo\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"acredito\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"creio\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"disse\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"falou\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"precionando\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"Apertando\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"assim\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"então\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"conseguir\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"poder\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"falam\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"dizem\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"agradavel\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"legal\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"obter\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"conseguir\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"adquirir\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"conseguir\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"olhar\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"enxergar\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"utilizar\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"manipular\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"exato\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"correto\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"deprimido\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"infeliz\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"encerrar\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"finalizar\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"excelente\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"ótima\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"grande\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"ótima\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"conforme\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"segundo\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"buscando\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"pegando\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"colhendo\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"pegando\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"aborrecido\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"chateado\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"do\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"da\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"carater\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"personalidade\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"meu\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"minha\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"agradecer\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"retribuir\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"sérios\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"importantes\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"necessitam\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"devem\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"precisam\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"devem\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"tagalerar\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"conversar\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"dialogar\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"conversar\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"moça\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"garota\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"certo\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"bem\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"qualquer\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"algum\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"sugere\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"significa\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"atual\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"recente\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"ocasião\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"oportunidade\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"sugestão\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"ideia\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"cedo\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"logo\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"desfrutaria\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"gostaria\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"acabado\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"pronto\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"convicção\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"certeza\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"jamais\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"nunca\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"instante\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"momento\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"ponderar\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"pensar\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"considerar\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"pensar\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"refletir\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"pensar\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"por\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"pela\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"consegue\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"pode\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"produzir\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"fazer\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"executar\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"fazer\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"igualmente\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"assim\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"portanto\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"assim\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"possuir\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"ter\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"dialogando\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"falando\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"consigo\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"posso\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"possivelmente\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"talvez\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"provavelmente\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"talvez\"\n" +
            "    ]\n" +
            "  },\n" +
            "  {\n" +
            "    \"palavra\": \"compensar\",\n" +
            "    \"sinonimos\": [\n" +
            "      \"retribuir\"\n" +
            "    ]\n" +
            "  }\n" +
            "  ]}";

}
