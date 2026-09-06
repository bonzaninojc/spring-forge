package io.springforge.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Configura se o Spring Forge pode enriquecer o pom.xml do projeto alvo
 * com dependências necessárias para as features habilitadas.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public class PomConfig {

    /**
     * Se true, o goal generate atualiza o pom.xml do projeto usuário.
     * Default: false para evitar alterações inesperadas.
     */
    private boolean autoUpdate = false;

    /** Se true, cria pom.xml.spring-forge.bak antes de modificar. */
    private boolean createBackup = true;

    /** Se true, adiciona comentários "Spring Forge" antes dos blocos criados. */
    private boolean addComments = true;

    /**
     * Se true, também adiciona plugins/configurações auxiliares, como
     * annotationProcessorPaths do MapStruct no maven-compiler-plugin.
     */
    private boolean managePlugins = true;

    public boolean isAutoUpdate() { return autoUpdate; }
    public void setAutoUpdate(boolean autoUpdate) { this.autoUpdate = autoUpdate; }

    public boolean isCreateBackup() { return createBackup; }
    public void setCreateBackup(boolean createBackup) { this.createBackup = createBackup; }

    public boolean isAddComments() { return addComments; }
    public void setAddComments(boolean addComments) { this.addComments = addComments; }

    public boolean isManagePlugins() { return managePlugins; }
    public void setManagePlugins(boolean managePlugins) { this.managePlugins = managePlugins; }
}
