<p align="center">
  <img src="https://img.shields.io/badge/Spring%20Boot-6DB33F?style=for-the-badge&logo=spring-boot&logoColor=white" alt="Spring Boot"/>
  <img src="https://img.shields.io/badge/Maven-C71A36?style=for-the-badge&logo=apache-maven&logoColor=white" alt="Maven"/>
  <img src="https://img.shields.io/badge/Java-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java"/>
</p>

<h1 align="center">⚒️ Spring Forge Maven Plugin</h1>

<p align="center">
  <strong>Gera backend Spring Boot completo a partir de um único arquivo <code>forge.json</code></strong><br/>
  Entity • Repository • Service • Controller • DTOs • Mapper • Migrations • RabbitMQ • OpenAPI • Spring Events • Scheduled Tasks • Security/RBAC • Cache • Export/Import • Tests • Frontend React
</p>

---

## 💡 Sobre o projeto

O **Spring Forge** nasceu de uma frustração comum: toda vez que iniciamos um novo microserviço ou módulo Spring Boot, precisamos criar dezenas de classes repetitivas — Entity, Repository, Service, Controller, DTOs, Mapper, testes... E quando o domínio cresce, manter tudo consistente vira um pesadelo.

A proposta é simples: **você descreve suas entidades, campos, relacionamentos e regras de negócio em um único arquivo JSON**, e o plugin gera todo o código boilerplate — seguindo as melhores práticas do ecossistema Spring. Você foca no que importa: a lógica de negócio.

**Não é um framework que toma conta do seu projeto.** O código gerado vai para uma pasta temporária (`target/`), e você decide o que copiar para `src/main/java`. Sem mágica em runtime, sem dependências escondidas, sem lock-in. É só geração de código que você pode auditar, editar e versionar como qualquer outro arquivo do projeto.

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
| 🔍 **Filtros Dinâmicos** | `POST /search` com operadores configuráveis (CONTAINS, IN, BETWEEN, IS_NULL...) |
| 🚀 **Cache (Redis/Caffeine)** | `@Cacheable`, `@CacheEvict`, `@CachePut` com TTL configurável |
| 📤 **Export/Import** | Endpoints CSV e Excel com upload/download automático |
| 🎨 **Frontend React** | Vite + MUI + Redux Toolkit + Dark Mode + Responsivo |
| 🔄 **MapStruct Mappers** | Conversão automática Entity ↔ DTO |
| 🛡️ **Soft Delete** | Exclusão lógica com `deletedAt` |
| 📋 **Auditoria** | `createdAt` e `updatedAt` automáticos |
| 🔀 **Reverse Engineering** | Gera `forge.json` a partir de banco existente |
| 📐 **JSON Schema** | Validação + autocomplete na IDE para o `forge.json` |
| 🖥️ **Dashboard Web** | Editor visual local para montar o `forge.json` (auto-shutdown) |

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

  <!-- Redis (se generateCache: true + provider: redis) -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
  </dependency>

  <!-- Caffeine (se generateCache: true + provider: caffeine) -->
  <dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
  </dependency>

  <!-- Apache POI (se exportImport com format: excel) -->
  <dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>5.2.5</version>
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

# Gerar JSON Schema para autocomplete na IDE
mvn spring-forge:schema

# Reverse engineering — gera forge.json a partir de um banco existente
mvn spring-forge:reverse -Dforge.jdbcUrl=jdbc:postgresql://localhost:5432/mydb -Dforge.jdbcUser=user -Dforge.jdbcPassword=pass
```

### Goals disponíveis

| Goal | Descrição |
|------|-----------|
| `spring-forge:generate` | Gera código a partir do `forge.json` |
| `spring-forge:schema` | Gera `forge-schema.json` para validação/autocomplete na IDE |
| `spring-forge:reverse` | Gera `forge.json` a partir de um banco de dados existente |
| `spring-forge:ui` | Abre dashboard web para editar o `forge.json` visualmente |

### Onde o código é gerado?

Por padrão o código vai para `target/generated-sources/spring-forge/`. Após revisar os arquivos, **copie-os manualmente para `src/main/java`** — isso garante que você tem controle total sobre o que entra no seu projeto.

### Tabela de parâmetros

| Parâmetro | Propriedade (`-D`) | Default | Descrição |
|-----------|-------------------|---------|-----------|
| `inputFile` | `forge.input` | `${project.basedir}/forge.json` | Caminho do `forge.json` |
| `outputDir` | `forge.outputDir` | `target/generated-sources/spring-forge` | Diretório de saída |
| `entitiesFilter` | `forge.entities` | *(todas)* | Gerar apenas entidades específicas (vírgula) |
| `skip` | `forge.skip` | `false` | Pular execução |
| `addSourceRoot` | `forge.addSourceRoot` | `false` | Registrar como compile source root |

### Parâmetros do `reverse`

| Propriedade (`-D`) | Default | Descrição |
|-------------------|---------|-----------|
| `forge.jdbcUrl` | *(obrigatório)* | URL JDBC do banco |
| `forge.jdbcUser` | *(obrigatório)* | Usuário do banco |
| `forge.jdbcPassword` | `""` | Senha |
| `forge.basePackage` | `com.myapp` | Pacote base no JSON gerado |
| `forge.schema` | *(default do driver)* | Schema a analisar |
| `forge.tables` | *(todas)* | Tabelas a incluir (vírgula) |
| `forge.excludeTables` | `flyway_schema_history,...` | Tabelas a excluir |
| `forge.output` | `forge.json` | Caminho de saída |

---

## 📐 Estrutura do `forge.json`

### `project` — Configuração global

| Campo | Tipo | Default | Descrição |
|-------|------|---------|-----------| 
| `basePackage` | String | **obrigatório** | Pacote base, ex: `com.myapp` |
| `name` | String | **obrigatório** | Nome do projeto |
| `database` | String | `postgres` | `postgres`, `mysql`, `mongodb`, `h2` |
| `generateMigrations` | Boolean | `false` | Gerar scripts Flyway |
| `generateMappers` | Boolean | `true` | Gerar Mappers MapStruct |
| `generateFrontend` | Boolean | `false` | Gerar frontend React (Vite+MUI+Redux) |
| `generateRabbitMQ` | Boolean | `false` | Gerar config e classes RabbitMQ |
| `generateOpenApi` | Boolean | `false` | Gerar anotações SpringDoc/OpenAPI |
| `generateSpringEvents` | Boolean | `false` | Gerar ApplicationEvent + Listeners |
| `generateScheduled` | Boolean | `false` | Gerar `@Scheduled` para actions agendadas |
| `generateTests` | Boolean | `false` | Gerar testes unitários (JUnit 5 + Mockito) |
| `generateSecurity` | Boolean | `false` | Gerar Spring Security + RBAC |
| `generateCache` | Boolean | `false` | Gerar Spring Cache (Redis ou Caffeine) |
| `frontendDir` | String | `frontend/src` | Diretório de saída do frontend |

---

### `entity` — Definição de entidade

| Campo | Tipo | Default | Descrição |
|-------|------|---------|-----------| 
| `name` | String | **obrigatório** | Nome em PascalCase, ex: `Product` |
| `tableName` | String | auto (snake_case) | Nome da tabela no banco |
| `auditable` | Boolean | `true` | Gera `createdAt` e `updatedAt` |
| `softDelete` | Boolean | `false` | Gera `deletedAt` + exclusão lógica |
| `apiPath` | String | auto | Path da API REST |
| `openApiTags` | Array | `[]` | Tags OpenAPI |
| `roles` | Array | `[]` | Roles RBAC |
| `filters` | Array | `[]` | Filtros de busca com operadores |
| `cache` | Object | — | Configuração de cache |
| `exportImport` | Object | — | Configuração de export/import |
| `generate` | Array | tudo | Layers a gerar |

---

### `cache` — Spring Cache

Definido na entidade. Requer `generateCache: true` no project.

| Campo | Tipo | Default | Descrição |
|-------|------|---------|-----------| 
| `enabled` | Boolean | `true` | Ativa cache para esta entidade |
| `ttlSeconds` | Integer | `300` | Time-to-live em segundos |
| `maxSize` | Integer | `500` | Tamanho máximo do cache |
| `provider` | String | `redis` | `redis` ou `caffeine` |

**Exemplo:**
```json
{
  "cache": {
    "enabled": true,
    "ttlSeconds": 600,
    "maxSize": 1000,
    "provider": "redis"
  }
}
```

**Gera:**
- `CacheConfig.java` — `@EnableCaching` + CacheManager com TTL por entidade
- `ProductCachedServiceImpl.java` — `@Primary` decorator que delega para o ServiceImpl e adiciona `@Cacheable` (findById), `@CachePut` (update), `@CacheEvict` (create/delete). Actions customizadas são delegadas diretamente.

---

### `exportImport` — Export/Import CSV e Excel

| Campo | Tipo | Default | Descrição |
|-------|------|---------|-----------| 
| `enabled` | Boolean | `true` | Ativa export/import |
| `formats` | Array | `["csv"]` | Formatos: `csv`, `excel` |
| `exportFields` | Array | *(todos do response)* | Campos a exportar |
| `importFields` | Array | *(todos do request)* | Campos aceitos no import |

**Exemplo:**
```json
{
  "exportImport": {
    "enabled": true,
    "formats": ["csv", "excel"],
    "exportFields": ["name", "sku", "price", "stock"],
    "importFields": ["name", "sku", "price", "stock"]
  }
}
```

**Endpoints gerados:**
- `GET /api/v1/products/export/csv` — download CSV
- `POST /api/v1/products/import/csv` — upload CSV (multipart)
- `GET /api/v1/products/export/excel` — download XLSX
- `POST /api/v1/products/import/excel` — upload XLSX (multipart)

**Resposta do import:**
```json
{ "imported": 42, "message": "42 registro(s) importado(s)" }
```

---

### `filter` — Filtros de busca com operadores

| Campo | Tipo | Default | Descrição |
|-------|------|---------|-----------|
| `name` | String | **obrigatório** | Nome do campo de filtro |
| `type` | String | **obrigatório** | Tipo do valor |
| `label` | String | = `name` | Label para exibição |
| `operator` | String | *(auto)* | Operador de comparação |
| `targetField` | String | *(inferido)* | Campo real na entidade |
| `enumValues` | Array | — | Valores do Enum |

**Operadores disponíveis:**

| Operador | SQL gerado | Tipo no DTO |
|----------|-----------|-------------|
| `EQUALS` | `= ?` | `T` |
| `NOT_EQUALS` | `!= ?` | `T` |
| `CONTAINS` | `LIKE %?%` (case-insensitive) | `String` |
| `STARTS_WITH` | `LIKE ?%` | `String` |
| `ENDS_WITH` | `LIKE %?` | `String` |
| `GREATER_THAN` | `> ?` | `T` |
| `GREATER_THAN_OR_EQUAL` | `>= ?` | `T` |
| `LESS_THAN` | `< ?` | `T` |
| `LESS_THAN_OR_EQUAL` | `<= ?` | `T` |
| `IN` | `IN (?)` | `List<T>` |
| `BETWEEN` | `BETWEEN ? AND ?` | `List<T>` (2 elementos) |
| `IS_NULL` | `IS NULL` | `Boolean` (flag) |
| `IS_NOT_NULL` | `IS NOT NULL` | `Boolean` (flag) |

**Convenções automáticas** (quando `operator` é omitido):
- Campos `String` → `CONTAINS`
- Campos `Enum` / `Boolean` → `EQUALS`
- Sufixo `Min` → `GREATER_THAN_OR_EQUAL`
- Sufixo `Max` → `LESS_THAN_OR_EQUAL`

**Exemplo avançado:**
```json
{
  "filters": [
    { "name": "name", "type": "String", "operator": "STARTS_WITH" },
    { "name": "tags", "type": "String", "operator": "IN" },
    { "name": "priceRange", "type": "BigDecimal", "operator": "BETWEEN", "targetField": "price" },
    { "name": "hasDescription", "type": "String", "operator": "IS_NOT_NULL", "targetField": "description" }
  ]
}
```

---

### `field` — Campos da entidade

| Campo | Tipo | Default | Descrição |
|-------|------|---------|-----------| 
| `name` | String | **obrigatório** | Nome em camelCase |
| `type` | String | **obrigatório** | `String`, `Integer`, `Long`, `Double`, `Float`, `BigDecimal`, `Boolean`, `LocalDate`, `LocalDateTime`, `UUID`, `Enum` |
| `required` | Boolean | `false` | `@NotNull` / `@NotBlank` |
| `unique` | Boolean | `false` | `unique = true` na coluna |
| `maxLength` | Integer | — | Tamanho máximo |
| `minLength` | Integer | — | Tamanho mínimo |
| `defaultValue` | String | — | Valor padrão no banco |
| `columnName` | String | auto | Nome da coluna |
| `enumValues` | Array | — | Valores do Enum |
| `validations` | Array | — | Anotações extras |
| `inResponse` | Boolean | `true` | Incluir no ResponseDTO |
| `inRequest` | Boolean | `true` | Incluir no RequestDTO |

---

### `relation` — Relacionamentos

| Campo | Tipo | Default | Descrição |
|-------|------|---------|-----------| 
| `type` | String | **obrigatório** | `ManyToOne`, `OneToMany`, `OneToOne`, `ManyToMany` |
| `targetEntity` | String | **obrigatório** | Entidade alvo |
| `fieldName` | String | **obrigatório** | Nome do campo |
| `fetch` | String | `LAZY` | `LAZY` ou `EAGER` |
| `cascade` | String | `MERGE,PERSIST` | Tipos de cascade |
| `mappedBy` | String | — | Para `OneToMany`/`ManyToMany` |
| `inResponse` | Boolean | `true` | Incluir no ResponseDTO |

> **Nota:** Para relações `ManyToOne`, o plugin gera automaticamente `{fieldName}Id` (tipo `Long`) no `RequestDTO` e `ResponseDTO`. No service, o ID é usado para buscar a entidade relacionada via repository.

---

### `action` — Endpoints customizados

| Campo | Tipo | Default | Descrição |
|-------|------|---------|-----------| 
| `name` | String | **obrigatório** | Nome em camelCase |
| `description` | String | — | Descrição Javadoc |
| `httpMethod` | String | — | `GET`, `POST`, `PUT`, `PATCH`, `DELETE` |
| `apiPath` | String | `/{id}/{name}` | Path relativo |
| `requiresId` | Boolean | `true` | Recebe ID como parâmetro |
| `request` | Array | `[]` | Campos do DTO de input |
| `response` | Array | `[]` | Campos do DTO de output |
| `event` | Object | — | Evento Spring a publicar |
| `queues` | Array | `[]` | Filas RabbitMQ |
| `scheduled` | Boolean | `false` | Gera `@Scheduled` |
| `scheduledCron` | String | — | Cron expression |
| `scheduledFixedRate` | Long | — | Fixed rate em ms |
| `roles` | Array | `[]` | Roles RBAC específicas |

---

### `event` — Spring Events

| Campo | Tipo | Default | Descrição |
|-------|------|---------|-----------| 
| `name` | String | `${ActionName}Event` | Nome da classe |
| `generateListener` | Boolean | `false` | Gera `@EventListener` |
| `async` | Boolean | `false` | Usa `@Async` |
| `description` | String | — | Descrição Javadoc |

---

### `queue` — RabbitMQ

| Campo | Tipo | Default | Descrição |
|-------|------|---------|-----------| 
| `name` | String | **obrigatório** | Nome da fila |
| `exchange` | String | `""` | Nome da exchange |
| `routingKey` | String | = `name` | Routing key |
| `durable` | Boolean | `true` | Fila durável |
| `direction` | String | `PUBLISH` | `PUBLISH`, `CONSUME` ou `BOTH` |
| `deadLetterExchange` | String | — | Cria DLQ automaticamente |
| `retryTtlMs` | Integer | `30000` | TTL de retry para DLQ |

---

## 📐 JSON Schema e Validação

### Gerar o schema

```bash
mvn spring-forge:schema
```

Gera `forge-schema.json` na raiz do projeto. Referencie no seu `forge.json`:

```json
{
  "$schema": "./forge-schema.json",
  "project": { ... }
}
```

**Benefícios na IDE:**
- ✅ Autocomplete de propriedades
- ✅ Validação em tempo real (tipos, enums, padrões)
- ✅ Documentação inline ao passar o mouse
- ✅ Detecção de erros antes de rodar o Maven

### Validação no build

O parser valida automaticamente com mensagens descritivas:

```
[ERROR] forge.json inválido (2 erro(s)):
  1. entities[0].name 'product' deve ser PascalCase (ex: Product, OrderItem).
  2. entities[0].fields[1].type 'Texto' inválido. Use: String, Integer, Long, ...

  Dica: execute 'mvn spring-forge:schema' para gerar o JSON Schema com autocomplete.
```

---

## 🖥️ Dashboard Web

Edite o `forge.json` visualmente com um dashboard local:

```bash
mvn spring-forge:ui
# ou em outra porta:
mvn spring-forge:ui -Dforge.ui.port=8080
```

Abre automaticamente `http://localhost:4200` no browser.

**O que o dashboard oferece:**
- Editor visual de entidades, campos e relacionamentos
- Configuração de filtros com operadores (CONTAINS, IN, BETWEEN...)
- Configuração de export/import (formatos, campos)
- Configuração do projeto com checkboxes para todas as flags
- Validação em tempo real com feedback de erros
- Botão "Gerar" que salva e executa a geração
- Aba JSON para edição raw quando necessário
- Toast notifications com resultado da operação
- Auto-shutdown: fecha o browser e o server libera a porta automaticamente
- Funciona em qualquer IDE — é um servidor web local

---

## 🔀 Reverse Engineering

Gera `forge.json` a partir de um banco de dados existente:

```bash
mvn spring-forge:reverse \
  -Dforge.jdbcUrl=jdbc:postgresql://localhost:5432/mydb \
  -Dforge.jdbcUser=postgres \
  -Dforge.jdbcPassword=secret \
  -Dforge.basePackage=com.myapp \
  -Dforge.schema=public
```

**O que detecta automaticamente:**
- Tabelas → entidades (PascalCase)
- Colunas → fields (camelCase) com tipos mapeados
- Foreign keys → relations `ManyToOne`
- `created_at` / `updated_at` → `auditable: true`
- `deleted_at` → `softDelete: true`
- Unique constraints → `unique: true`

---

## 📂 O que é gerado

```
target/generated-sources/spring-forge/
└── com/myapp/
    ├── config/
    │   └── CacheConfig.java                    ← (se generateCache: true)
    ├── entity/
    │   ├── Product.java
    │   └── ProductStatus.java                  ← Enum
    ├── repository/
    │   └── ProductRepository.java
    ├── service/
    │   ├── ProductService.java
    │   ├── ProductExportImportService.java     ← (se exportImport)
    │   └── impl/
    │       ├── ProductServiceImpl.java
    │       ├── ProductCachedServiceImpl.java   ← (se cache)
    │       └── ProductExportImportServiceImpl.java
    ├── controller/
    │   ├── ProductController.java
    │   ├── ProductExportImportController.java  ← (se exportImport)
    │   └── ProductControllerDocs.java          ← (se generateOpenApi)
    ├── dto/
    │   ├── ProductRequestDTO.java
    │   ├── ProductResponseDTO.java
    │   └── ProductFilterDTO.java               ← (se filters)
    ├── mapper/
    │   └── ProductMapper.java
    ├── specification/
    │   └── ProductSpecification.java           ← (se filters)
    ├── event/
    │   ├── ProductActivatedEvent.java
    │   └── ProductActivatedEventListener.java
    ├── messaging/
    │   ├── RabbitMQConfig.java
    │   ├── ProductStockPublisher.java
    │   └── ProductStockConsumer.java
    ├── scheduler/
    │   └── ProductScheduledTasks.java
    ├── security/
    │   ├── SecurityConfig.java
    │   └── AppRole.java
    └── exception/
        ├── ProductNotFoundException.java
        └── GlobalExceptionHandler.java
```

**Frontend** (se `generateFrontend: true`):
```
frontend/src/
├── App.tsx, theme.ts, routes.tsx, main.tsx
├── validation/productSchema.ts                 ← Zod
├── store/
│   ├── store.ts, hooks.ts
│   └── slices/productSlice.ts
├── components/
│   ├── AppMenu.tsx, AppHeader.tsx
│   ├── shared/
│   │   ├── PageHeader.tsx
│   │   ├── ConfirmDialog.tsx
│   │   ├── EmptyState.tsx
│   │   ├── EntitySelect.tsx
│   │   ├── FormTextField.tsx
│   │   ├── StatusChip.tsx
│   │   ├── LoadingOverlay.tsx
│   │   └── NotificationProvider.tsx
│   └── product/
│       └── ProductFilterPanel.tsx
└── pages/product/
    ├── ListProductPage.tsx
    ├── ProductFormPage.tsx
    └── ProductDetailPage.tsx
```

---

## 🧪 Exemplo completo de `forge.json`

<details>
<summary>📄 Clique para expandir</summary>

```json
{
  "$schema": "./forge-schema.json",
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
    "generateSecurity": true,
    "generateCache": true
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
      "cache": {
        "enabled": true,
        "ttlSeconds": 600,
        "maxSize": 1000,
        "provider": "redis"
      },
      "exportImport": {
        "enabled": true,
        "formats": ["csv", "excel"],
        "exportFields": ["name", "sku", "price", "stock", "status"]
      },
      "filters": [
        { "name": "name", "type": "String", "label": "Nome", "operator": "CONTAINS" },
        { "name": "status", "type": "Enum", "enumValues": ["ACTIVE", "INACTIVE"], "operator": "EQUALS" },
        { "name": "priceMin", "type": "BigDecimal", "operator": "GREATER_THAN_OR_EQUAL", "targetField": "price" },
        { "name": "priceMax", "type": "BigDecimal", "operator": "LESS_THAN_OR_EQUAL", "targetField": "price" },
        { "name": "tags", "type": "String", "operator": "IN" }
      ],
      "fields": [
        { "name": "name", "type": "String", "required": true, "maxLength": 200 },
        { "name": "price", "type": "BigDecimal", "required": true },
        { "name": "stock", "type": "Integer", "required": true },
        { "name": "sku", "type": "String", "required": true, "unique": true },
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
    <inputFile>${project.basedir}/forge.json</inputFile>
    <outputDir>${project.build.directory}/generated-sources/spring-forge</outputDir>
    <skip>false</skip>
    <addSourceRoot>false</addSourceRoot>
  </configuration>
</plugin>
```

---

## 🐛 Problemas conhecidos e soluções

### `duplicate class: com.myapp.controller.ProductController`

O `outputDir` aponta para `src/main/java`. O plugin detecta isso e lança erro. Mantenha o padrão (`target/generated-sources/spring-forge`) e copie manualmente.

### O plugin rodou sozinho no `mvn clean install`

Remova `<executions>` com `<phase>` do pom.xml. O plugin tem `defaultPhase = NONE`.

### Erro "propriedade desconhecida" no forge.json

O parser agora reporta propriedades desconhecidas com sugestões. Execute `mvn spring-forge:schema` e use o `$schema` no JSON para autocomplete.

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
