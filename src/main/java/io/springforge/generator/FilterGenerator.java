package io.springforge.generator;

import io.springforge.model.EntityDefinition;
import io.springforge.model.FilterDefinition;
import io.springforge.model.ForgeDefinition;
import io.springforge.util.NamingUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;

/**
 * Gera FilterDTO + Specification dinâmica com operadores configuráveis.
 *
 * Operadores suportados:
 *   EQUALS, NOT_EQUALS, CONTAINS, STARTS_WITH, ENDS_WITH,
 *   GREATER_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN, LESS_THAN_OR_EQUAL,
 *   IN, BETWEEN, IS_NULL, IS_NOT_NULL
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

        w.imp("java.util.List");

        for (FilterDefinition f : entity.getFilters()) {
            String imp = NamingUtils.toJavaImport(f.getType());
            if (imp != null) w.imp(imp);
        }

        w.javadoc("DTO de filtros para busca de " + name + ".\nGerado pelo Spring Forge.");
        w.line("public class " + name + "FilterDTO {").blank();
        w.indent();

        for (FilterDefinition f : entity.getFilters()) {
            String op = f.resolveOperator();
            String type = resolveFilterFieldType(f, op);
            w.line("private " + type + " " + f.getName() + ";");
        }
        w.blank();

        for (FilterDefinition f : entity.getFilters()) {
            String op = f.resolveOperator();
            String type = resolveFilterFieldType(f, op);
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

        w.javadoc("Specification de busca dinâmica para " + name + ".\nOperadores configuráveis por campo.\nGerado pelo Spring Forge.");
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
            String op = f.resolveOperator();
            String targetField = f.resolveTargetField();

            if ("IS_NULL".equals(op)) {
                // IS_NULL não depende de valor do filtro — usa Boolean como flag
                w.line("if (Boolean.TRUE.equals(filter.get" + cap + "())) {");
                w.indent();
                w.line("predicates.add(cb.isNull(root.get(\"" + targetField + "\")));");
                w.unindent().line("}");
            } else if ("IS_NOT_NULL".equals(op)) {
                w.line("if (Boolean.TRUE.equals(filter.get" + cap + "())) {");
                w.indent();
                w.line("predicates.add(cb.isNotNull(root.get(\"" + targetField + "\")));");
                w.unindent().line("}");
            } else {
                w.line("if (filter.get" + cap + "() != null) {");
                w.indent();
                writeOperatorPredicate(w, f, cap, op, targetField);
                w.unindent().line("}");
            }
        }

        w.blank()
         .line("return cb.and(predicates.toArray(new Predicate[0]));")
         .unindent().line("};")
         .unindent().line("}").blank();

        w.unindent().line("}");
        return w;
    }

    private void writeOperatorPredicate(CodeWriter w, FilterDefinition f, String cap, String op, String targetField) {
        switch (op) {
            case "EQUALS":
                if ("Enum".equalsIgnoreCase(f.getType())) {
                    w.line("predicates.add(cb.equal(cb.toString(root.get(\"" + targetField + "\")), filter.get" + cap + "()));");
                } else {
                    w.line("predicates.add(cb.equal(root.get(\"" + targetField + "\"), filter.get" + cap + "()));");
                }
                break;
            case "NOT_EQUALS":
                w.line("predicates.add(cb.notEqual(root.get(\"" + targetField + "\"), filter.get" + cap + "()));");
                break;
            case "CONTAINS":
                w.line("predicates.add(cb.like(cb.lower(root.get(\"" + targetField + "\")),")
                 .line("    \"%\" + filter.get" + cap + "().toLowerCase() + \"%\"));");
                break;
            case "STARTS_WITH":
                w.line("predicates.add(cb.like(cb.lower(root.get(\"" + targetField + "\")),")
                 .line("    filter.get" + cap + "().toLowerCase() + \"%\"));");
                break;
            case "ENDS_WITH":
                w.line("predicates.add(cb.like(cb.lower(root.get(\"" + targetField + "\")),")
                 .line("    \"%\" + filter.get" + cap + "().toLowerCase()));");
                break;
            case "GREATER_THAN":
                w.line("predicates.add(cb.greaterThan(root.get(\"" + targetField + "\"), filter.get" + cap + "()));");
                break;
            case "GREATER_THAN_OR_EQUAL":
                w.line("predicates.add(cb.greaterThanOrEqualTo(root.get(\"" + targetField + "\"), filter.get" + cap + "()));");
                break;
            case "LESS_THAN":
                w.line("predicates.add(cb.lessThan(root.get(\"" + targetField + "\"), filter.get" + cap + "()));");
                break;
            case "LESS_THAN_OR_EQUAL":
                w.line("predicates.add(cb.lessThanOrEqualTo(root.get(\"" + targetField + "\"), filter.get" + cap + "()));");
                break;
            case "IN":
                w.line("if (!filter.get" + cap + "().isEmpty()) {")
                 .indent()
                 .line("predicates.add(root.get(\"" + targetField + "\").in(filter.get" + cap + "()));")
                 .unindent().line("}");
                break;
            case "BETWEEN":
                // BETWEEN espera que o campo no DTO seja uma List com exatamente 2 elementos
                w.line("if (filter.get" + cap + "().size() == 2) {")
                 .indent()
                 .line("predicates.add(cb.between(root.get(\"" + targetField + "\"),")
                 .line("    filter.get" + cap + "().get(0), filter.get" + cap + "().get(1)));")
                 .unindent().line("}");
                break;
            default:
                w.line("predicates.add(cb.equal(root.get(\"" + targetField + "\"), filter.get" + cap + "()));");
                break;
        }
    }

    /**
     * Resolve o tipo do campo no FilterDTO baseado no operador.
     * IN e BETWEEN usam List<T> ao invés do tipo simples.
     */
    private String resolveFilterFieldType(FilterDefinition f, String op) {
        String baseType = "Enum".equalsIgnoreCase(f.getType()) ? "String" : NamingUtils.toJavaType(f.getType());
        if ("IS_NULL".equals(op) || "IS_NOT_NULL".equals(op)) {
            return "Boolean";
        }
        if ("IN".equals(op) || "BETWEEN".equals(op)) {
            return "List<" + baseType + ">";
        }
        return baseType;
    }
}
