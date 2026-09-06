package io.springforge.generator;

import io.springforge.model.EntityDefinition;
import io.springforge.model.ForgeDefinition;
import io.springforge.model.TemplateConfig;
import io.springforge.model.VisualTemplateDefinition;
import io.springforge.util.NamingUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Renderiza templates customizados simples usando placeholders {{...}}.
 *
 * Exemplo de caminho:
 * .spring-forge/templates/{{packagePath}}/modules/{{entityName}}/{{EntityName}}Policy.java.tpl
 *
 * Placeholders principais:
 * {{projectName}}, {{basePackage}}, {{packagePath}}, {{EntityName}}, {{entityName}},
 * {{entitySnake}}, {{entityKebab}}, {{EntityPlural}}, {{entityPlural}}, {{entityPluralKebab}}.
 */
public class CustomTemplateGenerator extends AbstractGenerator {

    private final File projectBaseDir;

    public CustomTemplateGenerator(Log log, File projectBaseDir) {
        super(log);
        this.projectBaseDir = projectBaseDir;
    }

    @Override
    public void generate(ForgeDefinition def, EntityDefinition entity, File outDir) throws MojoExecutionException {
        TemplateConfig cfg = def.getProject().getTemplates();
        if (cfg == null || !cfg.isEnabled()) return;
        File baseOut = resolveOutput(projectBaseDir, outDir, cfg);
        renderVisualTemplates(def, entity, baseOut, cfg, false);
        File templateDir = resolve(projectBaseDir, cfg.getTemplateDir());
        if (!templateDir.exists()) {
            log.warn("  [TEMPLATES] Diretório não encontrado: " + templateDir.getPath());
            return;
        }
        renderTemplates(def, entity, templateDir, baseOut, cfg, false);
    }

    public void generateGlobalTemplates(ForgeDefinition def, File outDir) throws MojoExecutionException {
        TemplateConfig cfg = def.getProject().getTemplates();
        if (cfg == null || !cfg.isEnabled()) return;
        File baseOut = resolveOutput(projectBaseDir, outDir, cfg);
        renderVisualTemplates(def, null, baseOut, cfg, true);
        File templateDir = new File(resolve(projectBaseDir, cfg.getTemplateDir()), "_global");
        if (!templateDir.exists()) return;
        renderTemplates(def, null, templateDir, baseOut, cfg, true);
    }


    private void renderVisualTemplates(ForgeDefinition def, EntityDefinition entity, File baseOut,
                                       TemplateConfig cfg, boolean globalOnly) throws MojoExecutionException {
        if (cfg.getVisualTemplates() == null || cfg.getVisualTemplates().isEmpty()) return;
        for (VisualTemplateDefinition template : cfg.getVisualTemplates()) {
            if (template == null || !template.isEnabled()) continue;
            String scope = template.getScope() == null ? "entity" : template.getScope();
            if (globalOnly && !"global".equalsIgnoreCase(scope)) continue;
            if (!globalOnly && !"entity".equalsIgnoreCase(scope)) continue;
            if (!globalOnly && entity == null) continue;
            if (template.getPath() == null || template.getPath().isBlank()) continue;

            try {
                String renderedPath = render(template.getPath().replace('\\', '/'), def, entity);
                File target = new File(baseOut, renderedPath);
                if (target.exists() && !cfg.isOverwrite()) {
                    log.warn("  [VISUAL TEMPLATE SKIP] Arquivo já existe: " + target.getPath());
                    continue;
                }
                String content = render(template.getContent() == null ? "" : template.getContent(), def, entity);
                Files.createDirectories(target.getParentFile().toPath());
                Files.writeString(target.toPath(), content, StandardCharsets.UTF_8);
                log.info("  [VISUAL TEMPLATE] " + target.getPath());
            } catch (Exception e) {
                throw new MojoExecutionException("Erro ao renderizar template visual '" + template.getName() + "': " + e.getMessage(), e);
            }
        }
    }

    private void renderTemplates(ForgeDefinition def, EntityDefinition entity, File templateDir, File baseOut,
                                 TemplateConfig cfg, boolean globalOnly) throws MojoExecutionException {
        String suffix = cfg.getSuffix() == null || cfg.getSuffix().isBlank() ? ".tpl" : cfg.getSuffix();
        try (Stream<Path> paths = Files.walk(templateDir.toPath())) {
            for (Path template : paths.filter(Files::isRegularFile).toList()) {
                Path relPath = templateDir.toPath().relativize(template);
                if (!globalOnly && relPath.startsWith("_global")) continue;
                String renderedPath = render(relPath.toString().replace(File.separatorChar, '/'), def, entity);
                if (renderedPath.endsWith(suffix)) {
                    renderedPath = renderedPath.substring(0, renderedPath.length() - suffix.length());
                }
                File target = new File(baseOut, renderedPath);
                if (target.exists() && !cfg.isOverwrite()) {
                    log.warn("  [TEMPLATE SKIP] Arquivo já existe: " + target.getPath());
                    continue;
                }
                String content = Files.readString(template, StandardCharsets.UTF_8);
                content = render(content, def, entity);
                Files.createDirectories(target.getParentFile().toPath());
                Files.writeString(target.toPath(), content, StandardCharsets.UTF_8);
                log.info("  [TEMPLATE] " + target.getPath());
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Erro ao renderizar templates customizados: " + e.getMessage(), e);
        }
    }

    private String render(String input, ForgeDefinition def, EntityDefinition entity) {
        String out = input;
        String basePackage = def.getProject().getBasePackage();
        out = out.replace("{{projectName}}", safe(def.getProject().getName()))
                 .replace("{{basePackage}}", safe(basePackage))
                 .replace("{{packagePath}}", safe(basePackage).replace('.', '/'));
        if (entity != null) {
            String entityName = entity.getName();
            String camel = NamingUtils.toCamelCase(entityName);
            String plural = NamingUtils.toPlural(camel);
            out = out.replace("{{EntityName}}", entityName)
                     .replace("{{entityName}}", camel)
                     .replace("{{entitySnake}}", NamingUtils.toSnakeCase(entityName))
                     .replace("{{entityKebab}}", NamingUtils.toSnakeCase(entityName).replace("_", "-"))
                     .replace("{{EntityPlural}}", NamingUtils.toPascalCase(NamingUtils.toPlural(camel)))
                     .replace("{{entityPlural}}", plural)
                     .replace("{{entityPluralKebab}}", NamingUtils.toSnakeCase(NamingUtils.toPlural(entityName)).replace("_", "-"));
        }
        return out;
    }

    private File resolveOutput(File projectBaseDir, File outDir, TemplateConfig cfg) {
        if (cfg.getOutputDir() == null || cfg.getOutputDir().isBlank()) return outDir;
        return resolve(projectBaseDir, cfg.getOutputDir());
    }

    private File resolve(File base, String path) {
        File file = new File(path);
        return file.isAbsolute() ? file : new File(base, path);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
