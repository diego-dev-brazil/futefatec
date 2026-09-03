#!/usr/bin/env bash
# ==============================================================================
# FUTEFATEC - GERADOR DE LINK PÚBLICO INSTANTÂNEO (100% GRÁTIS)
# ==============================================================================
# Este script cria um link público seguro com HTTPS para o seu FuteFatec
# sem precisar cadastrar cartão ou configurar servidores complexos.
# ==============================================================================

PORTA=8085

echo "==============================================================="
echo "   GERANDO LINK PÚBLICO PARA O FUTEFATEC (PORTA $PORTA)       "
echo "==============================================================="

# 1. Verifica se o cloudflared já está presente
if [ ! -f "./cloudflared" ]; then
    echo ">> Baixando o gerador de túnel seguro gratuito do Cloudflare..."
    curl -L https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64 -o cloudflared
    chmod +x cloudflared
fi

if [ -f "./cloudflared" ]; then
    echo ">> Iniciando túnel seguro HTTPS do Cloudflare..."
    echo ">> Copie o link 'https://....trycloudflare.com' que aparecer abaixo e mande para a galera!"
    echo "==============================================================="
    ./cloudflared tunnel --url "http://localhost:$PORTA"
else
    echo ">> Usando túnel via SSH nativo..."
    ssh -R 80:localhost:$PORTA nokey@localhost.run
fi

