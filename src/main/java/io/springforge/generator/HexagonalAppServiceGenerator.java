package io.springforge.generator;

import io.springforge.model.ActionDefinition;
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
 * Gerador — Arquitetura Hexagonal: Application Service (Use-Case Implementation).
 *
 * Gera a implementação do use-case em:
 *   {basePackage}.application.service.{EntityName}UseCaseImpl
 *
 * Esta classe:
 * - Implementa o port de entrada ({EntityName}UseCase)
 * - Depende apenas do port de saída ({EntityName}RepositoryPort) — nunca de JPA diretamente
 * - Contém a lógica de orquestração de domínio (conversão DTO ↔ Domain, regras de negócio)
 */
public class HexagonalAppServiceGenerator extends AbstractGenerator {

    public HexagonalAppServiceGenerator(Log log) { super(log); }

    @Override
    public void generate(ForgeDefinition def, EntityDefinition entity, File outDir)
            throws MojoExecutionException {

        if (!def.getProject().isHexagonal()) return;
        if (!entity.shouldGenerate("service")) return;

        String pkg  = hexAppServicePkg(def);
        String name = entity.getName();

        writeFile(buildAppService(def, entity, pkg),
                  javaFile(outDir, pkg, name + "UseCaseImpl"), pkg);
    }

    private CodeWriter buildAppService(ForgeDefinition def, EntityDefinition entity, String pkg) {
        String name      = entity.getName();
        String dtoPkg    = hexDtoPkg(def);
        String inPortPkg = hexPortInPkg(def);
        String outPortPkg= hexPortOutPkg(def);
        String domainPkg = hexDomainModelPkg(def);
        String excPkg    = exceptionPkg(def);
        boolean useMapper = def.getProject().isGenerateMappers();

        CodeWriter w = new CodeWriter();

        w.imp("org.springframework.stereotype.Service")
         .imp("org.springframework.transaction.annotation.Transactional")
         .imp("org.springframework.data.domain.Page")
         .imp("org.springframework.data.domain.Pageable")
         .imp(inPortPkg  + "." + name + "UseCase")
         .imp(outPortPkg + "." + name + "RepositoryPort")
         .imp(domainPkg  + "." + name)
         .imp(dtoPkg     + "." + name + "RequestDTO")
         .imp(dtoPkg     + "." + name + "ResponseDTO")
         .imp(excPkg     + "." + name + "NotFoundException");

        if (entity.hasFilters()) {
            w.imp(dtoPkg + "." + name + "FilterDTO");
        }

        if (entity.isSoftDelete()) w.imp("java.time.LocalDateTime");

        for (ActionDefinition a : entity.getActions()) {
            if (a.hasRequest())  w.imp(dtoPkg + "." + a.getRequestDtoName());
            if (a.hasResponse()) w.imp(dtoPkg + "." + a.getResponseDtoName());
        }

        // Imports manuais de enum (quando não usa MapStruct)
        if (!useMapper) {
            for (FieldDefinition f : entity.getFields()) {
                if ("Enum".equalsIgnoreCase(f.getType())) {
                    // enums ficam no persistence package no hexagonal
                    w.imp(hexPersistenceAdapterPkg(def) + "." + enumName(entity, f));
                }
            }
        }

        List<RelationDefinition> manyToOneRelations = entity.getRelations().stream()
            .filter(r -> "ManyToOne".equals(r.getType())).toList();

        // Portas dos relacionamentos
        for (RelationDefinition r : manyToOneRelations) {
            w.imp(outPortPkg + "." + r.getTargetEntity() + "RepositoryPort");
        }

        w.javadoc(
            "Implementação do Use-Case para " + name + ".\n" +
            "Orquestra as operações de domínio utilizando apenas o RepositoryPort.\n" +
            "Não possui dependências diretas de JPA ou frameworks de persistência.\n" +
            "Gerado pelo Spring Forge — Arquitetura Hexagonal.\n" +
            "\nNão edite os métodos CRUDL gerados; adicione sua lógica nos métodos de action marcados com TODO."
        );

        w.line("@Service")
         .line("@Transactional(readOnly = true)")
         .line("public class " + name + "UseCaseImpl implements " + name + "UseCase {")
         .blank();
        w.indent();

        // Campos
        w.line("private final " + name + "RepositoryPort repositoryPort;");
        for (RelationDefinition r : manyToOneRelations) {
            String camelTarget = NamingUtils.toCamelCase(r.getTargetEntity());
            w.line("private final " + r.getTargetEntity() + "RepositoryPort " + camelTarget + "RepositoryPort;");
        }
        w.blank();

        // Construtor
        StringBuilder ctorParams = new StringBuilder(name + "RepositoryPort repositoryPort");
        for (RelationDefinition r : manyToOneRelations) {
            ctorParams.append(", ").append(r.getTargetEntity()).append("RepositoryPort ")
                      .append(NamingUtils.toCamelCase(r.getTargetEntity())).append("RepositoryPort");
        }
        w.line("public " + name + "UseCaseImpl(" + ctorParams + ") {")
         .indent()
         .line("this.repositoryPort = repositoryPort;");
        for (RelationDefinition r : manyToOneRelations) {
            String camelTarget = NamingUtils.toCamelCase(r.getTargetEntity());
            w.line("this." + camelTarget + "RepositoryPort = " + camelTarget + "RepositoryPort;");
        }
        w.unindent().line("}").blank();

        // findAll
        w.line("@Override")
         .line("public Page<" + name + "ResponseDTO> findAll(Pageable pageable) {")
         .indent()
         .line("return repositoryPort.findAll(pageable).map(this::toResponseDTO);")
         .unindent().line("}").blank();

        // search (com filtros)
        if (entity.hasFilters()) {
            w.line("@Override")
             .line("public Page<" + name + "ResponseDTO> search(" + name + "FilterDTO filter, Pageable pageable) {")
             .indent()
             .line("// TODO: implemente a lógica de filtro usando o repositoryPort")
             .line("return repositoryPort.findAll(pageable).map(this::toResponseDTO);")
             .unindent().line("}").blank();
        }

        // findById
        w.line("@Override")
         .line("public " + name + "ResponseDTO findById(Long id) {")
         .indent()
         .line(name + " domain = findDomainById(id);")
         .line("return toResponseDTO(domain);")
         .unindent().line("}").blank();

        // create
        w.line("@Override")
         .line("@Transactional")
         .line("public " + name + "ResponseDTO create(" + name + "RequestDTO dto) {")
         .indent()
         .line(name + " domain = toDomain(dto);")
         .line("domain = repositoryPort.save(domain);")
         .line("return toResponseDTO(domain);")
         .unindent().line("}").blank();

        // update
        w.line("@Override")
         .line("@Transactional")
         .line("public " + name + "ResponseDTO update(Long id, " + name + "RequestDTO dto) {")
         .indent()
         .line(name + " domain = findDomainById(id);")
         .line("updateDomainFromDTO(dto, domain);")
         .line("domain = repositoryPort.save(domain);")
         .line("return toResponseDTO(domain);")
         .unindent().line("}").blank();

        // delete
        w.line("@Override")
         .line("@Transactional")
         .line("public void delete(Long id) {")
         .indent()
         .line(name + " domain = findDomainById(id);");
        if (entity.isSoftDelete()) {
            w.line("domain.setDeletedAt(LocalDateTime.now());")
             .line("repositoryPort.save(domain);");
        } else {
            w.line("repositoryPort.deleteById(domain.getId());");
        }
        w.unindent().line("}").blank();

        // ── Actions ──────────────────────────────────────────────────────────────
        if (entity.hasActions()) {
            w.line("// ── Actions customizadas — IMPLEMENTE AQUI ───────────────────────────────────────")
             .blank();

            for (ActionDefinition a : entity.getActions()) {
                String ret   = a.hasResponse() ? a.getResponseDtoName() : "void";
                String param = buildActionParams(a);
                String desc  = a.getDescription() != null ? a.getDescription()
                    : "Action '" + a.getName() + "' — lógica customizada.";

                w.line("/**")
                 .line(" * " + desc)
                 .line(" *")
                 .line(" * TODO: Implemente a lógica desta action.")
                 .line(" */")
                 .line("@Override")
                 .line("@Transactional")
                 .line("public " + ret + " " + a.getName() + "(" + param + ") {")
                 .indent()
                 .line("// TODO: implemente aqui a lógica da action '" + a.getName() + "'");

                if (a.isRequiresId()) {
                    w.line(name + " domain = findDomainById(id); // domínio disponível para uso");
                }

                w.line("throw new UnsupportedOperationException(")
                 .indent()
                 .line("\"Action '" + a.getName() + "' ainda não implementada em " + name + "UseCaseImpl\"")
                 .unindent()
                 .line(");");

                w.unindent().line("}").blank();
            }
        }

        // ── Métodos privados ──────────────────────────────────────────────────────
        w.line("// ── Métodos privados ─────────────────────────────────────────────────────────────")
         .blank();

        // findDomainById
        w.line("private " + name + " findDomainById(Long id) {")
         .indent();
        String findById = entity.isSoftDelete()
            ? "repositoryPort.findByIdActive(id)"
            : "repositoryPort.findById(id)";
        w.line("return " + findById)
         .indent()
         .line(".orElseThrow(() -> new " + name + "NotFoundException(id));")
         .unindent()
         .unindent().line("}").blank();

        // toResponseDTO (Domain → ResponseDTO)
        w.line("private " + name + "ResponseDTO toResponseDTO(" + name + " domain) {")
         .indent()
         .line(name + "ResponseDTO dto = new " + name + "ResponseDTO();")
         .line("dto.setId(domain.getId());");

        for (FieldDefinition f : entity.getFields()) {
            if (!f.isInResponse()) continue;
            String cap = NamingUtils.toPascalCase(f.getName());
            if ("Enum".equalsIgnoreCase(f.getType())) {
                w.line("dto.set" + cap + "(domain.get" + cap + "() != null ? domain.get" + cap + "().name() : null);");
            } else {
                w.line("dto.set" + cap + "(domain.get" + cap + "());");
            }
        }

        for (RelationDefinition r : manyToOneRelations) {
            if (r.isInResponse()) {
                String capField = NamingUtils.toPascalCase(r.getFieldName());
                w.line("dto.set" + capField + "Id(domain.get" + capField + "Id());");
            }
        }

        if (entity.isAuditable()) {
            w.line("dto.setCreatedAt(domain.getCreatedAt());")
             .line("dto.setUpdatedAt(domain.getUpdatedAt());");
        }

        w.line("return dto;")
         .unindent().line("}").blank();

        // toDomain (RequestDTO → Domain)
        w.line("private " + name + " toDomain(" + name + "RequestDTO dto) {")
         .indent()
         .line(name + " domain = new " + name + "();");

        for (FieldDefinition f : entity.getFields()) {
            if (!f.isInRequest()) continue;
            String cap = NamingUtils.toPascalCase(f.getName());
            if ("Enum".equalsIgnoreCase(f.getType())) {
                w.line("if (dto.get" + cap + "() != null) domain.set" + cap + "("
                    + enumName(entity, f) + ".valueOf(dto.get" + cap + "()));");
            } else {
                w.line("domain.set" + cap + "(dto.get" + cap + "());");
            }
        }

        for (RelationDefinition r : manyToOneRelations) {
            String capField = NamingUtils.toPascalCase(r.getFieldName());
            w.line("domain.set" + capField + "Id(dto.get" + capField + "Id());");
        }

        w.line("return domain;")
         .unindent().line("}").blank();

        // updateDomainFromDTO
        w.line("private void updateDomainFromDTO(" + name + "RequestDTO dto, " + name + " domain) {")
         .indent();

        for (FieldDefinition f : entity.getFields()) {
            if (!f.isInRequest()) continue;
            String cap = NamingUtils.toPascalCase(f.getName());
            if ("Enum".equalsIgnoreCase(f.getType())) {
                w.line("if (dto.get" + cap + "() != null) domain.set" + cap + "("
                    + enumName(entity, f) + ".valueOf(dto.get" + cap + "()));");
            } else {
                w.line("if (dto.get" + cap + "() != null) domain.set" + cap + "(dto.get" + cap + "());");
            }
        }

        for (RelationDefinition r : manyToOneRelations) {
            String capField = NamingUtils.toPascalCase(r.getFieldName());
            w.line("if (dto.get" + capField + "Id() != null) domain.set" + capField + "Id(dto.get" + capField + "Id());");
        }

        w.unindent().line("}").blank();

        w.unindent().line("}");
        return w;
    }

    private String buildActionParams(ActionDefinition a) {
        StringBuilder sb = new StringBuilder();
        if (a.isRequiresId()) sb.append("Long id");
        if (a.hasRequest()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(a.getRequestDtoName()).append(" dto");
        }
        return sb.toString();
    }
}
