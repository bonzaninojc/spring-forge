package io.springforge.mojo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.springforge.model.*;
import io.springforge.util.NamingUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

import java.io.File;
import java.sql.*;
import java.util.*;

/**
 * Goal "reverse" — gera forge.json a partir de um banco de dados existente.
 *
 * Uso:
 *   mvn spring-forge:reverse -Dforge.jdbcUrl=jdbc:postgresql://localhost:5432/mydb -Dforge.jdbcUser=user -Dforge.jdbcPassword=pass
 *   mvn spring-forge:reverse -Dforge.jdbcUrl=jdbc:mysql://localhost:3306/mydb -Dforge.jdbcUser=user -Dforge.jdbcPassword=pass
 *
 * Gera: forge.json na raiz do projeto (ou no caminho definido por forge.output).
 */
@Mojo(
    name = "reverse",
    defaultPhase = LifecyclePhase.NONE,
    requiresProject = false,
    threadSafe = true
)
public class ForgeReverseMojo extends AbstractMojo {

    @Parameter(property = "forge.jdbcUrl", required = true)
    private String jdbcUrl;

    @Parameter(property = "forge.jdbcUser", required = true)
    private String jdbcUser;

    @Parameter(property = "forge.jdbcPassword", defaultValue = "")
    private String jdbcPassword;

    @Parameter(property = "forge.basePackage", defaultValue = "com.myapp")
    private String basePackage;

    @Parameter(property = "forge.projectName", defaultValue = "MyApp")
    private String projectName;

    /** Caminho de saída do forge.json gerado */
    @Parameter(property = "forge.output", defaultValue = "forge.json")
    private File outputFile;

    /** Schema do banco a analisar. Se vazio, usa o default do driver. */
    @Parameter(property = "forge.schema", defaultValue = "")
    private String schema;

    /** Tabelas a incluir (separadas por vírgula). Se vazio, inclui todas. */
    @Parameter(property = "forge.tables", defaultValue = "")
    private String tablesFilter;

    /** Tabelas a excluir (separadas por vírgula). Ex: flyway_schema_history,databasechangelog */
    @Parameter(property = "forge.excludeTables", defaultValue = "flyway_schema_history,databasechangelog,databasechangeloglock")
    private String excludeTables;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("╔══════════════════════════════════════╗");
        getLog().info("║   Spring Forge — Reverse Engineer    ║");
        getLog().info("╚══════════════════════════════════════╝");
        getLog().info("  JDBC URL   : " + jdbcUrl);
        getLog().info("  Schema     : " + (schema.isBlank() ? "(default)" : schema));
        getLog().info("  Output     : " + outputFile.getPath());

        try (Connection conn = DriverManager.getConnection(jdbcUrl, jdbcUser, jdbcPassword)) {
            ForgeDefinition definition = reverseEngineer(conn);

            ObjectMapper mapper = new ObjectMapper();
            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.writeValue(outputFile, definition);

            getLog().info("✔ forge.json gerado com " + definition.getEntities().size() + " entidade(s).");
        } catch (SQLException e) {
            throw new MojoExecutionException("Erro ao conectar no banco: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new MojoExecutionException("Erro ao gerar forge.json: " + e.getMessage(), e);
        }
    }

    private ForgeDefinition reverseEngineer(Connection conn) throws SQLException {
        ForgeDefinition def = new ForgeDefinition();

        ProjectConfig project = new ProjectConfig();
        project.setBasePackage(basePackage);
        project.setName(projectName);
        project.setDatabase(detectDatabase(jdbcUrl));
        project.setGenerateMigrations(false);
        project.setGenerateMappers(true);
        def.setProject(project);

        DatabaseMetaData meta = conn.getMetaData();
        String catalog = conn.getCatalog();
        String schemaPattern = schema.isBlank() ? null : schema;

        Set<String> excludeSet = parseSet(excludeTables);
        Set<String> includeSet = parseSet(tablesFilter);

        List<EntityDefinition> entities = new ArrayList<>();
        Map<String, EntityDefinition> entityByTable = new LinkedHashMap<>();

        // 1. Listar tabelas
        try (ResultSet tables = meta.getTables(catalog, schemaPattern, "%", new String[]{"TABLE"})) {
            while (tables.next()) {
                String tableName = tables.getString("TABLE_NAME");

                if (excludeSet.contains(tableName.toLowerCase())) continue;
                if (!includeSet.isEmpty() && !includeSet.contains(tableName.toLowerCase())) continue;

                EntityDefinition entity = new EntityDefinition();
                entity.setName(NamingUtils.toPascalCase(tableName));
                entity.setTableName(tableName);
                entity.setAuditable(false);
                entity.setSoftDelete(false);
                entities.add(entity);
                entityByTable.put(tableName, entity);
            }
        }

        // 2. Para cada tabela, ler colunas
        for (Map.Entry<String, EntityDefinition> entry : entityByTable.entrySet()) {
            String tableName = entry.getKey();
            EntityDefinition entity = entry.getValue();

            List<String> pkColumns = getPrimaryKeyColumns(meta, catalog, schemaPattern, tableName);
            List<FieldDefinition> fields = new ArrayList<>();

            try (ResultSet columns = meta.getColumns(catalog, schemaPattern, tableName, "%")) {
                while (columns.next()) {
                    String colName = columns.getString("COLUMN_NAME");

                    // Pula PK se for "id" (gerada automaticamente)
                    if (pkColumns.contains(colName) && "id".equalsIgnoreCase(colName)) continue;

                    // Detecta auditoria
                    if ("created_at".equalsIgnoreCase(colName) || "createdat".equalsIgnoreCase(colName)) {
                        entity.setAuditable(true);
                        continue;
                    }
                    if ("updated_at".equalsIgnoreCase(colName) || "updatedat".equalsIgnoreCase(colName)) {
                        continue;
                    }
                    if ("deleted_at".equalsIgnoreCase(colName) || "deletedat".equalsIgnoreCase(colName)) {
                        entity.setSoftDelete(true);
                        continue;
                    }

                    FieldDefinition field = new FieldDefinition();
                    field.setName(NamingUtils.toCamelCase(colName));
                    field.setColumnName(colName);
                    field.setType(sqlTypeToForgeType(columns.getInt("DATA_TYPE"), columns.getInt("COLUMN_SIZE")));
                    field.setRequired("NO".equalsIgnoreCase(columns.getString("IS_NULLABLE")));

                    int size = columns.getInt("COLUMN_SIZE");
                    if ("String".equals(field.getType()) && size > 0 && size < 10000) {
                        field.setMaxLength(size);
                    }

                    fields.add(field);
                }
            }
            entity.setFields(fields);

            // 3. Ler foreign keys → relations
            List<RelationDefinition> relations = new ArrayList<>();
            try (ResultSet fks = meta.getImportedKeys(catalog, schemaPattern, tableName)) {
                while (fks.next()) {
                    String fkColumn = fks.getString("FKCOLUMN_NAME");
                    String pkTable = fks.getString("PKTABLE_NAME");

                    RelationDefinition rel = new RelationDefinition();
                    rel.setType("ManyToOne");
                    rel.setTargetEntity(NamingUtils.toPascalCase(pkTable));

                    // Remove sufixo _id do nome do campo
                    String fieldName = fkColumn.toLowerCase().endsWith("_id")
                        ? fkColumn.substring(0, fkColumn.length() - 3)
                        : fkColumn;
                    rel.setFieldName(NamingUtils.toCamelCase(fieldName));

                    relations.add(rel);

                    // Remove o campo FK da lista de fields (vai virar relation)
                    entity.getFields().removeIf(f ->
                        f.getName().equalsIgnoreCase(NamingUtils.toCamelCase(fkColumn)));
                }
            }
            entity.setRelations(relations);

            // 4. Detectar unique constraints
            try (ResultSet indexes = meta.getIndexInfo(catalog, schemaPattern, tableName, true, false)) {
                Set<String> uniqueColumns = new HashSet<>();
                while (indexes.next()) {
                    String colName = indexes.getString("COLUMN_NAME");
                    if (colName != null) uniqueColumns.add(colName.toLowerCase());
                }
                for (FieldDefinition f : entity.getFields()) {
                    if (uniqueColumns.contains(f.getColumnName() != null ? f.getColumnName().toLowerCase() : f.getName().toLowerCase())) {
                        f.setUnique(true);
                    }
                }
            }
        }

        def.setEntities(entities);
        return def;
    }

    private List<String> getPrimaryKeyColumns(DatabaseMetaData meta, String catalog, String schema, String table) throws SQLException {
        List<String> pks = new ArrayList<>();
        try (ResultSet rs = meta.getPrimaryKeys(catalog, schema, table)) {
            while (rs.next()) {
                pks.add(rs.getString("COLUMN_NAME"));
            }
        }
        return pks;
    }

    private String sqlTypeToForgeType(int sqlType, int columnSize) {
        return switch (sqlType) {
            case Types.VARCHAR, Types.CHAR, Types.LONGVARCHAR, Types.NVARCHAR, Types.NCHAR, Types.CLOB -> "String";
            case Types.INTEGER, Types.SMALLINT, Types.TINYINT -> "Integer";
            case Types.BIGINT -> "Long";
            case Types.FLOAT, Types.REAL -> "Float";
            case Types.DOUBLE -> "Double";
            case Types.DECIMAL, Types.NUMERIC -> "BigDecimal";
            case Types.BOOLEAN, Types.BIT -> "Boolean";
            case Types.DATE -> "LocalDate";
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> "LocalDateTime";
            case Types.OTHER -> {
                // UUID em PostgreSQL aparece como Types.OTHER
                if (columnSize == 36 || columnSize == 2147483647) yield "UUID";
                yield "String";
            }
            default -> "String";
        };
    }

    private String detectDatabase(String url) {
        if (url.contains("postgresql")) return "postgres";
        if (url.contains("mysql")) return "mysql";
        if (url.contains("mongodb")) return "mongodb";
        if (url.contains("h2")) return "h2";
        return "postgres";
    }

    private Set<String> parseSet(String csv) {
        Set<String> set = new HashSet<>();
        if (csv != null && !csv.isBlank()) {
            for (String s : csv.split(",")) {
                set.add(s.trim().toLowerCase());
            }
        }
        return set;
    }
}
