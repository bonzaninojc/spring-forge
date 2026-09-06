package io.springforge.generator;

import io.springforge.model.EntityDefinition;
import io.springforge.model.FieldDefinition;
import io.springforge.model.ForgeDefinition;
import io.springforge.model.RelationDefinition;
import io.springforge.util.NamingUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;

/**
 * Gerador — Arquitetura Hexagonal: Domain Model.
 *
 * Gera um POJO puro de domínio (sem anotações JPA/Spring) em:
 *   {basePackage}.domain.model.{EntityName}
 *
 * O modelo de domínio representa o conceito de negócio isolado de
 * frameworks, seguindo o princípio de Ports &amp; Adapters.
 */
public class HexagonalDomainModelGenerator extends AbstractGenerator {

    public HexagonalDomainModelGenerator(Log log) { super(log); }

    @Override
    public void generate(ForgeDefinition def, EntityDefinition entity, File outDir)
            throws MojoExecutionException {

        if (!def.getProject().isHexagonal()) return;
        if (!entity.shouldGenerate("entity")) return;

        String pkg  = hexDomainModelPkg(def);
        String name = entity.getName();

        writeFile(buildDomainModel(def, entity, pkg),
                  javaFile(outDir, pkg, name), pkg);
    }

    private CodeWriter buildDomainModel(ForgeDefinition def, EntityDefinition entity, String pkg) {
        String name = entity.getName();
        CodeWriter w = new CodeWriter();

        // Imports de tipos
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
        if (hasList) w.imp("java.util.List");

        w.javadoc(
            "Modelo de domínio para " + name + ".\n" +
            "POJO puro — sem dependências de framework (JPA, Spring, etc.).\n" +
            "Gerado pelo Spring Forge — Arquitetura Hexagonal."
        );

        w.line("public class " + name + " {").blank();
        w.indent();

        // ID
        w.line("private Long id;").blank();

        // Campos de domínio
        for (FieldDefinition f : entity.getFields()) {
            String type = "Enum".equalsIgnoreCase(f.getType())
                ? enumName(entity, f)
                : NamingUtils.toJavaType(f.getType());
            w.line("private " + type + " " + f.getName() + ";");
        }
        w.blank();

        // Relações (referências ao domínio, sem JPA)
        for (RelationDefinition r : entity.getRelations()) {
            if ("ManyToOne".equals(r.getType())) {
                w.line("private Long " + r.getFieldName() + "Id; // referência de domínio");
            } else if ("OneToMany".equals(r.getType()) || "ManyToMany".equals(r.getType())) {
                w.imp("java.util.List");
                w.line("private List<Long> " + NamingUtils.toCamelCase(r.getTargetEntity()) + "Ids; // referências de domínio");
            }
        }

        // Auditoria
        if (entity.isAuditable()) {
            w.blank()
             .line("private LocalDateTime createdAt;")
             .line("private LocalDateTime updatedAt;");
        }
        if (entity.isSoftDelete()) {
            w.blank().line("private LocalDateTime deletedAt;");
        }

        w.blank();

        // Construtor padrão
        w.line("public " + name + "() {}")
         .blank();

        // Getters e Setters
        w.line("public Long getId() { return id; }")
         .line("public void setId(Long id) { this.id = id; }")
         .blank();

        for (FieldDefinition f : entity.getFields()) {
            String type = "Enum".equalsIgnoreCase(f.getType())
                ? enumName(entity, f)
                : NamingUtils.toJavaType(f.getType());
            String cap = NamingUtils.toPascalCase(f.getName());
            w.line("public " + type + " get" + cap + "() { return " + f.getName() + "; }")
             .line("public void set" + cap + "(" + type + " " + f.getName() + ") { this." + f.getName() + " = " + f.getName() + "; }")
             .blank();
        }

        for (RelationDefinition r : entity.getRelations()) {
            if ("ManyToOne".equals(r.getType())) {
                String cap = NamingUtils.toPascalCase(r.getFieldName());
                w.line("public Long get" + cap + "Id() { return " + r.getFieldName() + "Id; }")
                 .line("public void set" + cap + "Id(Long " + r.getFieldName() + "Id) { this." + r.getFieldName() + "Id = " + r.getFieldName() + "Id; }")
                 .blank();
            }
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
}
