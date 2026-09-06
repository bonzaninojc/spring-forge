# Alterações feitas nesta revisão

## Validação e schema

- Parser agora falha em propriedades desconhecidas do `forge.json`.
- `$schema` continua permitido no objeto raiz.
- Validações adicionais para `architectureStyle`, `apiPath`, roles, enum values e `columnName`.
- `forge-schema.json` atualizado com `architectureStyle: LAYERED | HEXAGONAL | MODULAR`.
- Schema atualizado para `outputDir` e `migrationsDir` opcionais.

## Arquiteturas

- `HEXAGONAL` não gera mais camadas tradicionais junto com ports & adapters.
- Novo estilo `MODULAR`, com estrutura package-by-feature:
  - `com.app.modules.product.domain`
  - `com.app.modules.product.repository`
  - `com.app.modules.product.dto`
  - `com.app.modules.product.mapper`
  - `com.app.modules.product.service`
  - `com.app.modules.product.service.impl`
  - `com.app.modules.product.web`
  - `com.app.modules.product.exception`
  - `com.app.modules.product.specification`

## Geração Java

- Geradores principais agora usam pacotes por entidade quando `architectureStyle = MODULAR`.
- Controllers implementam a interface de documentação gerada quando OpenAPI está ativo.
- MapStruct ignora objetos de relação `ManyToOne` e mapeia IDs no response.
- Services resolvem relações `ManyToOne` por repository após MapStruct, evitando salvar relações nulas.
- Escrita de arquivos agora sobrescreve saídas geradas em `target/generated-*`, mas mantém proteção contra sobrescrita em código-fonte manual.

## Migrations

- Migrations agora usam dialeto básico por database: PostgreSQL, MySQL, H2.
- MongoDB pula geração SQL.
- Foreign keys usam `tableName` da entidade alvo quando definido.

## Dashboard e reverse

- Dashboard agora abre apenas em loopback/local.
- CORS do dashboard foi restringido para `localhost`/`127.0.0.1` na porta do dashboard.
- Senha JDBC do reverse chamada pelo dashboard deixou de ir em argumento `-D` e passou a ir por variável de ambiente temporária.

## Testes adicionados

- Parser rejeitando propriedades desconhecidas.
- Parser aceitando `architectureStyle = MODULAR`.
- Teste de geração da estrutura modular package-by-feature.

## Observação

O `.git` foi mantido no pacote final, conforme solicitado.

## 2026-06-25 - Expansão solicitada

### CRUD avançado

- Adicionado bloco `entity.crud`.
- `crud.sortable` define whitelist de campos aceitos para ordenação.
- `crud.filterable` define filtros novos sem quebrar `entity.filters`.
- `targetField` agora aceita caminhos aninhados como `category.name`.
- `Specification` gerada cria `LEFT JOIN` automaticamente para filtros relacionados.
- `Pageable` é sanitizado com `maxPageSize`, `defaultSort` e `defaultDirection`.
- Adicionado endpoint opcional `DELETE /bulk` quando `crud.bulkOperations=true`.

### Templates customizados

- Adicionado `project.templates`.
- Criado `CustomTemplateGenerator`.
- Templates em `_global` são renderizados uma vez; demais templates são renderizados por entidade.
- Suporte a placeholders como `{{EntityName}}`, `{{entityName}}`, `{{packagePath}}`, `{{entityKebab}}` etc.

### Frontend melhorado

- Gerado `api/client.ts` com interceptor JWT.
- Requests do frontend usam o client Axios com token.
- Listagens aceitam ordenação por campos definidos em `crud.sortable`, incluindo caminhos como `category.name`.
- Painel de filtros usa `entity.filters + entity.crud.filterable`.

### POM automático

- Adicionado `project.pom.autoUpdate`.
- Quando ativo, `spring-forge:generate` atualiza o `pom.xml` do projeto alvo com dependências necessárias pelas features habilitadas.
- OpenAPI adiciona SpringDoc; RabbitMQ adiciona AMQP; Cache adiciona Redis/Caffeine; migrations adicionam Flyway; MapStruct adiciona dependência e annotation processor.
- O dashboard ganhou preview das dependências que serão aplicadas e opção de criar backup `pom.xml.spring-forge.bak`.


## 2026-06-26 - Dashboard atualizado para as novas features

- Adicionada tela **Frontend** para editar `project.frontend`, `generateFrontend` e `frontendDir`.
- Adicionada tela **Templates** para editar `project.templates`.
- Adicionado card **CRUD avançado** na tela de entidade para configurar:
  - `crud.enabled`;
  - `crud.pagination`;
  - `crud.defaultPageSize`;
  - `crud.maxPageSize`;
  - `crud.defaultSort`;
  - `crud.defaultDirection`;
  - `crud.sortable`;
  - `crud.filterable`;
  - `crud.bulkOperations`.
- O editor de CRUD avançado agora sugere campos simples e campos de relacionamento, como `category.name`, baseado nas relações declaradas.
- Adicionado atalho visual para criar filtro por relacionamento, por exemplo `categoryName -> category.name`.
- Adicionado `project.frontend` ao model Java e ao `forge-schema.json`, mantendo compatibilidade com `generateFrontend`.

## Dashboard UI — Templates visuais, preview e filtros avançados

- Adicionado editor visual de templates no dashboard:
  - criação/edição de templates diretamente na UI;
  - templates por entidade ou globais;
  - presets prontos;
  - preview de path e conteúdo renderizado;
  - placeholders clicáveis.
- Adicionado suporte no modelo `TemplateConfig.visualTemplates` e no `CustomTemplateGenerator` para renderizar templates salvos no `forge.json`, sem depender de arquivos `.tpl` físicos.
- Melhorado o preview de código gerado:
  - usa o estado atual da UI, mesmo antes de salvar o `forge.json`;
  - inclui aba `Todos`;
  - adiciona previews de Filter, OpenAPI e Templates;
  - mostra lista de arquivos com caminho completo relativo.
- Melhorado o builder de filtros avançados:
  - navegador de campos simples e campos relacionados;
  - criação automática de filtro por clique;
  - sugestão automática de nome do parâmetro;
  - operador padrão baseado no tipo;
  - explicação por operador;
  - chips para campos ordenáveis;
  - exemplo de query gerado automaticamente.

## POM automático por feature

- Criado `PomConfig` (`project.pom`) com `autoUpdate`, `createBackup`, `addComments` e `managePlugins`.
- Criado `PomDependencyEnricher` para adicionar dependências ausentes sem duplicar `groupId:artifactId`.
- O goal `generate` agora chama o enricher antes da geração quando `project.pom.autoUpdate=true`.
- Adicionado endpoint `/api/pom/preview` no dashboard.
- A tela Projeto agora mostra preview das dependências/properties necessárias conforme as features marcadas.

## Validador com mensagens claras

- Adicionado `ForgeJsonParser.ValidationReport` e `ValidationIssue` com erros estruturados.
- O endpoint `/api/validate` agora retorna `issues[]` com `severity`, `code`, `path`, `title`, `message`, `fix`, `example`, `line` e `column`.
- Mensagens do CLI agora aparecem com `Onde`, `Problema`, `Como corrigir` e `Exemplo`.
- Validação semântica ampliada para:
  - propriedades desconhecidas com caminho e campos aceitos;
  - tipo JSON errado;
  - enums inválidos;
  - entity/field/action/filter duplicados;
  - relacionamento apontando para entidade inexistente;
  - `crud.sortable`, `crud.defaultSort` e `crud.filterable.targetField` apontando para campos inexistentes;
  - caminhos aninhados como `category.name`, validando se `category` é relação e se `name` existe na entidade alvo;
  - templates visuais com escopo/path/content inválidos.
- Dashboard agora mostra um painel de validação com cards por erro/aviso, caminho exato e sugestão de correção.


## Atualização: campos customizáveis no dashboard

- Dashboard ganhou editor de campos mais completo: label, descrição, coluna, placeholder, helper text, precision/scale, validações, request/response, read-only, filterable e sortable.
- Dashboard ganhou editor de relacionamentos mais completo: join column, required, display field, inRequest/inResponse e orphan removal.
- Adicionado preset visual para criar entidade `Session`.
