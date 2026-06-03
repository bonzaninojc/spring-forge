package io.springforge.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class FieldDefinition {

    /** Nome do campo em camelCase, ex: "firstName" */
    private String name;

    /**
     * Tipo do campo. Suportados:
     * String, Integer, Long, Double, Float, BigDecimal,
     * Boolean, LocalDate, LocalDateTime, UUID, Enum
     */
    private String type;

    /** Se o campo é obrigatório (@NotNull / @NotBlank) */
    private boolean required = false;

    /** Se o campo deve ser único (@Column(unique=true)) */
    private boolean unique = false;

    /** Tamanho máximo para Strings (@Size / @Column(length=)) */
    private Integer maxLength;

    /** Tamanho mínimo para Strings */
    private Integer minLength;

    /** Valor padrão (anotação @ColumnDefault) */
    private String defaultValue;

    /** Nome da coluna no banco (opcional, default = snake_case do name) */
    private String columnName;

    /**
     * Para tipo Enum: lista de valores possíveis
     * ex: ["ACTIVE", "INACTIVE", "PENDING"]
     */
    private List<String> enumValues = new ArrayList<>();

    /**
     * Validações extras, ex: ["@Email", "@Positive", "@Min(0)", "@Max(100)"]
     * Serão aplicadas nos DTOs e/ou Entity
     */
    private List<String> validations = new ArrayList<>();

    /** Se o campo deve aparecer no DTO de resposta */
    private boolean inResponse = true;

    /** Se o campo deve aparecer no DTO de criação/atualização */
    private boolean inRequest = true;

    // --- Getters e Setters ---

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public boolean isRequired() { return required; }
    public void setRequired(boolean required) { this.required = required; }

    public boolean isUnique() { return unique; }
    public void setUnique(boolean unique) { this.unique = unique; }

    public Integer getMaxLength() { return maxLength; }
    public void setMaxLength(Integer maxLength) { this.maxLength = maxLength; }

    public Integer getMinLength() { return minLength; }
    public void setMinLength(Integer minLength) { this.minLength = minLength; }

    public String getDefaultValue() { return defaultValue; }
    public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }

    public String getColumnName() { return columnName; }
    public void setColumnName(String columnName) { this.columnName = columnName; }

    public List<String> getEnumValues() { return enumValues; }
    public void setEnumValues(List<String> enumValues) { this.enumValues = enumValues; }

    public List<String> getValidations() { return validations; }
    public void setValidations(List<String> validations) { this.validations = validations; }

    public boolean isInResponse() { return inResponse; }
    public void setInResponse(boolean inResponse) { this.inResponse = inResponse; }

    public boolean isInRequest() { return inRequest; }
    public void setInRequest(boolean inRequest) { this.inRequest = inRequest; }
}
