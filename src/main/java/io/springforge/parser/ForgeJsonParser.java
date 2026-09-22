package io.springforge.parser;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.maven.plugin.MojoExecutionException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

import io.springforge.model.ActionDefinition;
import io.springforge.model.CrudDefinition;
import io.springforge.model.EntityDefinition;
import io.springforge.model.FieldDefinition;
import io.springforge.model.FilterDefinition;
import io.springforge.model.ForgeDefinition;
import io.springforge.model.ProjectConfig;
import io.springforge.model.RelationDefinition;
import io.springforge.model.TemplateConfig;
import io.springforge.model.VisualTemplateDefinition;

public class ForgeJsonParser {

    private static final Pattern PASCAL_CASE = Pattern.compile("^[A-Z][a-zA-Z0-9]*$");
    private static final Pattern CAMEL_CASE = Pattern.compile("^[a-z][a-zA-Z0-9]*$");
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*$");
    private static final Pattern JAVA_IDENTIFIER = Pattern.compile("^[A-Za-z_$][A-Za-z0-9_$]*$");
    private static final Pattern API_PATH = Pattern.compile("^/[A-Za-z0-9_{}\\-/.]*$");
    private static final Pattern FIELD_PATH = Pattern.compile("^[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*$");
    private static final Pattern SQL_IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

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

    private final ObjectMapper mapper = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    public ForgeDefinition parse(File jsonFile) throws MojoExecutionException {
        ValidationReport report = validateFile(jsonFile);
        if (!report.isValid()) {
            throw new MojoExecutionException(formatReport(report));
        }
        try {
            return mapper.readValue(jsonFile, ForgeDefinition.class);
        } catch (Exception e) {
            throw new MojoExecutionException("Erro ao ler forge.json após validação: " + e.getMessage(), e);
        }
    }

    /**
     * Valida o forge.json e retorna erros estruturados para o dashboard.
     */
    public ValidationReport validateFile(File jsonFile) {
        List<ValidationIssue> issues = new ArrayList<>();
        if (jsonFile == null || !jsonFile.exists()) {
            issues.add(error("FILE_NOT_FOUND", "forge.json", "Arquivo forge.json não encontrado",
                "O plugin não encontrou o arquivo de configuração no caminho informado.",
                "Verifique o parâmetro -Dforge.inputFile ou crie um forge.json na raiz do projeto.",
                "mvn spring-forge:generate -Dforge.inputFile=forge.json"));
            return ValidationReport.of(issues);
        }

        ForgeDefinition def = null;
        try {
            def = mapper.readValue(jsonFile, ForgeDefinition.class);
        } catch (UnrecognizedPropertyException e) {
            String path = jacksonPath(e);
            issues.add(new ValidationIssue("error", "UNKNOWN_PROPERTY", path,
                "Propriedade desconhecida: '" + e.getPropertyName() + "'",
                "O campo '" + e.getPropertyName() + "' não existe nesse ponto do forge.json. Isso geralmente é erro de digitação ou opção colocada no bloco errado.",
                "Remova o campo, corrija o nome, ou mova para o bloco correto. Campos aceitos aqui: " + e.getKnownPropertyIds(),
                "Verifique o forge-schema.json para ver os campos aceitos.",
                line(e), column(e)));
        } catch (InvalidFormatException e) {
            issues.add(new ValidationIssue("error", "INVALID_VALUE", jacksonPath(e),
                "Valor inválido para o tipo esperado",
                "O valor informado não pôde ser convertido para o tipo Java esperado. Isso acontece muito com enums como architectureStyle.",
                "Confira maiúsculas/minúsculas e use um dos valores suportados.",
                enumExample(e), line(e), column(e)));
        } catch (MismatchedInputException e) {
            issues.add(new ValidationIssue("error", "WRONG_JSON_TYPE", jacksonPath(e),
                "Tipo JSON incompatível",
                "O campo recebeu um tipo diferente do esperado. Exemplo: string onde deveria ser lista, objeto onde deveria ser texto, ou número onde deveria ser boolean.",
                "Ajuste o valor conforme o schema. Listas usam [], objetos usam {}, textos usam aspas.",
                "roles deve ser [\"ADMIN\", \"USER\"], não \"ADMIN\".",
                line(e), column(e)));
        } catch (JsonMappingException e) {
            issues.add(new ValidationIssue("error", "JSON_MAPPING_ERROR", jacksonPath(e),
                "Erro ao mapear o JSON",
                e.getOriginalMessage(),
                "Verifique o campo indicado e compare com o forge-schema.json.",
                null, line(e), column(e)));
        } catch (JsonProcessingException e) {
            issues.add(new ValidationIssue("error", "INVALID_JSON", "forge.json",
                "JSON inválido",
                e.getOriginalMessage(),
                "Corrija a sintaxe do JSON: vírgulas, aspas, chaves e colchetes.",
                "Use um validador JSON ou a aba JSON do dashboard.",
                line(e), column(e)));
        } catch (Exception e) {
            issues.add(error("PARSE_ERROR", "forge.json", "Erro ao ler forge.json", e.getMessage(),
                "Revise o arquivo e tente validar novamente.", null));
        }

        if (def != null) {
            validateDefinition(def, issues);
        }
        return ValidationReport.of(issues);
    }

    private void validateDefinition(ForgeDefinition def, List<ValidationIssue> issues) {
        validateProject(def, issues);
        validateEntities(def, issues);
    }

    private String formatReport(ValidationReport report) {
        StringBuilder msg = new StringBuilder("forge.json inválido — encontrei ")
            .append(report.getErrorCount()).append(" erro(s)");
        if (report.getWarningCount() > 0) {
            msg.append(" e ").append(report.getWarningCount()).append(" aviso(s)");
        }
        msg.append(":\n");
        int i = 1;
        for (ValidationIssue issue : report.getIssues()) {
            msg.append("\n").append(i++).append(") ").append(issue.getTitle()).append("\n")
               .append("   Onde: ").append(issue.getPath()).append("\n")
               .append("   Problema: ").append(issue.getMessage()).append("\n");
            if (issue.getFix() != null && !issue.getFix().isBlank()) {
                msg.append("   Como corrigir: ").append(issue.getFix()).append("\n");
            }
            if (issue.getExample() != null && !issue.getExample().isBlank()) {
                msg.append("   Exemplo: ").append(issue.getExample()).append("\n");
            }
        }
        msg.append("\nDica: abra o dashboard e clique em 'Validar' para ver esses erros em cards, ou gere o schema com 'mvn spring-forge:schema'.");
        return msg.toString();
    }

    private void validateProject(ForgeDefinition def, List<ValidationIssue> issues) {
        if (def == null || def.getProject() == null) {
            issues.add(error("MISSING_PROJECT", "project", "Bloco project é obrigatório",
                "O forge.json precisa ter um bloco project com pelo menos basePackage e name.",
                "Adicione o bloco project na raiz do JSON.",
                "{ \"project\": { \"basePackage\": \"com.myapp\", \"name\": \"MyApp\" }, \"entities\": [] }"));
            return;
        }
        ProjectConfig p = def.getProject();
        if (blank(p.getBasePackage())) {
            issues.add(error("MISSING_BASE_PACKAGE", "project.basePackage", "Pacote base obrigatório",
                "O gerador precisa saber o pacote Java raiz onde as classes serão criadas.",
                "Informe um pacote Java válido, todo em minúsculo.", "com.myapp"));
        } else if (!PACKAGE_PATTERN.matcher(p.getBasePackage()).matches()) {
            issues.add(error("INVALID_BASE_PACKAGE", "project.basePackage", "Pacote Java inválido",
                "Valor atual: '" + p.getBasePackage() + "'. Pacotes Java devem ser separados por ponto, iniciar com letra minúscula e não podem ter hífen/espaço.",
                "Use algo como com.empresa.sistema.", "com.acme.catalog"));
        }
        if (blank(p.getName())) {
            issues.add(error("MISSING_PROJECT_NAME", "project.name", "Nome do projeto obrigatório",
                "O dashboard e os templates usam project.name para nomes e documentação.",
                "Informe um nome curto do projeto.", "CatalogApi"));
        }
        if (p.getArchitectureStyle() == null) {
            issues.add(error("INVALID_ARCHITECTURE", "project.architectureStyle", "Arquitetura inválida",
                "O valor não foi reconhecido como arquitetura suportada.",
                "Use LAYERED, HEXAGONAL, MODULAR ou SESI_LABORAL.", "MODULAR"));
        }
        String db = p.getDatabase();
        if (db != null && !Set.of("postgres", "mysql", "mongodb", "h2").contains(db.toLowerCase())) {
            issues.add(error("INVALID_DATABASE", "project.database", "Banco de dados inválido",
                "Valor atual: '" + db + "'. O gerador só sabe configurar bancos suportados.",
                "Use postgres, mysql, mongodb ou h2.", "postgres"));
        }
        validateTemplates(p.getTemplates(), issues);
    }

    private void validateTemplates(TemplateConfig templates, List<ValidationIssue> issues) {
        if (templates == null) return;
        if (templates.isEnabled() && blank(templates.getTemplateDir()) && (templates.getVisualTemplates() == null || templates.getVisualTemplates().isEmpty())) {
            issues.add(warn("TEMPLATE_NO_SOURCE", "project.templates", "Templates habilitados, mas sem origem clara",
                "Você ativou templates, mas não informou templateDir e também não criou templates visuais.",
                "Informe templateDir ou crie pelo menos um visualTemplate.", ".spring-forge/templates"));
        }
        int i = 0;
        for (VisualTemplateDefinition t : safe(templates.getVisualTemplates())) {
            String path = "project.templates.visualTemplates[" + i + "]";
            if (!t.isEnabled()) { i++; continue; }
            if (blank(t.getName())) {
                issues.add(error("VISUAL_TEMPLATE_NAME", path + ".name", "Template visual sem nome",
                    "Templates visuais precisam de um nome para aparecerem claramente no dashboard.",
                    "Preencha o nome do template.", "README por entidade"));
            }
            if (blank(t.getScope()) || !Set.of("entity", "global").contains(t.getScope())) {
                issues.add(error("VISUAL_TEMPLATE_SCOPE", path + ".scope", "Escopo de template inválido",
                    "O escopo define se o template roda uma vez por entidade ou uma vez globalmente.",
                    "Use entity ou global.", "entity"));
            }
            if (blank(t.getPath())) {
                issues.add(error("VISUAL_TEMPLATE_PATH", path + ".path", "Template visual sem caminho de saída",
                    "Sem path o gerador não sabe onde salvar o arquivo renderizado.",
                    "Informe um caminho relativo. Você pode usar placeholders.", "docs/entities/{{entityKebab}}.md"));
            }
            if (t.getContent() == null) {
                issues.add(error("VISUAL_TEMPLATE_CONTENT", path + ".content", "Template visual sem conteúdo",
                    "O conteúdo pode ser vazio, mas precisa existir como string para evitar ambiguidade no JSON.",
                    "Defina content como texto.", "# {{EntityName}}"));
            }
            i++;
        }
    }

    private void validateEntities(ForgeDefinition def, List<ValidationIssue> issues) {
        if (def.getEntities() == null || def.getEntities().isEmpty()) {
            issues.add(error("MISSING_ENTITIES", "entities", "Nenhuma entidade definida",
                "O gerador precisa de pelo menos uma entidade para criar CRUD, frontend, migrations e filtros.",
                "Adicione uma entidade pelo dashboard ou no array entities.", "[{ \"name\": \"Product\", \"fields\": [] }]"));
            return;
        }

        Set<String> entityNames = new LinkedHashSet<>();
        Map<String, Integer> firstSeen = new HashMap<>();
        for (int i = 0; i < def.getEntities().size(); i++) {
            EntityDefinition e = def.getEntities().get(i);
            String ep = entityPath(def, i);
            String name = e != null ? e.getName() : null;
            if (blank(name)) {
                issues.add(error("ENTITY_NAME_REQUIRED", ep + ".name", "Entidade sem nome",
                    "Cada entidade precisa de um nome em PascalCase para gerar classes Java.",
                    "Informe um nome como Product, Category ou OrderItem.", "Product"));
            } else {
                if (!PASCAL_CASE.matcher(name).matches()) {
                    issues.add(error("ENTITY_NAME_CASE", ep + ".name", "Nome de entidade fora do padrão",
                        "Valor atual: '" + name + "'. Entidades viram classes Java e devem usar PascalCase.",
                        "Comece com letra maiúscula e remova espaços, hífens ou underscores.", "OrderItem"));
                }
                Integer previous = firstSeen.putIfAbsent(name, i);
                if (previous != null) {
                    issues.add(error("DUPLICATE_ENTITY", ep + ".name", "Entidade duplicada",
                        "A entidade '" + name + "' já foi definida em " + entityPath(def, previous) + ". Isso gera classes duplicadas.",
                        "Renomeie uma das entidades ou remova a duplicada.", "Product e ProductVariant"));
                }
                entityNames.add(name);
            }
        }

        for (int i = 0; i < def.getEntities().size(); i++) {
            EntityDefinition e = def.getEntities().get(i);
            if (e == null) continue;
            String ep = entityPath(def, i);
            validateEntityMetadata(e, ep, issues);
            validateFields(e, ep, issues);
            validateRelations(def, e, ep, entityNames, issues);
            validateActions(e, ep, issues);
            validateFilterList(def, e, e.getFilters(), ep + ".filters", issues);
            validateCrud(def, e, ep, issues);
        }
    }

    private void validateEntityMetadata(EntityDefinition e, String ep, List<ValidationIssue> issues) {
        if (!blank(e.getSchema()) && !SQL_IDENTIFIER.matcher(e.getSchema()).matches()) {
            issues.add(error("INVALID_ENTITY_SCHEMA", ep + ".schema", "Schema da entidade inválido",
                "Valor atual: '" + e.getSchema() + "'. O schema deve ser um identificador SQL simples.",
                "Use apenas letras, números e underscore, começando por letra ou underscore.", "public"));
        }
        if (!blank(e.getApiPath()) && !API_PATH.matcher(e.getApiPath()).matches()) {
            issues.add(error("INVALID_ENTITY_API_PATH", ep + ".apiPath", "Path da entidade inválido",
                "Valor atual: '" + e.getApiPath() + "'. O path precisa começar com / e não pode conter espaços/aspas.",
                "Use um path REST simples.", "/api/v1/products"));
        }
    }

    private void validateFields(EntityDefinition e, String ep, List<ValidationIssue> issues) {
        Set<String> fieldNames = new HashSet<>();
        if (e.getFields() == null) {
            issues.add(error("FIELDS_MUST_BE_ARRAY", ep + ".fields", "fields precisa ser uma lista",
                "O bloco fields deve ser um array JSON, mesmo quando estiver vazio.",
                "Use fields: [] ou adicione campos pelo dashboard.", "\"fields\": []"));
            return;
        }
        for (int j = 0; j < e.getFields().size(); j++) {
            FieldDefinition f = e.getFields().get(j);
            String fp = ep + ".fields[" + j + "]";
            if (f == null) {
                issues.add(error("FIELD_NULL", fp, "Campo vazio dentro de fields",
                    "Existe um item nulo no array fields.", "Remova o item ou preencha name/type.", "{ \"name\": \"name\", \"type\": \"String\" }"));
                continue;
            }
            if (blank(f.getName())) {
                issues.add(error("FIELD_NAME_REQUIRED", fp + ".name", "Campo sem nome",
                    "Cada field precisa de name para gerar atributo, coluna, DTO e filtros.",
                    "Informe um nome em camelCase.", "productName"));
            } else {
                if (!CAMEL_CASE.matcher(f.getName()).matches()) {
                    issues.add(error("FIELD_NAME_CASE", fp + ".name", "Nome de campo fora do padrão",
                        "Valor atual: '" + f.getName() + "'. Campos viram atributos Java e devem usar camelCase.",
                        "Comece com letra minúscula e remova espaços, hífens ou underscores.", "createdAt"));
                }
                if (!fieldNames.add(f.getName())) {
                    issues.add(error("DUPLICATE_FIELD", fp + ".name", "Campo duplicado na entidade " + safeEntityName(e),
                        "O campo '" + f.getName() + "' já existe nessa entidade. Isso gera atributo/coluna duplicada.",
                        "Renomeie ou remova um dos campos duplicados.", "name e displayName"));
                }
            }
            validateFieldType(f, fp, issues);
            if (!blank(f.getColumnName()) && !SQL_IDENTIFIER.matcher(f.getColumnName()).matches()) {
                issues.add(error("INVALID_COLUMN_NAME", fp + ".columnName", "Nome de coluna inválido",
                    "Valor atual: '" + f.getColumnName() + "'. Colunas devem usar letras, números e underscore, sem espaços/hífens.",
                    "Use snake_case quando quiser customizar o nome da coluna.", "product_name"));
            }
            if (f.getMaxLength() != null && f.getMinLength() != null && f.getMinLength() > f.getMaxLength()) {
                issues.add(error("INVALID_LENGTH_RANGE", fp, "minLength maior que maxLength",
                    "minLength=" + f.getMinLength() + " e maxLength=" + f.getMaxLength() + ". Essa validação nunca poderá ser satisfeita.",
                    "Deixe minLength menor ou igual a maxLength.", "minLength: 3, maxLength: 100"));
            }
        }
    }

    private void validateFieldType(FieldDefinition f, String fp, List<ValidationIssue> issues) {
        if (blank(f.getType())) {
            issues.add(error("FIELD_TYPE_REQUIRED", fp + ".type", "Tipo do campo obrigatório",
                "Sem type o gerador não sabe qual tipo Java, SQL, DTO e input frontend usar.",
                "Use um dos tipos suportados.", "String, Integer, Long, BigDecimal, Boolean, LocalDate, LocalDateTime, UUID, Enum"));
        } else if (!VALID_TYPES.contains(f.getType().toLowerCase())) {
            issues.add(error("INVALID_FIELD_TYPE", fp + ".type", "Tipo de campo inválido",
                "Valor atual: '" + f.getType() + "'. Esse tipo ainda não é suportado pelo gerador.",
                "Use: String, Integer, Long, Double, Float, BigDecimal, Boolean, LocalDate, LocalDateTime, UUID ou Enum.", "BigDecimal"));
        }
        if ("Enum".equalsIgnoreCase(f.getType()) && (f.getEnumValues() == null || f.getEnumValues().isEmpty())) {
            issues.add(error("ENUM_VALUES_REQUIRED", fp + ".enumValues", "Enum sem valores",
                "Campos do tipo Enum precisam declarar enumValues para gerar o enum Java e o select no frontend.",
                "Adicione ao menos um valor.", "[\"ACTIVE\", \"INACTIVE\"]"));
        }
        if ("Enum".equalsIgnoreCase(f.getType()) && f.getEnumValues() != null) {
            for (int i = 0; i < f.getEnumValues().size(); i++) {
                String value = f.getEnumValues().get(i);
                if (blank(value) || !JAVA_IDENTIFIER.matcher(value).matches()) {
                    issues.add(error("INVALID_ENUM_VALUE", fp + ".enumValues[" + i + "]", "Valor de enum inválido",
                        "Valor atual: '" + value + "'. Valores de enum viram constantes Java.",
                        "Use nomes sem espaços/hífens, preferencialmente em caixa alta.", "ACTIVE"));
                }
            }
        }
    }

    private void validateRelations(ForgeDefinition def, EntityDefinition e, String ep, Set<String> allEntityNames, List<ValidationIssue> issues) {
        if (e.getRelations() == null) return;
        Set<String> relationNames = new HashSet<>();
        for (int j = 0; j < e.getRelations().size(); j++) {
            RelationDefinition r = e.getRelations().get(j);
            String rp = ep + ".relations[" + j + "]";
            if (r == null) continue;
            if (blank(r.getType()) || !VALID_RELATIONS.contains(r.getType())) {
                issues.add(error("INVALID_RELATION_TYPE", rp + ".type", "Tipo de relacionamento inválido",
                    "Valor atual: '" + r.getType() + "'.", "Use ManyToOne, OneToMany, OneToOne ou ManyToMany.", "ManyToOne"));
            }
            if (blank(r.getTargetEntity())) {
                issues.add(error("RELATION_TARGET_REQUIRED", rp + ".targetEntity", "Relacionamento sem entidade alvo",
                    "O gerador precisa saber qual entidade é referenciada.",
                    "Escolha uma entidade existente no dashboard.", "Category"));
            } else if (!allEntityNames.contains(r.getTargetEntity())) {
                issues.add(error("RELATION_TARGET_NOT_FOUND", rp + ".targetEntity", "Entidade alvo não encontrada",
                    "'" + r.getTargetEntity() + "' não existe em entities. Disponíveis: " + allEntityNames + ".",
                    "Crie a entidade alvo ou corrija o nome. O nome precisa bater exatamente, incluindo maiúsculas.", bestEntitySuggestion(r.getTargetEntity(), allEntityNames)));
            }
            if (blank(r.getFieldName())) {
                issues.add(error("RELATION_FIELD_REQUIRED", rp + ".fieldName", "Relacionamento sem fieldName",
                    "fieldName é o nome do atributo Java que será criado na entidade atual.",
                    "Use camelCase, normalmente o nome da entidade alvo em minúsculo.", "category"));
            } else {
                if (!CAMEL_CASE.matcher(r.getFieldName()).matches()) {
                    issues.add(error("RELATION_FIELD_CASE", rp + ".fieldName", "fieldName do relacionamento fora do padrão",
                        "Valor atual: '" + r.getFieldName() + "'. Relacionamentos também viram atributos Java.",
                        "Use camelCase.", "category"));
                }
                if (!relationNames.add(r.getFieldName())) {
                    issues.add(error("DUPLICATE_RELATION_FIELD", rp + ".fieldName", "Relacionamento duplicado",
                        "Já existe outro relacionamento com fieldName '" + r.getFieldName() + "' nessa entidade.",
                        "Renomeie um deles para evitar conflito de atributo.", "mainCategory"));
                }
            }
            if (r.getFetch() != null && !Set.of("LAZY", "EAGER").contains(r.getFetch().toUpperCase())) {
                issues.add(error("INVALID_FETCH", rp + ".fetch", "Fetch inválido",
                    "Valor atual: '" + r.getFetch() + "'.", "Use LAZY ou EAGER.", "LAZY"));
            }
        }
    }

    private void validateActions(EntityDefinition e, String ep, List<ValidationIssue> issues) {
        if (e.getActions() == null) return;
        Set<String> actionNames = new HashSet<>();
        for (int k = 0; k < e.getActions().size(); k++) {
            ActionDefinition a = e.getActions().get(k);
            String ap = ep + ".actions[" + k + "]";
            if (a == null) continue;
            if (blank(a.getName())) {
                issues.add(error("ACTION_NAME_REQUIRED", ap + ".name", "Action sem nome",
                    "Cada action precisa de um nome para gerar método, endpoint e documentação.",
                    "Use um verbo em camelCase.", "activate"));
            } else {
                if (!CAMEL_CASE.matcher(a.getName()).matches()) {
                    issues.add(error("ACTION_NAME_CASE", ap + ".name", "Nome de action fora do padrão",
                        "Valor atual: '" + a.getName() + "'.", "Use camelCase.", "adjustStock"));
                }
                if (!actionNames.add(a.getName())) {
                    issues.add(error("DUPLICATE_ACTION", ap + ".name", "Action duplicada",
                        "A action '" + a.getName() + "' já existe nessa entidade.",
                        "Remova ou renomeie uma das actions.", "archive"));
                }
            }
            if (a.getHttpMethod() != null && !VALID_METHODS.contains(a.getHttpMethod().toUpperCase())) {
                issues.add(error("INVALID_HTTP_METHOD", ap + ".httpMethod", "Método HTTP inválido",
                    "Valor atual: '" + a.getHttpMethod() + "'.", "Use GET, POST, PUT, PATCH ou DELETE.", "PATCH"));
            }
            if (!blank(a.getApiPath()) && !API_PATH.matcher(a.getApiPath()).matches()) {
                issues.add(error("INVALID_ACTION_API_PATH", ap + ".apiPath", "Path da action inválido",
                    "Valor atual: '" + a.getApiPath() + "'.", "Use um path iniciado por / e sem espaços.", "/{id}/activate"));
            }
            if (a.isScheduled() && a.getScheduledCron() == null && a.getScheduledFixedRate() == null) {
                issues.add(error("SCHEDULE_WITHOUT_TRIGGER", ap, "Action agendada sem frequência",
                    "scheduled=true foi definido, mas não existe scheduledCron nem scheduledFixedRate.",
                    "Defina uma expressão cron ou uma frequência fixa.", "scheduledCron: \"0 0 * * * *\""));
            }
        }
    }

    private void validateCrud(ForgeDefinition def, EntityDefinition e, String ep, List<ValidationIssue> issues) {
        CrudDefinition crud = e.getCrud();
        if (crud == null) return;
        if (crud.getDefaultPageSize() < 1) {
            issues.add(error("INVALID_DEFAULT_PAGE_SIZE", ep + ".crud.defaultPageSize", "Tamanho padrão de página inválido",
                "defaultPageSize precisa ser maior que zero.", "Use um número positivo.", "20"));
        }
        if (crud.getMaxPageSize() < 1) {
            issues.add(error("INVALID_MAX_PAGE_SIZE", ep + ".crud.maxPageSize", "Tamanho máximo de página inválido",
                "maxPageSize precisa ser maior que zero.", "Use um número positivo.", "100"));
        }
        if (crud.getDefaultPageSize() > crud.getMaxPageSize()) {
            issues.add(error("PAGE_SIZE_RANGE", ep + ".crud", "defaultPageSize maior que maxPageSize",
                "defaultPageSize=" + crud.getDefaultPageSize() + " e maxPageSize=" + crud.getMaxPageSize() + ". O padrão não pode ultrapassar o limite.",
                "Aumente maxPageSize ou reduza defaultPageSize.", "defaultPageSize: 20, maxPageSize: 100"));
        }
        if (!blank(crud.getDefaultSort())) {
            validateFieldPath(def, e, crud.getDefaultSort(), ep + ".crud.defaultSort", "sort", issues);
        }
        if (crud.getDefaultDirection() != null && !Set.of("ASC", "DESC").contains(crud.getDefaultDirection().toUpperCase())) {
            issues.add(error("INVALID_SORT_DIRECTION", ep + ".crud.defaultDirection", "Direção de ordenação inválida",
                "Valor atual: '" + crud.getDefaultDirection() + "'.", "Use ASC ou DESC.", "ASC"));
        }
        if (crud.getSortable() != null) {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < crud.getSortable().size(); i++) {
                String sort = crud.getSortable().get(i);
                String sp = ep + ".crud.sortable[" + i + "]";
                validateFieldPath(def, e, sort, sp, "sort", issues);
                if (!blank(sort) && !seen.add(sort)) {
                    issues.add(warn("DUPLICATE_SORT", sp, "Campo de ordenação repetido",
                        "'" + sort + "' aparece mais de uma vez em sortable.",
                        "Remova a repetição.", sort));
                }
            }
        }
        validateFilterList(def, e, crud.getFilterable(), ep + ".crud.filterable", issues);
    }

    private void validateFilterList(ForgeDefinition def, EntityDefinition e, List<FilterDefinition> filters, String path, List<ValidationIssue> issues) {
        if (filters == null) return;
        Set<String> names = new HashSet<>();
        for (int j = 0; j < filters.size(); j++) {
            FilterDefinition f = filters.get(j);
            String fp = path + "[" + j + "]";
            if (f == null) continue;
            if (blank(f.getName())) {
                issues.add(error("FILTER_NAME_REQUIRED", fp + ".name", "Filtro sem nome",
                    "name é o nome do parâmetro que será aceito na URL e usado no frontend.",
                    "Use camelCase, de preferência descrevendo o campo filtrado.", "categoryName"));
            } else {
                if (!CAMEL_CASE.matcher(f.getName()).matches()) {
                    issues.add(error("FILTER_NAME_CASE", fp + ".name", "Nome de filtro fora do padrão",
                        "Valor atual: '" + f.getName() + "'.", "Use camelCase.", "categoryName"));
                }
                if (!names.add(f.getName())) {
                    issues.add(error("DUPLICATE_FILTER", fp + ".name", "Filtro duplicado",
                        "Já existe outro filtro com name '" + f.getName() + "'. Isso gera parâmetros duplicados na API.",
                        "Renomeie um dos filtros.", "categoryName e categoryId"));
                }
            }
            if (blank(f.getType())) {
                issues.add(error("FILTER_TYPE_REQUIRED", fp + ".type", "Tipo do filtro obrigatório",
                    "O tipo é necessário para converter o valor recebido na URL.",
                    "Use um tipo compatível com o campo alvo.", "String"));
            } else if (!VALID_TYPES.contains(f.getType().toLowerCase())) {
                issues.add(error("INVALID_FILTER_TYPE", fp + ".type", "Tipo de filtro inválido",
                    "Valor atual: '" + f.getType() + "'.", "Use um dos tipos suportados pelo gerador.", "String"));
            }
            if (f.getOperator() != null && !VALID_OPERATORS.contains(f.getOperator().toUpperCase())) {
                issues.add(error("INVALID_FILTER_OPERATOR", fp + ".operator", "Operador de filtro inválido",
                    "Valor atual: '" + f.getOperator() + "'.", "Use: " + VALID_OPERATORS + ".", "CONTAINS"));
            }
            String target = blank(f.getTargetField()) ? f.getName() : f.getTargetField();
            validateFieldPath(def, e, target, fp + (blank(f.getTargetField()) ? ".name" : ".targetField"), "filter", issues);
            if (f.getOperator() != null && isStringOnlyOperator(f.getOperator()) && !"string".equalsIgnoreCase(f.getType())) {
                issues.add(warn("FILTER_OPERATOR_TYPE", fp + ".operator", "Operador textual em filtro não textual",
                    "O operador " + f.getOperator() + " normalmente faz sentido para String, mas o filtro está como type=" + f.getType() + ".",
                    "Troque para EQUALS/GREATER_THAN/etc. ou mude type para String se o campo alvo for textual.", "EQUALS"));
            }
        }
    }

    private boolean isStringOnlyOperator(String operator) {
        if (operator == null) return false;
        String op = operator.toUpperCase();
        return op.equals("CONTAINS") || op.equals("STARTS_WITH") || op.equals("ENDS_WITH");
    }

    private void validateFieldPath(ForgeDefinition def, EntityDefinition entity, String fieldPath, String path, String usage, List<ValidationIssue> issues) {
        if (blank(fieldPath)) {
            issues.add(error("FIELD_PATH_REQUIRED", path, "Campo alvo obrigatório",
                "Esse item precisa apontar para um campo da entidade ou de relacionamento.",
                "Escolha um campo no builder visual.", "name ou category.name"));
            return;
        }
        if (!FIELD_PATH.matcher(fieldPath).matches()) {
            issues.add(error("INVALID_FIELD_PATH", path, "Caminho de campo inválido",
                "Valor atual: '" + fieldPath + "'. Use campo simples ou relacionamento.campo, sem espaços, colchetes ou caracteres especiais.",
                "Use o builder de filtros para selecionar o campo automaticamente.", "category.name"));
            return;
        }
        FieldPathResolution resolution = resolveFieldPath(def, entity, fieldPath);
        if (!resolution.exists) {
            String title = "Campo usado em " + ("sort".equals(usage) ? "ordenação" : "filtro") + " não existe";
            issues.add(error("UNKNOWN_FIELD_PATH", path, title,
                "O caminho '" + fieldPath + "' não foi encontrado a partir da entidade " + safeEntityName(entity) + ". " + resolution.reason,
                "Confira se o campo existe ou se o relacionamento foi declarado corretamente. Para filtrar por nome da categoria, Product precisa ter relação fieldName=category targetEntity=Category e Category precisa ter field name.",
                firstAvailableFieldPath(def, entity)));
        }
    }

    private FieldPathResolution resolveFieldPath(ForgeDefinition def, EntityDefinition start, String fieldPath) {
        if (start == null || blank(fieldPath)) return FieldPathResolution.missing("Entidade inicial não encontrada.");
        String[] parts = fieldPath.split("\\.");
        EntityDefinition current = start;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (hasField(current, part)) {
                if (i == parts.length - 1) return FieldPathResolution.ok();
                return FieldPathResolution.missing("'" + part + "' é um campo simples, então não pode ter subcampo depois dele.");
            }
            RelationDefinition rel = relationByField(current, part);
            if (rel != null) {
                if (i == parts.length - 1) return FieldPathResolution.ok();
                EntityDefinition target = findEntity(def, rel.getTargetEntity());
                if (target == null) return FieldPathResolution.missing("A relação '" + part + "' aponta para '" + rel.getTargetEntity() + "', mas essa entidade não existe.");
                current = target;
                continue;
            }
            return FieldPathResolution.missing("Na entidade " + safeEntityName(current) + ", não existe field nem relationship chamado '" + part + "'.");
        }
        return FieldPathResolution.ok();
    }

    private boolean hasField(EntityDefinition entity, String fieldName) {
        // O ID é gerado automaticamente pelo EntityGenerator e não precisa ser
        // repetido no array fields da entidade.
        if ("id".equals(fieldName)) return true;
        for (FieldDefinition f : safe(entity.getFields())) {
            if (f != null && fieldName.equals(f.getName())) return true;
        }
        return false;
    }

    private RelationDefinition relationByField(EntityDefinition entity, String fieldName) {
        for (RelationDefinition r : safe(entity.getRelations())) {
            if (r != null && fieldName.equals(r.getFieldName())) return r;
        }
        return null;
    }

    private EntityDefinition findEntity(ForgeDefinition def, String name) {
        if (def == null || def.getEntities() == null) return null;
        for (EntityDefinition e : def.getEntities()) {
            if (e != null && name != null && name.equals(e.getName())) return e;
        }
        return null;
    }

    private String firstAvailableFieldPath(ForgeDefinition def, EntityDefinition entity) {
        if (entity == null) return null;
        for (FieldDefinition f : safe(entity.getFields())) {
            if (f != null && !blank(f.getName())) return f.getName();
        }
        for (RelationDefinition r : safe(entity.getRelations())) {
            EntityDefinition target = findEntity(def, r.getTargetEntity());
            if (target != null) {
                for (FieldDefinition f : safe(target.getFields())) {
                    if (f != null && !blank(f.getName())) return r.getFieldName() + "." + f.getName();
                }
            }
        }
        return "name ou category.name";
    }

    private String bestEntitySuggestion(String target, Set<String> available) {
        if (available == null || available.isEmpty()) return "Crie a entidade alvo primeiro.";
        if (target == null) return available.iterator().next();
        for (String name : available) {
            if (name.equalsIgnoreCase(target)) return name;
        }
        return "Entidades disponíveis: " + available;
    }

    private String entityPath(ForgeDefinition def, int index) {
        String name = null;
        try { name = def.getEntities().get(index).getName(); } catch (Exception ignored) {}
        return blank(name) ? "entities[" + index + "]" : "entities[" + index + "](" + name + ")";
    }

    private String safeEntityName(EntityDefinition e) {
        return e == null || blank(e.getName()) ? "<sem nome>" : e.getName();
    }

    private static boolean blank(String s) { return s == null || s.isBlank(); }

    private static <T> List<T> safe(List<T> list) { return list == null ? List.of() : list; }

    private ValidationIssue error(String code, String path, String title, String message, String fix, String example) {
        return new ValidationIssue("error", code, path, title, message, fix, example, null, null);
    }

    private ValidationIssue warn(String code, String path, String title, String message, String fix, String example) {
        return new ValidationIssue("warning", code, path, title, message, fix, example, null, null);
    }

    private String jacksonPath(JsonMappingException e) {
        if (e.getPath() == null || e.getPath().isEmpty()) return "forge.json";
        StringBuilder sb = new StringBuilder();
        for (JsonMappingException.Reference ref : e.getPath()) {
            if (ref.getFieldName() != null) {
                if (sb.length() > 0) sb.append('.');
                sb.append(ref.getFieldName());
            } else if (ref.getIndex() >= 0) {
                sb.append('[').append(ref.getIndex()).append(']');
            }
        }
        return sb.length() == 0 ? "forge.json" : sb.toString();
    }

    private Integer line(JsonProcessingException e) {
        return e.getLocation() == null ? null : e.getLocation().getLineNr();
    }

    private Integer column(JsonProcessingException e) {
        return e.getLocation() == null ? null : e.getLocation().getColumnNr();
    }

    private String enumExample(InvalidFormatException e) {
        Class<?> targetType = e.getTargetType();
        if (targetType != null && targetType.isEnum()) {
            Object[] constants = targetType.getEnumConstants();
            if (constants != null && constants.length > 0) {
                StringBuilder sb = new StringBuilder("Valores aceitos: ");
                for (int i = 0; i < constants.length; i++) {
                    if (i > 0) sb.append(", ");
                    sb.append(constants[i]);
                }
                return sb.toString();
            }
        }
        return "Confira o forge-schema.json para ver os valores aceitos.";
    }

    private static class FieldPathResolution {
        private final boolean exists;
        private final String reason;
        private FieldPathResolution(boolean exists, String reason) { this.exists = exists; this.reason = reason; }
        static FieldPathResolution ok() { return new FieldPathResolution(true, null); }
        static FieldPathResolution missing(String reason) { return new FieldPathResolution(false, reason); }
    }

    public static class ValidationReport {
        private final boolean valid;
        private final String message;
        private final int errorCount;
        private final int warningCount;
        private final List<ValidationIssue> issues;

        private ValidationReport(List<ValidationIssue> issues) {
            this.issues = issues;
            int errors = 0;
            int warnings = 0;
            for (ValidationIssue issue : issues) {
                if ("warning".equals(issue.getSeverity())) warnings++; else errors++;
            }
            this.errorCount = errors;
            this.warningCount = warnings;
            this.valid = errors == 0;
            if (valid) {
                this.message = warnings == 0 ? "forge.json válido — nenhum problema encontrado." : "forge.json válido com " + warnings + " aviso(s).";
            } else {
                this.message = "forge.json inválido — " + errors + " erro(s) encontrado(s)" + (warnings > 0 ? " e " + warnings + " aviso(s)" : "") + ".";
            }
        }

        static ValidationReport of(List<ValidationIssue> issues) { return new ValidationReport(issues); }
        public boolean isValid() { return valid; }
        public String getMessage() { return message; }
        public int getErrorCount() { return errorCount; }
        public int getWarningCount() { return warningCount; }
        public List<ValidationIssue> getIssues() { return issues; }
    }

    public static class ValidationIssue {
        private final String severity;
        private final String code;
        private final String path;
        private final String title;
        private final String message;
        private final String fix;
        private final String example;
        private final Integer line;
        private final Integer column;

        public ValidationIssue(String severity, String code, String path, String title, String message, String fix, String example, Integer line, Integer column) {
            this.severity = severity;
            this.code = code;
            this.path = path;
            this.title = title;
            this.message = message;
            this.fix = fix;
            this.example = example;
            this.line = line;
            this.column = column;
        }
        public String getSeverity() { return severity; }
        public String getCode() { return code; }
        public String getPath() { return path; }
        public String getTitle() { return title; }
        public String getMessage() { return message; }
        public String getFix() { return fix; }
        public String getExample() { return example; }
        public Integer getLine() { return line; }
        public Integer getColumn() { return column; }
    }
}
