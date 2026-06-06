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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExportImportGeneratorTest {

    private final Log log = new SystemStreamLog();
    private ExportImportGenerator generator;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        generator = new ExportImportGenerator(log);
    }

    private ForgeDefinition buildDef(List<String> formats) {
        ForgeDefinition def = new ForgeDefinition();
        ProjectConfig project = new ProjectConfig();
        project.setBasePackage("com.example");
        project.setName("TestApp");
        def.setProject(project);

        EntityDefinition entity = new EntityDefinition();
        entity.setName("Product");

        FieldDefinition f1 = new FieldDefinition();
        f1.setName("name");
        f1.setType("String");
        FieldDefinition f2 = new FieldDefinition();
        f2.setName("price");
        f2.setType("BigDecimal");
        entity.setFields(List.of(f1, f2));

        ExportImportDefinition config = new ExportImportDefinition();
        config.setEnabled(true);
        config.setFormats(formats);
        entity.setExportImport(config);

        def.setEntities(List.of(entity));
        return def;
    }

    @Test
    void shouldGenerateCsvExportImport() throws Exception {
        ForgeDefinition def = buildDef(List.of("csv"));
        File outDir = tempDir.toFile();

        generator.generate(def, def.getEntities().get(0), outDir);

        File svcFile = new File(outDir, "com/example/service/ProductExportImportService.java");
        assertTrue(svcFile.exists());
        String svcContent = Files.readString(svcFile.toPath());
        assertTrue(svcContent.contains("exportCsv"));
        assertTrue(svcContent.contains("importCsv"));
        assertFalse(svcContent.contains("exportExcel"));

        File implFile = new File(outDir, "com/example/service/impl/ProductExportImportServiceImpl.java");
        assertTrue(implFile.exists());
        String implContent = Files.readString(implFile.toPath());
        assertTrue(implContent.contains("PrintWriter"));
        assertTrue(implContent.contains("parseCsvLine"));

        File ctrlFile = new File(outDir, "com/example/controller/ProductExportImportController.java");
        assertTrue(ctrlFile.exists());
        String ctrlContent = Files.readString(ctrlFile.toPath());
        assertTrue(ctrlContent.contains("/export/csv"));
        assertTrue(ctrlContent.contains("/import/csv"));
        assertTrue(ctrlContent.contains("MultipartFile"));
    }

    @Test
    void shouldGenerateExcelExportImport() throws Exception {
        ForgeDefinition def = buildDef(List.of("excel"));
        File outDir = tempDir.toFile();

        generator.generate(def, def.getEntities().get(0), outDir);

        File implFile = new File(outDir, "com/example/service/impl/ProductExportImportServiceImpl.java");
        String content = Files.readString(implFile.toPath());
        assertTrue(content.contains("XSSFWorkbook"));
        assertTrue(content.contains("exportExcel"));
        assertTrue(content.contains("importExcel"));
    }

    @Test
    void shouldGenerateBothFormats() throws Exception {
        ForgeDefinition def = buildDef(List.of("csv", "excel"));
        File outDir = tempDir.toFile();

        generator.generate(def, def.getEntities().get(0), outDir);

        File ctrlFile = new File(outDir, "com/example/controller/ProductExportImportController.java");
        String content = Files.readString(ctrlFile.toPath());
        assertTrue(content.contains("/export/csv"));
        assertTrue(content.contains("/export/excel"));
        assertTrue(content.contains("/import/csv"));
        assertTrue(content.contains("/import/excel"));
    }

    @Test
    void shouldNotGenerateWhenDisabled() throws Exception {
        ForgeDefinition def = buildDef(List.of("csv"));
        def.getEntities().get(0).getExportImport().setEnabled(false);
        File outDir = tempDir.toFile();

        generator.generate(def, def.getEntities().get(0), outDir);

        File svcFile = new File(outDir, "com/example/service/ProductExportImportService.java");
        assertFalse(svcFile.exists());
    }
}
