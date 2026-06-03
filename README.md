<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white" alt="Spring Boot"/>
  <img src="https://img.shields.io/badge/Maven-C71A36?style=for-the-badge&logo=apache-maven&logoColor=white" alt="Maven"/>
  <img src="https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java"/>
</p>

<h1 align="center">⚒️ Spring Forge Maven Plugin</h1>

<p align="center">
  <strong>Gera backend Spring Boot completo a partir de um único arquivo <code>forge.json</code></strong><br/>
  Entity • Repository • Service • Controller • DTOs • Mapper • Migrations • RabbitMQ • OpenAPI • Spring Events • Scheduled Tasks • Security/RBAC • Tests • Frontend React
</p>

---

## ✨ Features

| Feature | Descrição |
|---------|-----------|
| 🏗️ **CRUD Completo** | Entity, Repository, Service, Controller, DTOs e Mapper |
| 🎯 **Custom Actions** | Endpoints customizados com DTOs próprios de request/response |
| 🐇 **RabbitMQ** | Publishers, Consumers, DLQ e configuração automática |
| 📖 **OpenAPI/Swagger** | Anotações `@Operation`, `@Tag`, `@ApiResponse` nos controllers |
| 📡 **Spring Events** | `ApplicationEvent` + `@EventListener` (sync/async) |
| ⏰ **Scheduled Tasks** | `@Scheduled` com cron ou fixed rate |
| 🗃️ **Flyway Migrations** | Scripts SQL gerados automaticamente |
| 🔒 **Security/RBAC** | `@PreAuthorize` + SecurityConfig + roles por entidade/action |
| 🧪 **Testes Unitários** | JUnit 5 + Mockito para Service e Controller |
| 🔍 **Filtros/Search** | `POST /search` com FilterDTO + JPA Specification |
| 🎨 **Frontend React** | Vite + MUI + Redux Toolkit + Dark Mode + Responsivo |
| 🔄 **MapStruct Mappers** | Conversão automática Entity ↔ DTO |
| 🛡️ **Soft Delete** | Exclusão lógica com `deletedAt` |
| 📋 **Auditoria** | `createdAt` e `updatedAt` automáticos |

---

## 🚀 Instalação

### 1. Build local

```bash
cd spring-forge-maven-plugin
mvn clean install
```

### 2. Adicione o plugin ao seu projeto

> ⚠️ **Importante:** não configure `<executions>` com `<phase>`. O plugin foi projetado para rodar **somente sob demanda** via linha de comando. Ver seção [Uso](#-uso).

```xml
<build>
  <plugins>
    <plugin>
      <groupId>io.springforge</groupId>
      <artifactId>spring-forge-maven-plugin</artifactId>
      <version>1.0.0</version>
    </plugin>
  </plugins>
</build>
```

### 3. Dependências necessárias no projeto alvo

<details>
<summary>📦 Clique para expandir</summary>

```xml
<dependencies>
  <!-- Spring Boot -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
  </dependency>

  <!-- MapStruct (se generateMappers: true) -->
  <dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
    <version>1.5.5.Final</version>
  </dependency>

  <!-- Lombok -->
  <dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
  </dependency>

  <!-- PostgreSQL -->
  <dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
  </dependency>

  <!-- Flyway (se generateMigrations: true) -->
  <dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
  </dependency>

  <!-- RabbitMQ (se generateRabbitMQ: true) -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
  </dependency>

  <!-- OpenAPI/Swagger (se generateOpenApi: true) -->
  <dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.5.0</version>
  </dependency>
</dependencies>

<build>
  <plugins>
    <!-- MapStruct + Lombok Annotation Processor -->
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-compiler-plugin</artifactId>
      <configuration>
        <annotationProcessorPaths>
          <path>
            <groupId>org.mapstruct</groupId>
            <artifactId>mapstruct-processor</artifactId>
            <version>1.5.5.Final</version>
          </path>
          <path>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
          </path>
        </annotationProcessorPaths>
      </configuration>
    </plugin>
  </plugins>
</build>
```

</details>

---

## 📖 Uso

O plugin **não executa automaticamente** durante `mvn compile`, `mvn package` ou `mvn clean install`. Ele roda apenas quando você invoca o goal diretamente:

```bash
# Gerar tudo (lê forge.json na raiz do projeto)
mvn spring-forge:generate

# Arquivo forge.json em outro caminho
mvn spring-forge:generate -Dforge.input=config/minha-definicao.json

# Apenas entidades específicas
mvn spring-forge:generate -Dforge.entities=Product,Category

# Pular geração
mvn spring-forge:generate -Dforge.skip=true
```

### Onde o código é gerado?

Por padrão o código vai para `target/generated-sources/spring-forge/`. Após revisar os arquivos, **copie-os manualmente para `src/main/java`** — isso garante que você tem controle total sobre o que entra no seu projeto.

```
target/generated-sources/spring-forge/
└── com/myapp/
    ├── entity/Product.java
    ├── controller/ProductController.java
    └── ...
```

### Integrar ao build automático (opcional)

Se quiser que o Maven compile os arquivos gerados diretamente de `target/generated-sources/spring-forge` sem copiá-los para `src/main/java`, use a flag `forge.addSourceRoot=true`:

```bash
mvn spring-forge:generate -Dforge.addSourceRoot=true
```

Isso registra o diretório de saída como compile source root para a sessão atual do Maven. **Não é o fluxo recomendado** — prefira copiar os arquivos gerados para `src/main/java` e commitar.

> ⚠️ **Nunca** configure `forge.outputDir` apontando para `src/main/java`. O plugin detecta isso e lança um erro para evitar erros de `duplicate class` no compilador.

### Tabela de parâmetros

| Parâmetro | Propriedade (`-D`) | Default | Descrição |
|-----------|-------------------|---------|-----------|
| `inputFile` | `forge.input` | `${project.basedir}/forge.json` | Caminho do `forge.json` |
| `outputDir` | `forge.outputDir` | `target/generated-sources/spring-forge` | Diretório de saída do código Java gerado |
| `entitiesFilter` | `forge.entities` | *(todas)* | Gerar apenas entidades específicas (separadas por vírgula) |
| `skip` | `forge.skip` | `false` | Pular execução |
| `addSourceRoot` | `forge.addSourceRoot` | `false` | Registrar `outputDir` como compile source root do Maven |

---

## 📐 Estrutura do `forge.json`

### `project` — Configuração global

| Campo | Tipo | Default | Descrição |
|-------|------|---------|-----------| 
| `basePackage` | String | **obrigatório** | Pacote base, ex: `com.myapp` |
| `name` | String | **obrigatório** | Nome do projeto |
| `database` | String | `postgres` | `postgres`, `mysql`, `mongodb` |
| `generateMigrations` | Boolean | `false` | Gerar scripts Flyway |
| `generateMappers` | Boolean | `true` | Gerar Mappers MapStruct |
| `generateFrontend` | Boolean | `false` | Gerar frontend React (Vite+MUI+Redux) |
| `generateRabbitMQ` | Boolean | `false` | Gerar config e classes RabbitMQ |
| `generateOpenApi` | Boolean | `false` | Gerar anotações SpringDoc/OpenAPI |
| `generateSpringEvents` | Boolean | `false` | Gerar ApplicationEvent + Listeners |
| `generateScheduled` | Boolean | `false` | Gerar `@Scheduled` para actions agendadas |
| `generateTests` | Boolean | `false` | Gerar testes unitários (JUnit 5 + Mockito) |
| `generateSecurity` | Boolean | `false` | Gerar Spring Security + RBAC (`@PreAuthorize`) |
| `frontendDir` | String | `frontend/src` | Diretório de saída do frontend |

---

### `entity` — Definição de entidade

| Campo | Tipo | Default | Descrição |
|-------|------|---------|-----------| 
| `name` | String | **obrigatório** | Nome em PascalCase, ex: `Product` |
| `tableName` | String | auto (snake_case) | Nome da tabela no banco |
| `auditable` | Boolean | `true` | Gera `createdAt` e `updatedAt` |
| `softDelete` | Boolean | `false` | Gera `deletedAt` + exclusão lógica |
| `apiPath` | String | auto | Path da API REST, ex: `/api/v1/products` |
| `openApiTags` | Array | `[]` | Tags OpenAPI para os endpoints |
| `roles` | Array | `[]` | Roles RBAC para proteger os endpoints (`@PreAuthorize`) |
| `filters` | Array | `[]` | Filtros de busca (gera `POST /search` + FilterDTO + Specification) |
| `generate` | Array | tudo | Layers: `entity`, `repository`, `service`, `controller`, `dto`, `mapper`, `migration` |

---

### `field` — Campos da entidade

| Campo | Tipo | Default | Descrição |
|-------|------|---------|-----------| 
| `name` | String | **obrigatório** | Nome em camelCase |
| `type` | String | **obrigatório** | `String`, `Integer`, `Long`, `Double`, `Float`, `BigDecimal`, `Boolean`, `LocalDate`, `LocalDateTime`, `UUID`, `Enum` |
| `required` | Boolean | `false` | Gera `@NotNull` / `@NotBlank` |
| `unique` | Boolean | `false` | Gera `unique = true` na coluna |
| `maxLength` | Integer | — | Tamanho máximo (String) |
| `minLength` | Integer | — | Tamanho mínimo (String) |
| `defaultValue` | String | — | Valor padrão no banco |
| `columnName` | String | auto (snake_case) | Nome da coluna |
| `enumValues` | Array | — | Valores do Enum (obrigatório se `type=Enum`) |
| `validations` | Array | — | Anotações extras, ex: `["@Email", "@Positive"]` |
| `inResponse` | Boolean | `true` | Incluir no ResponseDTO |
| `inRequest` | Boolean | `true` | Incluir no RequestDTO |

---

### `relation` — Relacionamentos

| Campo | Tipo | Default | Descrição |
|-------|------|---------|-----------| 
| `type` | String | **obrigatório** | `ManyToOne`, `OneToMany`, `OneToOne`, `ManyToMany` |
| `targetEntity` | String | **obrigatório** | Entidade alvo, ex: `Category` |
| `fieldName` | String | **obrigatório** | Nome do campo, ex: `category` |
| `fetch` | String | `LAZY` | `LAZY` ou `EAGER` |
| `cascade` | String | `MERGE,PERSIST` | Tipos de cascade |
| `mappedBy` | String | — | Para `OneToMany`/`ManyToMany` |
| `inResponse` | Boolean | `true` | Incluir no ResponseDTO |

---

### `action` — Endpoints customizados

Actions permitem definir operações de negócio além do CRUD padrão, com DTOs próprios, eventos e integração com filas.

| Campo | Tipo | Default | Descrição |
|-------|------|---------|-----------| 
| `name` | String | **obrigatório** | Nome em camelCase, ex: `activate` |
| `description` | String | — | Descrição Javadoc do método |
| `httpMethod` | String | — | `GET`, `POST`, `PUT`, `PATCH`, `DELETE`. Se omitido, gera apenas no Service |
| `apiPath` | String | `/{id}/{name}` | Path relativo ao base path da entidade |
| `requiresId` | Boolean | `true` | Se recebe ID da entidade como parâmetro |
| `request` | Array\<Field\> | `[]` | Campos do DTO de input |
| `response` | Array\<Field\> | `[]` | Campos do DTO de output (se vazio, retorna `void`) |
| `event` | Object | — | Evento Spring a publicar (ver abaixo) |
| `queues` | Array | `[]` | Filas RabbitMQ da action |
| `scheduled` | Boolean | `false` | Gera `@Scheduled` (sem endpoint HTTP) |
| `scheduledCron` | String | — | Cron expression, ex: `"0 0 3 * * *"` |
| `scheduledFixedRate` | Long | — | Fixed rate em ms |
| `openApiTags` | Array | `[]` | Tags OpenAPI adicionais |
| `openApiResponses` | Array | `[]` | Respostas HTTP extras (`{code, description}`) |
| `roles` | Array | `[]` | Roles RBAC específicas desta action (sobrescreve roles da entidade) |

**Exemplo:**

```json
{
  "name": "activate",
  "httpMethod": "POST",
  "apiPath": "/{id}/activate",
  "requiresId": true,
  "request": [
    { "name": "reason", "type": "String", "required": true }
  ],
  "response": [
    { "name": "activated", "type": "Boolean" },
    { "name": "message", "type": "String" }
  ]
}
```

---

### `event` — Spring Events

Definido dentro de uma `action` para publicar um `ApplicationEvent` após a execução.

| Campo | Tipo | Default | Descrição |
|-------|------|---------|-----------| 
| `name` | String | `${ActionName}Event` | Nome da classe do evento |
| `generateListener` | Boolean | `false` | Gera classe `@EventListener` |
| `async` | Boolean | `false` | Listener usa `@Async` (requer `@EnableAsync`) |
| `description` | String | — | Descrição Javadoc |

**Exemplo:**

```json
{
  "event": {
    "name": "OrderConfirmedEvent",
    "generateListener": true,
    "async": true,
    "description": "Publicado quando um pedido é confirmado"
  }
}
```

---

### `queue` — RabbitMQ

Pode ser definido no nível da entidade (global) ou dentro de uma `action`.

| Campo | Tipo | Default | Descrição |
|-------|------|---------|-----------| 
| `name` | String | **obrigatório** | Nome da fila, ex: `order.confirmed` |
| `exchange` | String | `""` | Nome da exchange |
| `routingKey` | String | = `name` | Routing key |
| `durable` | Boolean | `true` | Fila durável |
| `direction` | String | `PUBLISH` | `PUBLISH`, `CONSUME` ou `BOTH` |
| `deadLetterExchange` | String | — | Se informado, cria DLQ automaticamente |
| `retryTtlMs` | Integer | `30000` | TTL de retry para DLQ (ms) |

**Exemplo:**

```json
{
  "queues": [
    {
      "name": "product.stock.adjusted",
      "exchange": "products",
      "routingKey": "product.stock.adjusted",
      "direction": "BOTH",
      "deadLetterExchange": "products.dlx",
      "retryTtlMs": 60000
    }
  ]
}
```

---

### `scheduled` — Tarefas agendadas

Uma action com `"scheduled": true` gera um `@Scheduled` sem expor endpoint HTTP.

```json
{
  "name": "processExpiredOrders",
  "description": "Cancela pedidos pendentes há mais de 24h",
  "scheduled": true,
  "scheduledCron": "0 0 1 * * *",
  "requiresId": false,
  "response": [
    { "name": "processed", "type": "Integer" }
  ]
}
```

---

### `filter` — Filtros de busca

Definido no nível da entidade. Gera `FilterDTO`, `Specification` (JPA Criteria) e endpoint `POST /search`. No frontend, gera um painel de filtros colapsável na listagem.

| Campo | Tipo | Default | Descrição |
|-------|------|---------|-----------|
| `name` | String | **obrigatório** | Nome do campo de filtro (camelCase) |
| `type` | String | **obrigatório** | Tipo: `String`, `Integer`, `BigDecimal`, `Boolean`, `Enum`, etc. |
| `label` | String | = `name` | Label para exibição no frontend |
| `enumValues` | Array | — | Valores do Enum (obrigatório se `type=Enum`) |

**Convenções automáticas:**
- Campos `String` → filtro `LIKE %...%` case-insensitive
- Campos com sufixo `Min` (ex: `priceMin`) → `>=` no campo base (`price`)
- Campos com sufixo `Max` (ex: `priceMax`) → `<=` no campo base (`price`)
- Campos `Enum` → comparação `equal`

**Exemplo:**

```json
{
  "filters": [
    { "name": "name", "type": "String", "label": "Nome do produto" },
    { "name": "status", "type": "Enum", "enumValues": ["ACTIVE", "INACTIVE"], "label": "Status" },
    { "name": "priceMin", "type": "BigDecimal", "label": "Preço mínimo" },
    { "name": "priceMax", "type": "BigDecimal", "label": "Preço máximo" }
  ]
}
```

O endpoint gerado é `POST /api/v1/products/search` com body:
```json
{
  "name": "notebook",
  "status": "ACTIVE",
  "priceMin": 100.00,
  "priceMax": 5000.00
}
```

---

### `roles` — Security/RBAC

Definido na entidade (protege todo o CRUD) ou na action (protege endpoint específico). Requer `generateSecurity: true`.

**Na entidade** — aplica `@PreAuthorize` no nível de classe do controller:
```json
{
  "name": "Product",
  "roles": ["ADMIN", "MANAGER"]
}
```
Gera: `@PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")`

**Na action** — aplica `@PreAuthorize` no método específico (sobrescreve o da entidade):
```json
{
  "name": "activate",
  "roles": ["ADMIN"]
}
```
Gera: `@PreAuthorize("hasRole('ADMIN')")`

---

## 📂 O que é gerado

```
target/generated-sources/spring-forge/   ← revise e copie para src/main/java
└── com/myapp/
    ├── entity/
    │   ├── Product.java                        ← JPA Entity
    │   └── ProductStatus.java                  ← Enum (se type=Enum)
    ├── repository/
    │   └── ProductRepository.java              ← JpaRepository + JpaSpecificationExecutor
    ├── service/
    │   ├── ProductService.java                 ← Interface (CRUD + Actions)
    │   └── impl/
    │       └── ProductServiceImpl.java         ← Implementação
    ├── controller/
    │   ├── ProductController.java              ← REST Controller + Action endpoints
    │   └── ProductControllerDocs.java          ← Interface OpenAPI (se generateOpenApi: true)
    ├── dto/
    │   ├── ProductRequestDTO.java              ← DTO de entrada
    │   ├── ProductResponseDTO.java             ← DTO de saída
    │   ├── ActivateRequestDTO.java             ← DTO da action (input)
    │   └── ActivateResponseDTO.java            ← DTO da action (output)
    ├── mapper/
    │   └── ProductMapper.java                  ← MapStruct Mapper
    ├── event/                                  ← (se generateSpringEvents: true)
    │   ├── ProductActivatedEvent.java          ← ApplicationEvent (campos final, só getters)
    │   └── ProductActivatedEventListener.java  ← @EventListener
    ├── messaging/                              ← (se generateRabbitMQ: true)
    │   ├── RabbitMQConfig.java                 ← Exchanges, Queues, Bindings
    │   ├── ProductStockPublisher.java          ← Publisher
    │   └── ProductStockConsumer.java           ← @RabbitListener
    ├── scheduler/                              ← (se generateScheduled: true)
    │   └── ProductScheduledTasks.java          ← @Scheduled methods
    ├── specification/                          ← (se entity tem filters)
    │   └── ProductSpecification.java           ← JPA Criteria Specification
    ├── security/                               ← (se generateSecurity: true)
    │   ├── SecurityConfig.java                 ← @Configuration + SecurityFilterChain
    │   └── AppRole.java                        ← Enum com todas as roles
    └── exception/
        ├── ProductNotFoundException.java
        └── GlobalExceptionHandler.java
```

**Testes** (em `target/generated-sources/spring-forge-test/`, se `generateTests: true`):
```
└── com/myapp/
    ├── service/impl/
    │   └── ProductServiceImplTest.java         ← JUnit 5 + Mockito
    └── controller/
        └── ProductControllerTest.java          ← @WebMvcTest + MockMvc
```

**Frontend** (em `target/frontend/`, se `generateFrontend: true`):
```
frontend/
├── package.json, vite.config.ts, tsconfig*.json
└── src/
    ├── App.tsx                                 ← Dark Mode + Layout Responsivo
    ├── theme.ts, routes.tsx, main.tsx
    ├── validation/
    │   └── productSchema.ts                    ← Zod schema (validação client-side)
    ├── store/
    │   ├── store.ts, hooks.ts
    │   └── slices/productSlice.ts              ← Redux Toolkit (CRUD async thunks)
    ├── components/
    │   ├── AppMenu.tsx                         ← Drawer responsivo (mobile/desktop)
    │   ├── AppHeader.tsx                       ← Header com toggle Dark Mode
    │   ├── product/
    │   │   └── ProductFilterPanel.tsx          ← Painel colapsável de filtros
    │   └── shared/                             ← PageHeader, ConfirmDialog, EmptyState...
    └── pages/product/
        ├── ListProductPage.tsx                 ← Tabela paginada + filtros + skeleton
        ├── ProductFormPage.tsx                 ← Formulário create/edit
        └── ProductDetailPage.tsx               ← Visualização read-only + actions
```

---

## 🧪 Exemplo completo de `forge.json`

<details>
<summary>📄 Clique para expandir</summary>

```json
{
  "project": {
    "basePackage": "com.myapp",
    "name": "MyEcommerceApp",
    "database": "postgres",
    "generateMigrations": true,
    "generateMappers": true,
    "generateFrontend": false,
    "generateRabbitMQ": true,
    "generateOpenApi": true,
    "generateSpringEvents": true,
    "generateScheduled": true,
    "generateTests": true,
    "generateSecurity": true
  },
  "entities": [
    {
      "name": "Product",
      "tableName": "products",
      "auditable": true,
      "softDelete": true,
      "apiPath": "/api/v1/products",
      "openApiTags": ["Products"],
      "roles": ["ADMIN", "MANAGER"],
      "filters": [
        { "name": "name", "type": "String", "label": "Nome do produto" },
        { "name": "status", "type": "Enum", "enumValues": ["ACTIVE", "INACTIVE"], "label": "Status" },
        { "name": "priceMin", "type": "BigDecimal", "label": "Preço mínimo" },
        { "name": "priceMax", "type": "BigDecimal", "label": "Preço máximo" }
      ],
      "fields": [
        { "name": "name", "type": "String", "required": true, "maxLength": 200 },
        { "name": "price", "type": "BigDecimal", "required": true },
        { "name": "stock", "type": "Integer", "required": true },
        { "name": "status", "type": "Enum", "enumValues": ["ACTIVE", "INACTIVE"], "required": true }
      ],
      "relations": [
        { "type": "ManyToOne", "targetEntity": "Category", "fieldName": "category" }
      ],
      "actions": [
        {
          "name": "activate",
          "httpMethod": "POST",
          "apiPath": "/{id}/activate",
          "requiresId": true,
          "roles": ["ADMIN"],
          "event": {
            "name": "ProductActivatedEvent",
            "generateListener": true,
            "async": true
          },
          "request": [
            { "name": "reason", "type": "String", "required": true }
          ],
          "response": [
            { "name": "activated", "type": "Boolean" }
          ]
        },
        {
          "name": "adjustStock",
          "httpMethod": "PATCH",
          "apiPath": "/{id}/stock",
          "queues": [
            {
              "name": "product.stock.adjusted",
              "exchange": "products",
              "direction": "PUBLISH"
            }
          ],
          "request": [
            { "name": "quantity", "type": "Integer", "required": true }
          ],
          "response": [
            { "name": "currentStock", "type": "Integer" }
          ]
        },
        {
          "name": "syncInventory",
          "scheduled": true,
          "scheduledCron": "0 0 3 * * *",
          "requiresId": false,
          "response": [
            { "name": "synced", "type": "Integer" }
          ]
        }
      ]
    }
  ]
}
```

</details>

Veja o arquivo completo em [`examples/forge.json`](examples/forge.json).

---

## ⚙️ Configuração completa no `pom.xml`

```xml
<plugin>
  <groupId>io.springforge</groupId>
  <artifactId>spring-forge-maven-plugin</artifactId>
  <version>1.0.0</version>
  <configuration>
    <!-- Caminho do forge.json (default: ${project.basedir}/forge.json) -->
    <inputFile>${project.basedir}/forge.json</inputFile>

    <!-- Saída do código gerado. NUNCA aponte para src/main/java. -->
    <!-- Default: ${project.build.directory}/generated-sources/spring-forge -->
    <outputDir>${project.build.directory}/generated-sources/spring-forge</outputDir>

    <!-- Pular execução -->
    <skip>false</skip>

    <!-- Registrar outputDir como compile source root (default: false) -->
    <!-- Use apenas se quiser compilar direto de target/ sem copiar para src/ -->
    <addSourceRoot>false</addSourceRoot>
  </configuration>
</plugin>
```

---

## 🐛 Problemas conhecidos e soluções

### `duplicate class: com.myapp.controller.ProductController`

Ocorre quando o `outputDir` aponta para `src/main/java` (ou subpasta dela) e o arquivo já existe lá. O plugin detecta isso na inicialização e lança um erro com mensagem explicativa. Solução: mantenha o `outputDir` padrão (`target/generated-sources/spring-forge`) e copie os arquivos manualmente para `src/main/java`.

### O plugin rodou sozinho no `mvn clean install`

Isso acontece se o `pom.xml` do projeto-alvo tiver a execução configurada com `<phase>generate-sources</phase>`. Remova o bloco `<executions>` — o plugin tem `defaultPhase = NONE` e não se vincula a nenhuma fase do lifecycle por conta própria.

### Arquivo gerado com `final` e `setter` (não compila)

Corrigido na v1.0.1. Ocorria em eventos Spring (`ApplicationEvent`) quando a action tinha campos no `response` — os campos eram declarados `private final` mas o gerador produzia setters para eles. Agora eventos geram apenas getters.

---

## 🤝 Contribuindo

1. Fork o repositório
2. Crie sua branch (`git checkout -b feature/minha-feature`)
3. Commit suas mudanças (`git commit -m 'feat: minha feature'`)
4. Push para a branch (`git push origin feature/minha-feature`)
5. Abra um Pull Request

---

## 📄 Licença

Este projeto está sob a licença MIT.
