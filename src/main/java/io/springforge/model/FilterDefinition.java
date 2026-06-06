package io.springforge.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

/**
 * Definição de um filtro de busca para a entidade.
 * Usado para gerar FilterDTO + endpoint POST /search + painel de filtros no frontend.
 *
 * Agora suporta operadores configuráveis:
 *   - EQUALS: comparação exata
 *   - NOT_EQUALS: diferente
 *   - CONTAINS: LIKE %...% (default para String)
 *   - STARTS_WITH: LIKE ...%
 *   - ENDS_WITH: LIKE %...
 *   - GREATER_THAN: >
 *   - GREATER_THAN_OR_EQUAL: >= (default para sufixo Min)
 *   - LESS_THAN: <
 *   - LESS_THAN_OR_EQUAL: <= (default para sufixo Max)
 *   - IN: valor está em uma lista
 *   - BETWEEN: entre dois valores (requer fieldMin/fieldMax no DTO)
 *   - IS_NULL: campo é null
 *   - IS_NOT_NULL: campo não é null
 *
 * Se operator não for definido, usa a convenção automática existente.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FilterDefinition {

    private String name;
    private String type;
    private String label;
    private List<String> enumValues = new ArrayList<>();

    /** Operador de comparação. Se null, usa a convenção automática. */
    private String operator;

    /** Campo alvo na entidade (se diferente do name). Ex: filter "priceMin" → targetField "price" */
    private String targetField;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getLabel() { return label != null ? label : name; }
    public void setLabel(String label) { this.label = label; }

    public List<String> getEnumValues() { return enumValues; }
    public void setEnumValues(List<String> enumValues) { this.enumValues = enumValues; }

    public String getOperator() { return operator; }
    public void setOperator(String operator) { this.operator = operator; }

    public String getTargetField() { return targetField; }
    public void setTargetField(String targetField) { this.targetField = targetField; }

    /**
     * Retorna o campo real na entidade para esta condição.
     * Se targetField está definido, usa ele. Caso contrário, infere pelo nome
     * (removendo sufixos Min/Max para BETWEEN/range).
     */
    public String resolveTargetField() {
        if (targetField != null && !targetField.isBlank()) return targetField;
        String resolved = name;
        if (resolved.toLowerCase().endsWith("min")) {
            resolved = resolved.substring(0, resolved.length() - 3);
        } else if (resolved.toLowerCase().endsWith("max")) {
            resolved = resolved.substring(0, resolved.length() - 3);
        }
        return resolved;
    }

    /**
     * Resolve o operador efetivo baseado na configuração explícita ou nas convenções.
     */
    public String resolveOperator() {
        if (operator != null && !operator.isBlank()) return operator.toUpperCase();

        // Convenções automáticas
        if ("String".equalsIgnoreCase(type)) return "CONTAINS";
        if ("Enum".equalsIgnoreCase(type)) return "EQUALS";
        if ("Boolean".equalsIgnoreCase(type)) return "EQUALS";
        if (name.toLowerCase().endsWith("min")) return "GREATER_THAN_OR_EQUAL";
        if (name.toLowerCase().endsWith("max")) return "LESS_THAN_OR_EQUAL";
        return "EQUALS";
    }
}
