# nfe-importer

Aplicação Quarkus que importa automaticamente, via webservice de **Distribuição de DFe** da
SEFAZ, todos os documentos fiscais (NF-e) em que o CNPJ configurado figura como **destinatário**
— sem precisar que o emitente envie o XML manualmente.

## Como funciona

1. Um job agendado (`nfe.import.cron`, padrão: 1x por hora) consulta o webservice
   `NFeDistribuicaoDFe`, autenticando com o certificado digital A1 (e-CNPJ) do destinatário via
   TLS mútuo (mTLS) — é assim que a SEFAZ identifica quem está consultando, não há usuário/senha.
2. A consulta é paginada por **NSU** (Número Sequencial Único): a aplicação guarda o último NSU
   processado (tabela `controle_distribuicao`) e, a cada execução, pede "o que vier depois desse
   NSU", processando múltiplas páginas até esgotar o backlog disponível.
3. Cada documento retornado (resumo de NF-e, NF-e completa ou evento) é descompactado (o
   protocolo retorna cada item em base64+gzip), classificado, tem os principais campos extraídos
   e é gravado na tabela `nota_fiscal`, junto com o XML original.
4. Por padrão, a SEFAZ só entrega o **resumo** (`resNFe`) de cada NF-e nova — o XML **completo**
   (`procNFe`, com itens/valores) só é liberado depois que o destinatário registra a
   **Manifestação do Destinatário**. Por isso, para cada resumo novo a aplicação registra
   automaticamente o evento **"Ciência da Operação" (210210)** junto ao webservice
   `NFeRecepcaoEvento4` — assinado digitalmente (XML-DSig) com o certificado — o que destrava o
   recebimento do XML completo numa importação seguinte. Só esse evento é automatizado: ele não
   confirma nem nega a operação em si, apenas dá ciência de que a nota existe.
5. Cada **NF-e completa** nova (as demais — resumos e eventos — não têm itens/valores e não são
   encaminhadas) é automaticamente enviada ao **mercadinho** via `POST multipart/form-data` (igual
   a um `curl -F "file=@nfe.xml;type=application/xml"`). Falha no envio não desfaz a importação —
   a nota fica marcada como pendente e pode ser reenviada depois.
6. Uma API REST expõe consulta às notas importadas, permite disparar uma importação manual,
   reenviar notas pendentes ao mercadinho e reenviar manifestações pendentes.

## ⚠️ Antes de rodar em produção

- **Certificado A1**: a aplicação foi feita para autenticação via certificado A1 (arquivo
  `.pfx`/`.p12`). Certificado A3 (token/HSM) exigiria um provider PKCS#11 adicional, não coberto
  aqui.
- **Endpoint por UF**: a maioria das UFs usa o Ambiente Nacional (AN) para este serviço
  (`endpoint-producao`/`endpoint-homologacao` em `application.yml`), mas uma minoria usa o
  ambiente SVRS. **Confirme o endpoint correto para a UF do certificado** no "Manual de
  Orientação do Contribuinte — NF-e Distribuição de DFe" antes de operar em produção; se o
  endpoint estiver errado, a chamada falhará ou retornará erro de autenticação.
- **Endpoint de manifestação não verificado**: os endpoints padrão em `nfe.manifestacao.*`
  (webservice `NFeRecepcaoEvento4`) são o melhor palpite, mas **não foram confirmados contra uma
  chamada real** à SEFAZ — confirme-os no manual oficial antes de habilitar
  `nfe.manifestacao.enviar-automaticamente` em produção.
- **Limite de consultas**: a SEFAZ recomenda no máximo 1 consulta por hora por CNPJ. Consultas
  excessivas resultam em `cStat 656` (Consumo Indevido) e bloqueio temporário. Isso já é
  respeitado por padrão (`nfe.import.intervalo-minimo-minutos=60`); só use `forcar=true` no
  endpoint manual para depuração pontual.
- **Primeira execução**: a Distribuição de DFe mantém um histórico limitado (tipicamente alguns
  meses). Se houver muito backlog, a primeira importação pode levar várias páginas — o limite por
  execução é configurável via `nfe.import.max-paginas-por-execucao` (padrão 50; cada página traz
  até 50 documentos).

## Configuração

Toda a configuração específica da integração está em
[`src/main/resources/application.yml`](src/main/resources/application.yml), sob o prefixo `nfe`,
e é resolvida a partir de variáveis de ambiente:

| Variável de ambiente | Descrição | Padrão |
|---|---|---|
| `QUARKUS_HTTP_PORT` | Porta HTTP da aplicação (API REST) | `8087` |
| `NFE_CNPJ` | CNPJ (só dígitos) do destinatário/titular do certificado | *(obrigatório)* |
| `NFE_UF` | UF do titular do certificado (ex: `SP`) | *(obrigatório)* |
| `NFE_AMBIENTE` | `producao` ou `homologacao` | `homologacao` |
| `NFE_CERT_PATH` | Caminho do arquivo `.pfx`/`.p12` | *(obrigatório)* |
| `NFE_CERT_PASSWORD` | Senha do certificado | *(obrigatório)* |
| `NFE_DISTRIBUICAO_URL_PROD` / `NFE_DISTRIBUICAO_URL_HOM` | Endpoints do webservice (ver aviso acima) | URLs do AN |
| `NFE_IMPORT_CRON` | Expressão cron da importação agendada | `0 5 * * * ?` (1x/hora) |
| `NFE_IMPORT_INTERVALO_MINIMO_MINUTOS` | Intervalo mínimo entre consultas | `60` |
| `NFE_IMPORT_MAX_PAGINAS` | Máx. de páginas por execução | `50` |
| `NFE_IMPORT_EXECUTAR_NO_STARTUP` | Dispara uma importação assim que a aplicação sobe, além do agendamento | `true` |
| `NFE_MANIFESTACAO_URL_PROD` / `NFE_MANIFESTACAO_URL_HOM` | Endpoints do webservice de Recepção de Evento (ver aviso acima) | URLs do AN (não verificadas) |
| `NFE_MANIFESTACAO_ENVIAR_AUTOMATICAMENTE` | Registra "Ciência da Operação" automaticamente para cada resumo novo | `true` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Conexão com o MariaDB | *(obrigatório)* |
| `MERCADINHO_API` | URL base da API do mercadinho | *(opcional — sem ela, o encaminhamento fica desabilitado e a app sobe normalmente)* |
| `MERCADINHO_TOKEN` | Token Bearer da API do mercadinho | *(opcional, mesma observação)* |
| `MERCADINHO_CAMINHO_IMPORTACAO` | Caminho do endpoint de importação de XML | `/api/notas-fiscais/importar/xml` |
| `MERCADINHO_TIMEOUT_SEGUNDOS` | Timeout da chamada HTTP | `30` |
| `MERCADINHO_ENVIAR_APOS_IMPORTAR` | Encaminha automaticamente cada NF-e completa nova ao mercadinho | `true` |

### Segredos: arquivo `.env`

As variáveis marcadas como *(obrigatório)* acima são segredos/dados específicos de cada
implantação e **não têm mais default no `application.yml`** — copie [`.env.example`](.env.example)
para `.env` (já no `.gitignore`, nunca é commitado) e preencha com os valores reais:

```shell script
cp .env.example .env
# edite o .env com CNPJ, UF, caminho/senha do certificado e credenciais do banco
```

⚠️ **Isso só é lido automaticamente em modo dev (`quarkus:dev`) e em testes.** Rodando o jar
empacotado em produção, o `.env` é ignorado — exporte as variáveis de fato no ambiente (systemd,
Docker, etc.) ou use outro mecanismo de secrets do seu ambiente de deploy.

O schema do banco (MariaDB) é criado automaticamente pelo Flyway na primeira subida
(`src/main/resources/db/migration/V1__create_tables.sql`).

## API REST

| Método | Caminho | Descrição |
|---|---|---|
| `GET` | `/notas-fiscais` | Lista paginada. Filtros: `pagina`, `tamanho`, `cnpjEmitente`, `tipoDocumento`, `dataInicio`, `dataFim` |
| `GET` | `/notas-fiscais/{id}` | Detalhe de uma nota, incluindo o XML completo |
| `GET` | `/notas-fiscais/chave/{chaveAcesso}` | Busca pela chave de acesso (44 dígitos) |
| `GET` | `/notas-fiscais/estatisticas` | Contagem total e por tipo de documento |
| `POST` | `/notas-fiscais/importar?forcar=false` | Dispara a importação manualmente |
| `POST` | `/mercadinho/notas-fiscais/{id}` | Envia (ou reenvia) o XML de uma nota específica ao mercadinho |
| `POST` | `/mercadinho/sincronizar?limite=100` | Reenvia todas as NF-e completas ainda não confirmadas como entregues |
| `POST` | `/manifestacao/chave/{chaveAcesso}` | Registra (ou reenvia) a "Ciência da Operação" para uma chave específica |
| `GET` | `/manifestacao/chave/{chaveAcesso}` | Consulta o status de manifestação de uma chave |
| `POST` | `/manifestacao/sincronizar?limite=100` | Reenvia todas as manifestações ainda pendentes |

## Rodando em modo de desenvolvimento

```shell script
./mvnw quarkus:dev
```

Garanta que o `.env` existe e está preenchido (veja a seção acima) antes de subir — é dele que
vêm CNPJ, UF, certificado e conexão com o banco em modo dev. Se o `.env` não definir `DB_URL`, o
datasource cai automaticamente para **Dev Services** (sobe um container MariaDB via Docker).
Recomenda-se começar com `NFE_AMBIENTE=homologacao` para validar a integração sem consumir a cota
de produção.

> **_NOTE:_** Quarkus disponibiliza uma Dev UI em <http://localhost:8080/q/dev/> apenas em modo dev.

## Empacotando e rodando em produção

```shell script
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

Não é um über-jar — as dependências ficam em `target/quarkus-app/lib/`. Para gerar um über-jar:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

## Testes

```shell script
./mvnw test
```

Os testes cobrem a montagem do XML/SOAP de requisição, a interpretação da resposta da SEFAZ
(descompactação dos `docZip`), a extração de campos dos XMLs de NF-e (resumo e completa) e a
**assinatura digital dos eventos de manifestação** — incluindo validação criptográfica real da
assinatura gerada (não só checar que existe uma tag `<Signature>`), usando um certificado de teste
gerado na hora com o `keytool` do próprio JDK. Nada disso depende de rede ou de um certificado real.

## Stack

- Quarkus 3.39.3 / Java 17
- Hibernate ORM com Panache + MariaDB + Flyway
- `java.net.http.HttpClient` com TLS mútuo para o webservice SOAP da SEFAZ (sem dependência de
  bibliotecas SOAP adicionais)
- Scheduler do Quarkus para a importação periódica
- REST (Jakarta REST / RESTEasy Reactive) + Jackson para a API de consulta
- Multipart `POST` manual (sem libs extras) para encaminhar o XML de cada NF-e ao mercadinho
- Assinatura XML (XML-DSig) para os eventos de manifestação via `javax.xml.crypto.dsig` (JSR 105,
  nativo do JDK — sem bibliotecas de assinatura externas)
