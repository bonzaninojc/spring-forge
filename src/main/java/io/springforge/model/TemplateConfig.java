package io.springforge.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Configuração do sistema de templates customizados.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public class TemplateConfig {

    /** Ativa a renderização de templates customizados. */
    private boolean enabled = false;

    /** Diretório onde ficam os templates. Ex: .spring-forge/templates */
    private String templateDir = ".spring-forge/templates";

    /** Diretório base de saída. Quando vazio, usa o outputDir Java do plugin. */
    private String outputDir;

    /** Sufixo de arquivos de template. */
    private String suffix = ".tpl";

    /** Se true, arquivos customizados podem sobrescrever arquivos em target/generated-*. */
    private boolean overwrite = true;

    /** Templates criados pelo dashboard visual. */
    private java.util.List<VisualTemplateDefinition> visualTemplates = new java.util.ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getTemplateDir() { return templateDir; }
    public void setTemplateDir(String templateDir) { this.templateDir = templateDir; }

    public String getOutputDir() { return outputDir; }
    public void setOutputDir(String outputDir) { this.outputDir = outputDir; }

    public String getSuffix() { return suffix; }
    public void setSuffix(String suffix) { this.suffix = suffix; }

    public boolean isOverwrite() { return overwrite; }
    public void setOverwrite(boolean overwrite) { this.overwrite = overwrite; }

    public java.util.List<VisualTemplateDefinition> getVisualTemplates() { return visualTemplates; }
    public void setVisualTemplates(java.util.List<VisualTemplateDefinition> visualTemplates) { this.visualTemplates = visualTemplates; }
}
