package io.springforge.generator;

import io.springforge.model.ActionDefinition;
import io.springforge.model.EntityDefinition;
import io.springforge.model.FieldDefinition;
import io.springforge.model.ForgeDefinition;
import io.springforge.util.NamingUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.util.List;

public class DtoGenerator extends AbstractGenerator {

    public DtoGenerator(Log log) { super(log); }

    @Override
    public void generate(ForgeDefinition def, EntityDefinition entity, File outDir) throws MojoExecutionException {
        if (!entity.shouldGenerate("dto")) return;

        String pkg = dtoPkg(def);

        // DTOs padrão CRUDL
        writeFile(buildRequestDto(def, entity, pkg),
                  javaFile(outDir, pkg, entity.getName() + "RequestDTO"), pkg);

        writeFile(buildResponseDto(def, entity, pkg),
                  javaFile(outDir, pkg, entity.getName() + "ResponseDTO"), pkg);

        // DTOs de cada action
        for (ActionDefinition action : entity.getActions()) {
            if (action.hasRequest()) {
                writeFile(
                    buildActionDto(pkg, action.getRequestDtoName(), action.getRequest(),
                        "DTO de entrada para a action '" + action.getName() + "' de " + entity.getName() + "."),
                    javaFile(outDir, pkg, action.getRequestDtoName()), pkg
                );
            }
            if (action.hasResponse()) {
                writeFile(
                    buildActionDto(pkg, action.getResponseDtoName(), action.getResponse(),
                        "DTO de saída para a action '" + action.getName() + "' de " + entity.getName() + "."),
                    javaFile(outDir, pkg, action.getResponseDtoName()), pkg
                );
            }
        }
    }

    // ── RequestDTO padrão ────────────────────────────────────────────────────────

    private CodeWriter buildRequestDto(ForgeDefinition def, EntityDefinition entity, String pkg) {
        CodeWriter w = new CodeWriter();
        w.imp("jakarta.validation.constraints.*");
        addFieldImports(w, entity.getFields());

        w.javadoc("DTO de entrada (criação/atualização) para " + entity.getName() + ".\nGerado pelo Spring Forge.");
        w.line("public class " + entity.getName() + "RequestDTO {").blank();
        w.indent();

        for (FieldDefinition f : entity.getFields()) {
            if (!f.isInRequest()) continue;
            writeFieldWithValidations(w, f, false);
        }

        w.unindent();
        w.line("    // ── Getters & Setters ──────────────────────────────────────────────────────────").blank();
        w.indent();

        for (FieldDefinition f : entity.getFields()) {
            if (!f.isInRequest()) continue;
            String type = "Enum".equalsIgnoreCase(f.getType()) ? "String" : NamingUtils.toJavaType(f.getType());
            writeGetterSetter(w, f, type);
        }

        w.unindent().line("}");
        return w;
    }

    // ── ResponseDTO padrão ───────────────────────────────────────────────────────

    private CodeWriter buildResponseDto(ForgeDefinition def, EntityDefinition entity, String pkg) {
        CodeWriter w = new CodeWriter();
        addFieldImports(w, entity.getFields());
        if (entity.isAuditable()) w.imp("java.time.LocalDateTime");

        w.javadoc("DTO de saída para " + entity.getName() + ".\nGerado pelo Spring Forge.");
        w.line("public class " + entity.getName() + "ResponseDTO {").blank();
        w.indent();

        w.line("private Long id;").blank();

        for (FieldDefinition f : entity.getFields()) {
            if (!f.isInResponse()) continue;
            // Enum vira String no response (serializado pelo name())
            String type = "Enum".equalsIgnoreCase(f.getType()) ? "String" : NamingUtils.toJavaType(f.getType());
            w.line("private " + type + " " + f.getName() + ";").blank();
        }

        if (entity.isAuditable()) {
            w.line("private LocalDateTime createdAt;").blank();
            w.line("private LocalDateTime updatedAt;").blank();
        }

        w.unindent();
        w.line("    // ── Getters & Setters ──────────────────────────────────────────────────────────").blank();
        w.indent();

        w.line("public Long getId() { return id; }")
         .line("public void setId(Long id) { this.id = id; }")
         .blank();

        for (FieldDefinition f : entity.getFields()) {
            if (!f.isInResponse()) continue;
            String type = "Enum".equalsIgnoreCase(f.getType()) ? "String" : NamingUtils.toJavaType(f.getType());
            writeGetterSetter(w, f, type);
        }

        if (entity.isAuditable()) {
            w.line("public LocalDateTime getCreatedAt() { return createdAt; }")
             .line("public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }")
             .blank()
             .line("public LocalDateTime getUpdatedAt() { return updatedAt; }")
             .line("public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }")
             .blank();
        }

        w.unindent().line("}");
        return w;
    }

    // ── DTO genérico de action ───────────────────────────────────────────────────

    private CodeWriter buildActionDto(String pkg, String className,
                                      List<FieldDefinition> fields, String javadoc) {
        CodeWriter w = new CodeWriter();
        w.imp("jakarta.validation.constraints.*");
        addFieldImports(w, fields);

        w.javadoc(javadoc + "\nGerado pelo Spring Forge — implemente a lógica no ServiceImpl.");
        w.line("public class " + className + " {").blank();
        w.indent();

        for (FieldDefinition f : fields) {
            writeFieldWithValidations(w, f, false);
        }

        w.unindent();
        w.line("    // ── Getters & Setters ──────────────────────────────────────────────────────────").blank();
        w.indent();

        for (FieldDefinition f : fields) {
            String type = "Enum".equalsIgnoreCase(f.getType()) ? "String" : NamingUtils.toJavaType(f.getType());
            writeGetterSetter(w, f, type);
        }

        w.unindent().line("}");
        return w;
    }

    // ── Utilitário: campo com anotações de validação ─────────────────────────────

    private void writeFieldWithValidations(CodeWriter w, FieldDefinition f, boolean isEnum) {
        if (f.isRequired()) {
            if ("String".equalsIgnoreCase(f.getType())) {
                w.line("@NotBlank(message = \"" + f.getName() + " é obrigatório\")");
            } else {
                w.line("@NotNull(message = \"" + f.getName() + " é obrigatório\")");
            }
        }
        if (f.getMaxLength() != null || f.getMinLength() != null) {
            StringBuilder size = new StringBuilder("@Size(");
            if (f.getMinLength() != null) size.append("min = ").append(f.getMinLength()).append(", ");
            if (f.getMaxLength() != null) size.append("max = ").append(f.getMaxLength()).append(", ");
            size.append("message = \"Tamanho inválido para " + f.getName() + "\")");
            w.line(size.toString());
        }
        if (f.getValidations() != null) {
            f.getValidations().forEach(w::line);
        }

        if ("Enum".equalsIgnoreCase(f.getType())) {
            w.line("private String " + f.getName() + ";").blank();
        } else {
            w.line("private " + NamingUtils.toJavaType(f.getType()) + " " + f.getName() + ";").blank();
        }
    }
}
