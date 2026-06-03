package io.springforge.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Raiz do arquivo forge.json
 *
 * Exemplo mínimo:
 * {
 *   "project": { "basePackage": "com.myapp", "name": "MyApp" },
 *   "entities": [ ... ]
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ForgeDefinition {

    private ProjectConfig project;
    private List<EntityDefinition> entities;

    public ProjectConfig getProject() { return project; }
    public void setProject(ProjectConfig project) { this.project = project; }

    public List<EntityDefinition> getEntities() { return entities; }
    public void setEntities(List<EntityDefinition> entities) { this.entities = entities; }
}
