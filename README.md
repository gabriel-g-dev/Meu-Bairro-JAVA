# Meu Bairro API

Backend em Java/Spring Boot do **Meu Bairro**, a solução CitizenTech do grupo para a Global Solution FIAP 2026/2 (2º ano de ADS). Tema: zeladoria colaborativa para identificar, priorizar e acompanhar problemas dos espaços públicos.

O cidadão registra problemas do bairro (buraco na rua, poste apagado, lixo acumulado, alagamento, calçada quebrada), com foto e localização opcionais, e acompanha o andamento pelo histórico da ocorrência. Uma IA generativa faz a triagem, sugerindo categoria, prioridade e uma justificativa. O gestor consulta as ocorrências, ajusta a prioridade e atualiza o status.

É a mesma API consumida pelo aplicativo da disciplina de Mobile.

## Integrantes

| Nome | RM | Turma |
| --- | --- | --- |
| (preencher) | (preencher) | (preencher) |

## Tecnologias

- Java 17 e Spring Boot 3.5.16 (Web, Data JPA, Validation, Security, AMQP)
- Spring Security com JWT (biblioteca jjwt 0.12.6) e senhas com BCrypt
- Spring AI 1.1.8 com Google Gemini (modelo `gemini-3.5-flash-lite`)
- RabbitMQ para mensageria
- H2 em arquivo no desenvolvimento, com driver do PostgreSQL já incluído
- Fotos das ocorrências guardadas em uma pasta do servidor ou, se configurado, no Azure Blob Storage (API REST com token SAS)
- springdoc-openapi 2.10.0 (Swagger)
- JUnit 5, Mockito, MockMvc, Awaitility e Apache Qpid (broker AMQP usado só nos testes)

## Arquitetura

Opção escolhida no requisito de arquitetura distribuída: **A) RabbitMQ**, com evento publicado e consumido de forma assíncrona.

```mermaid
flowchart LR
    App[App Mobile] -->|HTTP + JWT| API[Meu Bairro API<br/>Spring Boot]
    API -->|Spring Data JPA| DB[(H2 / PostgreSQL)]
    API -->|foto da ocorrência| FOTOS[(Pasta do servidor<br/>ou Azure Blob Storage)]
    API -->|publica OcorrenciaCriadaEvento| EX{{exchange<br/>meubairro.ocorrencias}}
    EX -->|routing key<br/>ocorrencia.criada| FILA[[fila<br/>meubairro.ocorrencias.criadas]]
    FILA -->|@RabbitListener| CONS[OcorrenciaEventoListener]
    CONS -->|Spring AI ChatClient| LLM[Google Gemini]
    CONS -->|histórico e triagem| DB
```

Fluxo principal:

1. O cidadão envia `POST /ocorrencias` (multipart/form-data). Se houver foto, a API grava o arquivo na pasta de fotos do servidor (ou no Azure Blob Storage, se estiver configurado). A ocorrência é salva com status `ABERTA`, prioridade `MEDIA` e análise da IA `PENDENTE`. O `usuarioId` vem do token, nunca do formulário.
2. Depois de salvar, a API publica o evento `OcorrenciaCriadaEvento` (JSON) na exchange `meubairro.ocorrencias` e responde `201` para o app.
3. O `OcorrenciaEventoListener` consome o evento da fila `meubairro.ocorrencias.criadas`, registra no histórico que recebeu o evento e chama a IA.
4. A IA devolve categoria, prioridade e justificativa. A API valida a resposta e grava a sugestão. Se a ocorrência ainda estiver `ABERTA` e o gestor não tiver definido a prioridade manualmente, a prioridade sugerida passa a valer. A categoria escolhida pelo cidadão não é alterada; a sugestão da IA fica disponível para o gestor.
5. O andamento aparece em `GET /ocorrencias/{id}/historico` e a triagem em `GET /ocorrencias/{id}/triagem`.

Organização do código (`src/main/java/br/com/fiap/meubairro`):

| Pacote | Conteúdo |
| --- | --- |
| `controller` | Endpoints REST |
| `service` | Regras de negócio, triagem, chamada à IA e envio de fotos |
| `repository` | Repositórios Spring Data JPA |
| `entity` | Entidades JPA e enums |
| `dto` | Records de entrada e saída da API |
| `security` | Geração e validação do JWT, filtro e respostas 401/403 |
| `messaging` | Evento, publicador e consumidor do RabbitMQ |
| `exception` | Exceções e tratamento global de erros |
| `config` | Segurança, prefixo `/api`, RabbitMQ, IA, JSON em UTF-8, Swagger e carga inicial |

Modelo de dados: `Usuario` 1-N `Ocorrencia` N-1 `Categoria`, e `Ocorrencia` 1-N `HistoricoOcorrencia`. A ocorrência também guarda a categoria sugerida pela IA (N-1 `Categoria`).

## Como executar (Windows e VS Code)

### Pré-requisitos

- JDK 17
- RabbitMQ, pelo Docker Desktop ou instalado no Ubuntu do WSL (veja a seção de mensageria)
- Uma chave da API do Gemini, para a triagem por IA

Não é preciso instalar o Maven: o projeto usa o Maven Wrapper (`mvnw.cmd`).

A API também sobe sem RabbitMQ e sem chave do Gemini. Nesse caso o cadastro de ocorrências (com ou sem foto) continua funcionando, o histórico registra que a fila não estava disponível, a análise por IA responde `503` e o log mostra tentativas de reconexão com o RabbitMQ a cada 5 segundos. Se o endereço do RabbitMQ não responder, cada tentativa de conexão desiste em 3 segundos, para o cadastro não ficar travado.

### Passo a passo

```powershell
docker compose up -d rabbitmq
$env:GEMINI_API_KEY = "cole-aqui-a-sua-chave"
.\mvnw.cmd spring-boot:run
```

Sem Docker, suba o RabbitMQ pelo Ubuntu do WSL (passos na seção de mensageria) e pule a primeira linha.

- Swagger: <http://localhost:8080/swagger-ui.html>
- Painel do RabbitMQ: <http://localhost:15672> (usuário `guest`, senha `guest`)

No VS Code, abra a pasta do projeto, instale as extensões recomendadas (Java e Spring Boot) e rode a configuração **Meu Bairro API** (F5). Para usar a IA pelo F5, defina `GEMINI_API_KEY` como variável de ambiente do usuário do Windows e reabra o VS Code. Não coloque a chave no `launch.json` nem em outro arquivo do repositório.

### Variáveis de ambiente

Nenhuma credencial real fica no código. Os valores padrão servem para rodar localmente.

| Variável | Padrão | Uso |
| --- | --- | --- |
| `PORT` | `8080` | Porta da API |
| `DB_URL` | `jdbc:h2:file:./data/meubairro;LOCK_TIMEOUT=10000` | URL JDBC do banco |
| `DB_USERNAME` / `DB_PASSWORD` | `sa` / vazio | Usuário e senha do banco |
| `JWT_SECRET` | vazio | Segredo do JWT, com pelo menos 32 caracteres. Se ficar vazio, a API gera uma chave aleatória a cada inicialização e os tokens antigos deixam de valer |
| `JWT_EXPIRACAO_MINUTOS` | `120` | Validade do token |
| `RABBITMQ_HOST` / `RABBITMQ_PORT` | `localhost` / `5672` | Endereço do RabbitMQ |
| `RABBITMQ_USERNAME` / `RABBITMQ_PASSWORD` | `guest` / `guest` | Usuário padrão do RabbitMQ local |
| `GEMINI_API_KEY` | vazio | Chave da API do Gemini. Sem ela, a IA fica desativada |
| `GEMINI_MODELO` | `gemini-3.5-flash-lite` | Modelo do Gemini usado na triagem |
| `IA_HABILITADA` | `true` | `false` desliga as chamadas à IA |
| `FOTOS_PASTA` | `./data/fotos` | Pasta onde a API guarda as fotos quando o Azure não está configurado |
| `FOTOS_AZURE_CONTAINER_URL` | vazio | Opcional: URL HTTPS do container, no formato `https://<conta>.blob.core.windows.net/<container>` |
| `FOTOS_AZURE_SAS_TOKEN` | vazio | Opcional: token SAS do container, com permissões de criar, gravar e excluir |
| `CORS_ORIGENS` | `http://localhost:8081,http://localhost:19006` | Origens liberadas para o app rodando no navegador (Expo Web), separadas por vírgula |
| `USUARIOS_TESTE_HABILITADO` | `true` | Cria os usuários de teste na inicialização |
| `USUARIOS_TESTE_SENHA` | `Teste@123` | Senha dos usuários de teste |

Exemplo no PowerShell, gerando um segredo para o JWT:

```powershell
$bytes = New-Object byte[] 48
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$env:JWT_SECRET = [Convert]::ToBase64String($bytes)
.\mvnw.cmd spring-boot:run
```

### Banco de dados

- Por padrão, a API usa H2 em arquivo (`./data/meubairro.mv.db`). Os dados continuam salvos depois de reiniciar a aplicação.
- As tabelas são criadas pelo Hibernate (`ddl-auto=update`) e as seis categorias padrão são cadastradas na primeira execução.
- Para usar PostgreSQL: `docker compose --profile postgres up -d` e defina `DB_URL=jdbc:postgresql://localhost:5432/meubairro`, `DB_USERNAME=meubairro` e `DB_PASSWORD=meubairro_dev`. O driver já está no projeto, mas essa configuração ainda não foi testada.

### Gemini

1. Crie uma chave da API pelo Google AI Studio (instruções em <https://aistudio.google.com/docs/api-key>).
2. Defina a variável `GEMINI_API_KEY` antes de iniciar a API: só no terminal atual, com `$env:GEMINI_API_KEY = "sua-chave"`, ou de forma permanente para o seu usuário do Windows, com `[Environment]::SetEnvironmentVariable("GEMINI_API_KEY", "sua-chave", "User")`. A forma permanente vale para os terminais abertos depois e para o VS Code depois de reaberto. A chave não deve ser gravada em nenhum arquivo do projeto.

O modelo padrão é o `gemini-3.5-flash-lite`, versão estável da linha Flash-Lite, sem data de desligamento anunciada e indicada pelo Google para projetos novos. Ele pode ser trocado pela variável `GEMINI_MODELO`. Pela documentação do Google, o modelo tem uso gratuito com limites de requisições, consultados no Google AI Studio, e no nível gratuito o conteúdo enviado pode ser usado para melhorar os produtos do Google. Por isso a API envia ao Gemini apenas o título e a descrição da ocorrência (veja a seção da IA).

### Fotos

Por padrão, a API guarda as fotos na pasta `./data/fotos` (variável `FOTOS_PASTA`) e as devolve em `GET /fotos/{arquivo}`. Essa rota é pública porque o app exibe a imagem sem enviar o token; o nome do arquivo é um UUID aleatório.

- A `fotoUrl` é uma URL completa montada com o endereço que o app usou para chamar a API. Assim a mesma foto abre no emulador (`http://10.0.2.2:8080/fotos/...`), no celular (`http://<IP do computador>:8080/fotos/...`) e na API publicada.
- Com a API publicada atrás de um proxy HTTPS (como um serviço de aplicativo na nuvem), a `fotoUrl` sai com `https://`, porque a API lê os cabeçalhos `X-Forwarded-*` enviados pelo proxy.
- Formatos aceitos: JPEG, PNG e WEBP, identificados pelo conteúdo do arquivo, e não pela extensão ou pelo tipo informado no envio. Tamanho máximo: 5 MB.
- No `PUT`, uma nova foto substitui a anterior, que é apagada. Ao excluir a ocorrência, a foto também é apagada.
- Em um deploy, a pasta de fotos precisa estar em um disco persistente; se o disco do servidor for temporário, use o Azure Blob Storage.

Opcionalmente, as fotos podem ficar no Azure Blob Storage, com URL HTTPS pública:

1. No portal do Azure, crie uma conta de armazenamento e habilite **Allow Blob anonymous access** na configuração da conta.
2. Crie um container com nível de acesso **Blob** (leitura anônima só dos arquivos), para que a `fotoUrl` abra no app sem autenticação.
3. Em **Shared access tokens** do container, gere um token SAS com as permissões **Create**, **Write** e **Delete**, somente HTTPS e com data de validade.
4. Defina `FOTOS_AZURE_CONTAINER_URL` com a URL do container e `FOTOS_AZURE_SAS_TOKEN` com o token gerado. Com as duas variáveis definidas, a API passa a enviar as fotos ao Azure.

## Usuários de teste

| Perfil | E-mail | Senha |
| --- | --- | --- |
| CIDADAO | `cidadao@meubairro.com` | `Teste@123` |
| GESTOR | `gestor@meubairro.com` | `Teste@123` |

Eles são criados automaticamente enquanto `USUARIOS_TESTE_HABILITADO=true`. Em um ambiente publicado, desligue essa opção ou troque a senha por `USUARIOS_TESTE_SENHA`.

O cadastro público (`POST /auth/register`) sempre cria um CIDADAO, mesmo que o JSON envie outros campos. Um novo gestor só pode ser criado por um gestor já logado, em `POST /usuarios/gestores`.

## Autenticação

1. Faça login em `POST /auth/login` e copie o campo `token`.
2. Envie o cabeçalho `Authorization: Bearer <token>` nas demais requisições.
3. No Swagger, use o botão **Authorize** e cole apenas o token.

O token é um JWT assinado (HMAC-SHA) que expira em 120 minutos e carrega o e-mail, o id e o perfil do usuário. Sem token, com token inválido ou expirado, a API responde `401`. Não há sessão no servidor: para sair, o app apaga o token.

## Contrato da API para o app Mobile

A API aceita as rotas com e sem o prefixo `/api` (`/api/ocorrencias` e `/ocorrencias` são a mesma rota, com as mesmas regras de segurança). Assim, o `EXPO_PUBLIC_API_URL` do app pode terminar em `/api`, como no `.env.example` do app Mobile:

| Onde o app roda | `EXPO_PUBLIC_API_URL` |
| --- | --- |
| Emulador Android | `http://10.0.2.2:8080/api` |
| Simulador iOS ou Expo Web no mesmo computador | `http://localhost:8080/api` |
| Celular físico (Expo Go) | `http://<IP do computador na rede>:8080/api` |
| API publicada | `https://<endereço da API>/api` |

- No celular físico, o computador e o celular precisam estar na mesma rede. Na primeira vez que a API sobe, o Windows pergunta se o Java pode receber conexões da rede: permita. Se o celular continuar sem acesso, libere a porta como administrador com `New-NetFirewallRule -DisplayName "Meu Bairro API" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow`.
- O APK gerado para a entrega bloqueia HTTP sem criptografia no Android, porque o `app.json` do app não libera esse tráfego. Para o APK, a API precisa estar publicada em HTTPS. Pelo Expo Go, na mesma rede, o HTTP funciona.
- O CORS só vale para o app rodando no navegador (Expo Web); apps Android e iOS não passam por ele.

- As respostas são JSON em UTF-8 e as datas seguem o formato `2026-11-09T10:00:00`.
- O cadastro e a alteração de ocorrência usam `multipart/form-data`; as demais rotas usam JSON.
- Todo erro devolve um JSON com o campo `message`.

### Endpoints do contrato

| Método | Rota | Quem pode | Envio | Resposta |
| --- | --- | --- | --- | --- |
| POST | `/auth/register` | Público | JSON `{ nome, email, senha }` | `201` `{ id, nome, email }`; `400` dados inválidos; `409` e-mail já cadastrado |
| POST | `/auth/login` | Público | JSON `{ email, senha }` | `200` `{ token, usuario: { id, nome, email } }`; `400` dados inválidos; `401` e-mail ou senha inválidos |
| GET | `/ocorrencias` | Logado | - | `200` array com todas as ocorrências, das mais recentes para as mais antigas |
| GET | `/ocorrencias/{id}` | Logado | - | `200` ocorrência; `404` se não existir |
| POST | `/ocorrencias` | CIDADAO | multipart | `201` ocorrência e cabeçalho `Location` |
| PUT | `/ocorrencias/{id}` | Dono, se `ABERTA` | multipart | `200` ocorrência; `403` se não for o dono; `409` se não estiver `ABERTA` |
| DELETE | `/ocorrencias/{id}` | Dono se `ABERTA`, ou GESTOR | - | `204` sem corpo; `403` se não for o dono; `409` se não estiver `ABERTA` |

Campos do formulário de ocorrência:

| Campo | Obrigatório | Regra |
| --- | --- | --- |
| `titulo` | sim | 5 a 120 caracteres |
| `descricao` | sim | 10 a 1000 caracteres |
| `categoria` | sim | `BURACO`, `ILUMINACAO`, `LIXO`, `ALAGAMENTO`, `CALCADA`, `OUTRO` ou outro código criado pelo gestor |
| `endereco` | sim | até 200 caracteres |
| `latitude` | não | entre -90 e 90 |
| `longitude` | não | entre -180 e 180 |
| `foto` | não | arquivo JPEG, PNG ou WEBP de até 5 MB |

No `PUT`, se `latitude`, `longitude` ou `foto` não forem enviados, os valores atuais são mantidos.

Ocorrência devolvida pela API:

```json
{
  "id": 1,
  "titulo": "Buraco na rua",
  "descricao": "Buraco grande perto da esquina",
  "categoria": "BURACO",
  "status": "ABERTA",
  "endereco": "Rua Exemplo, 123",
  "latitude": -23.55,
  "longitude": -46.63,
  "fotoUrl": null,
  "criadoEm": "2026-11-09T16:09:49",
  "usuarioId": 1
}
```

`fotoUrl` vem `null` quando a ocorrência não tem foto; com foto, vem a URL completa da imagem, por exemplo `http://192.168.0.10:8080/fotos/4def676b-c741-4d91-bdf1-e352dcafdf32.png`. Os valores de `status` são `ABERTA`, `EM_ANALISE` e `RESOLVIDA`.

Login:

```json
POST /auth/login
{ "email": "cidadao@meubairro.com", "senha": "Teste@123" }

200 OK
{
  "token": "eyJhbGciOi...",
  "usuario": { "id": 1, "nome": "Cidadão de Teste", "email": "cidadao@meubairro.com" }
}
```

### Outras rotas

| Método | Rota | Quem pode | O que faz |
| --- | --- | --- | --- |
| GET | `/usuarios/me` | Logado | Dados e perfil do usuário logado |
| POST | `/usuarios/gestores` | GESTOR | Cadastra outro gestor (mesmo JSON do cadastro) |
| GET | `/categorias` | Logado | Lista as categorias com `codigo`, `nome` e `descricao` |
| GET | `/categorias/{id}` | Logado | Detalha uma categoria |
| POST | `/categorias` | GESTOR | Cria uma categoria: `{ "codigo": "SINALIZACAO", "nome": "Sinalização", "descricao": "Placas danificadas" }` |
| PUT | `/categorias/{id}` | GESTOR | Atualiza uma categoria (mesmo JSON) |
| DELETE | `/categorias/{id}` | GESTOR | Exclui uma categoria sem ocorrências (`204`) |
| GET | `/fotos/{arquivo}` | Público | Devolve a imagem de uma ocorrência (é o endereço da `fotoUrl`) |
| GET | `/ocorrencias/paginado` | Logado | Lista paginada, com filtros `status`, `categoria` e `prioridade` |
| GET | `/ocorrencias/resumo` | GESTOR | Totais por status e prioridade |
| PATCH | `/ocorrencias/{id}/status` | GESTOR | Altera o status: `{ "status": "EM_ANALISE", "observacao": "Equipe agendada" }` (observação opcional, até 300 caracteres) |
| PATCH | `/ocorrencias/{id}/prioridade` | GESTOR | Define a prioridade manualmente: `{ "prioridade": "ALTA" }` |
| GET | `/ocorrencias/{id}/triagem` | Logado | Prioridade atual e última análise da IA |
| POST | `/ocorrencias/{id}/analise-ia` | Dono ou GESTOR | Pede uma nova análise da IA |
| GET | `/ocorrencias/{id}/historico` | Logado | Histórico de andamento |

Paginação: `GET /ocorrencias/paginado?page=0&size=10&sort=dataCriacao,desc`. O padrão é 10 itens por página, ordenados pelos mais recentes, com no máximo 50 por página. A ordenação aceita `id`, `titulo`, `status`, `dataCriacao` e `dataAtualizacao`; outro campo responde `400`. A resposta traz `conteudo`, `pagina`, `tamanho`, `totalElementos`, `totalPaginas` e `ultima`.

Formato da triagem (`GET /ocorrencias/{id}/triagem` e `POST /ocorrencias/{id}/analise-ia`), com valores ilustrativos:

```json
{
  "ocorrenciaId": 1,
  "prioridade": "ALTA",
  "prioridadeDefinidaPeloGestor": false,
  "statusAnaliseIa": "CONCLUIDA",
  "categoriaSugerida": "BURACO",
  "prioridadeSugerida": "ALTA",
  "justificativa": "Frase curta gerada pela IA explicando a prioridade.",
  "dataAnalise": "2026-11-09T10:00:05"
}
```

`statusAnaliseIa` pode ser `PENDENTE`, `CONCLUIDA` ou `FALHA`. Os tipos do histórico são `CRIACAO`, `ATUALIZACAO`, `FILA`, `ANALISE_IA`, `STATUS` e `PRIORIDADE`.

### Erros

Todos os erros seguem o mesmo formato. Exemplo real de cadastro inválido:

```json
{
  "timestamp": "2026-11-09T16:09:49",
  "status": 400,
  "error": "Bad Request",
  "message": "Existem campos inválidos na requisição",
  "path": "/auth/register",
  "fields": [
    { "field": "nome", "message": "O nome é obrigatório" },
    { "field": "senha", "message": "A senha deve ter entre 6 e 60 caracteres" },
    { "field": "email", "message": "O e-mail informado é inválido" }
  ]
}
```

`fields` só tem itens em erros de validação. Nos outros casos, o app deve mostrar `message`.

| Status | Quando acontece |
| --- | --- |
| 400 | Campos inválidos, categoria fora da lista, foto em formato inválido, JSON malformado ou parâmetro inválido |
| 401 | Sem token, token inválido ou expirado, e-mail ou senha incorretos |
| 403 | Perfil sem permissão ou ocorrência de outro cidadão |
| 404 | Ocorrência ou rota inexistente |
| 409 | E-mail ou código de categoria já cadastrado, ocorrência fora do status `ABERTA`, status repetido, ocorrência já resolvida ou categoria em uso |
| 413 | Foto maior que 5 MB |
| 415 | Formato do corpo diferente do esperado, como JSON no cadastro de ocorrência |
| 503 | IA indisponível ou falha ao salvar a foto |
| 500 | Erro inesperado no servidor |

## Regras de negócio

- Só o cidadão registra ocorrências, e toda ocorrência começa com status `ABERTA`.
- Qualquer usuário logado pode listar e consultar as ocorrências.
- Só o cidadão que registrou pode alterar a ocorrência, e somente enquanto ela estiver `ABERTA`.
- O cidadão exclui só as próprias ocorrências com status `ABERTA`. O gestor pode excluir qualquer uma.
- O gestor altera status e prioridade. Uma ocorrência `RESOLVIDA` não muda mais de status, e o status novo precisa ser diferente do atual.
- Depois que o gestor define a prioridade manualmente, a IA não altera mais a prioridade daquela ocorrência.
- O gestor cria, altera e exclui categorias. As seis categorias usadas pelo app (`BURACO`, `ILUMINACAO`, `LIXO`, `ALAGAMENTO`, `CALCADA` e `OUTRO`) não podem ser excluídas nem mudar de código, e uma categoria com ocorrências não pode ser excluída. Categorias novas passam a valer no cadastro de ocorrências e na triagem da IA.
- As alterações de uma ocorrência usam bloqueio de linha (`PESSIMISTIC_WRITE`) para que o consumidor da fila e o gestor não sobrescrevam a alteração um do outro. A chamada à IA, que é demorada, acontece fora desse bloqueio.

## Funcionalidade com IA (Spring AI)

A IA faz a **triagem das ocorrências**: lê o título e a descrição e sugere a categoria, a prioridade (`BAIXA`, `MEDIA` ou `ALTA`) e uma justificativa curta. Não é um chat: a análise faz parte do fluxo de cadastro.

- **Quando roda:** automaticamente, pelo consumidor da fila, após cada cadastro; e sob demanda em `POST /ocorrencias/{id}/analise-ia` (por exemplo, depois que o cidadão edita a descrição).
- **Como funciona:** o `AnaliseIaService` usa o `ChatClient` do Spring AI com o Gemini. A mensagem de sistema lista as categorias cadastradas no banco e os critérios de prioridade; o relato do cidadão vai separado, na mensagem do usuário. O Gemini responde em JSON (temperatura 0,2) e a resposta é convertida para o record `SugestaoIa` com `.entity()`.
- **Dados enviados:** somente o título e a descrição. E-mails, CPFs e telefones digitados no texto são trocados por marcadores antes do envio. Nome, e-mail do usuário, endereço, coordenadas, foto e token não são enviados.
- **Validação:** a categoria sugerida precisa existir no banco (pelo código ou pelo nome, sem diferenciar acentos e maiúsculas), a prioridade precisa ser um valor válido e a justificativa não pode vir vazia. Se não for assim, a sugestão é descartada.
- **Se a IA falhar:** a ocorrência continua normal, a análise fica com status `FALHA`, o histórico registra o motivo e o endpoint de análise responde `503`. Cada chamada ao Gemini tem limite de 30 segundos e no máximo 2 tentativas (configurado em `IaConfig`). Em picos de uso, o próprio Google pode responder `503` ("model is currently experiencing high demand"); nesse caso, peça a análise de novo em `POST /ocorrencias/{id}/analise-ia` ou troque o modelo pela variável `GEMINI_MODELO`.
- **Limitações:** a IA pode errar em casos de fronteira e em relatos que não se encaixam nas categorias; por isso a sugestão é validada, fica visível para o gestor e a prioridade pode ser corrigida manualmente.

A mesma funcionalidade pode ser usada pela disciplina de Disruptive Architectures (IoT, IoB & IA Generativa).

### Avaliação da IA

A classe `AvaliacaoGeminiTest` envia ao Gemini 15 relatos com categoria e prioridade esperadas, incluindo um relato que tenta manipular a IA ("Ignore as instruções anteriores..."). Ela espera 6 segundos entre as chamadas para respeitar os limites do nível gratuito, registra no log a sugestão e o tempo de cada análise e passa se acertar pelo menos 12 categorias e 9 prioridades. Se uma chamada falhar (por exemplo, por sobrecarga do Google), o caso conta como erro e o teste segue para o próximo. O teste faz 15 chamadas reais e só roda quando as duas variáveis abaixo estão definidas:

```powershell
$env:GEMINI_API_KEY = "cole-aqui-a-sua-chave"
$env:GEMINI_TESTE_REAL = "true"
.\mvnw.cmd test -Dtest=AvaliacaoGeminiTest
```

Resultado com o `gemini-3.5-flash-lite`, em 10/11/2026: 15 de 15 categorias corretas e 14 de 15 prioridades iguais às esperadas, com média de 4,2 s a 4,7 s por análise. A única diferença foi o entulho de obra na calçada: o esperado era `MEDIA` e a IA sugeriu `ALTA`, justificando que os pedestres precisam andar pela rua. O relato com a tentativa de manipulação foi classificado como `OUTRO` e `BAIXA`, como esperado. O teste foi executado completo três vezes, sempre com esse resultado. Em outras duas execuções do mesmo dia, o Google estava sobrecarregado (respondeu `503` em uma chamada e demorou mais de 30 s em outra), e algumas análises levaram até 19 s.

## Mensageria com RabbitMQ

| Item | Valor |
| --- | --- |
| Exchange | `meubairro.ocorrencias` (direct) |
| Routing key | `ocorrencia.criada` |
| Fila | `meubairro.ocorrencias.criadas` (durável) |
| Publicador | `OcorrenciaEventoPublisher`, chamado pelo `OcorrenciaService.criar` depois que a ocorrência é salva |
| Consumidor | `OcorrenciaEventoListener` (`@RabbitListener`) |

Mensagem publicada (JSON):

```json
{ "ocorrenciaId": 1, "usuarioId": 1, "titulo": "Buraco na rua", "dataCriacao": "2026-11-09T16:09:49" }
```

O consumidor registra no histórico que recebeu o evento e executa a triagem da IA. Se der erro inesperado, a mensagem é tentada 3 vezes e depois descartada, sem voltar para a fila indefinidamente. Se o RabbitMQ estiver fora do ar, o cadastro não falha: o histórico registra que a fila estava indisponível e a análise pode ser pedida manualmente.

### Como subir o RabbitMQ

Com Docker Desktop: `docker compose up -d rabbitmq`.

Sem Docker, no Ubuntu do WSL (rode no terminal do Ubuntu):

```bash
sudo apt update && sudo apt install -y rabbitmq-server
echo "listeners.tcp.default = 0.0.0.0:5672" | sudo tee /etc/rabbitmq/rabbitmq.conf
sudo rabbitmq-plugins enable rabbitmq_management
sudo systemctl restart rabbitmq-server
```

A segunda linha faz o RabbitMQ escutar em IPv4. Sem ela, o WSL repassa a porta 5672 ao Windows só por IPv6 e a API não conecta em `localhost`. Deixe um terminal do Ubuntu aberto enquanto usar a API: o WSL desliga a distribuição quando não há terminal aberto, e o RabbitMQ volta a subir sozinho quando ela é aberta de novo.

Nos dois casos, a API usa os valores padrão (`localhost`, porta 5672, usuário `guest`) e o painel fica em <http://localhost:15672> (usuário `guest`, senha `guest`).

### Como demonstrar

1. Suba o RabbitMQ e a API. No painel, em **Queues**, a fila `meubairro.ocorrencias.criadas` aparece com 1 consumidor.
2. Cadastre uma ocorrência (pelo Swagger ou pelos comandos da seção de testes do CRUD). A resposta `201` volta antes da triagem: logo depois do cadastro, `GET /ocorrencias/{id}/triagem` mostra `PENDENTE`.
3. No log da API, a linha `Evento OcorrenciaCriada publicado` sai na thread da requisição (aparece como `[nio-8080-exec-...]`) e a linha `Evento recebido da fila` sai na thread do consumidor (`[ntContainer#0-1]`).
4. Alguns segundos depois, a triagem fica `CONCLUIDA` e o histórico mostra `CRIACAO`, `FILA` ("Evento recebido pelo consumidor da fila de triagem (RabbitMQ)") e `ANALISE_IA`. No painel, os gráficos da fila mostram a mensagem publicada, entregue e confirmada.
5. Para mostrar a tolerância a falhas, pare o RabbitMQ (`sudo systemctl stop rabbitmq-server` ou `docker compose stop rabbitmq`) e cadastre outra ocorrência: a API responde `201` e o histórico registra que a fila estava indisponível. Ao subir o RabbitMQ de novo, o consumidor reconecta sozinho, sem reiniciar a API.

### Validação com o RabbitMQ real

Em 10/11/2026, com o RabbitMQ 4.0.5 instalado no Ubuntu do WSL:

- Os testes de integração rodaram contra o RabbitMQ real (veja a seção de testes) e os 150 passaram. O broker registrou que a aplicação criou a exchange, a fila durável e o binding com a routing key `ocorrencia.criada`.
- Com a API rodando e o Gemini configurado, o cadastro respondeu `201` em 18 a 123 ms. O consumidor recebeu o evento e concluiu a triagem real 1,2 s a 1,6 s depois (por exemplo, poste apagado perto da escola: `ILUMINACAO`, `ALTA`), sem chamar `POST /ocorrencias/{id}/analise-ia`. O broker contou as mensagens publicadas, entregues e confirmadas, sem sobrar nenhuma na fila. Como o consumidor analisa uma ocorrência por vez, cadastros feitos em sequência entram na fila e são analisados em ordem.
- Com o RabbitMQ parado, o cadastro respondeu `201` em 32 ms e registrou a falha no histórico. Depois que o RabbitMQ voltou, o consumidor reconectou sozinho e o cadastro seguinte foi processado pela fila.

## Como testar o CRUD

### Pelo Swagger

1. Abra <http://localhost:8080/swagger-ui.html>.
2. Execute `POST /auth/login` com um usuário de teste, copie o `token` e clique em **Authorize**.
3. Em `POST /ocorrencias`, clique em **Try it out**, preencha os campos do formulário (a foto é opcional) e execute.
4. Teste `GET /ocorrencias`, `GET /ocorrencias/{id}`, `PUT /ocorrencias/{id}` e `DELETE /ocorrencias/{id}` com o id devolvido.
5. Faça login com o gestor para testar `PATCH /ocorrencias/{id}/status`, `GET /ocorrencias/resumo` e o CRUD de `/categorias`.

### Pelo PowerShell

No Windows PowerShell, use `curl.exe` (e não `curl`, que é um apelido do `Invoke-WebRequest`). Troque o `1` pelo id devolvido no cadastro.

```powershell
$api = "http://localhost:8080"
$login = Invoke-RestMethod -Method Post -Uri "$api/auth/login" -ContentType "application/json" -Body '{"email":"cidadao@meubairro.com","senha":"Teste@123"}'
$token = $login.token
$auth = @{ Authorization = "Bearer $token" }

curl.exe -X POST "$api/ocorrencias" -H "Authorization: Bearer $token" -F "titulo=Buraco na rua" -F "descricao=Buraco grande perto da esquina" -F "categoria=BURACO" -F "endereco=Rua Exemplo, 123" -F "latitude=-23.55" -F "longitude=-46.63"

Invoke-RestMethod -Uri "$api/ocorrencias" -Headers $auth
Invoke-RestMethod -Uri "$api/ocorrencias/1" -Headers $auth

curl.exe -X PUT "$api/ocorrencias/1" -H "Authorization: Bearer $token" -F "titulo=Buraco grande na rua" -F "descricao=Buraco grande perto da esquina, os carros desviam" -F "categoria=BURACO" -F "endereco=Rua Exemplo, 123"

Invoke-RestMethod -Uri "$api/ocorrencias/paginado?page=0&size=5&status=ABERTA" -Headers $auth
```

Para enviar uma foto, acrescente `-F "foto=@buraco.jpg;type=image/jpeg"` ao `curl.exe`; a resposta traz a `fotoUrl`, que abre no navegador. Com o gestor:

```powershell
$gestor = Invoke-RestMethod -Method Post -Uri "$api/auth/login" -ContentType "application/json" -Body '{"email":"gestor@meubairro.com","senha":"Teste@123"}'
$authGestor = @{ Authorization = "Bearer $($gestor.token)" }
Invoke-RestMethod -Method Patch -Uri "$api/ocorrencias/1/status" -Headers $authGestor -ContentType "application/json" -Body '{"status":"EM_ANALISE","observacao":"Equipe agendada"}'
Invoke-RestMethod -Uri "$api/ocorrencias/resumo" -Headers $authGestor
```

Com o status `EM_ANALISE`, o cidadão não consegue mais alterar nem excluir a ocorrência (a API responde `409`), mas o gestor consegue excluir:

```powershell
curl.exe -X DELETE "$api/ocorrencias/1" -H "Authorization: Bearer $token"
Invoke-RestMethod -Method Delete -Uri "$api/ocorrencias/1" -Headers $authGestor
```

Enquanto a ocorrência está `ABERTA`, o próprio cidadão pode excluí-la com `Invoke-RestMethod -Method Delete -Uri "$api/ocorrencias/<id>" -Headers $auth`.

A saída do `curl.exe` pode mostrar acentos trocados no console do Windows, que não usa UTF-8 por padrão. A API responde em UTF-8, e o Swagger e o app mostram o texto corretamente.

## Testes automatizados

```powershell
.\mvnw.cmd test
```

São 150 testes. Sem chave do Gemini, 149 passam e 1 fica ignorado (`AvaliacaoGeminiTest`, que só roda com chave real). Com `GEMINI_API_KEY` e `GEMINI_TESTE_REAL=true`, os 150 rodam; em 10/11/2026, os 150 passaram, também com o RabbitMQ real no lugar do Qpid. Por padrão, os testes rodam sem Docker, sem RabbitMQ instalado e sem Azure:

- **Unitários:** geração e validação do JWT, regras do `OcorrenciaService` (com Mockito), validação da resposta da IA e remoção de dados pessoais do relato, regras da entidade `Ocorrencia` e fotos (gravação, leitura e exclusão na pasta do servidor, bloqueio de nomes fora do padrão, e envio ao Azure com um servidor simulado pelo `MockRestServiceServer`).
- **Integração (MockMvc + H2):** cadastro e login no formato do contrato, JWT ausente, inválido, expirado e de outra chave, permissões de CIDADAO e GESTOR, rotas com o prefixo `/api`, CRUD de ocorrências em multipart com e sem foto, `fotoUrl` completa e rota pública da foto, CRUD de categorias, paginação e filtros, ocorrência de outro cidadão, validações, tratamento global de erros e CORS.
- **Persistência (`@DataJpaTest`):** consultas com filtros e paginação, localização e URL da foto, contagens, exclusão do histórico em cascata, e-mail e código de categoria únicos e bloqueio de linha.
- **IA:** um `ChatModel` falso devolve respostas controladas (válida, fora do padrão, texto que não é JSON e falha de conexão), passando pelo `ChatClient` real do Spring AI. O `IaConfigTest` confere que o cliente do Gemini recebe a chave já resolvida da configuração, e o `AvaliacaoGeminiTest` faz a avaliação com o modelo real.
- **RabbitMQ:** por padrão, um broker AMQP 0-9-1 embarcado (Apache Qpid) sobe junto com os testes; eles conferem a publicação, o consumo assíncrono, a triagem disparada pelo evento e o descarte de evento de ocorrência inexistente. Para usar o RabbitMQ real no lugar do Qpid, suba o broker, pare a API (para ela não consumir as mensagens dos testes) e rode com `$env:RABBITMQ_TESTE_REAL = "true"`.

Também foi feita uma validação com a API rodando a partir do jar (H2 em arquivo, RabbitMQ do WSL e Gemini real), reproduzindo as chamadas do código do app Mobile com a URL base terminando em `/api`:

- Cadastro, login, listagem, detalhe, cadastro com foto, edição, edição com troca de foto e exclusão funcionaram no formato que o app espera, e outro cidadão recebeu `403` ao tentar editar ou excluir.
- Uma foto JPEG real de 680 KB e uma imagem PNG foram enviadas pelo formulário; a `fotoUrl` devolveu os mesmos bytes, com o tipo `image/jpeg` ou `image/png`, sem token. A foto antiga foi apagada ao ser trocada, e a foto da ocorrência excluída também. Arquivo que não é imagem respondeu `400` e foto de 6 MB respondeu `413`.
- Pelo IP do computador na rede, a `fotoUrl` saiu com esse IP e abriu; simulando um proxy HTTPS, saiu com `https://`.
- O token trouxe o papel (`CIDADAO` ou `GESTOR`), as rotas de gestor recusaram o cidadão e o cadastro público com `"perfil": "GESTOR"` continuou criando um CIDADAO. As senhas estão gravadas no banco em BCrypt (`$2a$10$...`).
- O CRUD de categorias, a paginação, o CORS e o Swagger responderam como descrito, e o cadastro respondeu antes da triagem, que o consumidor da fila concluiu com o Gemini em cerca de 1 s.
- Depois de reiniciar a API, os usuários, as ocorrências e as fotos continuaram disponíveis.

## Limitações e dependências externas

- **Gemini:** a triagem real depende de uma chave da API e da disponibilidade do serviço, e o nível gratuito tem limite de requisições. Em picos de uso, o Google pode responder `503` ou demorar mais de 30 s; a triagem fica `FALHA` e pode ser pedida de novo. Sem a chave, a API funciona normalmente, mas a análise por IA responde `503`.
- **Fotos:** por padrão, ficam na pasta do servidor. Em um deploy com disco temporário, use um volume persistente ou o Azure Blob Storage. O modo Azure foi testado apenas com um servidor simulado.
- **RabbitMQ:** validado com o RabbitMQ 4.0.5 no Ubuntu do WSL. O caminho pelo Docker (`docker compose`) não foi testado, porque a máquina de desenvolvimento não tem Docker. Uma ocorrência cadastrada com o RabbitMQ fora do ar não é reenviada depois; a análise dela precisa ser pedida em `POST /ocorrencias/{id}/analise-ia`.
- **PostgreSQL:** configuração preparada, mas não testada.
- **App Mobile:** o contrato foi conferido por testes e reproduzindo as chamadas do código do app (`src/services` do repositório do Mobile) contra a API rodando, mas o teste no celular depende do app em execução. O APK de entrega precisa da API publicada em HTTPS.
- **Autenticação:** não há refresh token; quando o token expira, o usuário faz login de novo.

## Entrega

- Repositório no GitHub: (preencher)
- Vídeo pitch: (preencher)
- Deploy: ainda não há deploy publicado desta API.

## Declaração de uso de IA

- **Na solução:** o Spring AI com o Google Gemini (modelo `gemini-3.5-flash-lite`) faz a triagem das ocorrências, como descrito acima.
- **No desenvolvimento:** o grupo usou o Claude Code (Anthropic) como apoio na implementação do código, dos testes automatizados e desta documentação.

Esta declaração deve ser conferida e completada pelo grupo antes da entrega, conforme as regras da FIAP.
