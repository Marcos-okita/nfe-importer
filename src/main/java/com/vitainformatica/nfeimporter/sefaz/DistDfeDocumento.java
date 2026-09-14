package com.vitainformatica.nfeimporter.sefaz;

/**
 * Um documento individual retornado dentro do lote de distribuicao (elemento {@code docZip}),
 * ja descompactado (o retorno original vem em base64 + gzip).
 *
 * @param nsu    numero sequencial unico deste documento no fluxo de distribuicao
 * @param schema identifica o tipo de conteudo, ex: {@code resNFe_v1.01.xsd}, {@code procNFe_v4.00.xsd},
 *               {@code resEvento_v1.01.xsd}, {@code procEventoNFe_v1.00.xsd}
 * @param xml    conteudo XML descompactado
 */
public record DistDfeDocumento(String nsu, String schema, String xml) {
}
