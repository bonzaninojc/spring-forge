package io.springforge.generator;

import io.springforge.model.ActionDefinition;
import io.springforge.model.EntityDefinition;
import io.springforge.model.ForgeDefinition;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Gera classes de tarefas agendadas (@Scheduled) para actions marcadas com "scheduled: true".
 *
 * Por entidade com actions scheduled, gera:
 *   - ${Entity}ScheduledTasks  — @Component com @Scheduled, injeta o Service da entidade
 *
 * Exemplo no forge.json:
 * {
 *   "name": "cleanupExpired",
 *   "scheduled": true,
 *   "scheduledCron": "0 0 2 * * *",
 *   "request": [],
 *   "response": []
 * }
 */
public class ScheduledTaskGenerator extends AbstractGenerator {

    public ScheduledTaskGenerator(Log log) { super(log); }

    @Override
    public void generate(ForgeDefinition def, EntityDefinition entity, File outDir) throws MojoExecutionException {
        if (!def.getProject().isGenerateScheduled()) return;
        if (!entity.hasActions()) return;

        List<ActionDefinition> scheduledActions = entity.getActions().stream()
            .filter(ActionDefinition::isScheduled)
            .collect(Collectors.toList());

        if (scheduledActions.isEmpty()) return;

        String pkg = scheduledPkg(def);
        writeFile(buildScheduledTasks(def, entity, scheduledActions, pkg),
                  javaFile(outDir, pkg, entity.getName() + "ScheduledTasks"), pkg);
    }

    private CodeWriter buildScheduledTasks(ForgeDefinition def, EntityDefinition entity,
                                           List<ActionDefinition> actions, String pkg) {
        String name = entity.getName();
        String svcPkg = servicePkg(def);

        CodeWriter w = new CodeWriter();

        w.imp("org.springframework.scheduling.annotation.Scheduled")
         .imp("org.springframework.stereotype.Component")
         .imp("org.slf4j.Logger")
         .imp("org.slf4j.LoggerFactory")
         .imp(svcPkg + "." + name + "Service");

        boolean hasFixedRate = actions.stream().anyMatch(a -> a.getScheduledFixedRate() != null);
        if (hasFixedRate) {
            w.imp("org.springframework.scheduling.annotation.EnableScheduling");
        }

        w.javadoc("Tarefas agendadas para " + name + ".\nGerado pelo Spring Forge.\n"
                + "IMPORTANTE: Adicione @EnableScheduling na sua classe principal ou em uma @Configuration.");
        w.line("@Component")
         .line("public class " + name + "ScheduledTasks {").blank();
        w.indent();

        w.line("private static final Logger log = LoggerFactory.getLogger(" + name + "ScheduledTasks.class);").blank();
        w.line("private final " + name + "Service service;").blank();

        w.line("public " + name + "ScheduledTasks(" + name + "Service service) {")
         .indent()
         .line("this.service = service;")
         .unindent().line("}").blank();

        for (ActionDefinition a : actions) {
            String schedAnnotation = buildScheduledAnnotation(a);
            String desc = a.getDescription() != null ? a.getDescription()
                : "Tarefa agendada: " + a.getName();

            w.javadoc(desc + "\nTODO: Verifique os parâmetros antes de ativar em produção.");
            w.line(schedAnnotation);

            // Constrói chamada ao service
            String serviceCall = buildServiceCall(a);
            w.line("public void scheduled" + capitalize(a.getName()) + "() {");
            w.indent();
            w.line("log.info(\"Executing scheduled task: " + a.getName() + "\");");
            w.line("try {");
            w.indent();
            w.line(serviceCall);
            w.line("log.info(\"Scheduled task '" + a.getName() + "' completed successfully.\");");
            w.unindent();
            w.line("} catch (Exception e) {");
            w.indent();
            w.line("log.error(\"Error in scheduled task '" + a.getName() + "': {}\", e.getMessage(), e);");
            w.unindent();
            w.line("}");
            w.unindent().line("}").blank();
        }

        w.unindent().line("}");
        return w;
    }

    private String buildScheduledAnnotation(ActionDefinition a) {
        if (a.getScheduledCron() != null && !a.getScheduledCron().isBlank()) {
            return "@Scheduled(cron = \"" + a.getScheduledCron() + "\")";
        }
        if (a.getScheduledFixedRate() != null) {
            return "@Scheduled(fixedRate = " + a.getScheduledFixedRate() + "L)";
        }
        // Default: a cada hora
        return "@Scheduled(cron = \"0 0 * * * *\") // TODO: ajuste o cron";
    }

    private String buildServiceCall(ActionDefinition a) {
        if (a.isRequiresId()) {
            return "// TODO: provide entity ID — scheduled tasks typically iterate or process in batch\n" +
                   "        // service." + a.getName() + "(id, dto);";
        }
        if (a.hasRequest()) {
            return "// TODO: construct request DTO\n" +
                   "        // " + a.getRequestDtoName() + " dto = new " + a.getRequestDtoName() + "();\n" +
                   "        // service." + a.getName() + "(dto);";
        }
        return "service." + a.getName() + "();";
    }


    private String scheduledPkg(ForgeDefinition def) {
        return def.getProject().getBasePackage() + ".scheduled";
    }
}
