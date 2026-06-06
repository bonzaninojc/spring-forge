package io.springforge.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import io.springforge.model.*;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class ForgeJsonParser {

    private static final Pattern PASCAL_CASE = Pattern.compile("^[A-Z][a-zA-Z0-9]*$");
    private static final Pattern CAMEL_CASE = Pattern.compile("^[a-z][a-zA-Z0-9]*$");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*$");
    private static final Set<String> VALID_TYPES = Set.of(
        "string", "integer", "int", "long", "double", "float",
        "bigdecimal", "boolean", "localdate", "localdatetime", "uuid", "enum"
    );
    private static final Set<String> VALID_METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE");
    private static final Set<String> VALID_RELATIONS = Set.of("ManyToOne", "OneToMany", "OneToOne", "ManyToMany");
    private static final Set<String> VALID_OPERATORS = Set.of(
        "EQUALS", "NOT_EQUALS", "CONTAINS", "STARTS_WITH", "ENDS_WITH",
        "GREATER_THAN", "GREATER_THAN_OR_EQUAL", "LESS_THAN", "LESS_THAN_OR_EQUAL",
        "IN", "BETWEEN", "IS_NULL", "IS_NOT_NULL"
    );

    private final ObjectMapper mapper = new ObjectMapper();

    public ForgeDefinition parse(File jsonFile) throws MojoExecutionException {
        if (!jsonFile.exists()) {
            throw new MojoExecutionException(
                "forge.json não encontrado em: " + jsonFile.getAbsolutePath());
        }
        ForgeDefinition def;
        try {
            def = mapper.readValue(jsonFile, ForgeDefinition.class);
        } catch (UnrecognizedPropertyException e) {
            String field = e.getPropertyName();
            String location = e.getLocation() != null
                ? " (linha " + e.getLocation().getLineNr() + ", coluna " + e.getLocation().getColumnNr() + ")"
                : "";
            throw new MojoExecutionException(
                "forge.json: propriedade desconhecida '" + field + "'" + location + ".\n" +
                "  Verifique se o nome está correto. Propriedades conhecidas: " + e.getKnownPropertyIds(), e);
        } catch (JsonMappingException e) {
            String location = e.getLocation() != null
                ? " (linha " + e.getLocation().getLineNr() + ", coluna " + e.getLocation().getColumnNr() + ")"
                : "";
            throw new MojoExecutionException(
                "forge.json: erro de mapeamento" + location + " — " + e.getOriginalMessage(), e);
        } catch (JsonProcessingException e) {
            String location = e.getLocation() != null
                ? " (linha " + e.getLocation().getLineNr() + ", coluna " + e.getLocation().getColumnNr() + ")"
                : "";
            throw new MojoExecutionException(
                "forge.json: JSON inválido" + location + " — " + e.getOriginalMessage(), e);
        } catch (Exception e) {
            throw new MojoExecutionException("Erro ao fazer parse do forge.json: " + e.getMessage(), e);
        }
        validate(def);
        return def;
    }

    private void validate(ForgeDefinition def) throws MojoExecutionException {
        List<String> errors = new ArrayList<>();

        validateProject(def, errors);
        validateEntities(def, errors);

        if (!errors.isEmpty()) {
            StringBuilder msg = new StringBuilder("forge.json inválido (")
                .append(errors.size()).append(" erro(s)):\n");
            for (int i = 0; i < errors.size(); i++) {
                msg.append("  ").append(i + 1).append(". ").append(errors.get(i)).append("\n");
            }
            msg.append("\n  Dica: execute 'mvn spring-forge:schema' para gerar o JSON Schema com autocomplete.");
            throw new MojoExecutionException(msg.toString());
        }
    }

    private void validateProject(ForgeDefinition def, List<String> errors) {
        if (def.getProject() == null) {
            errors.add("'project' é obrigatório. Exemplo: { \"project\": { \"basePackage\": \"com.myapp\", \"name\": \"MyApp\" } }");
            return;
        }
        ProjectConfig p = def.getProject();
        if (p.getBasePackage() == null || p.getBasePackage().isBlank()) {
            errors.add("project.basePackage é obrigatório. Ex: \"com.myapp\"");
        } else if (!PACKAGE_PATTERN.matcher(p.getBasePackage()).matches()) {
            errors.add("project.basePackage '" + p.getBasePackage() + "' não é um pacote Java válido. Use formato: com.example.app");
        }
        if (p.getName() == null || p.getName().isBlank()) {
            errors.add("project.name é obrigatório.");
        }
        String db = p.getDatabase();
        if (db != null && !Set.of("postgres", "mysql", "mongodb", "h2").contains(db.toLowerCase())) {
            errors.add("project.database '" + db + "' inválido. Use: postgres, mysql, mongodb ou h2.");
        }
    }

    private void validateEntities(ForgeDefinition def, List<String> errors) {
        if (def.getEntities() == null || def.getEntities().isEmpty()) {
            errors.add("'entities' é obrigatório e deve ter ao menos uma entidade.");
            return;
        }

        Set<String> entityNames = new HashSet<>();
        int i = 0;
        for (EntityDefinition e : def.getEntities()) {
            String ep = "entities[" + i + "]";

            if (e.getName() == null || e.getName().isBlank()) {
                errors.add(ep + ".name é obrigatório.");
            } else {
                if (!PASCAL_CASE.matcher(e.getName()).matches()) {
                    errors.add(ep + ".name '" + e.getName() + "' deve ser PascalCase (ex: Product, OrderItem).");
                }
                if (!entityNames.add(e.getName())) {
                    errors.add(ep + ".name '" + e.getName() + "' está duplicado.");
                }
            }

            validateFields(e, ep, errors);
            validateRelations(def, e, ep, errors);
            validateActions(e, ep, errors);
            validateFilters(e, ep, errors);
            i++;
        }
    }

    private void validateFields(EntityDefinition e, String ep, List<String> errors) {
        Set<String> fieldNames = new HashSet<>();
        int j = 0;
        for (FieldDefinition f : e.getFields()) {
            String fp = ep + ".fields[" + j + "]";
            if (f.getName() == null || f.getName().isBlank()) {
                errors.add(fp + ".name é obrigatório.");
            } else {
                if (!CAMEL_CASE.matcher(f.getName()).matches()) {
                    errors.add(fp + ".name '" + f.getName() + "' deve ser camelCase (ex: firstName, orderDate).");
                }
                if (!fieldNames.add(f.getName())) {
                    errors.add(fp + ".name '" + f.getName() + "' está duplicado na entidade " + e.getName() + ".");
                }
            }
            if (f.getType() == null || f.getType().isBlank()) {
                errors.add(fp + ".type é obrigatório. Valores: String, Integer, Long, Double, Float, BigDecimal, Boolean, LocalDate, LocalDateTime, UUID, Enum.");
            } else if (!VALID_TYPES.contains(f.getType().toLowerCase())) {
                errors.add(fp + ".type '" + f.getType() + "' inválido. Use: String, Integer, Long, Double, Float, BigDecimal, Boolean, LocalDate, LocalDateTime, UUID, Enum.");
            }
            if ("Enum".equalsIgnoreCase(f.getType()) && (f.getEnumValues() == null || f.getEnumValues().isEmpty())) {
                errors.add(fp + ": tipo Enum requer 'enumValues' com ao menos um valor. Ex: [\"ACTIVE\", \"INACTIVE\"]");
            }
            if (f.getMaxLength() != null && f.getMinLength() != null && f.getMinLength() > f.getMaxLength()) {
                errors.add(fp + ": minLength (" + f.getMinLength() + ") não pode ser maior que maxLength (" + f.getMaxLength() + ").");
            }
            j++;
        }
    }

    private void validateRelations(ForgeDefinition def, EntityDefinition e, String ep, List<String> errors) {
        int j = 0;
        Set<String> allEntityNames = new HashSet<>();
        if (def.getEntities() != null) {
            for (EntityDefinition ent : def.getEntities()) {
                if (ent.getName() != null) allEntityNames.add(ent.getName());
            }
        }
        for (RelationDefinition r : e.getRelations()) {
            String rp = ep + ".relations[" + j + "]";
            if (r.getType() == null || !VALID_RELATIONS.contains(r.getType())) {
                errors.add(rp + ".type inválido: '" + r.getType() + "'. Use: ManyToOne, OneToMany, OneToOne, ManyToMany.");
            }
            if (r.getTargetEntity() == null || r.getTargetEntity().isBlank()) {
                errors.add(rp + ".targetEntity é obrigatório.");
            } else if (!allEntityNames.contains(r.getTargetEntity())) {
                errors.add(rp + ".targetEntity '" + r.getTargetEntity() + "' não existe nas entidades definidas. Entidades disponíveis: " + allEntityNames);
            }
            if (r.getFieldName() == null || r.getFieldName().isBlank()) {
                errors.add(rp + ".fieldName é obrigatório.");
            }
            if (r.getFetch() != null && !Set.of("LAZY", "EAGER").contains(r.getFetch().toUpperCase())) {
                errors.add(rp + ".fetch inválido: '" + r.getFetch() + "'. Use: LAZY ou EAGER.");
            }
            j++;
        }
    }

    private void validateActions(EntityDefinition e, String ep, List<String> errors) {
        Set<String> actionNames = new HashSet<>();
        int k = 0;
        for (ActionDefinition a : e.getActions()) {
            String ap = ep + ".actions[" + k + "]";
            if (a.getName() == null || a.getName().isBlank()) {
                errors.add(ap + ".name é obrigatório.");
            } else {
                if (!CAMEL_CASE.matcher(a.getName()).matches()) {
                    errors.add(ap + ".name '" + a.getName() + "' deve ser camelCase (ex: activate, adjustStock).");
                }
                if (!actionNames.add(a.getName())) {
                    errors.add(ap + ".name '" + a.getName() + "' está duplicado na entidade " + e.getName() + ".");
                }
            }
            if (a.getHttpMethod() != null) {
                String m = a.getHttpMethod().toUpperCase();
                if (!VALID_METHODS.contains(m)) {
                    errors.add(ap + ".httpMethod '" + a.getHttpMethod() + "' inválido. Use: GET, POST, PUT, PATCH, DELETE.");
                }
            }
            if (a.isScheduled() && a.getScheduledCron() == null && a.getScheduledFixedRate() == null) {
                errors.add(ap + ": action com scheduled=true requer 'scheduledCron' ou 'scheduledFixedRate'.");
            }
            k++;
        }
    }

    private void validateFilters(EntityDefinition e, String ep, List<String> errors) {
        int j = 0;
        for (FilterDefinition f : e.getFilters()) {
            String fp = ep + ".filters[" + j + "]";
            if (f.getName() == null || f.getName().isBlank()) {
                errors.add(fp + ".name é obrigatório.");
            }
            if (f.getType() == null || f.getType().isBlank()) {
                errors.add(fp + ".type é obrigatório.");
            }
            if (f.getOperator() != null && !VALID_OPERATORS.contains(f.getOperator().toUpperCase())) {
                errors.add(fp + ".operator '" + f.getOperator() + "' inválido. Use: " + VALID_OPERATORS);
            }
            j++;
        }
    }
}
