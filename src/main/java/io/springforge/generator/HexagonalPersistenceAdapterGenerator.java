package io.springforge.generator;

import io.springforge.model.EntityDefinition;
import io.springforge.model.FieldDefinition;
import io.springforge.model.ForgeDefinition;
import io.springforge.model.RelationDefinition;
import io.springforge.util.NamingUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.util.List;

/**
 * Gerador — Arquitetura Hexagonal: Persistence Adapter (Out Adapter).
 *
 * Gera dois artefatos no pacote {basePackage}.adapter.out.persistence:
 *
 * 1. {@code {EntityName}JpaEntity} — entidade JPA (anotada com @Entity/@Table).
 *    Separada do modelo de domínio para isolar as anotações de infraestrutura.
 *
 * 2. {@code {EntityName}PersistenceAdapter} — implementação de {EntityName}RepositoryPort.
 *    Usa o JpaRepository interno e converte entre JpaEntity ↔ Domain Model.
 *
 * Também gera a interface Spring Data interna:
 * 3. {@code {EntityName}JpaRepository} — Spring Data JPA Repository interno do adapter.
 */
public class HexagonalPersistenceAdapterGenerator extends AbstractGenerator {

    public HexagonalPersistenceAdapterGenerator(Log log) { super(log); }

    @Override
    public void generate(ForgeDefinition def, EntityDefinition entity, File outDir)
            throws MojoExecutionException {

        if (!def.getProject().isHexagonal()) return;
        if (!entity.shouldGenerate("entity")) return;

        String pkg  = hexPersistenceAdapterPkg(def);
        String name = entity.getName();

        // 1. JPA Entity
        writeFile(buildJpaEntity(def, entity, pkg),
                  javaFile(outDir, pkg, name + "JpaEntity"), pkg);

        // 2. Spring Data JPA Repository (interno ao adapter)
        writeFile(buildJpaRepository(def, entity, pkg),
                  javaFile(outDir, pkg, name + "JpaRepository"), pkg);

        // 3. Persistence Adapter
        writeFile(buildPersistenceAdapter(def, entity, pkg),
                  javaFile(outDir, pkg, name + "PersistenceAdapter"), pkg);

        // 4. Enums (gerados aqui para não depender do EntityGenerator do Layered)
        for (FieldDefinition f : entity.getFields()) {
            if ("Enum".equalsIgnoreCase(f.getType()) && f.getEnumValues() != null && !f.getEnumValues().isEmpty()) {
                String eName = enumName(entity, f);
                writeFile(buildEnum(f, pkg, eName), javaFile(outDir, pkg, eName), pkg);
            }
        }
    }

    // ── JPA Entity ───────────────────────────────────────────────────────────────

    private CodeWriter buildJpaEntity(ForgeDefinition def, EntityDefinition entity, String pkg) {
        String name = entity.getName();
        String table = entity.getTableName() != null ? entity.getTableName() : NamingUtils.toSnakeCase(name);
        CodeWriter w = new CodeWriter();

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

        w.javadoc(
            "Entidade JPA para " + name + ".\n" +
            "Usada exclusivamente pelo adapter de persistência. Não deve vazar para fora do pacote adapter.out.persistence.\n" +
            "Gerado pelo Spring Forge — Arquitetura Hexagonal."
        );

        w.line("@Entity")
         .line("@Table(name = \"" + table + "\")");
        w.line("public class " + name + "JpaEntity implements Serializable {")
         .blank();
        w.indent();

        // ID
        w.line("@Id")
         .line("@GeneratedValue(strategy = GenerationType.IDENTITY)")
         .line("private Long id;")
         .blank();

        // Campos
        for (FieldDefinition f : entity.getFields()) {
            String type = "Enum".equalsIgnoreCase(f.getType())
                ? enumName(entity, f)
                : NamingUtils.toJavaType(f.getType());

            if (f.isUnique()) {
                w.line("@Column(unique = true" + (f.isRequired() ? ", nullable = false" : "") + ")");
            } else if (f.isRequired()) {
                w.line("@Column(nullable = false)");
            }

            if ("Enum".equalsIgnoreCase(f.getType())) {
                w.line("@Enumerated(EnumType.STRING)");
            }

            w.line("private " + type + " " + f.getName() + ";");
        }

        // Relações
        for (RelationDefinition r : entity.getRelations()) {
            if ("ManyToOne".equals(r.getType())) {
                String fkCol = NamingUtils.toSnakeCase(r.getFieldName()) + "_id";
                w.blank()
                 .line("@ManyToOne(fetch = FetchType.LAZY)")
                 .line("@JoinColumn(name = \"" + fkCol + "\")")
                 .line("private " + name + "JpaEntity " + r.getFieldName() + "; // simplificado: referência JPA interna");
            }
        }

        // Auditoria
        if (entity.isAuditable()) {
            w.blank()
             .line("private LocalDateTime createdAt;")
             .line("private LocalDateTime updatedAt;")
             .blank()
             .line("@PrePersist")
             .line("protected void onCreate() {")
             .indent()
             .line("createdAt = LocalDateTime.now();")
             .line("updatedAt = LocalDateTime.now();")
             .unindent().line("}")
             .blank()
             .line("@PreUpdate")
             .line("protected void onUpdate() {")
             .indent()
             .line("updatedAt = LocalDateTime.now();")
             .unindent().line("}");
        }

        if (entity.isSoftDelete()) {
            w.blank().line("private LocalDateTime deletedAt;");
        }

        w.blank();

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

        if (entity.isAuditable()) {
            w.line("public LocalDateTime getCreatedAt() { return createdAt; }")
             .line("public void setCreatedAt(LocalDateTime c) { this.createdAt = c; }")
             .blank()
             .line("public LocalDateTime getUpdatedAt() { return updatedAt; }")
             .line("public void setUpdatedAt(LocalDateTime u) { this.updatedAt = u; }")
             .blank();
        }
        if (entity.isSoftDelete()) {
            w.line("public LocalDateTime getDeletedAt() { return deletedAt; }")
             .line("public void setDeletedAt(LocalDateTime d) { this.deletedAt = d; }")
             .blank();
        }

        w.unindent().line("}");
        return w;
    }

    // ── Spring Data JPA Repository (interno) ─────────────────────────────────────

    private CodeWriter buildJpaRepository(ForgeDefinition def, EntityDefinition entity, String pkg) {
        String name = entity.getName();
        CodeWriter w = new CodeWriter();

        w.imp("org.springframework.data.domain.Page")
         .imp("org.springframework.data.domain.Pageable")
         .imp("org.springframework.data.jpa.repository.JpaRepository")
         .imp("org.springframework.stereotype.Repository");

        if (entity.isSoftDelete()) {
            w.imp("org.springframework.data.jpa.repository.Query")
             .imp("java.util.Optional");
        }

        w.javadoc(
            "Spring Data JPA Repository interno do adapter de persistência para " + name + ".\n" +
            "Visibilidade restrita ao pacote adapter.out.persistence — não deve ser exposto ao núcleo.\n" +
            "Gerado pelo Spring Forge — Arquitetura Hexagonal."
        );

        w.line("@Repository")
         .line("interface " + name + "JpaRepository extends JpaRepository<" + name + "JpaEntity, Long> {")
         .blank();
        w.indent();

        if (entity.isSoftDelete()) {
            w.line("@Query(\"SELECT e FROM " + name + "JpaEntity e WHERE e.id = :id AND e.deletedAt IS NULL\")")
             .line("Optional<" + name + "JpaEntity> findByIdActive(Long id);")
             .blank()
             .line("@Query(\"SELECT e FROM " + name + "JpaEntity e WHERE e.deletedAt IS NULL\")")
             .line("Page<" + name + "JpaEntity> findAllActive(Pageable pageable);")
             .blank();
        }

        // Finders por campos únicos
        entity.getFields().stream()
            .filter(FieldDefinition::isUnique)
            .forEach(f -> {
                String cap  = NamingUtils.toPascalCase(f.getName());
                String type = NamingUtils.toJavaType(f.getType());
                w.line("java.util.Optional<" + name + "JpaEntity> findBy" + cap + "(" + type + " " + f.getName() + ");")
                 .blank();
            });

        w.unindent().line("}");
        return w;
    }

    // ── Persistence Adapter ──────────────────────────────────────────────────────

    private CodeWriter buildPersistenceAdapter(ForgeDefinition def, EntityDefinition entity, String pkg) {
        String name       = entity.getName();
        String outPortPkg = hexPortOutPkg(def);
        String domainPkg  = hexDomainModelPkg(def);
        CodeWriter w      = new CodeWriter();

        w.imp("org.springframework.stereotype.Component")
         .imp("org.springframework.data.domain.Page")
         .imp("org.springframework.data.domain.Pageable")
         .imp("java.util.Optional")
         .imp(outPortPkg + "." + name + "RepositoryPort")
         .imp(domainPkg  + "." + name);

        List<RelationDefinition> manyToOneRelations = entity.getRelations().stream()
            .filter(r -> "ManyToOne".equals(r.getType())).toList();

        w.javadoc(
            "Adapter de persistência para " + name + ".\n" +
            "Implementa o port de saída " + name + "RepositoryPort usando Spring Data JPA.\n" +
            "Converte entre " + name + "JpaEntity (infraestrutura) e " + name + " (domínio).\n" +
            "Gerado pelo Spring Forge — Arquitetura Hexagonal."
        );

        w.line("@Component")
         .line("public class " + name + "PersistenceAdapter implements " + name + "RepositoryPort {")
         .blank();
        w.indent();

        w.line("private final " + name + "JpaRepository jpaRepository;")
         .blank()
         .line("public " + name + "PersistenceAdapter(" + name + "JpaRepository jpaRepository) {")
         .indent()
         .line("this.jpaRepository = jpaRepository;")
         .unindent().line("}").blank();

        // save
        w.line("@Override")
         .line("public " + name + " save(" + name + " domain) {")
         .indent()
         .line(name + "JpaEntity entity = toJpaEntity(domain);")
         .line("entity = jpaRepository.save(entity);")
         .line("return toDomain(entity);")
         .unindent().line("}").blank();

        // findById
        w.line("@Override")
         .line("public Optional<" + name + "> findById(Long id) {")
         .indent()
         .line("return jpaRepository.findById(id).map(this::toDomain);")
         .unindent().line("}").blank();

        // findByIdActive (soft delete)
        if (entity.isSoftDelete()) {
            w.line("@Override")
             .line("public Optional<" + name + "> findByIdActive(Long id) {")
             .indent()
             .line("return jpaRepository.findByIdActive(id).map(this::toDomain);")
             .unindent().line("}").blank();
        }

        // findAll
        w.line("@Override")
         .line("public Page<" + name + "> findAll(Pageable pageable) {")
         .indent();
        String findAll = entity.isSoftDelete() ? "jpaRepository.findAllActive(pageable)" : "jpaRepository.findAll(pageable)";
        w.line("return " + findAll + ".map(this::toDomain);")
         .unindent().line("}").blank();

        // deleteById
        w.line("@Override")
         .line("public void deleteById(Long id) {")
         .indent()
         .line("jpaRepository.deleteById(id);")
         .unindent().line("}").blank();

        // existsById
        w.line("@Override")
         .line("public boolean existsById(Long id) {")
         .indent()
         .line("return jpaRepository.existsById(id);")
         .unindent().line("}").blank();

        // ── Conversões privadas ──────────────────────────────────────────────────
        w.line("// ── Conversões Domain ↔ JpaEntity ───────────────────────────────────────────────")
         .blank();

        // toDomain
        w.line("private " + name + " toDomain(" + name + "JpaEntity e) {")
         .indent()
         .line(name + " domain = new " + name + "();")
         .line("domain.setId(e.getId());");

        for (FieldDefinition f : entity.getFields()) {
            String cap = NamingUtils.toPascalCase(f.getName());
            if ("Enum".equalsIgnoreCase(f.getType())) {
                w.line("domain.set" + cap + "(e.get" + cap + "()" +
                    " != null ? " + enumName(entity, f) + ".valueOf(e.get" + cap + "().name()) : null);");
            } else {
                w.line("domain.set" + cap + "(e.get" + cap + "());");
            }
        }

        if (entity.isAuditable()) {
            w.line("domain.setCreatedAt(e.getCreatedAt());")
             .line("domain.setUpdatedAt(e.getUpdatedAt());");
        }
        if (entity.isSoftDelete()) {
            w.line("domain.setDeletedAt(e.getDeletedAt());");
        }

        w.line("return domain;")
         .unindent().line("}").blank();

        // toJpaEntity
        w.line("private " + name + "JpaEntity toJpaEntity(" + name + " domain) {")
         .indent()
         .line(name + "JpaEntity e = new " + name + "JpaEntity();")
         .line("e.setId(domain.getId());");

        for (FieldDefinition f : entity.getFields()) {
            String cap = NamingUtils.toPascalCase(f.getName());
            if ("Enum".equalsIgnoreCase(f.getType())) {
                w.line("e.set" + cap + "(domain.get" + cap + "()" +
                    " != null ? " + enumName(entity, f) + ".valueOf(domain.get" + cap + "().name()) : null);");
            } else {
                w.line("e.set" + cap + "(domain.get" + cap + "());");
            }
        }

        if (entity.isAuditable()) {
            w.line("e.setCreatedAt(domain.getCreatedAt());")
             .line("e.setUpdatedAt(domain.getUpdatedAt());");
        }
        if (entity.isSoftDelete()) {
            w.line("e.setDeletedAt(domain.getDeletedAt());");
        }

        w.line("return e;")
         .unindent().line("}").blank();

        w.unindent().line("}");
        return w;
    }

    // ── Enum ─────────────────────────────────────────────────────────────────────

    private CodeWriter buildEnum(FieldDefinition f, String pkg, String enumClassName) {
        CodeWriter w = new CodeWriter();
        w.javadoc("Enum gerado pelo Spring Forge para o campo '" + f.getName() + "'.");
        w.line("public enum " + enumClassName + " {")
         .blank()
         .indent();

        java.util.List<String> values = f.getEnumValues();
        for (int i = 0; i < values.size(); i++) {
            boolean last = i == values.size() - 1;
            w.line(values.get(i) + (last ? "" : ","));
        }
        w.blank().unindent().line("}");
        return w;
    }
}
