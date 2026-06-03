package io.springforge.generator;

import io.springforge.model.EntityDefinition;
import io.springforge.model.FieldDefinition;
import io.springforge.model.ForgeDefinition;
import io.springforge.util.NamingUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;

public class MigrationGenerator extends AbstractGenerator {

    private static final AtomicInteger SEQ = new AtomicInteger(1);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    // Não precisa mais do projectBasedir — tudo fica no target/
    public MigrationGenerator(Log log) {
        super(log);
    }

    /**
     * Diretório raiz dos resources gerados.
     * Ex: target/generated-resources/spring-forge
     * Exposto estaticamente para o Mojo registrar como resource root.
     */
    public static File generatedResourcesDir(File buildOutputDir) {
        // buildOutputDir = target/generated-sources/spring-forge
        // sobe 2 níveis → target/
        File targetDir = buildOutputDir.getParentFile().getParentFile();
        return new File(targetDir, "generated-resources/spring-forge");
    }

    @Override
    public void generate(ForgeDefinition def, EntityDefinition entity, File outDir) throws MojoExecutionException {
        if (!entity.shouldGenerate("migration")) return;
        if (!def.getProject().isGenerateMigrations()) return;

        String table = entity.getTableName() != null
            ? entity.getTableName()
            : NamingUtils.toSnakeCase(entity.getName());

        String ts       = LocalDateTime.now().format(FMT) + String.format("%02d", SEQ.getAndIncrement());
        String fileName = "V" + ts + "__create_" + table + "_table.sql";

        // target/generated-resources/spring-forge/db/migration/
        File migrationsDir = new File(generatedResourcesDir(outDir), "db/migration");
        File target        = new File(migrationsDir, fileName);

        try {
            Files.createDirectories(migrationsDir.toPath());
            try (Writer w = new OutputStreamWriter(new FileOutputStream(target), StandardCharsets.UTF_8)) {
                w.write(buildSql(entity, table));
            }
            log.info("  [GERADO] " + target.getPath());
        } catch (Exception e) {
            throw new MojoExecutionException("Erro ao gerar migration: " + e.getMessage(), e);
        }
    }

    private String buildSql(EntityDefinition entity, String table) {
        StringBuilder sb = new StringBuilder();
        sb.append("-- Migration gerada pelo Spring Forge Maven Plugin\n");
        sb.append("-- Entidade: ").append(entity.getName()).append("\n\n");

        sb.append("CREATE TABLE IF NOT EXISTS ").append(table).append(" (\n");
        sb.append("    id BIGSERIAL,\n");

        for (FieldDefinition f : entity.getFields()) {
            String col = f.getColumnName() != null ? f.getColumnName() : NamingUtils.toSnakeCase(f.getName());
            sb.append("    ").append(col)
              .append(" ").append(toSqlType(f));
            if (f.isRequired()) sb.append(" NOT NULL");
            if (f.isUnique())   sb.append(" UNIQUE");
            if (f.getDefaultValue() != null) sb.append(" DEFAULT ").append(f.getDefaultValue());
            sb.append(",\n");
        }

        if (entity.isAuditable()) {
            sb.append("    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,\n");
            sb.append("    updated_at TIMESTAMP,\n");
        }
        if (entity.isSoftDelete()) {
            sb.append("    deleted_at TIMESTAMP,\n");
        }

        entity.getRelations().stream()
            .filter(r -> "ManyToOne".equals(r.getType()) || "OneToOne".equals(r.getType()))
            .forEach(r -> sb.append("    ")
                .append(NamingUtils.toSnakeCase(r.getFieldName())).append("_id BIGINT,\n"));

        sb.append("    CONSTRAINT pk_").append(table).append(" PRIMARY KEY (id)\n");
        sb.append(");\n\n");

        entity.getRelations().stream()
            .filter(r -> "ManyToOne".equals(r.getType()) || "OneToOne".equals(r.getType()))
            .forEach(r -> {
                String fkCol    = NamingUtils.toSnakeCase(r.getFieldName()) + "_id";
                String refTable = NamingUtils.toSnakeCase(NamingUtils.toPlural(r.getTargetEntity()));
                sb.append("ALTER TABLE ").append(table)
                  .append(" ADD CONSTRAINT fk_").append(table).append("_").append(NamingUtils.toSnakeCase(r.getFieldName()))
                  .append("\n    FOREIGN KEY (").append(fkCol).append(")")
                  .append(" REFERENCES ").append(refTable).append("(id);\n\n");
            });

        entity.getFields().stream().filter(FieldDefinition::isUnique).forEach(f -> {
            String col = f.getColumnName() != null ? f.getColumnName() : NamingUtils.toSnakeCase(f.getName());
            sb.append("CREATE UNIQUE INDEX IF NOT EXISTS uq_").append(table).append("_").append(col)
              .append("\n    ON ").append(table).append(" (").append(col).append(");\n\n");
        });

        sb.append("COMMENT ON TABLE ").append(table)
          .append(" IS 'Tabela de ").append(entity.getName()).append(" — Spring Forge';\n");

        return sb.toString();
    }

    private String toSqlType(FieldDefinition f) {
        return switch (f.getType().toLowerCase()) {
            case "string"         -> "VARCHAR(" + (f.getMaxLength() != null ? f.getMaxLength() : 255) + ")";
            case "integer", "int" -> "INTEGER";
            case "long"           -> "BIGINT";
            case "double"         -> "DOUBLE PRECISION";
            case "float"          -> "REAL";
            case "bigdecimal"     -> "NUMERIC(19,4)";
            case "boolean"        -> "BOOLEAN";
            case "localdate"      -> "DATE";
            case "localdatetime"  -> "TIMESTAMP";
            case "uuid"           -> "UUID";
            case "enum"           -> "VARCHAR(50)";
            default               -> "TEXT";
        };
    }
}
