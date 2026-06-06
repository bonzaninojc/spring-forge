package io.springforge.generator;

import io.springforge.model.EntityDefinition;
import io.springforge.model.ExportImportDefinition;
import io.springforge.model.FieldDefinition;
import io.springforge.model.ForgeDefinition;
import io.springforge.util.NamingUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.util.List;

/**
 * Gera endpoints e service de export (CSV/Excel) e import para entidades
 * que declaram "exportImport" no forge.json.
 *
 * Gera:
 *   - ExportImportService (interface + impl) com métodos exportCsv, exportExcel, importCsv, importExcel
 *   - ExportImportController com endpoints GET /export e POST /import
 */
public class ExportImportGenerator extends AbstractGenerator {

    public ExportImportGenerator(Log log) { super(log); }

    @Override
    public void generate(ForgeDefinition def, EntityDefinition entity, File outDir) throws MojoExecutionException {
        if (!entity.hasExportImport()) return;

        String svcPkg = servicePkg(def);
        String implPkg = serviceImplPkg(def);
        String ctrlPkg = controllerPkg(def);
        String name = entity.getName();

        writeFile(buildServiceInterface(def, entity, svcPkg),
                  javaFile(outDir, svcPkg, name + "ExportImportService"), svcPkg);

        writeFile(buildServiceImpl(def, entity, implPkg),
                  javaFile(outDir, implPkg, name + "ExportImportServiceImpl"), implPkg);

        writeFile(buildController(def, entity, ctrlPkg),
                  javaFile(outDir, ctrlPkg, name + "ExportImportController"), ctrlPkg);
    }

    private List<FieldDefinition> resolveExportFields(EntityDefinition entity) {
        ExportImportDefinition config = entity.getExportImport();
        List<String> explicit = config.getExportFields();
        if (explicit != null && !explicit.isEmpty()) {
            return entity.getFields().stream()
                .filter(f -> explicit.contains(f.getName()))
                .toList();
        }
        return entity.getFields().stream().filter(FieldDefinition::isInResponse).toList();
    }

    private List<FieldDefinition> resolveImportFields(EntityDefinition entity) {
        ExportImportDefinition config = entity.getExportImport();
        List<String> explicit = config.getImportFields();
        if (explicit != null && !explicit.isEmpty()) {
            return entity.getFields().stream()
                .filter(f -> explicit.contains(f.getName()))
                .toList();
        }
        // Fallback: use exportFields if defined, otherwise all request fields
        List<String> exportExplicit = config.getExportFields();
        if (exportExplicit != null && !exportExplicit.isEmpty()) {
            return entity.getFields().stream()
                .filter(f -> exportExplicit.contains(f.getName()))
                .toList();
        }
        return entity.getFields().stream().filter(FieldDefinition::isInRequest).toList();
    }

    private CodeWriter buildServiceInterface(ForgeDefinition def, EntityDefinition entity, String pkg) {
        String name = entity.getName();
        ExportImportDefinition config = entity.getExportImport();
        CodeWriter w = new CodeWriter();

        w.imp("java.io.InputStream")
         .imp("java.io.OutputStream");

        w.javadoc("Serviço de export/import para " + name + ".\nGerado pelo Spring Forge.");
        w.line("public interface " + name + "ExportImportService {").blank();
        w.indent();

        if (config.supportsCsv()) {
            w.javadoc("Exporta todos os registros em CSV para o OutputStream.")
             .line("void exportCsv(OutputStream out);").blank();
            w.javadoc("Importa registros a partir de CSV.")
             .line("int importCsv(InputStream in);").blank();
        }
        if (config.supportsExcel()) {
            w.javadoc("Exporta todos os registros em Excel (XLSX) para o OutputStream.")
             .line("void exportExcel(OutputStream out);").blank();
            w.javadoc("Importa registros a partir de Excel (XLSX).")
             .line("int importExcel(InputStream in);").blank();
        }

        w.unindent().line("}");
        return w;
    }

    private CodeWriter buildServiceImpl(ForgeDefinition def, EntityDefinition entity, String implPkg) {
        String name = entity.getName();
        String entPkg = entityPkg(def);
        String repoPkg = repoPkg(def);
        ExportImportDefinition config = entity.getExportImport();
        List<FieldDefinition> exportFields = resolveExportFields(entity);
        List<FieldDefinition> importFields = resolveImportFields(entity);

        CodeWriter w = new CodeWriter();

        w.imp("org.springframework.stereotype.Service")
         .imp("org.springframework.transaction.annotation.Transactional")
         .imp(entPkg + "." + name)
         .imp(repoPkg + "." + name + "Repository")
         .imp("java.io.InputStream")
         .imp("java.io.OutputStream")
         .imp("java.io.PrintWriter")
         .imp("java.io.BufferedReader")
         .imp("java.io.InputStreamReader")
         .imp("java.nio.charset.StandardCharsets")
         .imp("java.util.List")
         .imp("java.util.ArrayList");

        String svcPkg = servicePkg(def);
        w.imp(svcPkg + "." + name + "ExportImportService");

        // Import enums used in import fields
        for (FieldDefinition f : importFields) {
            if ("Enum".equalsIgnoreCase(f.getType())) {
                w.imp(entPkg + "." + name + NamingUtils.toPascalCase(f.getName()));
            }
        }

        w.javadoc("Implementação de export/import para " + name + ".\nGerado pelo Spring Forge.");
        w.line("@Service")
         .line("public class " + name + "ExportImportServiceImpl implements " + name + "ExportImportService {").blank();
        w.indent();

        w.line("private final " + name + "Repository repository;").blank();
        w.line("public " + name + "ExportImportServiceImpl(" + name + "Repository repository) {")
         .indent().line("this.repository = repository;").unindent().line("}").blank();

        // CSV Export
        if (config.supportsCsv()) {
            w.line("@Override")
             .line("@Transactional(readOnly = true)")
             .line("public void exportCsv(OutputStream out) {")
             .indent()
             .line("List<" + name + "> items = repository.findAll();")
             .line("PrintWriter writer = new PrintWriter(out, false, StandardCharsets.UTF_8);").blank()
             .line("// Header");

            StringBuilder header = new StringBuilder("writer.println(\"");
            for (int i = 0; i < exportFields.size(); i++) {
                if (i > 0) header.append(",");
                header.append(exportFields.get(i).getName());
            }
            header.append("\");");
            w.line(header.toString()).blank();

            w.line("// Data")
             .line("for (" + name + " item : items) {")
             .indent();

            StringBuilder row = new StringBuilder("writer.println(");
            for (int i = 0; i < exportFields.size(); i++) {
                FieldDefinition f = exportFields.get(i);
                String getter = "item.get" + capitalize(f.getName()) + "()";
                if (i == 0) {
                    row.append("escapeCsv(").append(getter).append(")");
                } else {
                    row.append(" + \",\" + escapeCsv(").append(getter).append(")");
                }
            }
            row.append(");");
            w.line(row.toString());

            w.unindent().line("}")
             .line("writer.flush();")
             .unindent().line("}").blank();

            // CSV Import
            w.line("@Override")
             .line("@Transactional")
             .line("public int importCsv(InputStream in) {")
             .indent()
             .line("List<" + name + "> batch = new ArrayList<>();")
             .line("try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {")
             .indent()
             .line("String header = reader.readLine(); // skip header")
             .line("String line;")
             .line("while ((line = reader.readLine()) != null) {")
             .indent()
             .line("if (line.isBlank()) continue;")
             .line("String[] parts = parseCsvLine(line);")
             .line(name + " entity = new " + name + "();");

            for (int i = 0; i < importFields.size(); i++) {
                FieldDefinition f = importFields.get(i);
                String setter = "entity.set" + capitalize(f.getName());
                String conversion = convertFromString(entity, f, "parts[" + i + "]");
                w.line("if (parts.length > " + i + " && !parts[" + i + "].isBlank()) " + setter + "(" + conversion + ");");
            }

            w.line("batch.add(entity);")
             .unindent().line("}")
             .unindent().line("} catch (Exception e) {")
             .indent().line("throw new RuntimeException(\"Erro ao importar CSV: \" + e.getMessage(), e);")
             .unindent().line("}")
             .line("repository.saveAll(batch);")
             .line("return batch.size();")
             .unindent().line("}").blank();
        }

        // Excel Export
        if (config.supportsExcel()) {
            w.imp("org.apache.poi.ss.usermodel.*")
             .imp("org.apache.poi.xssf.usermodel.XSSFWorkbook");

            w.line("@Override")
             .line("@Transactional(readOnly = true)")
             .line("public void exportExcel(OutputStream out) {")
             .indent()
             .line("List<" + name + "> items = repository.findAll();")
             .line("try (Workbook workbook = new XSSFWorkbook()) {")
             .indent()
             .line("Sheet sheet = workbook.createSheet(\"" + name + "\");")
             .line("Row headerRow = sheet.createRow(0);");

            for (int i = 0; i < exportFields.size(); i++) {
                w.line("headerRow.createCell(" + i + ").setCellValue(\"" + exportFields.get(i).getName() + "\");");
            }

            w.blank()
             .line("int rowIdx = 1;")
             .line("for (" + name + " item : items) {")
             .indent()
             .line("Row row = sheet.createRow(rowIdx++);");

            for (int i = 0; i < exportFields.size(); i++) {
                FieldDefinition f = exportFields.get(i);
                String getter = "item.get" + capitalize(f.getName()) + "()";
                w.line("row.createCell(" + i + ").setCellValue(String.valueOf(" + getter + " != null ? " + getter + " : \"\"));");
            }

            w.unindent().line("}")
             .line("workbook.write(out);")
             .unindent().line("} catch (Exception e) {")
             .indent().line("throw new RuntimeException(\"Erro ao exportar Excel: \" + e.getMessage(), e);")
             .unindent().line("}")
             .unindent().line("}").blank();

            // Excel Import
            w.line("@Override")
             .line("@Transactional")
             .line("public int importExcel(InputStream in) {")
             .indent()
             .line("List<" + name + "> batch = new ArrayList<>();")
             .line("try (Workbook workbook = new XSSFWorkbook(in)) {")
             .indent()
             .line("Sheet sheet = workbook.getSheetAt(0);")
             .line("for (int i = 1; i <= sheet.getLastRowNum(); i++) {")
             .indent()
             .line("Row row = sheet.getRow(i);")
             .line("if (row == null) continue;")
             .line(name + " entity = new " + name + "();");

            for (int i = 0; i < importFields.size(); i++) {
                FieldDefinition f = importFields.get(i);
                String setter = "entity.set" + capitalize(f.getName());
                String cellValue = "getCellString(row, " + i + ")";
                String conversion = convertFromString(entity, f, cellValue);
                w.line("if (!getCellString(row, " + i + ").isBlank()) " + setter + "(" + conversion + ");");
            }

            w.line("batch.add(entity);")
             .unindent().line("}")
             .unindent().line("} catch (Exception e) {")
             .indent().line("throw new RuntimeException(\"Erro ao importar Excel: \" + e.getMessage(), e);")
             .unindent().line("}")
             .line("repository.saveAll(batch);")
             .line("return batch.size();")
             .unindent().line("}").blank();

            // Helper getCellString
            w.line("private String getCellString(Row row, int idx) {")
             .indent()
             .line("Cell cell = row.getCell(idx);")
             .line("if (cell == null) return \"\";")
             .line("cell.setCellType(CellType.STRING);")
             .line("return cell.getStringCellValue().trim();")
             .unindent().line("}").blank();
        }

        // Helpers
        w.line("private String escapeCsv(Object value) {")
         .indent()
         .line("if (value == null) return \"\";")
         .line("String s = String.valueOf(value);")
         .line("if (s.contains(\",\") || s.contains(\"\\\"\") || s.contains(\"\\n\")) {")
         .indent().line("return \"\\\"\" + s.replace(\"\\\"\", \"\\\"\\\"\") + \"\\\"\";")
         .unindent().line("}")
         .line("return s;")
         .unindent().line("}").blank();

        w.line("private String[] parseCsvLine(String line) {")
         .indent()
         .line("// Parsing simples — para CSV complexo, usar biblioteca")
         .line("return line.split(\",(?=(?:[^\\\"]*\\\"[^\\\"]*\\\")*[^\\\"]*$)\", -1);")
         .unindent().line("}").blank();

        w.unindent().line("}");
        return w;
    }

    private CodeWriter buildController(ForgeDefinition def, EntityDefinition entity, String ctrlPkg) {
        String name = entity.getName();
        String svcPkg = servicePkg(def);
        ExportImportDefinition config = entity.getExportImport();

        String apiPath = entity.getApiPath() != null ? entity.getApiPath()
            : "/api/v1/" + NamingUtils.toSnakeCase(NamingUtils.toPlural(name)).replace("_", "-");

        CodeWriter w = new CodeWriter();

        w.imp("org.springframework.http.HttpHeaders")
         .imp("org.springframework.http.MediaType")
         .imp("org.springframework.http.ResponseEntity")
         .imp("org.springframework.web.bind.annotation.*")
         .imp("org.springframework.web.multipart.MultipartFile")
         .imp("jakarta.servlet.http.HttpServletResponse")
         .imp(svcPkg + "." + name + "ExportImportService")
         .imp("java.util.Map");

        w.javadoc("Controller de export/import para " + name + ".\nGerado pelo Spring Forge.");
        w.line("@RestController")
         .line("@RequestMapping(\"" + apiPath + "\")")
         .line("public class " + name + "ExportImportController {").blank();
        w.indent();

        w.line("private final " + name + "ExportImportService service;").blank();
        w.line("public " + name + "ExportImportController(" + name + "ExportImportService service) {")
         .indent().line("this.service = service;").unindent().line("}").blank();

        // Export CSV
        if (config.supportsCsv()) {
            w.line("@GetMapping(\"/export/csv\")")
             .line("public void exportCsv(HttpServletResponse response) throws Exception {")
             .indent()
             .line("response.setContentType(\"text/csv; charset=UTF-8\");")
             .line("response.setHeader(HttpHeaders.CONTENT_DISPOSITION, \"attachment; filename=" + NamingUtils.toSnakeCase(name) + ".csv\");")
             .line("service.exportCsv(response.getOutputStream());")
             .unindent().line("}").blank();

            // Import CSV
            w.line("@PostMapping(value = \"/import/csv\", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)")
             .line("public ResponseEntity<Map<String, Object>> importCsv(@RequestParam(\"file\") MultipartFile file) throws Exception {")
             .indent()
             .line("int count = service.importCsv(file.getInputStream());")
             .line("return ResponseEntity.ok(Map.of(\"imported\", count, \"message\", count + \" registro(s) importado(s)\"));")
             .unindent().line("}").blank();
        }

        // Export Excel
        if (config.supportsExcel()) {
            w.line("@GetMapping(\"/export/excel\")")
             .line("public void exportExcel(HttpServletResponse response) throws Exception {")
             .indent()
             .line("response.setContentType(\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet\");")
             .line("response.setHeader(HttpHeaders.CONTENT_DISPOSITION, \"attachment; filename=" + NamingUtils.toSnakeCase(name) + ".xlsx\");")
             .line("service.exportExcel(response.getOutputStream());")
             .unindent().line("}").blank();

            // Import Excel
            w.line("@PostMapping(value = \"/import/excel\", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)")
             .line("public ResponseEntity<Map<String, Object>> importExcel(@RequestParam(\"file\") MultipartFile file) throws Exception {")
             .indent()
             .line("int count = service.importExcel(file.getInputStream());")
             .line("return ResponseEntity.ok(Map.of(\"imported\", count, \"message\", count + \" registro(s) importado(s)\"));")
             .unindent().line("}").blank();
        }

        w.unindent().line("}");
        return w;
    }

    private String convertFromString(EntityDefinition entity, FieldDefinition f, String varExpr) {
        if ("Enum".equalsIgnoreCase(f.getType())) {
            String enumCls = entity.getName() + NamingUtils.toPascalCase(f.getName());
            return enumCls + ".valueOf(" + varExpr + ".trim())";
        }
        return switch (f.getType().toLowerCase()) {
            case "integer", "int" -> "Integer.parseInt(" + varExpr + ".trim())";
            case "long" -> "Long.parseLong(" + varExpr + ".trim())";
            case "double" -> "Double.parseDouble(" + varExpr + ".trim())";
            case "float" -> "Float.parseFloat(" + varExpr + ".trim())";
            case "bigdecimal" -> "new java.math.BigDecimal(" + varExpr + ".trim())";
            case "boolean" -> "Boolean.parseBoolean(" + varExpr + ".trim())";
            case "localdate" -> "java.time.LocalDate.parse(" + varExpr + ".trim())";
            case "localdatetime" -> "java.time.LocalDateTime.parse(" + varExpr + ".trim())";
            case "uuid" -> "java.util.UUID.fromString(" + varExpr + ".trim())";
            default -> varExpr + ".trim()";
        };
    }
}
