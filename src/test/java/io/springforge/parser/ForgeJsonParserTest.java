package io.springforge.parser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.maven.plugin.MojoExecutionException;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.springforge.model.ForgeDefinition;

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
        assertTrue(ex.getMessage().contains("INVALID_BASE_PACKAGE") || ex.getMessage().contains("Pacote Java inválido") || ex.getMessage().contains("pacote Java"));
    }

    @Test
    void shouldValidateEntitySchemaAsSqlIdentifier() throws Exception {
        String json = """
            {
              "project": { "basePackage": "com.app", "name": "App" },
              "entities": [{ "name": "Product", "schema": "laboral-data", "fields": [] }]
            }
            """;
        MojoExecutionException ex = assertThrows(MojoExecutionException.class, () -> parser.parse(writeJson(json)));
        assertTrue(ex.getMessage().toLowerCase().contains("schema"));
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
        assertTrue(ex.getMessage().contains("DUPLICATE_ENTITY") || ex.getMessage().contains("duplicado") || ex.getMessage().contains("Entidade duplicada"));
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

    @Test
    void shouldRejectUnknownProperties() throws Exception {
        String json = """
            {
              "project": { "basePackage": "com.app", "name": "App", "generateMappres": true },
              "entities": [{ "name": "Product", "fields": [{ "name": "title", "type": "String" }] }]
            }
            """;
        MojoExecutionException ex = assertThrows(MojoExecutionException.class, () -> parser.parse(writeJson(json)));
        assertTrue(ex.getMessage().contains("UNKNOWN_PROPERTY") || ex.getMessage().contains("Propriedade desconhecida") || ex.getMessage().contains("generateMappres"));
    }

    @Test
    void shouldParseModularArchitecture() throws Exception {
        String json = """
            {
              "project": {
                "basePackage": "com.app",
                "name": "App",
                "architectureStyle": "MODULAR"
              },
              "entities": [{ "name": "Product", "fields": [{ "name": "title", "type": "String" }] }]
            }
            """;
        ForgeDefinition def = parser.parse(writeJson(json));
        assertTrue(def.getProject().isModular());
    }

    @Test
    void shouldParseAdvancedCrudWithNestedFilter() throws Exception {
        String json = """
            {
              "project": { "basePackage": "com.app", "name": "App" },
              "entities": [
                { "name": "Category", "fields": [{ "name": "name", "type": "String" }] },
                {
                  "name": "Product",
                  "fields": [{ "name": "name", "type": "String" }],
                  "relations": [{ "type": "ManyToOne", "targetEntity": "Category", "fieldName": "category" }],
                  "crud": {
                    "defaultSort": "category.name",
                    "sortable": ["name", "category.name"],
                    "filterable": [
                      { "name": "categoryName", "type": "String", "operator": "CONTAINS", "targetField": "category.name" }
                    ]
                  }
                }
              ]
            }
            """;
        ForgeDefinition def = parser.parse(writeJson(json));
        assertEquals("category.name", def.getEntities().get(1).getCrud().getDefaultSort());
        assertEquals(1, def.getEntities().get(1).getEffectiveFilters().size());
    }

    @Test
    void shouldParseDashboardManagedTemplatesFrontendAndCrud() throws Exception {
        String json = """
            {
              "project": {
                "basePackage": "com.app",
                "name": "App",
                "templates": { "enabled": true, "templateDir": ".spring-forge/templates", "suffix": ".tpl" },
                "frontend": {
                  "enabled": true,
                  "framework": "react",
                  "ui": "mui",
                  "state": "redux-toolkit",
                  "forms": "react-hook-form",
                  "validation": "zod",
                  "filterPanel": true,
                  "tableSorting": true
                }
              },
              "entities": [
                { "name": "Category", "fields": [{ "name": "name", "type": "String" }] },
                {
                  "name": "Product",
                  "fields": [{ "name": "name", "type": "String" }],
                  "relations": [{ "type": "ManyToOne", "targetEntity": "Category", "fieldName": "category" }],
                  "crud": {
                    "pagination": true,
                    "defaultPageSize": 20,
                    "maxPageSize": 100,
                    "defaultSort": "category.name",
                    "sortable": ["name", "category.name"],
                    "filterable": [
                      { "name": "categoryName", "type": "String", "operator": "CONTAINS", "targetField": "category.name" }
                    ],
                    "bulkOperations": true
                  }
                }
              ]
            }
            """;
        ForgeDefinition def = parser.parse(writeJson(json));
        assertTrue(def.getProject().isGenerateFrontend());
        assertTrue(def.getProject().getTemplates().isEnabled());
        assertEquals("react", def.getProject().getFrontend().getFramework());
        assertEquals("category.name", def.getEntities().get(1).getCrud().getFilterable().get(0).getTargetField());
        assertTrue(def.getEntities().get(1).getCrud().isBulkOperations());
    }

}
