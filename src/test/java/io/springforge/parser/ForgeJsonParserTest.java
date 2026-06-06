package io.springforge.parser;

import io.springforge.model.ForgeDefinition;
import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ForgeJsonParserTest {

    private final ForgeJsonParser parser = new ForgeJsonParser();

    @TempDir
    Path tempDir;

    private File writeJson(String content) throws Exception {
        File f = tempDir.resolve("forge.json").toFile();
        Files.writeString(f.toPath(), content);
        return f;
    }

    @Test
    void shouldParseValidJson() throws Exception {
        String json = """
            {
              "project": { "basePackage": "com.myapp", "name": "MyApp" },
              "entities": [{ "name": "Product", "fields": [{ "name": "title", "type": "String" }] }]
            }
            """;
        ForgeDefinition def = parser.parse(writeJson(json));
        assertEquals("com.myapp", def.getProject().getBasePackage());
        assertEquals(1, def.getEntities().size());
    }

    @Test
    void shouldRejectMissingProject() throws Exception {
        String json = """
            { "entities": [{ "name": "Product", "fields": [] }] }
            """;
        MojoExecutionException ex = assertThrows(MojoExecutionException.class, () -> parser.parse(writeJson(json)));
        assertTrue(ex.getMessage().contains("project"));
    }

    @Test
    void shouldRejectInvalidBasePackage() throws Exception {
        String json = """
            {
              "project": { "basePackage": "Com.Invalid", "name": "App" },
              "entities": [{ "name": "Product", "fields": [{ "name": "x", "type": "String" }] }]
            }
            """;
        MojoExecutionException ex = assertThrows(MojoExecutionException.class, () -> parser.parse(writeJson(json)));
        assertTrue(ex.getMessage().contains("pacote Java válido"));
    }

    @Test
    void shouldRejectDuplicateEntityNames() throws Exception {
        String json = """
            {
              "project": { "basePackage": "com.app", "name": "App" },
              "entities": [
                { "name": "Product", "fields": [{ "name": "x", "type": "String" }] },
                { "name": "Product", "fields": [{ "name": "y", "type": "String" }] }
              ]
            }
            """;
        MojoExecutionException ex = assertThrows(MojoExecutionException.class, () -> parser.parse(writeJson(json)));
        assertTrue(ex.getMessage().contains("duplicado"));
    }

    @Test
    void shouldRejectInvalidFieldType() throws Exception {
        String json = """
            {
              "project": { "basePackage": "com.app", "name": "App" },
              "entities": [{ "name": "Product", "fields": [{ "name": "x", "type": "InvalidType" }] }]
            }
            """;
        MojoExecutionException ex = assertThrows(MojoExecutionException.class, () -> parser.parse(writeJson(json)));
        assertTrue(ex.getMessage().contains("inválido"));
    }

    @Test
    void shouldRejectEnumWithoutValues() throws Exception {
        String json = """
            {
              "project": { "basePackage": "com.app", "name": "App" },
              "entities": [{ "name": "Product", "fields": [{ "name": "status", "type": "Enum" }] }]
            }
            """;
        MojoExecutionException ex = assertThrows(MojoExecutionException.class, () -> parser.parse(writeJson(json)));
        assertTrue(ex.getMessage().contains("enumValues"));
    }

    @Test
    void shouldRejectInvalidHttpMethod() throws Exception {
        String json = """
            {
              "project": { "basePackage": "com.app", "name": "App" },
              "entities": [{
                "name": "Product",
                "fields": [{ "name": "x", "type": "String" }],
                "actions": [{ "name": "doThing", "httpMethod": "INVALID" }]
              }]
            }
            """;
        MojoExecutionException ex = assertThrows(MojoExecutionException.class, () -> parser.parse(writeJson(json)));
        assertTrue(ex.getMessage().contains("httpMethod"));
    }

    @Test
    void shouldShowHintAboutSchema() throws Exception {
        String json = """
            {
              "project": { "basePackage": "com.app", "name": "App" },
              "entities": [{ "name": "bad name", "fields": [] }]
            }
            """;
        MojoExecutionException ex = assertThrows(MojoExecutionException.class, () -> parser.parse(writeJson(json)));
        assertTrue(ex.getMessage().contains("spring-forge:schema"));
    }

    @Test
    void shouldRejectInvalidFilterOperator() throws Exception {
        String json = """
            {
              "project": { "basePackage": "com.app", "name": "App" },
              "entities": [{
                "name": "Product",
                "fields": [{ "name": "x", "type": "String" }],
                "filters": [{ "name": "x", "type": "String", "operator": "NOPE" }]
              }]
            }
            """;
        MojoExecutionException ex = assertThrows(MojoExecutionException.class, () -> parser.parse(writeJson(json)));
        assertTrue(ex.getMessage().contains("operator"));
    }
}
