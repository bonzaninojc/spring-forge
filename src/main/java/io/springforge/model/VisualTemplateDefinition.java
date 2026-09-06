package io.springforge.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Template customizado criado/administrado pelo dashboard visual.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public class VisualTemplateDefinition {

    /** Nome amigável exibido no dashboard. */
    private String name;

    /** Escopo: entity ou global. */
    private String scope = "entity";

    /** Caminho de saída/template com placeholders. Ex: {{packagePath}}/{{EntityName}}Policy.java */
    private String path;

    /** Conteúdo do template. */
    private String content;

    /** Se false, ignora este template. */
    private boolean enabled = true;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
}
