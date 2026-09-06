package io.springforge.generator;

import io.springforge.model.EntityDefinition;
import io.springforge.model.FieldDefinition;
import io.springforge.model.ForgeDefinition;
import io.springforge.util.NamingUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;

public class MapperGenerator extends AbstractGenerator {

    public MapperGenerator(Log log) { super(log); }

    @Override
    public void generate(ForgeDefinition def, EntityDefinition entity, File outDir) throws MojoExecutionException {
        if (!entity.shouldGenerate("mapper")) return;
        if (!def.getProject().isGenerateMappers()) return;

        String pkg  = mapperPkg(def, entity);
        String name = entity.getName();

        CodeWriter w = new CodeWriter();

        w.imp("org.mapstruct.*")
         .imp(entityPkg(def, entity) + "." + name)
         .imp(dtoPkg(def, entity)    + "." + name + "RequestDTO")
         .imp(dtoPkg(def, entity)    + "." + name + "ResponseDTO");

        // Import dos Enums usados nas expressions
        for (FieldDefinition f : entity.getFields()) {
            if ("Enum".equalsIgnoreCase(f.getType())) {
                w.imp(entityPkg(def, entity) + "." + enumName(entity, f));
            }
        }

        w.javadoc("Mapper MapStruct para " + name + ".\nGerado pelo Spring Forge.");
        w.line("@Mapper(")
         .line("    componentModel = \"spring\",")
         .line("    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE")
         .line(")")
         .line("public interface " + name + "Mapper {")
         .blank();
        w.indent();

        // toResponseDTO — mapeamentos especiais para Enum e relações ManyToOne
        for (io.springforge.model.RelationDefinition r : entity.getRelations()) {
            if ("ManyToOne".equals(r.getType()) && r.isInResponse()) {
                String cap = NamingUtils.toPascalCase(r.getFieldName());
                w.line("@Mapping(target = \"" + r.getFieldName() + "Id\", expression = \"java(entity.get" + cap + "() != null ? entity.get" + cap + "().getId() : null)\")");
            }
        }
        boolean hasEnumResponse = entity.getFields().stream()
            .anyMatch(f -> "Enum".equalsIgnoreCase(f.getType()) && f.isInResponse());

        if (hasEnumResponse) {
            for (FieldDefinition f : entity.getFields()) {
                if (!"Enum".equalsIgnoreCase(f.getType()) || !f.isInResponse()) continue;
                String cap = NamingUtils.toPascalCase(f.getName());
                w.line("@Mapping(target = \"" + f.getName() + "\", "
                    + "expression = \"java(entity.get" + cap + "() != null "
                    + "? entity.get" + cap + "().name() : null)\")");
            }
        }
        w.line(name + "ResponseDTO toResponseDTO(" + name + " entity);")
         .blank();

        // toEntity
        for (io.springforge.model.RelationDefinition r : entity.getRelations()) {
            if ("ManyToOne".equals(r.getType())) {
                w.line("@Mapping(target = \"" + r.getFieldName() + "\", ignore = true)");
            }
        }
        boolean hasEnumRequest = entity.getFields().stream()
            .anyMatch(f -> "Enum".equalsIgnoreCase(f.getType()) && f.isInRequest());

        if (hasEnumRequest) {
            for (FieldDefinition f : entity.getFields()) {
                if (!"Enum".equalsIgnoreCase(f.getType()) || !f.isInRequest()) continue;
                String cap     = NamingUtils.toPascalCase(f.getName());
                String enumCls = enumName(entity, f);
                w.line("@Mapping(target = \"" + f.getName() + "\", "
                    + "expression = \"java(dto.get" + cap + "() != null "
                    + "? " + enumCls + ".valueOf(dto.get" + cap + "()) : null)\")");
            }
        }
        w.line(name + " toEntity(" + name + "RequestDTO dto);")
         .blank();

        // updateEntityFromDTO
        for (io.springforge.model.RelationDefinition r : entity.getRelations()) {
            if ("ManyToOne".equals(r.getType())) {
                w.line("@Mapping(target = \"" + r.getFieldName() + "\", ignore = true)");
            }
        }
        w.line("@BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)")
         .line("void updateEntityFromDTO(" + name + "RequestDTO dto, @MappingTarget " + name + " entity);")
         .blank();

        w.unindent().line("}");
        writeFile(w, javaFile(outDir, pkg, name + "Mapper"), pkg);
    }
}
