package com.vitainformatica.nfeimporter.sefaz;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Constroi o XML {@code envEvento} (ainda sem assinatura) e o envelope SOAP do
 * pedido de Recepcao
 * de Evento, para o evento de Manifestacao do Destinatario "Ciencia da
 * Operacao" (210210) -
 * conforme o "Manual de Orientacao do Contribuinte" (SEFAZ/AN), Eventos da
 * NF-e.
 * <p>
 * So a Ciencia da Operacao e automatizada aqui de proposito: e o unico evento
 * de manifestacao que
 * nao exige justificativa nem confirma/nega o negocio em si (apenas destrava o
 * recebimento do XML
 * completo da NF-e) - os demais (Confirmacao, Desconhecimento, Operacao nao
 * Realizada) implicam
 * julgamento sobre a operacao e nao devem ser enviados automaticamente.
 */
public final class ManifestacaoEventoRequestBuilder {

    private static final String NS_NFE = "http://www.portalfiscal.inf.br/nfe";
    private static final String NS_WSDL = "http://www.portalfiscal.inf.br/nfe/wsdl/NFeRecepcaoEvento4";
    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssxxx");

    public static final int TP_EVENTO_CIENCIA_OPERACAO = 210210;
    // Sem acentuacao - confirmado contra o formulario oficial de Manifestacao do
    // Destinatario
    // no portal da SEFAZ (as opcoes do dropdown la sao "Ciencia da Operacao",
    // "Confirmacao da
    // Operacao" etc., todas sem acento). Chegamos a tentar com acento por suspeita
    // de um texto
    // fixo no schema, mas essa nao era a forma correta - revertido.
    private static final String DESC_EVENTO_CIENCIA_OPERACAO = "Ciencia da Operacao";
    private static final String VERSAO_EVENTO = "1.00";

    private ManifestacaoEventoRequestBuilder() {
    }

    /**
     * Id exigido pelo schema: "ID" + tpEvento(6) + chNFe(44) + nSeqEvento(2), total
     * 54 caracteres.
     */
    public static String idEvento(String chaveAcesso, int nSeqEvento) {
        return "ID" + TP_EVENTO_CIENCIA_OPERACAO + chaveAcesso + String.format("%02d", nSeqEvento);
    }

    /**
     * Monta o {@code envEvento} (XML de dados, ainda sem assinatura) para "Ciencia
     * da Operacao".
     *
     * @param cOrgao Tipo Código de orgão (UF da tabela do IBGE + 90 SUFRAMA + 91 -
     *               RFB)
     */
    public static String cienciaOperacao(int tpAmb, int cOrgao, String chaveAcesso, String cnpjAutor, Instant dhEvento,
            int nSeqEvento) {
        String idEvento = idEvento(chaveAcesso, nSeqEvento);
        String dhEventoFormatado = FORMATO_DATA.format(dhEvento.atZone(ZoneId.of("America/Sao_Paulo")));

        String infEvento = "<infEvento Id=\"" + idEvento + "\">"
                + "<cOrgao>" + cOrgao + "</cOrgao>"
                + "<tpAmb>" + tpAmb + "</tpAmb>"
                + "<CNPJ>" + cnpjAutor + "</CNPJ>"
                + "<chNFe>" + chaveAcesso + "</chNFe>"
                + "<dhEvento>" + dhEventoFormatado + "</dhEvento>"
                + "<tpEvento>" + TP_EVENTO_CIENCIA_OPERACAO + "</tpEvento>"
                + "<nSeqEvento>" + nSeqEvento + "</nSeqEvento>"
                + "<verEvento>" + VERSAO_EVENTO + "</verEvento>"
                + "<detEvento versao=\"" + VERSAO_EVENTO + "\">"
                + "<descEvento>" + DESC_EVENTO_CIENCIA_OPERACAO + "</descEvento>"
                + "</detEvento>"
                + "</infEvento>";

        return "<envEvento xmlns=\"" + NS_NFE + "\" versao=\"" + VERSAO_EVENTO + "\">"
                + "<idLote>1</idLote>"
                + "<evento versao=\"" + VERSAO_EVENTO + "\">"
                + infEvento
                + "</evento>"
                + "</envEvento>";
    }

    /**
     * Envelopa o envEvento (ja assinado) em um envelope SOAP 1.2.
     * <p>
     * Sem wrapper com o nome da operacao ({@code nfeRecepcaoEventoNF}) em volta do
     * {@code nfeDadosMsg} - o WSDL declara a mensagem de entrada assim:
     * 
     * <pre>
     * &lt;wsdl:message name="nfeRecepcaoEventoNFSoapIn"&gt;
     *   &lt;wsdl:part name="nfeDadosMsg" element="tns:nfeDadosMsg" /&gt;
     * &lt;/wsdl:message&gt;
     * </pre>
     * 
     * A part referencia um ELEMENTO global ({@code element=}, nao {@code type=}) -
     * esse e
     * exatamente o padrao "document/literal bare" do WSDL 1.1 (usado pelos servicos
     * .asmx
     * classicos da SEFAZ): quando ha uma unica part referenciando um elemento
     * global, o
     * corpo do SOAP contem esse elemento DIRETAMENTE, sem nenhum wrapper com o nome
     * da
     * operacao. Envelopar {@code nfeDadosMsg} dentro de
     * {@code <nfeRecepcaoEventoNF>}
     * (como fazíamos antes) faz o parametro chegar nulo do lado do servidor - e
     * essa e a
     * causa mais provavel do NullReferenceException que persistia mesmo depois de
     * corrigir
     * a cadeia de certificados.
     * <p>
     * Confirmado contra o WSDL real (2026-09-17): este servico nao usa cabecalho
     * SOAP
     * (nfeCabecMsg).
     */
    public static String envelopeSoap(String envEventoAssinado) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<soap12:Envelope xmlns:soap12=\"http://www.w3.org/2003/05/soap-envelope\">"
                + "<soap12:Body>"
                + "<nfeDadosMsg xmlns=\"" + NS_WSDL + "\">" + envEventoAssinado + "</nfeDadosMsg>"
                + "</soap12:Body>"
                + "</soap12:Envelope>";
    }

    /**
     * Confirmado contra o WSDL real:
     * {@code .../NFeRecepcaoEvento4/nfeRecepcaoEventoNF} (nao "nfeRecepcaoEvento").
     * A action identifica a OPERACAO independente do corpo estar ou nao envelopado
     * por um
     * elemento com esse mesmo nome.
     */
    public static String soapAction() {
        return NS_WSDL + "/nfeRecepcaoEventoNF";
    }
}
