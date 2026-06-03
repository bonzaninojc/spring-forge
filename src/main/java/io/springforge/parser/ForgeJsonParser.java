package io.springforge.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.springforge.model.*;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ForgeJsonParser {

    private final ObjectMapper mapper = new ObjectMapper();

    public ForgeDefinition parse(File jsonFile) throws MojoExecutionException {
        if (!jsonFile.exists()) {
            throw new MojoExecutionException(
                "forge.json não encontrado em: " + jsonFile.getAbsolutePath());
        }
        ForgeDefinition def;
        try {
            def = mapper.readValue(jsonFile, ForgeDefinition.class);
        } catch (Exception e) {
            throw new MojoExecutionException("Erro ao fazer parse do forge.json: " + e.getMessage(), e);
        }
        validate(def);
        return def;
    }

    private void validate(ForgeDefinition def) throws MojoExecutionException {
        List<String> errors = new ArrayList<>();

        if (def.getProject() == null) {
            errors.add("'project' é obrigatório.");
        } else if (def.getProject().getBasePackage() == null || def.getProject().getBasePackage().isBlank()) {
            errors.add("'project.basePackage' é obrigatório.");
        }

        if (def.getEntities() == null || def.getEntities().isEmpty()) {
            errors.add("Defina ao menos uma entidade em 'entities'.");
        } else {
            int i = 0;
            for (EntityDefinition e : def.getEntities()) {
                String ep = "entities[" + i + "]";
                if (e.getName() == null || e.getName().isBlank())
                    errors.add(ep + ".name é obrigatório.");

                int j = 0;
                for (FieldDefinition f : e.getFields()) {
                    String fp = ep + ".fields[" + j + "]";
                    if (f.getName() == null || f.getName().isBlank()) errors.add(fp + ".name é obrigatório.");
                    if (f.getType() == null || f.getType().isBlank()) errors.add(fp + ".type é obrigatório.");
                    if ("Enum".equalsIgnoreCase(f.getType()) && (f.getEnumValues() == null || f.getEnumValues().isEmpty()))
                        errors.add(fp + ": tipo Enum requer 'enumValues' com ao menos um valor.");
                    j++;
                }

                // Valida actions
                int k = 0;
                for (ActionDefinition a : e.getActions()) {
                    String ap = ep + ".actions[" + k + "]";
                    if (a.getName() == null || a.getName().isBlank())
                        errors.add(ap + ".name é obrigatório.");
                    if (a.getHttpMethod() != null) {
                        String m = a.getHttpMethod().toUpperCase();
                        if (!List.of("GET","POST","PUT","PATCH","DELETE").contains(m))
                            errors.add(ap + ".httpMethod inválido: " + m);
                    }
                    k++;
                }
                i++;
            }
        }

        if (!errors.isEmpty()) {
            throw new MojoExecutionException("forge.json inválido:\n  - " + String.join("\n  - ", errors));
        }
    }
}
