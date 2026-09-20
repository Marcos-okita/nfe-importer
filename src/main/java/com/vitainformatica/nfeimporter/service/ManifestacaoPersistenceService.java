package com.vitainformatica.nfeimporter.service;

import java.time.Instant;
import java.util.List;

import com.vitainformatica.nfeimporter.domain.ManifestacaoDestinatario;
import com.vitainformatica.nfeimporter.sefaz.ManifestacaoResponse;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

/**
 * Operacoes de banco de dados do controle de Manifestacao do Destinatario, em transacoes curtas -
 * mesma razao de design de {@link NfeImportPersistenceService} e {@link MercadinhoEnvioPersistenceService}:
 * nao segurar uma transacao aberta durante a assinatura XML e a chamada de rede.
 */
@ApplicationScoped
public class ManifestacaoPersistenceService {

    public record DadosParaEnvio(Long id, String chaveAcesso, int tentativaAnterior) {
    }

    @Transactional
    public DadosParaEnvio carregar(Long manifestacaoId) {
        ManifestacaoDestinatario m = ManifestacaoDestinatario.findById(manifestacaoId);
        return m == null ? null : new DadosParaEnvio(m.id, m.chaveAcesso, m.tentativas);
    }

    @Transactional
    public void marcarResultado(Long manifestacaoId, ManifestacaoResponse resposta) {
        ManifestacaoDestinatario m = ManifestacaoDestinatario.findById(manifestacaoId);
        if (m == null) {
            return;
        }
        m.tentativas++;
        // cStatEfetivo/xMotivoEfetivo cobrem tambem o lote rejeitado antes de processar qualquer
        // evento (ex: cStat 225 - falha de schema), caso em que cStatEvento/xMotivoEvento vem null.
        m.cStat = resposta.cStatEfetivo();
        m.xMotivo = resposta.xMotivoEfetivo();
        if (resposta.sucesso()) {
            m.enviada = true;
            m.dataEnvio = Instant.now();
            m.protocolo = resposta.protocolo();
        }
    }

    @Transactional
    public void marcarFalha(Long manifestacaoId, String erro) {
        ManifestacaoDestinatario m = ManifestacaoDestinatario.findById(manifestacaoId);
        if (m != null) {
            m.tentativas++;
            m.xMotivo = erro != null && erro.length() > 200 ? erro.substring(0, 200) : erro;
        }
    }

    @Transactional
    public List<Long> listarIdsPendentes(int limite) {
        return ManifestacaoDestinatario.listarIdsPendentes(limite);
    }

    @Transactional
    public Long obterOuCriarId(String chaveAcesso, String cnpjAutor) {
        return ManifestacaoDestinatario.obterOuCriar(chaveAcesso, cnpjAutor).id;
    }
}
