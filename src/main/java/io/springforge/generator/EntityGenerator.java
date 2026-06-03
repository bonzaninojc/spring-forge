package io.springforge.generator;

import io.springforge.model.EntityDefinition;
import io.springforge.model.FieldDefinition;
import io.springforge.model.ForgeDefinition;
import io.springforge.model.RelationDefinition;
import io.springforge.util.NamingUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;

public class EntityGenerator extends AbstractGenerator {

    public EntityGenerator(Log log) { super(log); }

    @Override
    public void generate(ForgeDefinition def, EntityDefinition entity, File outDir) throws MojoExecutionException {
        if (!entity.shouldGenerate("entity")) return;

        String pkg = entityPkg(def);
        writeFile(buildEntity(def, entity, pkg), javaFile(outDir, pkg, entity.getName()), pkg);

        // Enums embutidos
        for (FieldDefinition f : entity.getFields()) {
            if ("Enum".equalsIgnoreCase(f.getType()) && f.getEnumValues() != null && !f.getEnumValues().isEmpty()) {
                String eName = enumName(entity, f);
                writeFile(buildEnum(f, pkg, eName), javaFile(outDir, pkg, eName), pkg);
            }
        }
    }

    private CodeWriter buildEntity(ForgeDefinition def, EntityDefinition entity, String pkg) {
        CodeWriter w = new CodeWriter();

        // Imports
        w.imp("jakarta.persistence.*")
         .imp("java.io.Serializable");

        boolean hasLDT = entity.isAuditable() || entity.isSoftDelete()
            || entity.getFields().stream().anyMatch(f -> "LocalDateTime".equalsIgnoreCase(f.getType()));
        if (hasLDT) w.imp("java.time.LocalDateTime");

        boolean hasLD = entity.getFields().stream().anyMatch(f -> "LocalDate".equalsIgnoreCase(f.getType()));
        if (hasLD) w.imp("java.time.LocalDate");

        boolean hasBD = entity.getFields().stream().anyMatch(f -> "BigDecimal".equalsIgnoreCase(f.getType()));
        if (hasBD) w.imp("java.math.BigDecimal");

        boolean hasUUID = entity.getFields().stream().anyMatch(f -> "UUID".equalsIgnoreCase(f.getType()));
        if (hasUUID) w.imp("java.util.UUID");

        boolean hasList = entity.getRelations().stream()
            .anyMatch(r -> "OneToMany".equals(r.getType()) || "ManyToMany".equals(r.getType()));
        if (hasList) { w.imp("java.util.List"); w.imp("java.util.Set"); }

        if (entity.isAuditable()) {
            w.imp("jakarta.persistence.PrePersist")
             .imp("jakarta.persistence.PreUpdate");
        }

        // Anotações da classe
        String table = entity.getTableName() != null ? entity.getTableName() : NamingUtils.toSnakeCase(entity.getName());
        w.line("@Entity")
         .line("@Table(name = \"" + table + "\")");

        if (entity.isAuditable()) w.line("");

        w.line("public class " + entity.getName() + " implements Serializable {")
         .blank();
        w.indent();

        // ID
        w.line("@Id")
         .line("@GeneratedValue(strategy = GenerationType.IDENTITY)")
         .line("private Long id;")
         .blank();

        // Campos
        for (FieldDefinition f : entity.getFields()) {
            writeEntityField(w, f, pkg, entity);
        }

        // Relacionamentos
        for (RelationDefinition r : entity.getRelations()) {
            writeRelation(w, r);
        }

        // Auditoria
        if (entity.isAuditable()) {
            w.line("@Column(name = \"created_at\", nullable = false, updatable = false)")
             .line("private LocalDateTime createdAt;")
             .blank()
             .line("@Column(name = \"updated_at\")")
             .line("private LocalDateTime updatedAt;")
             .blank()
             .line("@PrePersist")
             .line("protected void onCreate() {")
             .line("    this.createdAt = LocalDateTime.now();")
             .line("    this.updatedAt = LocalDateTime.now();")
             .line("}")
             .blank()
             .line("@PreUpdate")
             .line("protected void onUpdate() {")
             .line("    this.updatedAt = LocalDateTime.now();")
             .line("}")
             .blank();
        }

        // Soft delete
        if (entity.isSoftDelete()) {
            w.line("@Column(name = \"deleted_at\")")
             .line("private LocalDateTime deletedAt;")
             .blank()
             .line("public boolean isDeleted() { return this.deletedAt != null; }")
             .blank();
        }

        // Getters/Setters
        w.line("// ── Getters & Setters ──────────────────────────────────────────────────────────")
         .blank();

        w.line("public Long getId() { return id; }")
         .line("public void setId(Long id) { this.id = id; }")
         .blank();

        for (FieldDefinition f : entity.getFields()) {
            String type = "Enum".equalsIgnoreCase(f.getType()) ? enumName(entity, f) : NamingUtils.toJavaType(f.getType());
            writeGetterSetter(w, f, type);
        }

        for (RelationDefinition r : entity.getRelations()) {
            String type = ("OneToMany".equals(r.getType()) || "ManyToMany".equals(r.getType()))
                ? "List<" + r.getTargetEntity() + ">"
                : r.getTargetEntity();
            String cap = NamingUtils.toPascalCase(r.getFieldName());
            w.line("public " + type + " get" + cap + "() { return " + r.getFieldName() + "; }")
             .line("public void set" + cap + "(" + type + " " + r.getFieldName() + ") { this." + r.getFieldName() + " = " + r.getFieldName() + "; }")
             .blank();
        }

        if (entity.isAuditable()) {
            w.line("public LocalDateTime getCreatedAt() { return createdAt; }")
             .line("public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }")
             .blank()
             .line("public LocalDateTime getUpdatedAt() { return updatedAt; }")
             .line("public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }")
             .blank();
        }

        if (entity.isSoftDelete()) {
            w.line("public LocalDateTime getDeletedAt() { return deletedAt; }")
             .line("public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }")
             .blank();
        }

        w.unindent().line("}");
        return w;
    }

    private void writeEntityField(CodeWriter w, FieldDefinition f, String pkg, EntityDefinition entity) {
        // Validações
        if (f.isRequired()) {
            if ("String".equalsIgnoreCase(f.getType())) {
                w.line("@jakarta.validation.constraints.NotBlank");
            } else {
                w.line("@jakarta.validation.constraints.NotNull");
            }
        }
        if (f.getValidations() != null) {
            f.getValidations().forEach(w::line);
        }

        // @Column
        StringBuilder col = new StringBuilder("@Column(name = \"");
        String colName = f.getColumnName() != null ? f.getColumnName() : NamingUtils.toSnakeCase(f.getName());
        col.append(colName).append("\"");
        if (f.isUnique()) col.append(", unique = true");
        if (f.isRequired()) col.append(", nullable = false");
        if (f.getMaxLength() != null && "String".equalsIgnoreCase(f.getType())) {
            col.append(", length = ").append(f.getMaxLength());
        }
        col.append(")");
        w.line(col.toString());

        if ("Enum".equalsIgnoreCase(f.getType())) {
            String eName = enumName(entity, f);
            w.line("@Enumerated(EnumType.STRING)");
            w.line("private " + eName + " " + f.getName() + ";");
        } else {
            w.line("private " + NamingUtils.toJavaType(f.getType()) + " " + f.getName() + ";");
        }
        w.blank();
    }

    private void writeRelation(CodeWriter w, RelationDefinition r) {
        String cascadeStr = buildCascade(r.getCascade());
        switch (r.getType()) {
            case "ManyToOne" -> {
                w.line("@ManyToOne(fetch = FetchType." + r.getFetch() + ", cascade = {" + cascadeStr + "})")
                 .line("@JoinColumn(name = \"" + NamingUtils.toSnakeCase(r.getFieldName()) + "_id\")")
                 .line("private " + r.getTargetEntity() + " " + r.getFieldName() + ";")
                 .blank();
            }
            case "OneToMany" -> {
                String mb = r.getMappedBy() != null ? "mappedBy = \"" + r.getMappedBy() + "\", " : "";
                w.line("@OneToMany(" + mb + "fetch = FetchType." + r.getFetch() + ", cascade = {" + cascadeStr + "})")
                 .line("private List<" + r.getTargetEntity() + "> " + r.getFieldName() + ";")
                 .blank();
            }
            case "OneToOne" -> {
                w.line("@OneToOne(fetch = FetchType." + r.getFetch() + ", cascade = {" + cascadeStr + "})")
                 .line("@JoinColumn(name = \"" + NamingUtils.toSnakeCase(r.getFieldName()) + "_id\", unique = true)")
                 .line("private " + r.getTargetEntity() + " " + r.getFieldName() + ";")
                 .blank();
            }
            case "ManyToMany" -> {
                w.line("@ManyToMany(fetch = FetchType." + r.getFetch() + ", cascade = {" + cascadeStr + "})")
                 .line("private Set<" + r.getTargetEntity() + "> " + r.getFieldName() + ";")
                 .blank();
            }
        }
    }

    private String buildCascade(String cascade) {
        if (cascade == null || cascade.isBlank()) return "CascadeType.MERGE, CascadeType.PERSIST";
        StringBuilder sb = new StringBuilder();
        for (String c : cascade.split(",")) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append("CascadeType.").append(c.trim());
        }
        return sb.toString();
    }

    private CodeWriter buildEnum(FieldDefinition f, String pkg, String enumName) {
        CodeWriter w = new CodeWriter();
        w.javadoc("Enum " + enumName + " gerado pelo Spring Forge.");
        w.line("public enum " + enumName + " {").blank();
        w.indent();
        for (int i = 0; i < f.getEnumValues().size(); i++) {
            String val = f.getEnumValues().get(i);
            boolean last = i == f.getEnumValues().size() - 1;
            w.line(val + (last ? ";" : ","));
        }
        w.unindent().blank().line("}");
        return w;
    }
}
