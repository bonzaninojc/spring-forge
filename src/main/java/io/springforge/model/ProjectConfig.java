package io.springforge.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ProjectConfig {

    /** Pacote base do projeto alvo, ex: com.myapp */
    private String basePackage;

    /** Nome do projeto/módulo, ex: MyApp */
    private String name;

    /**
     * Banco de dados: postgres | mysql | mongodb
     * Default: postgres
     */
    private String database = "postgres";

    /**
     * Se true, gera scripts de migration Flyway (SQL).
     * Default: false
     */
    private boolean generateMigrations = false;

    /**
     * Se true, gera Mappers MapStruct.
     * Default: true
     */
    private boolean generateMappers = true;

    /**
     * Caminho de saída relativo ao projeto alvo.
     * Default: src/main/java
     */
    private String outputDir = "src/main/java";

    /**
     * Caminho para migrations relativo ao projeto alvo.
     * Default: src/main/resources/db/migration
     */
    private String migrationsDir = "src/main/resources/db/migration";

    /**
     * Se true, gera frontend React (Vite + MUI + Redux Toolkit).
     * Default: false
     */
    private boolean generateFrontend = false;

    /**
     * Diretório de saída do frontend gerado.
     * Default: frontend/src
     */
    private String frontendDir = "frontend/src";

    /**
     * Se true, gera configuração e classes RabbitMQ (RabbitMQConfig,
     * publishers e consumers) para as filas definidas em cada entidade.
     * Default: false
     */
    private boolean generateRabbitMQ = false;

    /**
     * Se true, gera anotações SpringDoc/OpenAPI (@Operation, @Tag, @ApiResponse)
     * nos controllers gerados.
     * Default: false
     */
    private boolean generateOpenApi = false;

    /**
     * Se true, gera ApplicationEvent + ApplicationEventPublisher
     * para as actions que declaram um "event".
     * Default: false
     */
    private boolean generateSpringEvents = false;

    /**
     * Se true, gera @Scheduled para actions marcadas com "scheduled: true".
     * Default: false
     */
    private boolean generateScheduled = false;

    /**
     * Se true, gera testes unitários (JUnit 5 + Mockito) para Service e Controller.
     * Default: false
     */
    private boolean generateTests = false;

    /**
     * Se true, gera Spring Security config com RBAC (@PreAuthorize) nos controllers.
     * Default: false
     */
    private boolean generateSecurity = false;

    /**
     * Se true, gera configuração de cache (Spring Cache + Redis ou Caffeine).
     * Default: false
     */
    private boolean generateCache = false;

    // --- Getters e Setters ---

    public String getBasePackage() { return basePackage; }
    public void setBasePackage(String basePackage) { this.basePackage = basePackage; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDatabase() { return database; }
    public void setDatabase(String database) { this.database = database; }

    public boolean isGenerateMigrations() { return generateMigrations; }
    public void setGenerateMigrations(boolean generateMigrations) { this.generateMigrations = generateMigrations; }

    public boolean isGenerateMappers() { return generateMappers; }
    public void setGenerateMappers(boolean generateMappers) { this.generateMappers = generateMappers; }

    public String getOutputDir() { return outputDir; }
    public void setOutputDir(String outputDir) { this.outputDir = outputDir; }

    public String getMigrationsDir() { return migrationsDir; }
    public void setMigrationsDir(String migrationsDir) { this.migrationsDir = migrationsDir; }

    public boolean isGenerateFrontend() { return generateFrontend; }
    public void setGenerateFrontend(boolean generateFrontend) { this.generateFrontend = generateFrontend; }

    public String getFrontendDir() { return frontendDir; }
    public void setFrontendDir(String frontendDir) { this.frontendDir = frontendDir; }

    public boolean isGenerateRabbitMQ() { return generateRabbitMQ; }
    public void setGenerateRabbitMQ(boolean generateRabbitMQ) { this.generateRabbitMQ = generateRabbitMQ; }

    public boolean isGenerateOpenApi() { return generateOpenApi; }
    public void setGenerateOpenApi(boolean generateOpenApi) { this.generateOpenApi = generateOpenApi; }

    public boolean isGenerateSpringEvents() { return generateSpringEvents; }
    public void setGenerateSpringEvents(boolean generateSpringEvents) { this.generateSpringEvents = generateSpringEvents; }

    public boolean isGenerateScheduled() { return generateScheduled; }
    public void setGenerateScheduled(boolean generateScheduled) { this.generateScheduled = generateScheduled; }

    public boolean isGenerateTests() { return generateTests; }
    public void setGenerateTests(boolean generateTests) { this.generateTests = generateTests; }

    public boolean isGenerateSecurity() { return generateSecurity; }
    public void setGenerateSecurity(boolean generateSecurity) { this.generateSecurity = generateSecurity; }

    public boolean isGenerateCache() { return generateCache; }
    public void setGenerateCache(boolean generateCache) { this.generateCache = generateCache; }
}
