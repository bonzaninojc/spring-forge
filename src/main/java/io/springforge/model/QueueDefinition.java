package io.springforge.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Define uma fila RabbitMQ associada a uma entidade ou action.
 *
 * Exemplo no forge.json:
 * {
 *   "queues": [
 *     {
 *       "name": "order.confirmed",
 *       "exchange": "orders",
 *       "routingKey": "order.confirmed",
 *       "durable": true,
 *       "action": "confirm",
 *       "direction": "PUBLISH"
 *     }
 *   ]
 * }
 *
 * direction:
 *   PUBLISH  → gera RabbitTemplate.convertAndSend() no ServiceImpl da action
 *   CONSUME  → gera @RabbitListener na classe ${Entity}MessageConsumer
 *   BOTH     → gera publisher no Service E consumer separado
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class QueueDefinition {

    /** Nome da fila, ex: "order.confirmed" */
    private String name;

    /** Nome da exchange, ex: "orders". Default: "" (default exchange) */
    private String exchange = "";

    /** Routing key. Default: igual ao nome da fila */
    private String routingKey;

    /** Se a fila é durável. Default: true */
    private boolean durable = true;

    /**
     * Nome da action que dispara/consome esta fila.
     * Se null, gera listener/publisher genérico para a entidade.
     */
    private String action;

    /**
     * Direção: PUBLISH | CONSUME | BOTH
     * Default: PUBLISH
     */
    private String direction = "PUBLISH";

    /**
     * Dead Letter Exchange. Se informado, cria DLQ automaticamente.
     */
    private String deadLetterExchange;

    /**
     * Tempo de retry em ms para DLQ (x-message-ttl).
     * Default: 30000
     */
    private int retryTtlMs = 30000;

    // --- Getters e Setters ---

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }

    public String getRoutingKey() { return routingKey != null ? routingKey : name; }
    public void setRoutingKey(String routingKey) { this.routingKey = routingKey; }

    public boolean isDurable() { return durable; }
    public void setDurable(boolean durable) { this.durable = durable; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public String getDeadLetterExchange() { return deadLetterExchange; }
    public void setDeadLetterExchange(String deadLetterExchange) { this.deadLetterExchange = deadLetterExchange; }

    public int getRetryTtlMs() { return retryTtlMs; }
    public void setRetryTtlMs(int retryTtlMs) { this.retryTtlMs = retryTtlMs; }

    public boolean isPublish() {
        return "PUBLISH".equalsIgnoreCase(direction) || "BOTH".equalsIgnoreCase(direction);
    }

    public boolean isConsume() {
        return "CONSUME".equalsIgnoreCase(direction) || "BOTH".equalsIgnoreCase(direction);
    }

    public boolean hasDlq() {
        return deadLetterExchange != null && !deadLetterExchange.isBlank();
    }

    /** Nome da DLQ derivado da fila principal */
    public String getDlqName() {
        return name + ".dlq";
    }

    /** Nome da constante Java para a fila, ex: ORDER_CONFIRMED */
    public String getConstantName() {
        return name.toUpperCase().replace(".", "_").replace("-", "_");
    }

    /** Nome da classe consumer, ex: "OrderConfirmedConsumer" */
    public String getConsumerClassName(String entityName) {
        String[] parts = name.split("[._-]");
        StringBuilder sb = new StringBuilder(entityName);
        for (String p : parts) {
            if (!p.isBlank()) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1).toLowerCase());
        }
        return sb.append("Consumer").toString();
    }
}
