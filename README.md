# VoxelCore

Servidor dedicado de **Minecraft Bedrock** preparado para mundo persistente,
add-ons, backups e acesso externo. Ele roda com Docker Compose e usa o servidor
Bedrock oficial, baixado da Mojang na primeira inicialização.

## O que já está pronto

- servidor Bedrock atualizado automaticamente;
- mundo e configurações persistentes na pasta `data/`;
- instalador seguro de `.mcaddon`, `.mcpack` e `.zip`;
- ativação automática de behavior packs e resource packs no mundo;
- túnel UDP opcional pela Playit para redes sem redirecionamento de porta;
- allowlist, operadores, resource pack obrigatório e modo online;
- backup consistente com parada e reinício automático;
- ambiente de Codespaces e testes no GitHub Actions.

## Importante: Codespaces não é hospedagem 24/7

O GitHub Codespaces serve para desenvolver e testar. Ele para após inatividade
(30 minutos por padrão), tem franquia mensal e o encaminhamento normal de portas
é HTTP/HTTPS. Minecraft Bedrock usa **UDP 19132**, portanto o modo Codespaces
precisa do túnel Playit e continuará disponível somente enquanto o Codespace
estiver ligado.

Para ficar realmente 24 horas, use o mesmo repositório em um PC ligado ou em um
VPS Ubuntu 22.04+ **x86_64/AMD64** com Docker. O `restart: unless-stopped` já faz
o servidor voltar após reinicializações da máquina.

Documentação oficial:

- [ciclo de vida do Codespaces](https://docs.github.com/en/codespaces/about-codespaces/understanding-the-codespace-lifecycle);
- [cobrança e franquia do Codespaces](https://docs.github.com/billing/managing-billing-for-github-codespaces/about-billing-for-github-codespaces);
- [servidor Bedrock oficial](https://www.minecraft.net/en-us/download/server/bedrock).

## Início rápido no Codespaces

1. No GitHub, abra **Code → Codespaces → Create codespace on main**.
2. Quando o terminal estiver pronto, execute:

   ```bash
   ./voxelcore init
   ```

3. Abra o arquivo `.env` e ajuste:

   ```dotenv
   EULA=TRUE
   ALLOW_LIST_USERS=SeuGamertag
   OPS=SeuGamertag
   PLAYIT_SECRET=sua_chave_secreta
   ```

   Use `EULA=TRUE` somente depois de ler e aceitar a
   [EULA do Minecraft](https://www.minecraft.net/eula). Crie a chave do agente
   no [painel da Playit](https://playit.gg/account/agents) e configure lá um
   túnel **Minecraft Bedrock/UDP** apontando para `127.0.0.1:19132`.

4. Envie o add-on para o Codespace e instale:

   ```bash
   ./voxelcore install-addon /caminho/MeuAddon.mcaddon
   ```

5. Inicie servidor e túnel:

   ```bash
   ./voxelcore start-tunnel
   ./voxelcore logs
   ```

6. No Minecraft Bedrock, use o endereço e a porta mostrados no painel da
   Playit.

## Hospedagem realmente contínua

Em uma máquina Ubuntu 22.04 ou mais recente, com Docker e Docker Compose v2:

```bash
git clone https://github.com/gustavogordinbr-spec/VoxelCore.git
cd VoxelCore
./voxelcore init
```

Edite `.env`, aceite a EULA e preencha ao menos seu gamertag. Depois escolha:

```bash
# VPS com a porta UDP 19132 liberada no firewall
./voxelcore start

# Ou máquina sem porta pública, usando Playit
./voxelcore start-tunnel
```

No VPS, libere **UDP**, não TCP. Se usar UFW:

```bash
sudo ufw allow 19132/udp
```

## Add-ons

O comando `install-addon` faz quatro coisas: valida o arquivo compactado, lê os
`manifest.json`, copia cada pack para a pasta correta e atualiza
`world_behavior_packs.json`/`world_resource_packs.json`.

```bash
./voxelcore install-addon MeuAddon.mcaddon
```

Add-ons que dependem de experimentos podem exigir um mundo criado no próprio
Minecraft com os experimentos ligados e depois exportado. Um instalador não
consegue habilitar com segurança todo experimento apenas alterando JSON.

## Comandos úteis

| Comando | Função |
|---|---|
| `./voxelcore status` | Mostra o estado do servidor e do túnel |
| `./voxelcore logs` | Acompanha o console Bedrock |
| `./voxelcore tunnel-logs` | Acompanha o agente Playit |
| `./voxelcore command say Ola` | Envia um comando ao servidor |
| `./voxelcore backup` | Salva `data/` em `backups/` com parada segura |
| `./voxelcore update` | Baixa imagens novas e reinicia o servidor |
| `./voxelcore restart` | Reinicia o servidor |
| `./voxelcore stop` | Encerra servidor e túnel |

## Segurança e dados

- `.env`, mundos, add-ons e backups não são enviados ao GitHub;
- a allowlist começa ligada; adicione os gamertags autorizados;
- nunca publique `PLAYIT_SECRET`;
- instale add-ons apenas de fontes confiáveis;
- faça backups antes de atualizar add-ons ou a versão do Bedrock.

O projeto usa a imagem comunitária
[`itzg/minecraft-bedrock-server`](https://github.com/itzg/docker-minecraft-bedrock-server),
que baixa o binário do servidor diretamente da Mojang e só inicia após a
aceitação explícita da EULA. O túnel usa o agente oficial de código aberto da
[Playit](https://github.com/playit-cloud/playit-agent).
