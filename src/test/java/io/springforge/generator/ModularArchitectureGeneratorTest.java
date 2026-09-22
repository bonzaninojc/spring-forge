package io.springforge.generator;

import io.springforge.model.ArchitectureStyle;
import io.springforge.model.EntityDefinition;
import io.springforge.model.FieldDefinition;
import io.springforge.model.ForgeDefinition;
import io.springforge.model.ProjectConfig;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ModularArchitectureGeneratorTest {

    private final Log log = new SystemStreamLog();

    @TempDir
    Path tempDir;

    @Test
    void shouldGeneratePackageByFeatureStructure() throws Exception {
        ForgeDefinition def = new ForgeDefinition();
        ProjectConfig project = new ProjectConfig();
        project.setBasePackage("com.example");
        project.setName("TestApp");
        project.setArchitectureStyle(ArchitectureStyle.MODULAR);
        def.setProject(project);

        EntityDefinition product = new EntityDefinition();
        product.setName("Product");
        FieldDefinition title = new FieldDefinition();
        title.setName("title");
        title.setType("String");
        product.setFields(List.of(title));
        def.setEntities(List.of(product));

        File outDir = tempDir.toFile();
        new EntityGenerator(log).generate(def, product, outDir);
        new RepositoryGenerator(log).generate(def, product, outDir);
        new DtoGenerator(log).generate(def, product, outDir);
        new ServiceGenerator(log).generate(def, product, outDir);
        new ControllerGenerator(log).generate(def, product, outDir);

        assertTrue(new File(outDir, "com/example/modules/product/domain/Product.java").exists());
        assertTrue(new File(outDir, "com/example/modules/product/repository/ProductRepository.java").exists());
        assertTrue(new File(outDir, "com/example/modules/product/dto/ProductRequestDTO.java").exists());
        assertTrue(new File(outDir, "com/example/modules/product/service/ProductService.java").exists());
        assertTrue(new File(outDir, "com/example/modules/product/web/ProductController.java").exists());

        String controller = Files.readString(new File(outDir, "com/example/modules/product/web/ProductController.java").toPath());
        assertTrue(controller.contains("package com.example.modules.product.web;"));
        assertTrue(controller.contains("import com.example.modules.product.service.ProductService;"));
    }

    @Test
    void shouldApplySchemaToHexagonalJpaEntity() throws Exception {
        ForgeDefinition def = new ForgeDefinition();
        ProjectConfig project = new ProjectConfig();
        project.setBasePackage("com.example");
        project.setName("TestApp");
        project.setArchitectureStyle(ArchitectureStyle.HEXAGONAL);
        def.setProject(project);

        EntityDefinition product = new EntityDefinition();
        product.setName("Product");
        product.setTableName("products");
        product.setSchema("laboral");
        def.setEntities(List.of(product));

        new HexagonalPersistenceAdapterGenerator(log).generate(def, product, tempDir.toFile());

        Path entity = tempDir.resolve("com/example/adapter/out/persistence/ProductJpaEntity.java");
        assertTrue(Files.readString(entity).contains("@Table(name = \"products\", schema = \"laboral\")"));
    }
}
