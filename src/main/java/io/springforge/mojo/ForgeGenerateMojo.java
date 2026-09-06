package io.springforge.mojo;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import io.springforge.generator.AbstractGenerator;
import io.springforge.generator.CacheGenerator;
import io.springforge.generator.ControllerGenerator;
import io.springforge.generator.CustomTemplateGenerator;
import io.springforge.generator.DtoGenerator;
import io.springforge.generator.EntityGenerator;
import io.springforge.generator.ExceptionGenerator;
import io.springforge.generator.ExportImportGenerator;
import io.springforge.generator.FilterGenerator;
import io.springforge.generator.FrontendGenerator;
import io.springforge.generator.FrontendProjectGenerator;
import io.springforge.generator.HexagonalAppServiceGenerator;
import io.springforge.generator.HexagonalDomainModelGenerator;
import io.springforge.generator.HexagonalPersistenceAdapterGenerator;
import io.springforge.generator.HexagonalPortGenerator;
import io.springforge.generator.HexagonalRestAdapterGenerator;
import io.springforge.generator.MapperGenerator;
import io.springforge.generator.MigrationGenerator;
import io.springforge.generator.OpenApiEnricher;
import io.springforge.generator.PomDependencyEnricher;
import io.springforge.generator.RabbitMQGenerator;
import io.springforge.generator.RepositoryGenerator;
import io.springforge.generator.ScheduledTaskGenerator;
import io.springforge.generator.ServiceGenerator;
import io.springforge.generator.SpringEventGenerator;
import io.springforge.generator.TestGenerator;
import io.springforge.model.EntityDefinition;
import io.springforge.model.ForgeDefinition;
import io.springforge.parser.ForgeJsonParser;

/**
 * Gera código Spring Boot completo a partir de um forge.json.
 *
 * Saídas:
 *   Java  → target/generated-sources/spring-forge/
 *   SQL   → target/generated-resources/spring-forge/db/migration/
 *
 * Uso:
 *   mvn spring-forge:generate                          ← modo padrão (não roda no build)
 *   mvn spring-forge:generate -Dforge.input=outro.json
 *   mvn spring-forge:generate -Dforge.entities=Product,Category
 *   mvn spring-forge:generate -Dforge.addSourceRoot=true  ← adiciona ao compile source root
 */
@Mojo(
    name = "generate",
    defaultPhase = LifecyclePhase.NONE,
    requiresDependencyResolution = ResolutionScope.NONE,
    threadSafe = true
)
public class ForgeGenerateMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    /** Caminho para o forge.json. Default: ${project.basedir}/forge.json */
    @Parameter(property = "forge.input", defaultValue = "${project.basedir}/forge.json")
    private File inputFile;

    /**
     * Diretório de saída do código Java gerado.
     * Default: target/generated-sources/spring-forge
     */
    @Parameter(property = "forge.outputDir",
               defaultValue = "${project.build.directory}/generated-sources/spring-forge")
    private File outputDir;

    /**
     * Filtro de entidades (separadas por vírgula).
     * Se vazio, gera todas.
     */
    @Parameter(property = "forge.entities", defaultValue = "")
    private String entitiesFilter;

    /** Se true, pula a execução. */
    @Parameter(property = "forge.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Se true, registra o outputDir como compile source root do Maven.
     * Útil apenas se você quiser integrar ao build automático.
     * Default: false — o código gerado vai para target/generated-sources/spring-forge
     * e você copia/move manualmente para src/main/java quando quiser.
     */
    @Parameter(property = "forge.addSourceRoot", defaultValue = "false")
    private boolean addSourceRoot;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip) {
            getLog().info("Spring Forge: execução pulada (forge.skip=true)");
            return;
        }

        getLog().info("╔══════════════════════════════════════╗");
        getLog().info("║      Spring Forge Maven Plugin       ║");
        getLog().info("╚══════════════════════════════════════╝");
        getLog().info("  forge.json : " + inputFile.getPath());

        // 1. Parse
        ForgeDefinition definition = new ForgeJsonParser().parse(inputFile);

        // Atualiza o pom.xml do projeto alvo quando habilitado em project.pom.autoUpdate.
        // Executa cedo para garantir que as dependências estejam disponíveis antes de compilar o código gerado.
        new PomDependencyEnricher(getLog()).enrich(project.getBasedir(), definition);

        applyJsonOutputDir(definition);
        // Garante que o outputDir nunca aponta para src/main/java (evita duplicate class)
        validateOutputDir();
        getLog().info("  Java out   : " + outputDir.getPath());
        getLog().info("  Pacote     : " + definition.getProject().getBasePackage());
        getLog().info("  Entidades  : " + definition.getEntities().size());
        getLog().info("  Arquitetura: " + definition.getProject().getArchitectureStyle());

        // 2. Filtro
        List<EntityDefinition> entities = filterEntities(definition);

        // 3. Geradores
        List<AbstractGenerator> generators = new ArrayList<>();
        if (definition.getProject().isHexagonal()) {
            generators.addAll(Arrays.asList(
                new DtoGenerator(getLog()),
                new ExceptionGenerator(getLog()),
                new MigrationGenerator(getLog()),
                new HexagonalDomainModelGenerator(getLog()),
                new HexagonalPortGenerator(getLog()),
                new HexagonalAppServiceGenerator(getLog()),
                new HexagonalPersistenceAdapterGenerator(getLog()),
                new HexagonalRestAdapterGenerator(getLog()),
                new OpenApiEnricher(getLog())
            ));
        } else {
            generators.addAll(Arrays.asList(
                new EntityGenerator(getLog()),
                new RepositoryGenerator(getLog()),
                new DtoGenerator(getLog()),
                new FilterGenerator(getLog()),
                new MapperGenerator(getLog()),
                new ServiceGenerator(getLog()),
                new ControllerGenerator(getLog()),
                new ExceptionGenerator(getLog()),
                new MigrationGenerator(getLog()),
                new RabbitMQGenerator(getLog()),
                new SpringEventGenerator(getLog()),
                new OpenApiEnricher(getLog()),
                new ScheduledTaskGenerator(getLog()),
                new ExportImportGenerator(getLog())
            ));
        }

        FrontendGenerator frontendGenerator = new FrontendGenerator(getLog());
        CustomTemplateGenerator customTemplateGenerator = new CustomTemplateGenerator(getLog(), project.getBasedir());

        // 4. Gera
        int total = 0;
        for (EntityDefinition entity : entities) {
            getLog().info("");
            getLog().info("► Gerando: " + entity.getName());
            for (AbstractGenerator gen : generators) {
                gen.generate(definition, entity, outputDir);
            }
            frontendGenerator.generate(definition, entity, outputDir);
            customTemplateGenerator.generate(definition, entity, outputDir);
            total++;
        }

        // 4.0 Templates globais customizados (_global)
        customTemplateGenerator.generateGlobalTemplates(definition, outputDir);

        // 4.1 Frontend: arquivos globais (store, routes, menu, App)
        frontendGenerator.generateGlobalFiles(definition, outputDir);

        // 4.2 Frontend: arquivos de projeto (package.json, vite.config, tsconfigs, index.html, main.tsx)
        new FrontendProjectGenerator(getLog()).generate(definition, outputDir);

        // 4.3 RabbitMQ: config global (Jackson converter, RabbitTemplate)
        new RabbitMQGenerator(getLog()).generateGlobalConfig(definition, outputDir);

        // 4.4 Testes unitários (JUnit 5 + Mockito)
        if (definition.getProject().isGenerateTests()) {
            TestGenerator testGen = new TestGenerator(getLog());
            for (EntityDefinition entity : entities) {
                testGen.generate(definition, entity, outputDir);
            }
        }

        // 4.6 Cache: CacheConfig + CachedServiceImpl
        if (definition.getProject().isGenerateCache()) {
            CacheGenerator cacheGen = new CacheGenerator(getLog());
            cacheGen.generateGlobalCacheConfig(definition, outputDir);
            for (EntityDefinition entity : entities) {
                cacheGen.generateCachedService(definition, entity, outputDir);
            }
        }

        // 5. Registra source root somente se explicitamente solicitado (-Dforge.addSourceRoot=true)
        //    Por padrão NÃO registra — o dev copia os arquivos gerados para src/main/java manualmente.
        if (addSourceRoot) {
            project.addCompileSourceRoot(outputDir.getPath());
            getLog().info("  [INFO] outputDir adicionado ao compile source root do Maven.");
        } else {
            getLog().info("  [INFO] Código gerado em: " + outputDir.getPath());
            getLog().info("  [INFO] Copie os arquivos para src/main/java quando quiser integrá-los.");
            getLog().info("  [INFO] Use -Dforge.addSourceRoot=true para adicionar ao source root automaticamente.");
        }

        // 6. Registra target/generated-resources/spring-forge como resource root
        //    → apenas se addSourceRoot também estiver ativo (evita migrations órfãs no classpath)
        if (addSourceRoot && definition.getProject().isGenerateMigrations()) {
            File resourcesDir = MigrationGenerator.generatedResourcesDir(outputDir);
            getLog().info("  SQL out    : " + resourcesDir.getPath());

            Resource resource = new Resource();
            resource.setDirectory(resourcesDir.getPath());
            resource.setFiltering(false);
            project.addResource(resource);
        }

        getLog().info("");
        getLog().info("✔ Spring Forge concluído: " + total + " entidade(s) gerada(s).");

        // Auto-gera forge-schema.json na raiz do projeto (se ainda não existir)
        autoGenerateSchema();
    }

    /**
     * Permite configurar project.outputDir no forge.json sem perder o parâmetro Maven forge.outputDir.
     * O JSON só sobrescreve o parâmetro quando o campo vem explicitamente preenchido.
     */
    private void applyJsonOutputDir(ForgeDefinition definition) {
        String configured = definition.getProject().getOutputDir();
        if (configured == null || configured.isBlank()) return;
        File configuredFile = new File(configured);
        outputDir = configuredFile.isAbsolute()
            ? configuredFile
            : new File(project.getBasedir(), configured);
    }

    private void autoGenerateSchema() {
        try {
            File schemaFile = new File(project.getBasedir(), "forge-schema.json");
            if (schemaFile.exists()) return;
            java.io.InputStream is = getClass().getResourceAsStream("/forge-schema.json");
            if (is == null) return;
            java.nio.file.Files.copy(is, schemaFile.toPath());
            is.close();
            getLog().info("  [GERADO] forge-schema.json (adicione \"$schema\": \"./forge-schema.json\" ao forge.json para autocomplete)");
        } catch (Exception e) {
            getLog().debug("Não foi possível gerar forge-schema.json: " + e.getMessage());
        }
    }

    /**
     * Impede que forge.outputDir aponte para src/main/java ou qualquer subdiretório dela,
     * o que causaria erros de duplicate class no compile.
     */
    private void validateOutputDir() throws MojoExecutionException {
        try {
            File srcMain = new File(project.getBasedir(), "src/main/java").getCanonicalFile();
            File out     = outputDir.getCanonicalFile();
            if (out.toPath().startsWith(srcMain.toPath())) {
                throw new MojoExecutionException(
                    "forge.outputDir não pode apontar para src/main/java nem subdiretórios dela!\n" +
                    "  outputDir atual : " + out + "\n" +
                    "  Use o padrão    : target/generated-sources/spring-forge"
                );
            }
        } catch (java.io.IOException e) {
            throw new MojoExecutionException("Erro ao validar outputDir: " + e.getMessage(), e);
        }
    }

    private List<EntityDefinition> filterEntities(ForgeDefinition definition) {
        if (entitiesFilter == null || entitiesFilter.isBlank()) {
            return definition.getEntities();
        }
        List<String> names = Arrays.asList(entitiesFilter.split(","));
        return definition.getEntities().stream()
            .filter(e -> names.contains(e.getName()))
            .toList();
    }
}
