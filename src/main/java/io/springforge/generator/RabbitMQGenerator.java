package io.springforge.generator;

import io.springforge.model.ActionDefinition;
import io.springforge.model.EntityDefinition;
import io.springforge.model.ForgeDefinition;
import io.springforge.model.QueueDefinition;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Gera infraestrutura RabbitMQ para as entidades que declaram filas.
 *
 * Por entidade que tem queues, gera:
 *   1. ${Entity}QueueConstants  — constantes de nomes de filas/exchanges/routing keys
 *   2. ${Entity}RabbitMQConfig  — @Configuration com Bean de Queue, Exchange, Binding (e DLQ se configurado)
 *   3. ${Entity}MessagePublisher — @Component com RabbitTemplate para as queues de PUBLISH
 *   4. ${Entity}MessageConsumer  — @Component com @RabbitListener para as queues de CONSUME
 *
 * Também gera (uma vez por projeto):
 *   5. RabbitMQGlobalConfig     — @Configuration com ConnectionFactory, MessageConverter, RabbitTemplate global
 */
public class RabbitMQGenerator extends AbstractGenerator {

    public RabbitMQGenerator(Log log) { super(log); }

    @Override
    public void generate(ForgeDefinition def, EntityDefinition entity, File outDir) throws MojoExecutionException {
        if (!def.getProject().isGenerateRabbitMQ()) return;
        if (!entity.hasQueues()) return;

        List<QueueDefinition> queues = entity.getAllQueues();
        String pkg = rabbitPkg(def);

        writeFile(buildConstants(def, entity, queues, pkg),
                  javaFile(outDir, pkg, entity.getName() + "QueueConstants"), pkg);

        writeFile(buildConfig(def, entity, queues, pkg),
                  javaFile(outDir, pkg, entity.getName() + "RabbitMQConfig"), pkg);

        boolean hasPublish = queues.stream().anyMatch(QueueDefinition::isPublish);
        boolean hasConsume = queues.stream().anyMatch(QueueDefinition::isConsume);

        if (hasPublish) {
            writeFile(buildPublisher(def, entity, queues, pkg),
                      javaFile(outDir, pkg, entity.getName() + "MessagePublisher"), pkg);
        }

        if (hasConsume) {
            writeFile(buildConsumer(def, entity, queues, pkg),
                      javaFile(outDir, pkg, entity.getName() + "MessageConsumer"), pkg);
        }
    }

    /** Gera o RabbitMQGlobalConfig (chamado uma vez pelo Mojo, não por entidade) */
    public void generateGlobalConfig(ForgeDefinition def, File outDir) throws MojoExecutionException {
        if (!def.getProject().isGenerateRabbitMQ()) return;
        boolean anyQueue = def.getEntities().stream().anyMatch(EntityDefinition::hasQueues);
        if (!anyQueue) return;

        String pkg = rabbitPkg(def);
        writeFile(buildGlobalConfig(pkg), javaFile(outDir, pkg, "RabbitMQGlobalConfig"), pkg);
    }

    // ── Constantes ─────────────────────────────────────────────────────────────

    private CodeWriter buildConstants(ForgeDefinition def, EntityDefinition entity,
                                      List<QueueDefinition> queues, String pkg) {
        CodeWriter w = new CodeWriter();
        w.javadoc("Constantes de filas RabbitMQ para " + entity.getName() + ".\nGerado pelo Spring Forge.");
        w.line("public final class " + entity.getName() + "QueueConstants {").blank();
        w.indent();
        w.line("private " + entity.getName() + "QueueConstants() {}").blank();

        for (QueueDefinition q : queues) {
            String cname = q.getConstantName();
            w.line("public static final String QUEUE_" + cname + " = \"" + q.getName() + "\";");
            if (!q.getExchange().isBlank()) {
                w.line("public static final String EXCHANGE_" + cname + " = \"" + q.getExchange() + "\";");
            }
            w.line("public static final String ROUTING_KEY_" + cname + " = \"" + q.getRoutingKey() + "\";");
            if (q.hasDlq()) {
                w.line("public static final String DLQ_" + cname + " = \"" + q.getDlqName() + "\";");
            }
            w.blank();
        }

        w.unindent().line("}");
        return w;
    }

    // ── Config ──────────────────────────────────────────────────────────────────

    private CodeWriter buildConfig(ForgeDefinition def, EntityDefinition entity,
                                   List<QueueDefinition> queues, String pkg) {
        CodeWriter w = new CodeWriter();

        w.imp("org.springframework.amqp.core.Binding")
         .imp("org.springframework.amqp.core.BindingBuilder")
         .imp("org.springframework.amqp.core.Queue")
         .imp("org.springframework.amqp.core.QueueBuilder")
         .imp("org.springframework.amqp.core.TopicExchange")
         .imp("org.springframework.context.annotation.Bean")
         .imp("org.springframework.context.annotation.Configuration");

        w.javadoc("Configuração de filas, exchanges e bindings RabbitMQ para " + entity.getName() + ".\nGerado pelo Spring Forge.");
        w.line("@Configuration")
         .line("public class " + entity.getName() + "RabbitMQConfig {").blank();
        w.indent();

        String constClass = entity.getName() + "QueueConstants";
        Set<String> declaredExchanges = new HashSet<>();
        Set<String> declaredDlqExchanges = new HashSet<>();

        for (QueueDefinition q : queues) {
            String cname = q.getConstantName();
            String durable = String.valueOf(q.isDurable());

            // Queue bean
            w.line("@Bean")
             .line("public Queue " + safeBeanName("queue", q.getName()) + "() {");
            w.indent();
            if (q.hasDlq()) {
                w.imp("java.util.HashMap")
                 .imp("java.util.Map");
                w.line("Map<String, Object> args = new HashMap<>();")
                 .line("args.put(\"x-dead-letter-exchange\", \"" + q.getDeadLetterExchange() + "\");")
                 .line("args.put(\"x-dead-letter-routing-key\", " + constClass + ".ROUTING_KEY_" + cname + ");")
                 .line("return QueueBuilder.durable(" + constClass + ".QUEUE_" + cname + ")")
                 .line("        .withArguments(args).build();");
            } else {
                w.line("return new Queue(" + constClass + ".QUEUE_" + cname + ", " + durable + ");");
            }
            w.unindent().line("}").blank();

            // DLQ bean
            if (q.hasDlq()) {
                w.line("@Bean")
                 .line("public Queue " + safeBeanName("dlq", q.getName()) + "() {")
                 .indent()
                 .line("return QueueBuilder.durable(" + constClass + ".DLQ_" + cname + ").build();")
                 .unindent().line("}").blank();
            }

            // Exchange bean (se não for default exchange) — evita duplicados
            if (!q.getExchange().isBlank()) {
                String exchangeBeanName = safeBeanName("exchange", q.getExchange());
                if (declaredExchanges.add(exchangeBeanName)) {
                    w.line("@Bean")
                     .line("public TopicExchange " + exchangeBeanName + "() {")
                     .indent()
                     .line("return new TopicExchange(\"" + q.getExchange() + "\");")
                     .unindent().line("}").blank();
                }

                // Binding bean
                w.line("@Bean")
                 .line("public Binding " + safeBeanName("binding", q.getName()) + "(")
                 .line("        Queue " + safeBeanName("queue", q.getName()) + ",")
                 .line("        TopicExchange " + exchangeBeanName + ") {")
                 .indent()
                 .line("return BindingBuilder")
                 .line("        .bind(" + safeBeanName("queue", q.getName()) + ")")
                 .line("        .to(" + exchangeBeanName + ")")
                 .line("        .with(" + constClass + ".ROUTING_KEY_" + cname + ");")
                 .unindent().line("}").blank();

                // DLQ exchange binding
                if (q.hasDlq()) {
                    String dlqExchangeBeanName = safeBeanName("dlqExchange", q.getDeadLetterExchange());
                    if (declaredDlqExchanges.add(dlqExchangeBeanName)) {
                        w.line("@Bean")
                         .line("public TopicExchange " + dlqExchangeBeanName + "() {")
                         .indent()
                         .line("return new TopicExchange(\"" + q.getDeadLetterExchange() + "\");")
                         .unindent().line("}").blank();
                    }

                    w.line("@Bean")
                     .line("public Binding " + safeBeanName("dlqBinding", q.getName()) + "(")
                     .line("        Queue " + safeBeanName("dlq", q.getName()) + ",")
                     .line("        TopicExchange " + dlqExchangeBeanName + ") {")
                     .indent()
                     .line("return BindingBuilder")
                     .line("        .bind(" + safeBeanName("dlq", q.getName()) + ")")
                     .line("        .to(" + dlqExchangeBeanName + ")")
                     .line("        .with(" + constClass + ".ROUTING_KEY_" + cname + ");")
                     .unindent().line("}").blank();
                }
            }
        }

        w.unindent().line("}");
        return w;
    }

    // ── Publisher ───────────────────────────────────────────────────────────────

    private CodeWriter buildPublisher(ForgeDefinition def, EntityDefinition entity,
                                      List<QueueDefinition> queues, String pkg) {
        CodeWriter w = new CodeWriter();
        String dtoPkg = dtoPkg(def);
        String constClass = entity.getName() + "QueueConstants";

        w.imp("org.springframework.amqp.rabbit.core.RabbitTemplate")
         .imp("org.springframework.stereotype.Component")
         .imp("org.slf4j.Logger")
         .imp("org.slf4j.LoggerFactory");

        w.javadoc("Publisher RabbitMQ para " + entity.getName() + ".\nGerado pelo Spring Forge — injete e chame nos ServiceImpl.");
        w.line("@Component")
         .line("public class " + entity.getName() + "MessagePublisher {").blank();
        w.indent();

        w.line("private static final Logger log = LoggerFactory.getLogger(" + entity.getName() + "MessagePublisher.class);").blank();
        w.line("private final RabbitTemplate rabbitTemplate;").blank();
        w.line("public " + entity.getName() + "MessagePublisher(RabbitTemplate rabbitTemplate) {")
         .indent()
         .line("this.rabbitTemplate = rabbitTemplate;")
         .unindent().line("}").blank();

        for (QueueDefinition q : queues) {
            if (!q.isPublish()) continue;
            String cname = q.getConstantName();
            String methodName = "publish" + toPascalFromQueue(q.getName());

            // Descobre se há uma action com DTO de request para usar como payload
            String payloadType = resolvePayloadType(def, entity, q, dtoPkg);

            w.javadoc("Publica mensagem na fila " + q.getName() + ".\n@param payload objeto a serializar como JSON");
            w.line("public void " + methodName + "(" + payloadType + " payload) {");
            w.indent();
            w.line("log.debug(\"Publishing to " + q.getName() + ": {}\", payload);");
            if (q.getExchange().isBlank()) {
                w.line("rabbitTemplate.convertAndSend(\"\", " + constClass + ".QUEUE_" + cname + ", payload);");
            } else {
                w.line("rabbitTemplate.convertAndSend(")
                 .line("    " + constClass + ".EXCHANGE_" + cname + ",")
                 .line("    " + constClass + ".ROUTING_KEY_" + cname + ",")
                 .line("    payload);");
            }
            w.unindent().line("}").blank();
        }

        w.unindent().line("}");
        return w;
    }

    // ── Consumer ────────────────────────────────────────────────────────────────

    private CodeWriter buildConsumer(ForgeDefinition def, EntityDefinition entity,
                                     List<QueueDefinition> queues, String pkg) {
        CodeWriter w = new CodeWriter();
        String svcPkg = servicePkg(def);
        String constClass = entity.getName() + "QueueConstants";

        w.imp("org.springframework.amqp.rabbit.annotation.RabbitListener")
         .imp("org.springframework.stereotype.Component")
         .imp("org.slf4j.Logger")
         .imp("org.slf4j.LoggerFactory");

        w.javadoc("Consumer RabbitMQ para " + entity.getName() + ".\nGerado pelo Spring Forge — implemente a lógica nos métodos marcados com TODO.");
        w.line("@Component")
         .line("public class " + entity.getName() + "MessageConsumer {").blank();
        w.indent();

        w.line("private static final Logger log = LoggerFactory.getLogger(" + entity.getName() + "MessageConsumer.class);").blank();
        w.line("private final " + entity.getName() + "Service service;").blank();

        w.line("public " + entity.getName() + "MessageConsumer(" + entity.getName() + "Service service) {")
         .indent()
         .line("this.service = service;")
         .unindent().line("}").blank();
        w.imp(svcPkg + "." + entity.getName() + "Service");

        for (QueueDefinition q : queues) {
            if (!q.isConsume()) continue;
            String cname = q.getConstantName();
            String methodName = "consume" + toPascalFromQueue(q.getName());

            w.javadoc("Consome mensagens da fila " + q.getName() + ".\nTODO: Implemente a lógica de processamento.");
            w.line("@RabbitListener(queues = " + constClass + ".QUEUE_" + cname + ")")
             .line("public void " + methodName + "(String message) {");
            w.indent();
            w.line("log.info(\"Received from " + q.getName() + ": {}\", message);")
             .line("// TODO: deserialize message and call service");
            if (q.getAction() != null) {
                w.line("// Hint: this queue is associated with action '" + q.getAction() + "'");
            }
            w.unindent().line("}").blank();
        }

        w.unindent().line("}");
        return w;
    }

    // ── Global Config ───────────────────────────────────────────────────────────

    private CodeWriter buildGlobalConfig(String pkg) {
        CodeWriter w = new CodeWriter();

        w.imp("com.fasterxml.jackson.databind.ObjectMapper")
         .imp("com.fasterxml.jackson.databind.SerializationFeature")
         .imp("com.fasterxml.jackson.datatype.jsr310.JavaTimeModule")
         .imp("org.springframework.amqp.rabbit.connection.ConnectionFactory")
         .imp("org.springframework.amqp.rabbit.core.RabbitTemplate")
         .imp("org.springframework.amqp.support.converter.Jackson2JsonMessageConverter")
         .imp("org.springframework.amqp.support.converter.MessageConverter")
         .imp("org.springframework.context.annotation.Bean")
         .imp("org.springframework.context.annotation.Configuration");

        w.javadoc("Configuração global RabbitMQ: MessageConverter Jackson, RabbitTemplate.\nGerado pelo Spring Forge.");
        w.line("@Configuration")
         .line("public class RabbitMQGlobalConfig {").blank();
        w.indent();

        w.line("@Bean")
         .line("public MessageConverter jsonMessageConverter() {")
         .indent()
         .line("ObjectMapper mapper = new ObjectMapper();")
         .line("mapper.registerModule(new JavaTimeModule());")
         .line("mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);")
         .line("return new Jackson2JsonMessageConverter(mapper);")
         .unindent().line("}").blank();

        w.line("@Bean")
         .line("public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,")
         .line("                                      MessageConverter jsonMessageConverter) {")
         .indent()
         .line("RabbitTemplate template = new RabbitTemplate(connectionFactory);")
         .line("template.setMessageConverter(jsonMessageConverter);")
         .line("return template;")
         .unindent().line("}").blank();

        w.unindent().line("}");
        return w;
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private String rabbitPkg(ForgeDefinition def) {
        return def.getProject().getBasePackage() + ".messaging";
    }

    /** "order.confirmed" → "orderConfirmedQueue" style bean name */
    private String safeBeanName(String prefix, String raw) {
        String[] parts = raw.split("[._\\-]");
        StringBuilder sb = new StringBuilder(prefix);
        for (String p : parts) {
            if (!p.isBlank()) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    /** "order.confirmed" → "OrderConfirmed" */
    private String toPascalFromQueue(String name) {
        String[] parts = name.split("[._\\-]");
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (!p.isBlank()) sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1).toLowerCase());
        }
        return sb.toString();
    }

    /** Tenta encontrar o DTO de request da action associada à queue */
    private String resolvePayloadType(ForgeDefinition def, EntityDefinition entity,
                                      QueueDefinition q, String dtoPkg) {
        if (q.getAction() == null) return "Object";
        return entity.getActions().stream()
            .filter(a -> a.getName().equals(q.getAction()) && a.hasRequest())
            .findFirst()
            .map(a -> a.getRequestDtoName())
            .orElse("Object");
    }
}
