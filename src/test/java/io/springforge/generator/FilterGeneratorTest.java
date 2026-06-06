package io.springforge.generator;

import io.springforge.model.*;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FilterGeneratorTest {

    private final Log log = new SystemStreamLog();
    private FilterGenerator generator;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        generator = new FilterGenerator(log);
    }

    private ForgeDefinition buildDefinition(List<FilterDefinition> filters) {
        ForgeDefinition def = new ForgeDefinition();
        ProjectConfig project = new ProjectConfig();
        project.setBasePackage("com.example");
        project.setName("TestApp");
        def.setProject(project);

        EntityDefinition entity = new EntityDefinition();
        entity.setName("Product");
        entity.setFilters(filters);

        FieldDefinition field = new FieldDefinition();
        field.setName("name");
        field.setType("String");
        entity.setFields(List.of(field));
        def.setEntities(List.of(entity));
        return def;
    }

    @Test
    void shouldGenerateContainsOperator() throws Exception {
        FilterDefinition f = new FilterDefinition();
        f.setName("name");
        f.setType("String");
        f.setOperator("CONTAINS");

        ForgeDefinition def = buildDefinition(List.of(f));
        File outDir = tempDir.toFile();
        generator.generate(def, def.getEntities().get(0), outDir);

        File specFile = new File(outDir, "com/example/specification/ProductSpecification.java");
        assertTrue(specFile.exists());

        String content = Files.readString(specFile.toPath());
        assertTrue(content.contains("cb.like(cb.lower(root.get(\"name\"))"));
        assertTrue(content.contains("toLowerCase()"));
    }

    @Test
    void shouldGenerateInOperator() throws Exception {
        FilterDefinition f = new FilterDefinition();
        f.setName("status");
        f.setType("String");
        f.setOperator("IN");

        ForgeDefinition def = buildDefinition(List.of(f));
        File outDir = tempDir.toFile();
        generator.generate(def, def.getEntities().get(0), outDir);

        File dtoFile = new File(outDir, "com/example/dto/ProductFilterDTO.java");
        assertTrue(dtoFile.exists());

        String dtoContent = Files.readString(dtoFile.toPath());
        assertTrue(dtoContent.contains("List<String>"), "IN deve usar List<T>");

        File specFile = new File(outDir, "com/example/specification/ProductSpecification.java");
        String specContent = Files.readString(specFile.toPath());
        assertTrue(specContent.contains(".in(filter.getStatus())"));
    }

    @Test
    void shouldGenerateBetweenOperator() throws Exception {
        FilterDefinition f = new FilterDefinition();
        f.setName("price");
        f.setType("BigDecimal");
        f.setOperator("BETWEEN");
        f.setTargetField("price");

        ForgeDefinition def = buildDefinition(List.of(f));
        File outDir = tempDir.toFile();
        generator.generate(def, def.getEntities().get(0), outDir);

        File specFile = new File(outDir, "com/example/specification/ProductSpecification.java");
        String content = Files.readString(specFile.toPath());
        assertTrue(content.contains("cb.between"));
    }

    @Test
    void shouldGenerateIsNullOperator() throws Exception {
        FilterDefinition f = new FilterDefinition();
        f.setName("deletedAt");
        f.setType("LocalDateTime");
        f.setOperator("IS_NULL");

        ForgeDefinition def = buildDefinition(List.of(f));
        File outDir = tempDir.toFile();
        generator.generate(def, def.getEntities().get(0), outDir);

        File dtoFile = new File(outDir, "com/example/dto/ProductFilterDTO.java");
        String dtoContent = Files.readString(dtoFile.toPath());
        assertTrue(dtoContent.contains("Boolean"), "IS_NULL deve usar Boolean no DTO");

        File specFile = new File(outDir, "com/example/specification/ProductSpecification.java");
        String specContent = Files.readString(specFile.toPath());
        assertTrue(specContent.contains("cb.isNull"));
    }

    @Test
    void shouldResolveOperatorByConvention() throws Exception {
        List<FilterDefinition> filters = new ArrayList<>();

        FilterDefinition nameFilter = new FilterDefinition();
        nameFilter.setName("name");
        nameFilter.setType("String");
        // Sem operator definido → deve usar CONTAINS por ser String
        filters.add(nameFilter);

        FilterDefinition minFilter = new FilterDefinition();
        minFilter.setName("priceMin");
        minFilter.setType("BigDecimal");
        // Sem operator → sufixo Min → GREATER_THAN_OR_EQUAL
        filters.add(minFilter);

        ForgeDefinition def = buildDefinition(filters);
        File outDir = tempDir.toFile();
        generator.generate(def, def.getEntities().get(0), outDir);

        File specFile = new File(outDir, "com/example/specification/ProductSpecification.java");
        String content = Files.readString(specFile.toPath());
        assertTrue(content.contains("cb.like"), "String sem operator → CONTAINS (LIKE)");
        assertTrue(content.contains("greaterThanOrEqualTo"), "Sufixo Min → >=");
    }

    @Test
    void shouldUseTargetFieldWhenDefined() throws Exception {
        FilterDefinition f = new FilterDefinition();
        f.setName("minPrice");
        f.setType("BigDecimal");
        f.setOperator("GREATER_THAN_OR_EQUAL");
        f.setTargetField("price");

        ForgeDefinition def = buildDefinition(List.of(f));
        File outDir = tempDir.toFile();
        generator.generate(def, def.getEntities().get(0), outDir);

        File specFile = new File(outDir, "com/example/specification/ProductSpecification.java");
        String content = Files.readString(specFile.toPath());
        assertTrue(content.contains("root.get(\"price\")"), "Deve usar targetField 'price'");
    }

    @Test
    void shouldGenerateStartsWithOperator() throws Exception {
        FilterDefinition f = new FilterDefinition();
        f.setName("sku");
        f.setType("String");
        f.setOperator("STARTS_WITH");

        ForgeDefinition def = buildDefinition(List.of(f));
        File outDir = tempDir.toFile();
        generator.generate(def, def.getEntities().get(0), outDir);

        File specFile = new File(outDir, "com/example/specification/ProductSpecification.java");
        String content = Files.readString(specFile.toPath());
        assertTrue(content.contains("toLowerCase() + \"%\""));
        assertFalse(content.contains("\"%\" + filter.getSku"));
    }
}
