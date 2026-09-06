package io.springforge.generator;

import io.springforge.model.EntityDefinition;
import io.springforge.model.FieldDefinition;
import io.springforge.model.ForgeDefinition;
import io.springforge.util.NamingUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.nio.file.Files;

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
            if (target.exists() && !shouldOverwrite(target)) {
                log.warn("  [SKIP] Arquivo já existe, pulando geração: " + target.getPath());
                return;
            }
            Files.createDirectories(target.getParentFile().toPath());
            writer.writeTo(target, packageName);
            log.info((target.exists() ? "  [GERADO] " : "  [GERADO] ") + target.getPath());
        } catch (Exception e) {
            throw new MojoExecutionException("Erro ao gravar " + target.getName() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Em diretórios gerados/temporários a saída deve ser determinística e substituível.
     * Em código-fonte manual, mantém a proteção histórica contra sobrescrita acidental.
     */
    private boolean shouldOverwrite(File target) throws java.io.IOException {
        String path = target.getCanonicalPath().replace(File.separatorChar, '/');
        return path.contains("/target/generated-")
            || path.contains("/target/spring-forge-")
            || path.contains("/forge-preview-")
            || path.contains("/generated-sources/")
            || path.contains("/generated-resources/");
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
    protected String entityPkg(ForgeDefinition def, EntityDefinition entity) {
        return entityPkg(def, entity.getName());
    }
    protected String entityPkg(ForgeDefinition def, String entityName) {
        return def.getProject().isModular()
            ? moduleBasePkg(def, entityName) + ".domain"
            : entityPkg(def);
    }

    protected String repoPkg(ForgeDefinition def) {
        return def.getProject().getBasePackage() + ".repository";
    }
    protected String repoPkg(ForgeDefinition def, EntityDefinition entity) {
        return repoPkg(def, entity.getName());
    }
    protected String repoPkg(ForgeDefinition def, String entityName) {
        return def.getProject().isModular()
            ? moduleBasePkg(def, entityName) + ".repository"
            : repoPkg(def);
    }

    protected String servicePkg(ForgeDefinition def) {
        return def.getProject().getBasePackage() + ".service";
    }
    protected String servicePkg(ForgeDefinition def, EntityDefinition entity) {
        return servicePkg(def, entity.getName());
    }
    protected String servicePkg(ForgeDefinition def, String entityName) {
        return def.getProject().isModular()
            ? moduleBasePkg(def, entityName) + ".service"
            : servicePkg(def);
    }

    protected String serviceImplPkg(ForgeDefinition def) {
        return def.getProject().getBasePackage() + ".service.impl";
    }
    protected String serviceImplPkg(ForgeDefinition def, EntityDefinition entity) {
        return serviceImplPkg(def, entity.getName());
    }
    protected String serviceImplPkg(ForgeDefinition def, String entityName) {
        return def.getProject().isModular()
            ? moduleBasePkg(def, entityName) + ".service.impl"
            : serviceImplPkg(def);
    }

    protected String controllerPkg(ForgeDefinition def) {
        return def.getProject().getBasePackage() + ".controller";
    }
    protected String controllerPkg(ForgeDefinition def, EntityDefinition entity) {
        return controllerPkg(def, entity.getName());
    }
    protected String controllerPkg(ForgeDefinition def, String entityName) {
        return def.getProject().isModular()
            ? moduleBasePkg(def, entityName) + ".web"
            : controllerPkg(def);
    }

    protected String dtoPkg(ForgeDefinition def) {
        return def.getProject().getBasePackage() + ".dto";
    }
    protected String dtoPkg(ForgeDefinition def, EntityDefinition entity) {
        return dtoPkg(def, entity.getName());
    }
    protected String dtoPkg(ForgeDefinition def, String entityName) {
        return def.getProject().isModular()
            ? moduleBasePkg(def, entityName) + ".dto"
            : dtoPkg(def);
    }

    protected String mapperPkg(ForgeDefinition def) {
        return def.getProject().getBasePackage() + ".mapper";
    }
    protected String mapperPkg(ForgeDefinition def, EntityDefinition entity) {
        return mapperPkg(def, entity.getName());
    }
    protected String mapperPkg(ForgeDefinition def, String entityName) {
        return def.getProject().isModular()
            ? moduleBasePkg(def, entityName) + ".mapper"
            : mapperPkg(def);
    }

    protected String exceptionPkg(ForgeDefinition def) {
        return def.getProject().getBasePackage() + ".exception";
    }
    protected String exceptionPkg(ForgeDefinition def, EntityDefinition entity) {
        return exceptionPkg(def, entity.getName());
    }
    protected String exceptionPkg(ForgeDefinition def, String entityName) {
        return def.getProject().isModular()
            ? moduleBasePkg(def, entityName) + ".exception"
            : exceptionPkg(def);
    }

    protected String specificationPkg(ForgeDefinition def) {
        return def.getProject().getBasePackage() + ".specification";
    }
    protected String specificationPkg(ForgeDefinition def, EntityDefinition entity) {
        return def.getProject().isModular()
            ? moduleBasePkg(def, entity.getName()) + ".specification"
            : specificationPkg(def);
    }

    protected String moduleBasePkg(ForgeDefinition def, String entityName) {
        return def.getProject().getBasePackage() + ".modules." + NamingUtils.toSnakeCase(entityName).replace("_", "");
    }

    protected String javaString(String value) {
        if (value == null) return "";
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n");
    }

    protected String safeJavadoc(String value) {
        if (value == null) return "";
        return value.replace("*/", "*&#47;");
    }

    // ===================== Hexagonal Ports & Adapters — Helpers de pacote =====================

    /** Domínio puro (POJO sem JPA). Ex: com.myapp.domain.model */
    protected String hexDomainModelPkg(ForgeDefinition def) {
        return def.getProject().getBasePackage() + ".domain.model";
    }

    /** Port de entrada (use-case interface). Ex: com.myapp.application.port.in */
    protected String hexPortInPkg(ForgeDefinition def) {
        return def.getProject().getBasePackage() + ".application.port.in";
    }

    /** Port de saída (repository interface). Ex: com.myapp.application.port.out */
    protected String hexPortOutPkg(ForgeDefinition def) {
        return def.getProject().getBasePackage() + ".application.port.out";
    }

    /** Serviço de aplicação (use-case impl). Ex: com.myapp.application.service */
    protected String hexAppServicePkg(ForgeDefinition def) {
        return def.getProject().getBasePackage() + ".application.service";
    }

    /** Adapter REST (entrada). Ex: com.myapp.adapter.in.rest */
    protected String hexRestAdapterPkg(ForgeDefinition def) {
        return def.getProject().getBasePackage() + ".adapter.in.rest";
    }

    /** Adapter de persistência JPA (saída). Ex: com.myapp.adapter.out.persistence */
    protected String hexPersistenceAdapterPkg(ForgeDefinition def) {
        return def.getProject().getBasePackage() + ".adapter.out.persistence";
    }

    /** DTO de entrada/saída no contexto hexagonal (reutiliza o pacote dto existente). */
    protected String hexDtoPkg(ForgeDefinition def) {
        return def.getProject().getBasePackage() + ".dto";
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
