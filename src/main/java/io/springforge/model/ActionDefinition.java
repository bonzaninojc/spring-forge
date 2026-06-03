package io.springforge.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

/**
 * Define uma action customizada de uma entidade.
 *
 * Cada action gera:
 *   - Um DTO de input  (${ActionName}RequestDTO)
 *   - Um DTO de output (${ActionName}ResponseDTO)  ← pode ser void
 *   - Um método abstrato na interface ${Entity}Service
 *   - Um método não-implementado (throw) no ${Entity}ServiceImpl
 *   - Um endpoint POST no controller (se httpMethod != null)
 *
 * Exemplo no forge.json:
 * {
 *   "name": "activate",
 *   "description": "Ativa um produto e notifica o estoque",
 *   "httpMethod": "POST",
 *   "apiPath": "/{id}/activate",
 *   "request": [
 *     { "name": "reason",  "type": "String",  "required": true },
 *     { "name": "notifyStock", "type": "Boolean", "required": false }
 *   ],
 *   "response": [
 *     { "name": "activated", "type": "Boolean" },
 *     { "name": "message",   "type": "String"  }
 *   ]
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ActionDefinition {

    /** Nome da action em camelCase, ex: "activate", "transferStock", "generateReport" */
    private String name;

    /** Descrição Javadoc do método */
    private String description;

    /**
     * Método HTTP para expor no controller.
     * Valores: GET | POST | PUT | PATCH | DELETE
     * Se null/omitido, gera apenas no Service (sem endpoint).
     */
    private String httpMethod;

    /**
     * Path do endpoint relativo ao base path da entidade.
     * Ex: "/{id}/activate"
     * Default: "/{id}/${name}"
     */
    private String apiPath;

    /**
     * Campos do DTO de input (${ActionName}RequestDTO).
     * Se vazio, o método não recebe DTO (apenas Long id, se pathVariable=true).
     */
    private List<FieldDefinition> request = new ArrayList<>();

    /**
     * Campos do DTO de output (${ActionName}ResponseDTO).
     * Se vazio, o método retorna void.
     */
    private List<FieldDefinition> response = new ArrayList<>();

    /**
     * Se true, o método recebe o ID da entidade como parâmetro (Long id).
     * Default: true
     */
    private boolean requiresId = true;

    /**
     * Evento Spring a ser publicado após a execução da action.
     * Se null, nenhum evento é gerado.
     */
    private EventDefinition event;

    /**
     * Filas RabbitMQ relacionadas a esta action.
     * Pode publicar e/ou consumir.
     */
    private java.util.List<QueueDefinition> queues = new java.util.ArrayList<>();

    /**
     * Se true, gera @Scheduled para esta action (sem endpoint HTTP).
     * Requer scheduledCron ou scheduledFixedRate.
     */
    private boolean scheduled = false;

    /**
     * Cron expression para @Scheduled, ex: "0 0 * * * *"
     */
    private String scheduledCron;

    /**
     * Fixed rate em ms para @Scheduled.
     */
    private Long scheduledFixedRate;

    /**
     * Tags OpenAPI/Swagger para este endpoint.
     * Ex: ["Products", "Inventory"]
     */
    private java.util.List<String> openApiTags = new java.util.ArrayList<>();

    /**
     * Roles necessárias para executar esta action (RBAC).
     * Sobrescreve as roles da entidade para este endpoint específico.
     * Ex: ["ADMIN", "MANAGER"]
     */
    private java.util.List<String> roles = new java.util.ArrayList<>();

    /**
     * Códigos de resposta HTTP adicionais para OpenAPI.
     * Ex: [{"code": 400, "description": "Estoque insuficiente"}]
     */
    private java.util.List<OpenApiResponse> openApiResponses = new java.util.ArrayList<>();

    // --- Getters e Setters ---

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getHttpMethod() { return httpMethod; }
    public void setHttpMethod(String httpMethod) { this.httpMethod = httpMethod; }

    public String getApiPath() { return apiPath; }
    public void setApiPath(String apiPath) { this.apiPath = apiPath; }

    public List<FieldDefinition> getRequest() { return request; }
    public void setRequest(List<FieldDefinition> request) { this.request = request; }

    public List<FieldDefinition> getResponse() { return response; }
    public void setResponse(List<FieldDefinition> response) { this.response = response; }

    public boolean isRequiresId() { return requiresId; }
    public void setRequiresId(boolean requiresId) { this.requiresId = requiresId; }

    public EventDefinition getEvent() { return event; }
    public void setEvent(EventDefinition event) { this.event = event; }

    public java.util.List<QueueDefinition> getQueues() { return queues; }
    public void setQueues(java.util.List<QueueDefinition> queues) { this.queues = queues; }

    public boolean isScheduled() { return scheduled; }
    public void setScheduled(boolean scheduled) { this.scheduled = scheduled; }

    public String getScheduledCron() { return scheduledCron; }
    public void setScheduledCron(String scheduledCron) { this.scheduledCron = scheduledCron; }

    public Long getScheduledFixedRate() { return scheduledFixedRate; }
    public void setScheduledFixedRate(Long scheduledFixedRate) { this.scheduledFixedRate = scheduledFixedRate; }

    public java.util.List<String> getOpenApiTags() { return openApiTags; }
    public void setOpenApiTags(java.util.List<String> openApiTags) { this.openApiTags = openApiTags; }

    public java.util.List<String> getRoles() { return roles; }
    public void setRoles(java.util.List<String> roles) { this.roles = roles; }

    public java.util.List<OpenApiResponse> getOpenApiResponses() { return openApiResponses; }
    public void setOpenApiResponses(java.util.List<OpenApiResponse> openApiResponses) { this.openApiResponses = openApiResponses; }

    public boolean hasQueues() { return queues != null && !queues.isEmpty(); }
    public boolean hasEvent() { return event != null; }
    public boolean hasScheduled() { return scheduled; }

    // --- Helpers ---

    /** Inner class para respostas OpenAPI customizadas */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public static class OpenApiResponse {
        private int code;
        private String description;
        public int getCode() { return code; }
        public void setCode(int code) { this.code = code; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    /** "activate" → "Activate" */
    public String getNameCapitalized() {
        if (name == null || name.isEmpty()) return name;
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    /** Nome do RequestDTO, ex: "ActivateRequestDTO" */
    public String getRequestDtoName() {
        return getNameCapitalized() + "RequestDTO";
    }

    /** Nome do ResponseDTO, ex: "ActivateResponseDTO" */
    public String getResponseDtoName() {
        return getNameCapitalized() + "ResponseDTO";
    }

    /** Se a action tem campos de input */
    public boolean hasRequest() {
        return request != null && !request.isEmpty();
    }

    /** Se a action tem campos de output (senão retorna void) */
    public boolean hasResponse() {
        return response != null && !response.isEmpty();
    }

    /** Tipo de retorno Java do método: "ActivateResponseDTO" ou "void" */
    public String getReturnType(String entityName) {
        if (!hasResponse()) return "void";
        return getNameCapitalized() + "ResponseDTO";
    }

    /** Path efetivo do endpoint */
    public String getEffectiveApiPath() {
        if (apiPath != null && !apiPath.isBlank()) return apiPath;
        return requiresId ? "/{id}/" + name : "/" + name;
    }
}
