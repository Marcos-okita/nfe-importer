package com.vitainformatica.nfeimporter.service;

import java.time.Instant;
import java.util.List;

import com.vitainformatica.nfeimporter.domain.NotaFiscal;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Operacoes de banco de dados do envio ao mercadinho, em transacoes curtas e separadas da
 * chamada HTTP em si (que fica no {@link MercadinhoEnvioService}) - a mesma razao de design do
 * {@link NfeImportPersistenceService} para a importacao da SEFAZ: nao segurar uma transacao (e a
 * conexao com o banco) aberta durante uma chamada de rede.
 */
@ApplicationScoped
public class MercadinhoEnvioPersistenceService {

    /** Dados minimos de uma nota necessarios para monta o upload ao mercadinho. */
    public record DadosParaEnvio(Long id, String nomeArquivo, String xml) {
    }

    @Transactional
    public DadosParaEnvio carregarParaEnvio(Long notaFiscalId) {
        NotaFiscal nota = NotaFiscal.findById(notaFiscalId);
        if (nota == null) {
            return null;
        }
        String identificador = (nota.chaveAcesso != null && !nota.chaveAcesso.isBlank()) ? nota.chaveAcesso : nota.nsu;
        return new DadosParaEnvio(nota.id, identificador + ".xml", nota.xml);
    }

    @Transactional
    public void marcarEnviado(Long notaFiscalId) {
        NotaFiscal nota = NotaFiscal.findById(notaFiscalId);
        if (nota != null) {
            nota.enviadoMercadinho = true;
            nota.dataEnvioMercadinho = Instant.now();
            nota.erroEnvioMercadinho = null;
        }
    }

    @Transactional
    public void marcarFalha(Long notaFiscalId, String erro) {
        NotaFiscal nota = NotaFiscal.findById(notaFiscalId);
        if (nota != null) {
            nota.erroEnvioMercadinho = erro != null && erro.length() > 500 ? erro.substring(0, 500) : erro;
        }
    }

    @Transactional
    public List<Long> listarIdsPendentes(int limite) {
        return NotaFiscal.listarIdsPendentesDeEnvioMercadinho(limite);
    }
}
