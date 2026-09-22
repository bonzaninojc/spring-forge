package io.springforge.generator;

import io.springforge.model.CrudDefinition;
import io.springforge.model.EntityDefinition;
import io.springforge.model.FilterDefinition;
import io.springforge.model.ForgeDefinition;
import io.springforge.model.RelationDefinition;
import io.springforge.util.NamingUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Gera FilterDTO + Specification dinâmica com operadores configuráveis.
 *
 * Agora também suporta:
 * - filtros declarados em entity.crud.filterable;
 * - campos aninhados por relacionamento, ex: category.name;
 * - whitelist de ordenação via entity.crud.sortable;
 * - sanitização de Pageable para limitar page size e ignorar sorts não permitidos.
 */
public class FilterGenerator extends AbstractGenerator {

    public FilterGenerator(Log log) { super(log); }

    @Override
    public void generate(ForgeDefinition def, EntityDefinition entity, File outDir) throws MojoExecutionException {
        if (!entity.hasFilters() && !entity.hasAdvancedCrud()) return;

        String name = entity.getName();

        if (entity.hasFilters()) {
            String dtoPkg = dtoPkg(def, entity);
            writeFile(buildFilterDTO(def, entity, dtoPkg),
                      javaFile(outDir, dtoPkg, name + "FilterDTO"), dtoPkg);
        }

        String specPkg = specificationPkg(def, entity);
        writeFile(buildSpecification(def, entity, specPkg),
                  javaFile(outDir, specPkg, name + "Specification"), specPkg);
    }

    private CodeWriter buildFilterDTO(ForgeDefinition def, EntityDefinition entity, String pkg) {
        String name = entity.getName();
        CodeWriter w = new CodeWriter();

        w.imp("java.util.List");
        if (def.getProject().isSesiLaboral()) {
            w.imp("org.springframework.data.domain.PageRequest")
             .imp("org.springframework.data.domain.Pageable")
             .imp("org.springframework.data.domain.Sort");
        }

        for (FilterDefinition f : entity.getEffectiveFilters()) {
            String imp = NamingUtils.toJavaImport(f.getType());
            if (imp != null) w.imp(imp);
        }

        w.javadoc("DTO de filtros para busca de " + name + ".\nGerado pelo Spring Forge.");
        w.line("public class " + name + "FilterDTO {").blank();
        w.indent();

        for (FilterDefinition f : entity.getEffectiveFilters()) {
            String op = f.resolveOperator();
            String type = resolveFilterFieldType(f, op);
            w.line("private " + type + " " + f.getName() + ";");
        }
        if (def.getProject().isSesiLaboral()) {
            CrudDefinition crud = entity.getCrud();
            int defaultSize = crud != null ? Math.max(1, crud.getDefaultPageSize()) : 20;
            String defaultSort = crud != null && crud.getDefaultSort() != null && !crud.getDefaultSort().isBlank()
                ? crud.getDefaultSort() : "id";
            w.line("private int page = 0;")
             .line("private int size = " + defaultSize + ";")
             .line("private String sortBy = \"" + javaString(defaultSort) + "\";")
             .line("private boolean ascending = true;");
        }
        w.blank();

        for (FilterDefinition f : entity.getEffectiveFilters()) {
            String op = f.resolveOperator();
            String type = resolveFilterFieldType(f, op);
            String cap = NamingUtils.toPascalCase(f.getName());
            w.line("public " + type + " get" + cap + "() { return " + f.getName() + "; }")
             .line("public void set" + cap + "(" + type + " " + f.getName() + ") { this." + f.getName() + " = " + f.getName() + "; }");
        }

        if (def.getProject().isSesiLaboral()) {
            w.blank()
             .line("public int getPage() { return page; }")
             .line("public void setPage(int page) { this.page = page; }")
             .line("public int getSize() { return size; }")
             .line("public void setSize(int size) { this.size = size; }")
             .line("public String getSortBy() { return sortBy; }")
             .line("public void setSortBy(String sortBy) { this.sortBy = sortBy; }")
             .line("public boolean isAscending() { return ascending; }")
             .line("public void setAscending(boolean ascending) { this.ascending = ascending; }")
             .blank()
             .line("public Pageable toPageable() {")
             .indent()
             .line("String property = sortBy == null || sortBy.isBlank() ? \"id\" : sortBy;")
             .line("Sort.Direction direction = ascending ? Sort.Direction.ASC : Sort.Direction.DESC;")
             .line("return PageRequest.of(Math.max(page, 0), Math.max(size, 1), Sort.by(direction, property));")
             .unindent().line("}");
        }

        w.unindent().line("}");
        return w;
    }

    private CodeWriter buildSpecification(ForgeDefinition def, EntityDefinition entity, String pkg) {
        String name = entity.getName();
        String entPkg = entityPkg(def, entity);
        String dtoPkg = dtoPkg(def, entity);
        CodeWriter w = new CodeWriter();

        w.imp("jakarta.persistence.criteria.Expression")
         .imp("jakarta.persistence.criteria.From")
         .imp("jakarta.persistence.criteria.JoinType")
         .imp("jakarta.persistence.criteria.Path")
         .imp("jakarta.persistence.criteria.Predicate")
         .imp("jakarta.persistence.criteria.Root")
         .imp("org.springframework.data.domain.PageRequest")
         .imp("org.springframework.data.domain.Pageable")
         .imp("org.springframework.data.domain.Sort")
         .imp("org.springframework.data.jpa.domain.Specification")
         .imp("java.util.ArrayList")
         .imp("java.util.List")
         .imp("java.util.Set")
         .imp(entPkg + "." + name);

        if (entity.hasFilters()) {
            w.imp(dtoPkg + "." + name + "FilterDTO");
        }

        w.javadoc("Specification de busca dinâmica para " + name + ".\n"
                + "Suporta filtros simples e aninhados como category.name, além de whitelist de ordenação.\n"
                + "Gerado pelo Spring Forge.");
        w.line("public class " + name + "Specification {").blank();
        w.indent();

        CrudDefinition crud = entity.getCrud();
        int defaultPageSize = crud != null ? Math.max(1, crud.getDefaultPageSize()) : 20;
        int maxPageSize = crud != null ? Math.max(1, crud.getMaxPageSize()) : 100;
        String defaultSort = crud != null && crud.getDefaultSort() != null && !crud.getDefaultSort().isBlank() ? crud.getDefaultSort() : "id";
        String defaultDirection = crud != null && crud.getDefaultDirection() != null ? crud.getDefaultDirection().toUpperCase() : "ASC";
        if (!"DESC".equals(defaultDirection)) defaultDirection = "ASC";

        w.line("private static final int DEFAULT_PAGE_SIZE = " + defaultPageSize + ";")
         .line("private static final int MAX_PAGE_SIZE = " + maxPageSize + ";")
         .line("private static final String DEFAULT_SORT = \"" + javaString(defaultSort) + "\";")
         .line("private static final Sort.Direction DEFAULT_DIRECTION = Sort.Direction." + defaultDirection + ";")
         .line("private static final Set<String> ALLOWED_SORTS = Set.of(" + sortSetLiteral(entity) + ");")
         .blank();

        w.line("private " + name + "Specification() {}").blank();

        if (entity.hasFilters()) {
            w.line("public static Specification<" + name + "> fromFilter(" + name + "FilterDTO filter) {")
             .indent()
             .line("return (root, query, cb) -> {")
             .indent()
             .line("List<Predicate> predicates = new ArrayList<>();")
             .line("if (filter == null) return cb.and(predicates.toArray(new Predicate[0]));")
             .blank();

            for (FilterDefinition f : entity.getEffectiveFilters()) {
                String cap = NamingUtils.toPascalCase(f.getName());
                String op = f.resolveOperator();
                String targetField = f.resolveTargetField();

                if ("IS_NULL".equals(op)) {
                    w.line("if (Boolean.TRUE.equals(filter.get" + cap + "())) {");
                    w.indent();
                    w.line("predicates.add(cb.isNull(path(root, \"" + javaString(targetField) + "\")));");
                    w.unindent().line("}");
                } else if ("IS_NOT_NULL".equals(op)) {
                    w.line("if (Boolean.TRUE.equals(filter.get" + cap + "())) {");
                    w.indent();
                    w.line("predicates.add(cb.isNotNull(path(root, \"" + javaString(targetField) + "\")));");
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
        }

        w.line("public static Pageable sanitizePageable(Pageable pageable) {")
         .indent()
         .line("if (pageable == null || pageable.isUnpaged()) {")
         .indent()
         .line("return PageRequest.of(0, DEFAULT_PAGE_SIZE, Sort.by(DEFAULT_DIRECTION, DEFAULT_SORT));")
         .unindent().line("}")
         .line("int size = Math.min(Math.max(pageable.getPageSize(), 1), MAX_PAGE_SIZE);")
         .line("List<Sort.Order> accepted = pageable.getSort().stream()")
         .line("    .filter(order -> ALLOWED_SORTS.contains(order.getProperty()))")
         .line("    .toList();")
         .line("Sort sort = accepted.isEmpty() ? Sort.by(DEFAULT_DIRECTION, DEFAULT_SORT) : Sort.by(accepted);")
         .line("return PageRequest.of(pageable.getPageNumber(), size, sort);")
         .unindent().line("}").blank();

        w.line("private static Path<?> path(Root<" + name + "> root, String fieldPath) {")
         .indent()
         .line("String[] parts = fieldPath.split(\"\\\\.\");")
         .line("if (parts.length == 1) return root.get(parts[0]);")
         .line("From<?, ?> from = root;")
         .line("for (int i = 0; i < parts.length - 1; i++) {")
         .indent()
         .line("from = from.join(parts[i], JoinType.LEFT);")
         .unindent().line("}")
         .line("return from.get(parts[parts.length - 1]);")
         .unindent().line("}").blank();

        w.line("@SuppressWarnings({\"unchecked\", \"rawtypes\"})")
         .line("private static Expression<Comparable> comparablePath(Root<" + name + "> root, String fieldPath) {")
         .indent()
         .line("return (Expression<Comparable>) path(root, fieldPath);")
         .unindent().line("}").blank();

        w.unindent().line("}");
        return w;
    }

    private void writeOperatorPredicate(CodeWriter w, FilterDefinition f, String cap, String op, String targetField) {
        String path = "path(root, \"" + javaString(targetField) + "\")";
        String getter = "filter.get" + cap + "()";
        switch (op) {
            case "EQUALS" -> {
                if ("Enum".equalsIgnoreCase(f.getType())) {
                    w.line("predicates.add(cb.equal(" + path + ".as(String.class), " + getter + ")); ");
                } else {
                    w.line("predicates.add(cb.equal(" + path + ", " + getter + ")); ");
                }
            }
            case "NOT_EQUALS" -> w.line("predicates.add(cb.notEqual(" + path + ", " + getter + ")); ");
            case "CONTAINS" -> w.line("predicates.add(cb.like(cb.lower(" + path + ".as(String.class)),")
                .line("    \"%\" + " + getter + ".toLowerCase() + \"%\"));");
            case "STARTS_WITH" -> w.line("predicates.add(cb.like(cb.lower(" + path + ".as(String.class)),")
                .line("    " + getter + ".toLowerCase() + \"%\"));");
            case "ENDS_WITH" -> w.line("predicates.add(cb.like(cb.lower(" + path + ".as(String.class)),")
                .line("    \"%\" + " + getter + ".toLowerCase()));");
            case "GREATER_THAN" -> w.line("predicates.add(cb.greaterThan(comparablePath(root, \"" + javaString(targetField) + "\"), (Comparable) " + getter + ")); ");
            case "GREATER_THAN_OR_EQUAL" -> w.line("predicates.add(cb.greaterThanOrEqualTo(comparablePath(root, \"" + javaString(targetField) + "\"), (Comparable) " + getter + ")); ");
            case "LESS_THAN" -> w.line("predicates.add(cb.lessThan(comparablePath(root, \"" + javaString(targetField) + "\"), (Comparable) " + getter + ")); ");
            case "LESS_THAN_OR_EQUAL" -> w.line("predicates.add(cb.lessThanOrEqualTo(comparablePath(root, \"" + javaString(targetField) + "\"), (Comparable) " + getter + ")); ");
            case "IN" -> w.line("if (!" + getter + ".isEmpty()) {")
                .indent()
                .line("predicates.add(" + path + ".in(" + getter + ")); ")
                .unindent().line("}");
            case "BETWEEN" -> w.line("if (" + getter + ".size() == 2) {")
                .indent()
                .line("predicates.add(cb.between(comparablePath(root, \"" + javaString(targetField) + "\"),")
                .line("    (Comparable) " + getter + ".get(0), (Comparable) " + getter + ".get(1))); ")
                .unindent().line("}");
            default -> w.line("predicates.add(cb.equal(" + path + ", " + getter + ")); ");
        }
    }

    private String resolveFilterFieldType(FilterDefinition f, String op) {
        String baseType = "Enum".equalsIgnoreCase(f.getType()) ? "String" : NamingUtils.toJavaType(f.getType());
        if ("IS_NULL".equals(op) || "IS_NOT_NULL".equals(op)) return "Boolean";
        if ("IN".equals(op) || "BETWEEN".equals(op)) return "List<" + baseType + ">";
        return baseType;
    }

    private String sortSetLiteral(EntityDefinition entity) {
        Set<String> fields = resolveSortableFields(entity);
        return fields.stream().map(v -> "\"" + javaString(v) + "\"").reduce((a, b) -> a + ", " + b).orElse("\"id\"");
    }

    private Set<String> resolveSortableFields(EntityDefinition entity) {
        CrudDefinition crud = entity.getCrud();
        Set<String> fields = new LinkedHashSet<>();
        if (crud != null && crud.getSortable() != null && !crud.getSortable().isEmpty()) {
            fields.addAll(crud.getSortable());
        } else {
            fields.add("id");
            entity.getFields().forEach(f -> fields.add(f.getName()));
            for (RelationDefinition r : entity.getRelations()) {
                if ("ManyToOne".equals(r.getType())) {
                    fields.add(r.getFieldName() + ".id");
                }
            }
        }
        String defaultSort = crud != null ? crud.getDefaultSort() : null;
        if (defaultSort != null && !defaultSort.isBlank()) fields.add(defaultSort);
        fields.add("id");
        return fields;
    }
}
