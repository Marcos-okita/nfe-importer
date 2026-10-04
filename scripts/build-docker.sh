#!/usr/bin/env bash
#
# Gera o pacote Quarkus (JVM) e constroi a imagem Docker do nfe-importer.
#
# 1. Roda ./mvnw package -DskipTests (gera target/quarkus-app/).
# 2. Roda docker build usando src/main/docker/Dockerfile.jvm.
#
# O .env NAO e copiado para a imagem (segredos ficam fora dela - configure-os
# na hora de rodar o container, via -e / --env-file).
#
# Uso:
#   ./scripts/build-docker.sh                 # maven + build
#   ./scripts/build-docker.sh --skip-maven    # usa target/quarkus-app existente
#   ./scripts/build-docker.sh --push          # build + push (requer docker login)
#   ./scripts/build-docker.sh --tag outra/tag # sobrescreve a tag padrao

set -euo pipefail

TAG="registry.vitainformatica.com/marcos.okita1/nfe-importer"
SKIP_MAVEN=false
PUSH=false

while [[ $# -gt 0 ]]; do
    case "$1" in
        --tag)        TAG="$2"; shift 2 ;;
        --skip-maven) SKIP_MAVEN=true; shift ;;
        --push)       PUSH=true; shift ;;
        -h|--help)
            sed -n '2,15p' "$0"
            exit 0
            ;;
        *)
            echo "Opcao desconhecida: $1" >&2
            exit 1
            ;;
    esac
done

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT"

if [[ "$SKIP_MAVEN" == false ]]; then
    echo ">> Gerando pacote Quarkus (./mvnw package -DskipTests)..."
    ./mvnw -q clean package -DskipTests
fi

if [[ ! -f target/quarkus-app/quarkus-run.jar ]]; then
    echo "ERRO: target/quarkus-app/quarkus-run.jar nao encontrado. Rode sem --skip-maven." >&2
    exit 1
fi

echo ">> Construindo imagem $TAG ..."
docker build -f src/main/docker/Dockerfile.jvm -t "$TAG" .

if [[ "$PUSH" == true ]]; then
    echo ">> Enviando $TAG para o registry ..."
    docker push "$TAG"
fi

echo ">> Pronto: $TAG"
echo "Exemplo de execucao (porta padrao da app = 8087):"
echo "  docker run --rm -p 8087:8087 --env-file .env $TAG"
