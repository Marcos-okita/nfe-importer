<#
.SYNOPSIS
    Gera o pacote Quarkus (JVM) e constroi a imagem Docker do nfe-importer.

.DESCRIPTION
    1. Roda ./mvnw package (gera target/quarkus-app/).
    2. Roda docker build usando src/main/docker/Dockerfile.jvm.

    O .env NAO e copiado para a imagem (segredos ficam fora dela - configure-os
    na hora de rodar o container, via -e / --env-file).

.PARAMETER Tag
    Tag completa da imagem. Padrao: registry.vitainformatica.com/marcos.okita1/nfe-importer

.PARAMETER SkipMaven
    Pula o ./mvnw package (usa o target/quarkus-app ja existente).

.PARAMETER Push
    Envia a imagem para o registry apos o build (requer docker login previo).

.EXAMPLE
    .\scripts\build-docker.ps1

.EXAMPLE
    .\scripts\build-docker.ps1 -Push
#>
[CmdletBinding()]
param(
    [string]$Tag = "registry.vitainformatica.com/marcos.okita1/nfe-importer",
    [switch]$SkipMaven,
    [switch]$Push
)

$ErrorActionPreference = "Stop"

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

if (-not $SkipMaven) {
    Write-Host ">> Gerando pacote Quarkus (./mvnw package -DskipTests)..." -ForegroundColor Cyan
    & .\mvnw.cmd -q clean package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "Falha no ./mvnw package (codigo $LASTEXITCODE)" }
}

if (-not (Test-Path "target\quarkus-app\quarkus-run.jar")) {
    throw "target\quarkus-app\quarkus-run.jar nao encontrado. Rode sem -SkipMaven."
}

Write-Host ">> Construindo imagem $Tag ..." -ForegroundColor Cyan
& docker build -f src/main/docker/Dockerfile.jvm -t $Tag .
if ($LASTEXITCODE -ne 0) { throw "Falha no docker build (codigo $LASTEXITCODE)" }

if ($Push) {
    Write-Host ">> Enviando $Tag para o registry ..." -ForegroundColor Cyan
    & docker push $Tag
    if ($LASTEXITCODE -ne 0) { throw "Falha no docker push (codigo $LASTEXITCODE)" }
}

Write-Host ">> Pronto: $Tag" -ForegroundColor Green
Write-Host "Exemplo de execucao (porta padrao da app = 8087):"
Write-Host "  docker run --rm -p 8087:8087 --env-file .env $Tag"
