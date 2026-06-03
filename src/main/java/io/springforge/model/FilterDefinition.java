package io.springforge.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

/**
 * Definição de um filtro de busca para a entidade.
 * Usado para gerar FilterDTO + endpoint POST /search + painel de filtros no frontend.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FilterDefinition {

    private String name;
    private String type;
    private String label;
    private List<String> enumValues = new ArrayList<>();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getLabel() { return label != null ? label : name; }
    public void setLabel(String label) { this.label = label; }

    public List<String> getEnumValues() { return enumValues; }
    public void setEnumValues(List<String> enumValues) { this.enumValues = enumValues; }
}
