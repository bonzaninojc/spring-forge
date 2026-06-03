package io.springforge.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RelationDefinition {

    /**
     * Tipo do relacionamento:
     * ManyToOne | OneToMany | OneToOne | ManyToMany
     */
    private String type;

    /** Nome da entidade alvo, ex: "Category" */
    private String targetEntity;

    /** Nome do campo nesta entidade, ex: "category" */
    private String fieldName;

    /**
     * Cascade: ALL | PERSIST | MERGE | REMOVE | REFRESH | DETACH
     * Default: MERGE,PERSIST
     */
    private String cascade = "MERGE,PERSIST";

    /**
     * Fetch type: LAZY | EAGER
     * Default: LAZY
     */
    private String fetch = "LAZY";

    /**
     * Para OneToMany/ManyToMany: nome do campo mapeado na entidade alvo
     * ex: "product" (mappedBy = "product")
     */
    private String mappedBy;

    /**
     * Se true, inclui este relacionamento no DTO de resposta
     * Default: true
     */
    private boolean inResponse = true;

    // --- Getters e Setters ---

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTargetEntity() { return targetEntity; }
    public void setTargetEntity(String targetEntity) { this.targetEntity = targetEntity; }

    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }

    public String getCascade() { return cascade; }
    public void setCascade(String cascade) { this.cascade = cascade; }

    public String getFetch() { return fetch; }
    public void setFetch(String fetch) { this.fetch = fetch; }

    public String getMappedBy() { return mappedBy; }
    public void setMappedBy(String mappedBy) { this.mappedBy = mappedBy; }

    public boolean isInResponse() { return inResponse; }
    public void setInResponse(boolean inResponse) { this.inResponse = inResponse; }
}
