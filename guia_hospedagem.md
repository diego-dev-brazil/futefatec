# 🚀 Guia de Hospedagem e Publicação do FuteFatec

Este guia explica como disponibilizar o **FuteFatec** para acesso público na internet com segurança, controle de acesso e persistência dos dados cadastrados.

---

## 🔒 Regras de Segurança Ativas

1. **Usuários Comuns (Público Geral)**:
   - Podem cadastrar seus times com até 8 atletas e escudo.
   - Enquanto o administrador não liberar o chaveamento, verão a mensagem oficial:
     > *"🔒 O chaveamento será liberado quando todas as equipes estiverem cadastradas."*
   - Quando liberado, o público visualiza todas as partidas e o modal de síntese de cada equipe em modo **somente leitura** (não podem alterar placares nem excluir times).
2. **Administrador do Torneio**:
   - Acesso exclusivo pelo botão **`🔐 Área Admin`** no cabeçalho.
   - Usuário padrão: `admin`
   - Senha padrão: `fatec2026` (alterável via variável de ambiente `ADMIN_PASSWORD`).
   - Recursos exclusivos:
     - Botão para **Liberar / Bloquear** a visualização do chaveamento com 1 clique.
     - Sortear/gerar chaveamento e registrar placares.
     - Editar dados ou excluir equipes do banco relacional.

---

## 🌐 Opção 1: Link Público Imediato (Ideal para Apresentações e Testes Hoje)

Se você já tem o servidor rodando na sua máquina e quer gerar um link público seguro com **HTTPS** para a turma da FATEC acessar pelo celular ou computador agora mesmo, use o **Cloudflare Tunnel** ou o **Ngrok**:

### Com Cloudflare Tunnel (100% Grátis, sem cadastro):
1. Baixe o executável rápido do Cloudflare (`cloudflared`):
   ```bash
   # No Linux:
   wget https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64 -O cloudflared
   chmod +x cloudflared
   ```
2. Inicie o túnel apontando para a porta `8085`:
   ```bash
   ./cloudflared tunnel --url http://localhost:8085
   ```
3. O Cloudflare gerará uma URL pública (ex: `https://torneio-fatec-xyz.trycloudflare.com`). Basta enviar para os alunos!

---

## ☁️ Opção 2: Hospedagem 24/7 na Nuvem com Render / Railway

Para manter o site no ar 24 horas por dia:

### No Render.com (Plano Free com Docker):
1. Suba o projeto para um repositório no **GitHub**.
2. Acesse [render.com](https://render.com) e clique em **New +** -> **Web Service**.
3. Conecte o repositório do FuteFatec.
4. O Render detectará automaticamente o [`Dockerfile`](./Dockerfile).
5. Adicione as variáveis de ambiente:
   - `PORT`: `8085`
   - `ADMIN_PASSWORD`: `<sua-senha-secreta>`
6. **Importante para salvar dados:** Em *Disks*, adicione um disco persistente montado em `/app/data` para que o banco H2 não seja resetado ao reiniciar a máquina.

---

## 🐳 Opção 3: Servidor VPS Próprio com Docker Compose

Se você possui uma VPS (Oracle Cloud Free Tier, DigitalOcean, Linode, AWS EC2, GCP):

1. Clone o repositório na sua VPS:
   ```bash
   git clone <url-do-repositorio>
   cd files
   ```
2. Inicie os containers com persistência total:
   ```bash
   docker compose up -d --build
   ```
3. O serviço subirá na porta `8085`. Os volumes `futefatec_data` e `futefatec_uploads` garantirão que os times e as fotos nunca sejam apagados.
4. Para parar ou reiniciar:
   ```bash
   docker compose restart
   # ou
   docker compose down
   ```

---

## 💾 Onde Ficam Salvos os Dados?

- **Banco de Dados Relacional:**
  - Arquivo local: `data/futefatec.mv.db`
  - Para fazer backup antes do torneio, basta copiar esse arquivo:
    ```bash
    cp data/futefatec.mv.db data/futefatec_backup.mv.db
    ```
- **Escudos e Fotos dos Times:**
  - Diretório local: `uploads/`

