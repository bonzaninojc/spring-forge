package io.springforge.generator;

import io.springforge.model.ActionDefinition;
import io.springforge.model.EntityDefinition;
import io.springforge.model.EventDefinition;
import io.springforge.model.FieldDefinition;
import io.springforge.model.ForgeDefinition;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;

/**
 * Gera Spring Application Events para actions que declaram um "event".
 *
 * Por action com evento, gera:
 *   1. ${EventName}  — estende ApplicationEvent, com campos do response da action
 *   2. (opcional) ${EventName}Listener  — @Component com @EventListener (e @Async se configurado)
 *
 * O ServiceImpl receberá ApplicationEventPublisher injetado e publicará o evento
 * ao final da action (a injeção é feita pelo ServiceGenerator quando detecta eventos).
 */
public class SpringEventGenerator extends AbstractGenerator {

    public SpringEventGenerator(Log log) { super(log); }

    @Override
    public void generate(ForgeDefinition def, EntityDefinition entity, File outDir) throws MojoExecutionException {
        if (!def.getProject().isGenerateSpringEvents()) return;
        if (!entity.hasActions()) return;

        for (ActionDefinition action : entity.getActions()) {
            if (!action.hasEvent()) continue;

            EventDefinition event = action.getEvent();
            String eventPkg = eventPkg(def);
            String eventName = event.getEffectiveName(action.getName());

            // Gera a classe do evento
            writeFile(buildEventClass(def, entity, action, event, eventName, eventPkg),
                      javaFile(outDir, eventPkg, eventName), eventPkg);

            // Gera o listener (opcional)
            if (event.isGenerateListener()) {
                String listenerName = event.getListenerClassName(action.getName());
                writeFile(buildEventListener(def, entity, action, event, eventName, listenerName, eventPkg),
                          javaFile(outDir, eventPkg, listenerName), eventPkg);
            }
        }
    }

    // ── Classe do Evento ────────────────────────────────────────────────────────

    private CodeWriter buildEventClass(ForgeDefinition def, EntityDefinition entity,
                                       ActionDefinition action, EventDefinition event,
                                       String eventName, String pkg) {
        CodeWriter w = new CodeWriter();
        String dtoPkg = dtoPkg(def);

        w.imp("org.springframework.context.ApplicationEvent");
        addFieldImports(w, action.getResponse());

        String desc = event.getDescription() != null
            ? event.getDescription()
            : "Evento publicado após a action '" + action.getName() + "' em " + entity.getName() + ".";

        w.javadoc(desc + "\nGerado pelo Spring Forge.");
        w.line("public class " + eventName + " extends ApplicationEvent {").blank();
        w.indent();

        w.line("private static final long serialVersionUID = 1L;").blank();

        // Campos do response da action (ou só entityId se não tiver response)
        w.line("private final Long entityId;");
        for (FieldDefinition f : action.getResponse()) {
            w.line("private final " + javaType(f) + " " + f.getName() + ";");
        }
        w.blank();

        // Construtor
        StringBuilder ctorParams = new StringBuilder("Object source, Long entityId");
        for (FieldDefinition f : action.getResponse()) {
            ctorParams.append(", ").append(javaType(f)).append(" ").append(f.getName());
        }
        w.line("public " + eventName + "(" + ctorParams + ") {")
         .indent()
         .line("super(source);")
         .line("this.entityId = entityId;");
        for (FieldDefinition f : action.getResponse()) {
            w.line("this." + f.getName() + " = " + f.getName() + ";");
        }
        w.unindent().line("}").blank();

        // Getters (campos são final — sem setters)
        w.line("public Long getEntityId() { return entityId; }").blank();
        for (FieldDefinition f : action.getResponse()) {
            writeGetter(w, f, javaType(f));
        }

        w.unindent().line("}");
        return w;
    }

    // ── Listener ─────────────────────────────────────────────────────────────────

    private CodeWriter buildEventListener(ForgeDefinition def, EntityDefinition entity,
                                          ActionDefinition action, EventDefinition event,
                                          String eventName, String listenerName, String pkg) {
        CodeWriter w = new CodeWriter();

        w.imp("org.springframework.context.event.EventListener")
         .imp("org.springframework.stereotype.Component")
         .imp("org.slf4j.Logger")
         .imp("org.slf4j.LoggerFactory");

        if (event.isAsync()) {
            w.imp("org.springframework.scheduling.annotation.Async");
        }

        String desc = "Listener para " + eventName + ".\nTODO: Implemente a lógica de tratamento do evento.";
        w.javadoc(desc + "\nGerado pelo Spring Forge.");
        w.line("@Component")
         .line("public class " + listenerName + " {").blank();
        w.indent();

        w.line("private static final Logger log = LoggerFactory.getLogger(" + listenerName + ".class);").blank();

        if (event.isAsync()) {
            w.line("@Async");
        }
        w.line("@EventListener")
         .line("public void on" + eventName + "(" + eventName + " event) {");
        w.indent();
        w.line("log.info(\"Handling " + eventName + " for entity id: {}\", event.getEntityId());")
         .line("// TODO: implemente aqui a lógica de resposta ao evento");
        w.unindent().line("}").blank();

        w.unindent().line("}");
        return w;
    }

    private String eventPkg(ForgeDefinition def) {
        return def.getProject().getBasePackage() + ".event";
    }
}
