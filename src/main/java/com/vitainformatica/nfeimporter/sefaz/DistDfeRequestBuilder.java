package com.vitainformatica.nfeimporter.sefaz;

/**
 * Constroi o XML {@code distDFeInt} e o envelope SOAP do pedido de Distribuicao de DFe,
 * conforme o "Manual de Orientacao do Contribuinte - NF-e Distribuicao de DFe" (SEFAZ/AN).
 */
public final class DistDfeRequestBuilder {

    private static final String VERSAO = "1.35";
    private static final String NS_NFE = "http://www.portalfiscal.inf.br/nfe";
    private static final String NS_WSDL = "http://www.portalfiscal.inf.br/nfe/wsdl/NFeDistribuicaoDFe";

    private DistDfeRequestBuilder() {
    }

    /** Pedido de distribuicao continua a partir do ultimo NSU processado (paginacao). */
    public static String distNsu(int tpAmb, int cUfAutor, String cnpj, String ultNsu) {
        String consulta = "<distNSU><ultNSU>" + formatarNsu(ultNsu) + "</ultNSU></distNSU>";
        return distDfeInt(tpAmb, cUfAutor, cnpj, consulta);
    }

    /** Consulta um documento especifico pela chave de acesso (nao usado no fluxo de importacao continua). */
    public static String consChNFe(int tpAmb, int cUfAutor, String cnpj, String chaveAcesso) {
        String consulta = "<consChNFe><chNFe>" + chaveAcesso + "</chNFe></consChNFe>";
        return distDfeInt(tpAmb, cUfAutor, cnpj, consulta);
    }

    private static String distDfeInt(int tpAmb, int cUfAutor, String cnpj, String consultaXml) {
        return "<distDFeInt xmlns=\"" + NS_NFE + "\" versao=\"" + VERSAO + "\">"
                + "<tpAmb>" + tpAmb + "</tpAmb>"
                + "<cUFAutor>" + cUfAutor + "</cUFAutor>"
                + "<CNPJ>" + cnpj + "</CNPJ>"
                + consultaXml
                + "</distDFeInt>";
    }

    /** Envelopa o XML de dados em um envelope SOAP 1.1, com o cabecalho exigido pelo webservice. */
    public static String envelopeSoap(String distDfeIntXml, int cUf) {
        String dadosMsgEscapado = escaparParaTagXml(distDfeIntXml);
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">"
                + "<soap12:Header>"
                + "<nfeCabecMsg xmlns=\"" + NS_WSDL + "\">"
                + "<cUF>" + cUf + "</cUF>"
                + "<versaoDados>" + VERSAO + "</versaoDados>"
                + "</nfeCabecMsg>"
                + "</soap12:Header>"
                + "<soap12:Body>"
                + "<nfeDistDFeInteresse xmlns=\"" + NS_WSDL + "\">"
                + "<nfeDadosMsg>" + dadosMsgEscapado + "</nfeDadosMsg>"
                + "</nfeDistDFeInteresse>"
                + "</soap12:Body>"
                + "</soap12:Envelope>";
    }

    public static String soapAction() {
        return NS_WSDL + "/nfeDistDFeInteresse";
    }

    /** Formata o NSU com zero-padding para 15 digitos, como exigido pelo schema. */
    static String formatarNsu(String nsu) {
        String apenasDigitos = nsu == null ? "0" : nsu.replaceAll("\\D", "");
        if (apenasDigitos.isEmpty()) {
            apenasDigitos = "0";
        }
        return String.format("%015d", Long.parseLong(apenasDigitos));
    }

    // O nfeDadosMsg carrega o distDFeInt como XML "cru" dentro do body SOAP (nao escapado como texto),
    // pratica adotada pela SEFAZ nesse webservice - mantemos o XML embutido diretamente.
    private static String escaparParaTagXml(String xml) {
        return xml;
    }
}
