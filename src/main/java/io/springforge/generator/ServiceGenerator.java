package io.springforge.generator;

import io.springforge.model.ActionDefinition;
import io.springforge.model.EntityDefinition;
import io.springforge.model.FieldDefinition;
import io.springforge.model.ForgeDefinition;
import io.springforge.util.NamingUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;

public class ServiceGenerator extends AbstractGenerator {

    public ServiceGenerator(Log log) { super(log); }

    @Override
    public void generate(ForgeDefinition def, EntityDefinition entity, File outDir) throws MojoExecutionException {
        if (!entity.shouldGenerate("service")) return;

        String svcPkg  = servicePkg(def);
        String implPkg = serviceImplPkg(def);

        writeFile(buildInterface(def, entity, svcPkg),
                  javaFile(outDir, svcPkg, entity.getName() + "Service"), svcPkg);

        writeFile(buildImpl(def, entity, implPkg),
                  javaFile(outDir, implPkg, entity.getName() + "ServiceImpl"), implPkg);
    }

    // ── Interface ────────────────────────────────────────────────────────────────

    private CodeWriter buildInterface(ForgeDefinition def, EntityDefinition entity, String pkg) {
        String name    = entity.getName();
        String dtoPkg  = dtoPkg(def);
        String excPkg  = exceptionPkg(def);
        CodeWriter w   = new CodeWriter();

        w.imp("org.springframework.data.domain.Page")
         .imp("org.springframework.data.domain.Pageable")
         .imp(dtoPkg + "." + name + "RequestDTO")
         .imp(dtoPkg + "." + name + "ResponseDTO");

        // Imports dos DTOs de actions
        for (ActionDefinition a : entity.getActions()) {
            if (a.hasRequest())  w.imp(dtoPkg + "." + a.getRequestDtoName());
            if (a.hasResponse()) w.imp(dtoPkg + "." + a.getResponseDtoName());
        }

        w.javadoc("Contrato de serviço para " + name + ".\nGerado pelo Spring Forge.");
        w.line("public interface " + name + "Service {").blank();
        w.indent();

        // CRUDL padrão
        w.javadoc("Lista todos com paginação.")
         .line("Page<" + name + "ResponseDTO> findAll(Pageable pageable);")
         .blank();

        // Search com filtros
        if (entity.hasFilters()) {
            w.imp(dtoPkg + "." + name + "FilterDTO");
            w.javadoc("Busca com filtros via DTO.")
             .line("Page<" + name + "ResponseDTO> search(" + name + "FilterDTO filter, Pageable pageable);")
             .blank();
        }

        w.javadoc("Busca pelo ID.\n@throws " + excPkg + "." + name + "NotFoundException se não encontrado")
         .line(name + "ResponseDTO findById(Long id);")
         .blank();

        w.javadoc("Cria um novo registro.")
         .line(name + "ResponseDTO create(" + name + "RequestDTO dto);")
         .blank();

        w.javadoc("Atualiza um registro existente.\n@throws " + excPkg + "." + name + "NotFoundException se não encontrado")
         .line(name + "ResponseDTO update(Long id, " + name + "RequestDTO dto);")
         .blank();

        w.javadoc("Remove" + (entity.isSoftDelete() ? " (soft delete)" : "") + " pelo ID.\n@throws " + excPkg + "." + name + "NotFoundException se não encontrado")
         .line("void delete(Long id);")
         .blank();

        // Actions customizadas
        if (entity.hasActions()) {
            w.line("// ── Actions customizadas ─────────────────────────────────────────────────────────")
             .blank();

            for (ActionDefinition a : entity.getActions()) {
                String ret    = a.hasResponse() ? a.getResponseDtoName() : "void";
                String param  = buildActionParams(a);
                String desc   = a.getDescription() != null ? a.getDescription()
                    : "Action '" + a.getName() + "' — implemente no ServiceImpl.";

                w.javadoc(desc + "\n@implSpec Implemente em " + name + "ServiceImpl.")
                 .line(ret + " " + a.getName() + "(" + param + ");")
                 .blank();
            }
        }

        w.unindent().line("}");
        return w;
    }

    // ── Implementação ────────────────────────────────────────────────────────────

    private CodeWriter buildImpl(ForgeDefinition def, EntityDefinition entity, String implPkg) {
        String name    = entity.getName();
        String dtoPkg  = dtoPkg(def);
        String svcPkg  = servicePkg(def);
        String repoPkg = repoPkg(def);
        String entPkg  = entityPkg(def);
        String excPkg  = exceptionPkg(def);
        boolean useMapper = def.getProject().isGenerateMappers();

        CodeWriter w = new CodeWriter();

        w.imp("org.springframework.data.domain.Page")
         .imp("org.springframework.data.domain.Pageable")
         .imp("org.springframework.stereotype.Service")
         .imp("org.springframework.transaction.annotation.Transactional")
         .imp(entPkg  + "." + name)
         .imp(repoPkg + "." + name + "Repository")
         .imp(svcPkg  + "." + name + "Service")
         .imp(dtoPkg  + "." + name + "RequestDTO")
         .imp(dtoPkg  + "." + name + "ResponseDTO")
         .imp(excPkg  + "." + name + "NotFoundException");

        if (useMapper) w.imp(mapperPkg(def) + "." + name + "Mapper");
        if (entity.isSoftDelete()) w.imp("java.time.LocalDateTime");

        // Imports dos Enums (necessário para conversão manual)
        if (!useMapper) {
            for (FieldDefinition f : entity.getFields()) {
                if ("Enum".equalsIgnoreCase(f.getType())) {
                    w.imp(entPkg + "." + enumName(entity, f));
                }
            }
        }

        // Imports dos DTOs de actions
        for (ActionDefinition a : entity.getActions()) {
            if (a.hasRequest())  w.imp(dtoPkg + "." + a.getRequestDtoName());
            if (a.hasResponse()) w.imp(dtoPkg + "." + a.getResponseDtoName());
        }

        w.javadoc("Implementação de serviço para " + name + ".\nGerado pelo Spring Forge — não edite os métodos CRUDL;\nadicione sua lógica nos métodos de action marcados com TODO.");
        w.line("@Service")
         .line("@Transactional(readOnly = true)")
         .line("public class " + name + "ServiceImpl implements " + name + "Service {")
         .blank();
        w.indent();

        // Campos
        w.line("private final " + name + "Repository repository;");
        if (useMapper) w.line("private final " + name + "Mapper mapper;");
        w.blank();

        // Construtor
        if (useMapper) {
            w.line("public " + name + "ServiceImpl(" + name + "Repository repository, " + name + "Mapper mapper) {")
             .indent()
             .line("this.repository = repository;")
             .line("this.mapper = mapper;")
             .unindent().line("}").blank();
        } else {
            w.line("public " + name + "ServiceImpl(" + name + "Repository repository) {")
             .indent()
             .line("this.repository = repository;")
             .unindent().line("}").blank();
        }

        // findAll
        w.line("@Override")
         .line("public Page<" + name + "ResponseDTO> findAll(Pageable pageable) {")
         .indent();
        String findAll = entity.isSoftDelete() ? "repository.findAllActive(pageable)" : "repository.findAll(pageable)";
        String mapCall = useMapper ? ".map(mapper::toResponseDTO)" : ".map(this::toResponseDTO)";
        w.line("return " + findAll + mapCall + ";");
        w.unindent().line("}").blank();

        // search (com filtros)
        if (entity.hasFilters()) {
            String specPkg = def.getProject().getBasePackage() + ".specification";
            w.imp(dtoPkg + "." + name + "FilterDTO")
             .imp(specPkg + "." + name + "Specification")
             .imp("org.springframework.data.jpa.domain.Specification");

            w.line("@Override")
             .line("public Page<" + name + "ResponseDTO> search(" + name + "FilterDTO filter, Pageable pageable) {")
             .indent()
             .line("Specification<" + name + "> spec = " + name + "Specification.fromFilter(filter);")
             .line("return repository.findAll(spec, pageable)" + mapCall + ";")
             .unindent().line("}").blank();
        }

        // findById
        w.line("@Override")
         .line("public " + name + "ResponseDTO findById(Long id) {")
         .indent()
         .line(name + " entity = findEntityById(id);")
         .line("return " + (useMapper ? "mapper.toResponseDTO(entity);" : "toResponseDTO(entity);"))
         .unindent().line("}").blank();

        // create
        w.line("@Override")
         .line("@Transactional")
         .line("public " + name + "ResponseDTO create(" + name + "RequestDTO dto) {")
         .indent()
         .line(name + " entity = " + (useMapper ? "mapper.toEntity(dto);" : "toEntity(dto);"))
         .line("entity = repository.save(entity);")
         .line("return " + (useMapper ? "mapper.toResponseDTO(entity);" : "toResponseDTO(entity);"))
         .unindent().line("}").blank();

        // update
        w.line("@Override")
         .line("@Transactional")
         .line("public " + name + "ResponseDTO update(Long id, " + name + "RequestDTO dto) {")
         .indent()
         .line(name + " entity = findEntityById(id);");
        if (useMapper) {
            w.line("mapper.updateEntityFromDTO(dto, entity);");
        } else {
            w.line("updateEntityFromDTO(dto, entity);");
        }
        w.line("entity = repository.save(entity);")
         .line("return " + (useMapper ? "mapper.toResponseDTO(entity);" : "toResponseDTO(entity);"))
         .unindent().line("}").blank();

        // delete
        w.line("@Override")
         .line("@Transactional")
         .line("public void delete(Long id) {")
         .indent()
         .line(name + " entity = findEntityById(id);");
        if (entity.isSoftDelete()) {
            w.line("entity.setDeletedAt(LocalDateTime.now());")
             .line("repository.save(entity);");
        } else {
            w.line("repository.delete(entity);");
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
                    w.line(name + " entity = findEntityById(id); // entidade disponível para uso");
                }

                w.line("throw new UnsupportedOperationException(")
                 .indent()
                 .line("\"Action '" + a.getName() + "' ainda não implementada em " + name + "ServiceImpl\"")
                 .unindent()
                 .line(");");

                w.unindent().line("}").blank();
            }
        }

        // ── Privados ─────────────────────────────────────────────────────────────
        w.line("// ── Métodos privados ─────────────────────────────────────────────────────────────")
         .blank();

        // findEntityById
        w.line("private " + name + " findEntityById(Long id) {")
         .indent();
        String findById = entity.isSoftDelete()
            ? "repository.findByIdActive(id)"
            : "repository.findById(id)";
        w.line("return " + findById)
         .indent()
         .line(".orElseThrow(() -> new " + name + "NotFoundException(id));")
         .unindent()
         .unindent().line("}").blank();

        // Conversão manual (só se não usar MapStruct)
        if (!useMapper) {
            writeManualConversions(w, entity, name);
        }

        w.unindent().line("}");
        return w;
    }

    private void writeManualConversions(CodeWriter w, EntityDefinition entity, String name) {
        // toResponseDTO
        w.line("private " + name + "ResponseDTO toResponseDTO(" + name + " entity) {")
         .indent()
         .line(name + "ResponseDTO dto = new " + name + "ResponseDTO();")
         .line("dto.setId(entity.getId());");
        for (FieldDefinition f : entity.getFields()) {
            if (!f.isInResponse()) continue;
            String cap = NamingUtils.toPascalCase(f.getName());
            if ("Enum".equalsIgnoreCase(f.getType())) {
                w.line("dto.set" + cap + "(entity.get" + cap + "() != null ? entity.get" + cap + "().name() : null);");
            } else {
                w.line("dto.set" + cap + "(entity.get" + cap + "());");
            }
        }
        if (entity.isAuditable()) {
            w.line("dto.setCreatedAt(entity.getCreatedAt());")
             .line("dto.setUpdatedAt(entity.getUpdatedAt());");
        }
        w.line("return dto;")
         .unindent().line("}").blank();

        // toEntity
        w.line("private " + name + " toEntity(" + name + "RequestDTO dto) {")
         .indent()
         .line(name + " entity = new " + name + "();");
        for (FieldDefinition f : entity.getFields()) {
            if (!f.isInRequest()) continue;
            String cap = NamingUtils.toPascalCase(f.getName());
            if ("Enum".equalsIgnoreCase(f.getType())) {
                w.line("if (dto.get" + cap + "() != null) entity.set" + cap + "("
                    + enumName(entity, f) + ".valueOf(dto.get" + cap + "()));");
            } else {
                w.line("entity.set" + cap + "(dto.get" + cap + "());");
            }
        }
        w.line("return entity;")
         .unindent().line("}").blank();

        // updateEntityFromDTO
        w.line("private void updateEntityFromDTO(" + name + "RequestDTO dto, " + name + " entity) {")
         .indent();
        for (FieldDefinition f : entity.getFields()) {
            if (!f.isInRequest()) continue;
            String cap = NamingUtils.toPascalCase(f.getName());
            if ("Enum".equalsIgnoreCase(f.getType())) {
                w.line("if (dto.get" + cap + "() != null) entity.set" + cap + "("
                    + enumName(entity, f) + ".valueOf(dto.get" + cap + "()));");
            } else {
                w.line("if (dto.get" + cap + "() != null) entity.set" + cap + "(dto.get" + cap + "());");
            }
        }
        w.unindent().line("}").blank();
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
