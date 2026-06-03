package io.springforge.mojo;

import io.springforge.generator.*;
import io.springforge.model.EntityDefinition;
import io.springforge.model.ForgeDefinition;
import io.springforge.parser.ForgeJsonParser;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.Arrays;
import java.util.List;

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

        // Garante que o outputDir nunca aponta para src/main/java (evita duplicate class)
        validateOutputDir();

        getLog().info("╔══════════════════════════════════════╗");
        getLog().info("║      Spring Forge Maven Plugin       ║");
        getLog().info("╚══════════════════════════════════════╝");
        getLog().info("  forge.json : " + inputFile.getPath());
        getLog().info("  Java out   : " + outputDir.getPath());

        // 1. Parse
        ForgeDefinition definition = new ForgeJsonParser().parse(inputFile);
        getLog().info("  Pacote     : " + definition.getProject().getBasePackage());
        getLog().info("  Entidades  : " + definition.getEntities().size());

        // 2. Filtro
        List<EntityDefinition> entities = filterEntities(definition);

        // 3. Geradores
        List<AbstractGenerator> generators = Arrays.asList(
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
            new ScheduledTaskGenerator(getLog())
        );

        FrontendGenerator frontendGenerator = new FrontendGenerator(getLog());

        // 4. Gera
        int total = 0;
        for (EntityDefinition entity : entities) {
            getLog().info("");
            getLog().info("► Gerando: " + entity.getName());
            for (AbstractGenerator gen : generators) {
                gen.generate(definition, entity, outputDir);
            }
            frontendGenerator.generate(definition, entity, outputDir);
            total++;
        }

        // 4.1 Frontend: arquivos globais (store, routes, menu, App)
        frontendGenerator.generateGlobalFiles(definition, outputDir);

        // 4.2 Frontend: arquivos de projeto (package.json, vite.config, tsconfigs, index.html, main.tsx)
        new FrontendProjectGenerator(getLog()).generate(definition, outputDir);

        // 4.3 RabbitMQ: config global (Jackson converter, RabbitTemplate)
        new RabbitMQGenerator(getLog()).generateGlobalConfig(definition, outputDir);

        // 4.4 Security: SecurityConfig + AppRole enum
        new SecurityGenerator(getLog()).generateGlobalSecurity(definition, outputDir);

        // 4.5 Testes unitários (JUnit 5 + Mockito)
        if (definition.getProject().isGenerateTests()) {
            TestGenerator testGen = new TestGenerator(getLog());
            for (EntityDefinition entity : entities) {
                testGen.generate(definition, entity, outputDir);
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
