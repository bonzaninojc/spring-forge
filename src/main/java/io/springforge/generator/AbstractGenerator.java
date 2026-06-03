package io.springforge.generator;

import io.springforge.model.EntityDefinition;
import io.springforge.model.FieldDefinition;
import io.springforge.model.ForgeDefinition;
import io.springforge.util.NamingUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;

/**
 * Base para todos os geradores.
 * Sem dependências externas — usa apenas CodeWriter (StringBuilder).
 */
public abstract class AbstractGenerator {

    protected final Log log;

    protected AbstractGenerator(Log log) {
        this.log = log;
    }

    /** Gera todos os arquivos para uma entidade */
    public abstract void generate(
        ForgeDefinition definition,
        EntityDefinition entity,
        File outputBaseDir
    ) throws MojoExecutionException;

    // ===================== Helpers de path =====================

    protected File javaFile(File baseDir, String packageName, String className) {
        String path = packageName.replace('.', '/');
        return new File(baseDir, path + "/" + className + ".java");
    }

    protected void writeFile(CodeWriter writer, File target, String packageName) throws MojoExecutionException {
        try {
            if (target.exists()) {
                log.warn("  [SKIP] Arquivo já existe, pulando geração: " + target.getPath());
                return;
            }
            writer.writeTo(target, packageName);
            log.info("  [GERADO] " + target.getPath());
        } catch (Exception e) {
            throw new MojoExecutionException("Erro ao gravar " + target.getName() + ": " + e.getMessage(), e);
        }
    }

    // ===================== Helpers de tipo Java =====================

    protected String javaType(FieldDefinition f) {
        return NamingUtils.toJavaType(f.getType());
    }

    /**
     * Tipo Java real para campo Enum: usa o nome do Enum (ex: "ProdutoStatus").
     * Para todos os outros, usa o tipo Java padrão.
     */
    protected String resolvedType(FieldDefinition f) {
        if ("Enum".equalsIgnoreCase(f.getType())) {
            return NamingUtils.toPascalCase(f.getName());
        }
        return NamingUtils.toJavaType(f.getType());
    }

    /**
     * Nome do Enum com prefixo da entidade para evitar colisão.
     * Ex: entidade "Produto", campo "status" → "ProdutoStatus"
     */
    protected String enumName(EntityDefinition entity, FieldDefinition f) {
        return entity.getName() + NamingUtils.toPascalCase(f.getName());
    }

    /** Getter: "getName" */
    protected String getter(FieldDefinition f) {
        return "get" + NamingUtils.toPascalCase(f.getName());
    }

    /** Setter: "setName" */
    protected String setter(FieldDefinition f) {
        return "set" + NamingUtils.toPascalCase(f.getName());
    }

    // ===================== Helpers de pacote =====================

    protected String entityPkg(ForgeDefinition def) {
        return def.getProject().getBasePackage() + ".entity";
    }
    protected String repoPkg(ForgeDefinition def) {
        return def.getProject().getBasePackage() + ".repository";
    }
    protected String servicePkg(ForgeDefinition def) {
        return def.getProject().getBasePackage() + ".service";
    }
    protected String serviceImplPkg(ForgeDefinition def) {
        return def.getProject().getBasePackage() + ".service.impl";
    }
    protected String controllerPkg(ForgeDefinition def) {
        return def.getProject().getBasePackage() + ".controller";
    }
    protected String dtoPkg(ForgeDefinition def) {
        return def.getProject().getBasePackage() + ".dto";
    }
    protected String mapperPkg(ForgeDefinition def) {
        return def.getProject().getBasePackage() + ".mapper";
    }
    protected String exceptionPkg(ForgeDefinition def) {
        return def.getProject().getBasePackage() + ".exception";
    }

    // ===================== Helpers comuns de escrita =====================

    /**
     * Escreve bloco de getter + setter para um campo.
     */
    protected void writeGetterSetter(CodeWriter w, FieldDefinition f, String type) {
        String cap = capitalize(f.getName());
        w.line("public " + type + " get" + cap + "() { return " + f.getName() + "; }")
         .line("public void set" + cap + "(" + type + " " + f.getName() + ") { this." + f.getName() + " = " + f.getName() + "; }")
         .blank();
    }

    /**
     * Escreve apenas o getter para um campo (usar em classes com campos final).
     */
    protected void writeGetter(CodeWriter w, FieldDefinition f, String type) {
        String cap = capitalize(f.getName());
        w.line("public " + type + " get" + cap + "() { return " + f.getName() + "; }")
         .blank();
    }

    /**
     * Escreve imports de tipos Java para uma lista de campos.
     */
    protected void addFieldImports(CodeWriter w, Iterable<FieldDefinition> fields) {
        for (FieldDefinition f : fields) {
            String imp = NamingUtils.toJavaImport(f.getType());
            if (imp != null) w.imp(imp);
        }
    }

    /** Capitaliza primeira letra preservando o resto (confirmedAt → ConfirmedAt) */
    protected String capitalize(String name) {
        if (name == null || name.isEmpty()) return name;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
