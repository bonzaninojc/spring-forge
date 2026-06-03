package io.springforge.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Define um Spring Application Event associado a uma action.
 *
 * Quando configurado, o Spring Forge gera:
 *   - ${ActionName}Event  — classe que estende ApplicationEvent
 *   - Publicação via ApplicationEventPublisher no ServiceImpl da action
 *   - (opcional) ${ActionName}EventListener  — listener com @EventListener
 *
 * Exemplo no forge.json:
 * {
 *   "name": "OrderConfirmedEvent",
 *   "action": "confirm",
 *   "generateListener": true,
 *   "async": true
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class EventDefinition {

    /** Nome do evento, ex: "OrderConfirmedEvent". Default: "${ActionName}Event" */
    private String name;

    /** Nome da action que publica este evento */
    private String action;

    /** Se true, gera classe ${ActionName}EventListener com @EventListener */
    private boolean generateListener = false;

    /**
     * Se true, o listener usa @Async (requer @EnableAsync no projeto).
     * Default: false
     */
    private boolean async = false;

    /**
     * Descrição Javadoc do evento.
     */
    private String description;

    // --- Getters e Setters ---

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public boolean isGenerateListener() { return generateListener; }
    public void setGenerateListener(boolean generateListener) { this.generateListener = generateListener; }

    public boolean isAsync() { return async; }
    public void setAsync(boolean async) { this.async = async; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /** Nome efetivo do evento (gera default se não informado) */
    public String getEffectiveName(String actionName) {
        if (name != null && !name.isBlank()) return name;
        String cap = Character.toUpperCase(actionName.charAt(0)) + actionName.substring(1);
        return cap + "Event";
    }

    /** Nome do listener, ex: "OrderConfirmedEventListener" */
    public String getListenerClassName(String actionName) {
        return getEffectiveName(actionName) + "Listener";
    }
}
