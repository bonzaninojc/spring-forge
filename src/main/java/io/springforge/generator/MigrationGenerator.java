package io.springforge.generator;

import io.springforge.model.EntityDefinition;
import io.springforge.model.FieldDefinition;
import io.springforge.model.ForgeDefinition;
import io.springforge.model.RelationDefinition;
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
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

public class MigrationGenerator extends AbstractGenerator {

    private static final AtomicInteger SEQ = new AtomicInteger(1);
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public MigrationGenerator(Log log) {
        super(log);
    }

    public static File generatedResourcesDir(File buildOutputDir) {
        File targetDir = buildOutputDir.getParentFile().getParentFile();
        return new File(targetDir, "generated-resources/spring-forge");
    }

    @Override
    public void generate(ForgeDefinition def, EntityDefinition entity, File outDir) throws MojoExecutionException {
        if (!entity.shouldGenerate("migration")) return;
        if (!def.getProject().isGenerateMigrations()) return;

        SqlDialect dialect = SqlDialect.forDatabase(def.getProject().getDatabase());
        if (dialect.isNoop()) {
            log.warn("  [SKIP] Migrations SQL não são geradas para database='" + def.getProject().getDatabase() + "'.");
            return;
        }

        String table = tableName(entity);
        String ts = LocalDateTime.now().format(FMT) + String.format("%02d", SEQ.getAndIncrement());
        String fileName = "V" + ts + "__create_" + table + "_table.sql";

        File migrationsDir = resolveMigrationsDir(def, outDir);
        File target = new File(migrationsDir, fileName);

        try {
            Files.createDirectories(migrationsDir.toPath());
            try (Writer w = new OutputStreamWriter(new FileOutputStream(target), StandardCharsets.UTF_8)) {
                w.write(buildSql(def, entity, table, dialect));
            }
            log.info("  [GERADO] " + target.getPath());
        } catch (Exception e) {
            throw new MojoExecutionException("Erro ao gerar migration: " + e.getMessage(), e);
        }
    }

    private File resolveMigrationsDir(ForgeDefinition def, File outDir) {
        String configured = def.getProject().getMigrationsDir();
        if (configured == null || configured.isBlank()) {
            return new File(generatedResourcesDir(outDir), "db/migration");
        }
        return new File(configured);
    }

    private String buildSql(ForgeDefinition def, EntityDefinition entity, String table, SqlDialect dialect) {
        StringBuilder sb = new StringBuilder();
        sb.append("-- Migration gerada pelo Spring Forge Maven Plugin\n");
        sb.append("-- Entidade: ").append(entity.getName()).append("\n");
        sb.append("-- Dialeto: ").append(dialect.name()).append("\n\n");

        sb.append("CREATE TABLE IF NOT EXISTS ").append(table).append(" (\n");
        sb.append("    id ").append(dialect.idColumn()).append(",\n");

        for (FieldDefinition f : entity.getFields()) {
            String col = f.getColumnName() != null ? f.getColumnName() : NamingUtils.toSnakeCase(f.getName());
            sb.append("    ").append(col).append(" ").append(dialect.toSqlType(f));
            if (f.isRequired()) sb.append(" NOT NULL");
            if (f.isUnique()) sb.append(" UNIQUE");
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
            .forEach(r -> appendForeignKey(sb, def, entity, table, r));

        entity.getFields().stream().filter(FieldDefinition::isUnique).forEach(f -> {
            String col = f.getColumnName() != null ? f.getColumnName() : NamingUtils.toSnakeCase(f.getName());
            sb.append(dialect.uniqueIndex("uq_" + table + "_" + col, table, col)).append("\n\n");
        });

        if (dialect.supportsComments()) {
            sb.append("COMMENT ON TABLE ").append(table)
              .append(" IS 'Tabela de ").append(entity.getName()).append(" - Spring Forge';\n");
        }

        return sb.toString();
    }

    private void appendForeignKey(StringBuilder sb, ForgeDefinition def, EntityDefinition entity,
                                  String table, RelationDefinition r) {
        String fkCol = NamingUtils.toSnakeCase(r.getFieldName()) + "_id";
        String refTable = def.getEntities().stream()
            .filter(e -> r.getTargetEntity().equals(e.getName()))
            .findFirst()
            .map(this::tableName)
            .orElseGet(() -> NamingUtils.toSnakeCase(NamingUtils.toPlural(r.getTargetEntity())));

        sb.append("ALTER TABLE ").append(table)
          .append(" ADD CONSTRAINT fk_").append(table).append("_").append(NamingUtils.toSnakeCase(r.getFieldName()))
          .append("\n    FOREIGN KEY (").append(fkCol).append(")")
          .append(" REFERENCES ").append(refTable).append("(id);\n\n");
    }

    private String tableName(EntityDefinition entity) {
        return entity.getTableName() != null ? entity.getTableName() : NamingUtils.toSnakeCase(entity.getName());
    }

    private interface SqlDialect {
        String name();
        String idColumn();
        String toSqlType(FieldDefinition f);
        String uniqueIndex(String indexName, String table, String column);
        boolean supportsComments();
        default boolean isNoop() { return false; }

        static SqlDialect forDatabase(String database) {
            String db = database == null ? "postgres" : database.toLowerCase(Locale.ROOT);
            return switch (db) {
                case "mysql" -> new MySqlDialect();
                case "h2" -> new H2Dialect();
                case "mongodb" -> new NoopDialect();
                default -> new PostgresDialect();
            };
        }
    }

    private static class PostgresDialect implements SqlDialect {
        public String name() { return "postgres"; }
        public String idColumn() { return "BIGSERIAL"; }
        public boolean supportsComments() { return true; }
        public String uniqueIndex(String indexName, String table, String column) {
            return "CREATE UNIQUE INDEX IF NOT EXISTS " + indexName + "\n    ON " + table + " (" + column + ");";
        }
        public String toSqlType(FieldDefinition f) {
            return switch (f.getType().toLowerCase(Locale.ROOT)) {
                case "string" -> "VARCHAR(" + (f.getMaxLength() != null ? f.getMaxLength() : 255) + ")";
                case "integer", "int" -> "INTEGER";
                case "long" -> "BIGINT";
                case "double" -> "DOUBLE PRECISION";
                case "float" -> "REAL";
                case "bigdecimal" -> "NUMERIC(19,4)";
                case "boolean" -> "BOOLEAN";
                case "localdate" -> "DATE";
                case "localdatetime" -> "TIMESTAMP";
                case "uuid" -> "UUID";
                case "enum" -> "VARCHAR(50)";
                default -> "TEXT";
            };
        }
    }

    private static class MySqlDialect extends PostgresDialect {
        public String name() { return "mysql"; }
        public String idColumn() { return "BIGINT AUTO_INCREMENT"; }
        public boolean supportsComments() { return false; }
        public String uniqueIndex(String indexName, String table, String column) {
            return "CREATE UNIQUE INDEX " + indexName + " ON " + table + " (" + column + ");";
        }
        public String toSqlType(FieldDefinition f) {
            return switch (f.getType().toLowerCase(Locale.ROOT)) {
                case "double" -> "DOUBLE";
                case "float" -> "FLOAT";
                case "uuid" -> "CHAR(36)";
                default -> super.toSqlType(f);
            };
        }
    }

    private static class H2Dialect extends PostgresDialect {
        public String name() { return "h2"; }
        public String idColumn() { return "BIGINT GENERATED BY DEFAULT AS IDENTITY"; }
        public boolean supportsComments() { return false; }
        public String toSqlType(FieldDefinition f) {
            if ("uuid".equalsIgnoreCase(f.getType())) return "UUID";
            if ("double".equalsIgnoreCase(f.getType())) return "DOUBLE";
            return super.toSqlType(f);
        }
    }

    private static class NoopDialect implements SqlDialect {
        public String name() { return "noop"; }
        public String idColumn() { return ""; }
        public String toSqlType(FieldDefinition f) { return ""; }
        public String uniqueIndex(String indexName, String table, String column) { return ""; }
        public boolean supportsComments() { return false; }
        public boolean isNoop() { return true; }
    }
}
