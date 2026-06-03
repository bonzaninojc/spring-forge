package io.springforge.generator;

import io.springforge.model.EntityDefinition;
import io.springforge.model.FilterDefinition;
import io.springforge.model.ForgeDefinition;
import io.springforge.util.NamingUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;

/**
 * Gera FilterDTO + endpoint POST /search + Specification para filtragem via DTO.
 */
public class FilterGenerator extends AbstractGenerator {

    public FilterGenerator(Log log) { super(log); }

    @Override
    public void generate(ForgeDefinition def, EntityDefinition entity, File outDir) throws MojoExecutionException {
        if (!entity.hasFilters()) return;

        String dtoPkg = dtoPkg(def);
        String name = entity.getName();

        writeFile(buildFilterDTO(def, entity, dtoPkg),
                  javaFile(outDir, dtoPkg, name + "FilterDTO"), dtoPkg);

        String specPkg = def.getProject().getBasePackage() + ".specification";
        writeFile(buildSpecification(def, entity, specPkg),
                  javaFile(outDir, specPkg, name + "Specification"), specPkg);
    }

    private CodeWriter buildFilterDTO(ForgeDefinition def, EntityDefinition entity, String pkg) {
        String name = entity.getName();
        CodeWriter w = new CodeWriter();

        for (FilterDefinition f : entity.getFilters()) {
            String imp = NamingUtils.toJavaImport(f.getType());
            if (imp != null) w.imp(imp);
        }

        w.javadoc("DTO de filtros para busca de " + name + ".\nGerado pelo Spring Forge.");
        w.line("public class " + name + "FilterDTO {").blank();
        w.indent();

        for (FilterDefinition f : entity.getFilters()) {
            String type = "Enum".equalsIgnoreCase(f.getType()) ? "String" : NamingUtils.toJavaType(f.getType());
            w.line("private " + type + " " + f.getName() + ";");
        }
        w.blank();

        for (FilterDefinition f : entity.getFilters()) {
            String type = "Enum".equalsIgnoreCase(f.getType()) ? "String" : NamingUtils.toJavaType(f.getType());
            String cap = Character.toUpperCase(f.getName().charAt(0)) + f.getName().substring(1);
            w.line("public " + type + " get" + cap + "() { return " + f.getName() + "; }")
             .line("public void set" + cap + "(" + type + " " + f.getName() + ") { this." + f.getName() + " = " + f.getName() + "; }");
        }

        w.unindent().line("}");
        return w;
    }

    private CodeWriter buildSpecification(ForgeDefinition def, EntityDefinition entity, String pkg) {
        String name = entity.getName();
        String entPkg = entityPkg(def);
        String dtoPkg = dtoPkg(def);
        CodeWriter w = new CodeWriter();

        w.imp("jakarta.persistence.criteria.Predicate")
         .imp("org.springframework.data.jpa.domain.Specification")
         .imp("java.util.ArrayList")
         .imp("java.util.List")
         .imp(entPkg + "." + name)
         .imp(dtoPkg + "." + name + "FilterDTO");

        w.javadoc("Specification de busca para " + name + " baseado no FilterDTO.\nGerado pelo Spring Forge.");
        w.line("public class " + name + "Specification {").blank();
        w.indent();

        w.line("private " + name + "Specification() {}").blank();

        w.line("public static Specification<" + name + "> fromFilter(" + name + "FilterDTO filter) {")
         .indent()
         .line("return (root, query, cb) -> {")
         .indent()
         .line("List<Predicate> predicates = new ArrayList<>();").blank();

        for (FilterDefinition f : entity.getFilters()) {
            String cap = Character.toUpperCase(f.getName().charAt(0)) + f.getName().substring(1);
            String fieldName = f.getName();

            w.line("if (filter.get" + cap + "() != null) {");
            w.indent();

            if ("String".equalsIgnoreCase(f.getType())) {
                // String → LIKE case-insensitive
                w.line("predicates.add(cb.like(cb.lower(root.get(\"" + fieldName + "\")),")
                 .line("    \"%\" + filter.get" + cap + "().toLowerCase() + \"%\"));");
            } else if (fieldName.toLowerCase().endsWith("min")) {
                // Fields ending with Min → greaterThanOrEqualTo
                String baseField = fieldName.substring(0, fieldName.length() - 3);
                w.line("predicates.add(cb.greaterThanOrEqualTo(root.get(\"" + baseField + "\"), filter.get" + cap + "()));");
            } else if (fieldName.toLowerCase().endsWith("max")) {
                // Fields ending with Max → lessThanOrEqualTo
                String baseField = fieldName.substring(0, fieldName.length() - 3);
                w.line("predicates.add(cb.lessThanOrEqualTo(root.get(\"" + baseField + "\"), filter.get" + cap + "()));");
            } else if ("Enum".equalsIgnoreCase(f.getType())) {
                // Enum → equal com string (comparação do name())
                w.line("predicates.add(cb.equal(cb.toString(root.get(\"" + fieldName + "\")), filter.get" + cap + "()));");
            } else {
                // Outros tipos → equal
                w.line("predicates.add(cb.equal(root.get(\"" + fieldName + "\"), filter.get" + cap + "()));");
            }

            w.unindent().line("}");
        }

        w.blank()
         .line("return cb.and(predicates.toArray(new Predicate[0]));")
         .unindent().line("};")
         .unindent().line("}").blank();

        w.unindent().line("}");
        return w;
    }
}
