package io.springforge.generator;

import io.springforge.model.ActionDefinition;
import io.springforge.model.EntityDefinition;
import io.springforge.model.ForgeDefinition;
import io.springforge.util.NamingUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.util.List;

/**
 * Enriquece o controller gerado com anotações SpringDoc/OpenAPI.
 *
 * Como o ControllerGenerator já escreveu o arquivo, este generator
 * produz um arquivo separado: ${Entity}ControllerOpenApiMixin.
 *
 * Estratégia: gera uma interface com os @Operation/@ApiResponse que pode
 * ser implementada pelo controller — ou, se preferir, o usuário pode
 * configurar spring-forge para sobrescrever o controller diretamente.
 *
 * Gera por entidade:
 *   - ${Entity}ControllerDocs  — interface com @Tag, @Operation, @ApiResponse
 */
public class OpenApiEnricher extends AbstractGenerator {

    public OpenApiEnricher(Log log) { super(log); }

    @Override
    public void generate(ForgeDefinition def, EntityDefinition entity, File outDir) throws MojoExecutionException {
        if (!def.getProject().isGenerateOpenApi()) return;
        if (!entity.shouldGenerate("controller")) return;

        String pkg = controllerPkg(def);
        writeFile(buildDocs(def, entity, pkg),
                  javaFile(outDir, pkg, entity.getName() + "ControllerDocs"), pkg);
    }

    private CodeWriter buildDocs(ForgeDefinition def, EntityDefinition entity, String pkg) {
        String name = entity.getName();
        String dtoPkg = dtoPkg(def);
        String apiPath = entity.getApiPath() != null ? entity.getApiPath()
            : "/api/v1/" + NamingUtils.toSnakeCase(NamingUtils.toPlural(name)).replace("_", "-");

        // OpenAPI tags: prioriza as da entidade, fallback para o nome
        List<String> tags = entity.getOpenApiTags().isEmpty()
            ? List.of(name)
            : entity.getOpenApiTags();

        CodeWriter w = new CodeWriter();

        w.imp("io.swagger.v3.oas.annotations.Operation")
         .imp("io.swagger.v3.oas.annotations.Parameter")
         .imp("io.swagger.v3.oas.annotations.media.Content")
         .imp("io.swagger.v3.oas.annotations.media.Schema")
         .imp("io.swagger.v3.oas.annotations.responses.ApiResponse")
         .imp("io.swagger.v3.oas.annotations.responses.ApiResponses")
         .imp("io.swagger.v3.oas.annotations.tags.Tag")
         .imp("org.springframework.data.domain.Page")
         .imp("org.springframework.data.domain.Pageable")
         .imp("org.springframework.http.ResponseEntity")
         .imp(dtoPkg + "." + name + "RequestDTO")
         .imp(dtoPkg + "." + name + "ResponseDTO");

        for (ActionDefinition a : entity.getActions()) {
            if (a.getHttpMethod() == null) continue;
            if (a.hasRequest())  w.imp(dtoPkg + "." + a.getRequestDtoName());
            if (a.hasResponse()) w.imp(dtoPkg + "." + a.getResponseDtoName());
        }

        w.javadoc("Interface de documentação OpenAPI/Swagger para " + name + "Controller.\n"
                + "Implemente esta interface no controller ou use como referência.\n"
                + "Gerado pelo Spring Forge.");

        if (tags.size() == 1) {
            w.line("@Tag(name = \"" + tags.get(0) + "\", description = \"Operações sobre " + name + "\")");
        } else {
            w.imp("io.swagger.v3.oas.annotations.tags.Tags");
            StringBuilder tagsAnnotation = new StringBuilder("@Tags({");
            for (int i = 0; i < tags.size(); i++) {
                if (i > 0) tagsAnnotation.append(", ");
                tagsAnnotation.append("@Tag(name = \"").append(tags.get(i)).append("\")");
            }
            tagsAnnotation.append("})");
            w.line(tagsAnnotation.toString());
        }
        w.line("public interface " + name + "ControllerDocs {").blank();
        w.indent();

        // findAll
        w.line("@Operation(summary = \"Lista " + name + " com paginação\")")
         .line("@ApiResponses({")
         .line("    @ApiResponse(responseCode = \"200\", description = \"Lista retornada com sucesso\",")
         .line("                 content = @Content(schema = @Schema(implementation = " + name + "ResponseDTO.class)))")
         .line("})")
         .line("ResponseEntity<Page<" + name + "ResponseDTO>> findAll(Pageable pageable);").blank();

        // findById
        w.line("@Operation(summary = \"Busca " + name + " por ID\")")
         .line("@ApiResponses({")
         .line("    @ApiResponse(responseCode = \"200\", description = \"Encontrado\",")
         .line("                 content = @Content(schema = @Schema(implementation = " + name + "ResponseDTO.class))),")
         .line("    @ApiResponse(responseCode = \"404\", description = \"" + name + " não encontrado\")")
         .line("})")
         .line("ResponseEntity<" + name + "ResponseDTO> findById(")
         .line("    @Parameter(description = \"ID do " + name + "\", required = true) Long id);").blank();

        // create
        w.line("@Operation(summary = \"Cria novo " + name + "\")")
         .line("@ApiResponses({")
         .line("    @ApiResponse(responseCode = \"201\", description = \"Criado com sucesso\",")
         .line("                 content = @Content(schema = @Schema(implementation = " + name + "ResponseDTO.class))),")
         .line("    @ApiResponse(responseCode = \"400\", description = \"Dados inválidos\")")
         .line("})")
         .line("ResponseEntity<" + name + "ResponseDTO> create(" + name + "RequestDTO dto);").blank();

        // update
        w.line("@Operation(summary = \"Atualiza " + name + "\")")
         .line("@ApiResponses({")
         .line("    @ApiResponse(responseCode = \"200\", description = \"Atualizado com sucesso\"),")
         .line("    @ApiResponse(responseCode = \"404\", description = \"" + name + " não encontrado\")")
         .line("})")
         .line("ResponseEntity<" + name + "ResponseDTO> update(")
         .line("    @Parameter(description = \"ID do " + name + "\", required = true) Long id,")
         .line("    " + name + "RequestDTO dto);").blank();

        // delete
        w.line("@Operation(summary = \"Remove " + name + "\")")
         .line("@ApiResponses({")
         .line("    @ApiResponse(responseCode = \"204\", description = \"Removido com sucesso\"),")
         .line("    @ApiResponse(responseCode = \"404\", description = \"" + name + " não encontrado\")")
         .line("})")
         .line("ResponseEntity<Void> delete(")
         .line("    @Parameter(description = \"ID do " + name + "\", required = true) Long id);").blank();

        // Actions com endpoint HTTP
        for (ActionDefinition a : entity.getActions()) {
            if (a.getHttpMethod() == null) continue;

            String desc = a.getDescription() != null ? a.getDescription() : "Action " + a.getName();
            String retType = a.hasResponse()
                ? "ResponseEntity<" + a.getResponseDtoName() + ">"
                : "ResponseEntity<Void>";

            w.line("@Operation(summary = \"" + escapeQuotes(desc) + "\")");

            // Respostas customizadas + padrões
            w.line("@ApiResponses({");
            w.line("    @ApiResponse(responseCode = \"200\", description = \"Operação realizada com sucesso\"),");
            w.line("    @ApiResponse(responseCode = \"404\", description = \"" + name + " não encontrado\"),");
            for (ActionDefinition.OpenApiResponse r : a.getOpenApiResponses()) {
                w.line("    @ApiResponse(responseCode = \"" + r.getCode()
                    + "\", description = \"" + escapeQuotes(r.getDescription()) + "\"),");
            }
            w.line("})");

            // Assinatura do método
            StringBuilder sig = new StringBuilder(retType + " " + a.getName() + "(");
            boolean first = true;
            if (a.isRequiresId()) {
                sig.append("@Parameter(description = \"ID do ").append(name).append("\", required = true) Long id");
                first = false;
            }
            if (a.hasRequest()) {
                if (!first) sig.append(", ");
                sig.append(a.getRequestDtoName()).append(" dto");
            }
            sig.append(");");
            w.line(sig.toString()).blank();
        }

        w.unindent().line("}");
        return w;
    }

    private String escapeQuotes(String s) {
        return s == null ? "" : s.replace("\"", "\\\"");
    }
}
