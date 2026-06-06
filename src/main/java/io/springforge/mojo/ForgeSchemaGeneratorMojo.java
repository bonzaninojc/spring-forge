package io.springforge.mojo;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Goal "schema" — gera o JSON Schema do forge.json para validação e autocomplete em IDEs.
 *
 * Uso:
 *   mvn spring-forge:schema
 *   mvn spring-forge:schema -Dforge.schemaOutput=forge-schema.json
 *
 * O schema gerado pode ser referenciado no forge.json:
 *   { "$schema": "./forge-schema.json", "project": {...} }
 */
@Mojo(
    name = "schema",
    defaultPhase = LifecyclePhase.NONE,
    requiresProject = false,
    threadSafe = true
)
public class ForgeSchemaGeneratorMojo extends AbstractMojo {

    @Parameter(property = "forge.schemaOutput", defaultValue = "forge-schema.json")
    private File outputFile;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("Gerando JSON Schema do forge.json -> " + outputFile.getPath());

        try {
            InputStream is = getClass().getResourceAsStream("/forge-schema.json");
            if (is == null) {
                throw new MojoExecutionException("Resource forge-schema.json não encontrado no classpath do plugin.");
            }
            String schema = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            is.close();

            File parent = outputFile.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            try (Writer w = new OutputStreamWriter(new FileOutputStream(outputFile), StandardCharsets.UTF_8)) {
                w.write(schema);
            }
        } catch (MojoExecutionException e) {
            throw e;
        } catch (Exception e) {
            throw new MojoExecutionException("Erro ao gravar schema: " + e.getMessage(), e);
        }

        getLog().info("forge-schema.json gerado com sucesso.");
        getLog().info("  Adicione ao forge.json: \"$schema\": \"./" + outputFile.getName() + "\"");
    }
}
