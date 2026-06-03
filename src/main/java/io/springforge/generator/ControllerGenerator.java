package io.springforge.generator;

import io.springforge.model.ActionDefinition;
import io.springforge.model.EntityDefinition;
import io.springforge.model.ForgeDefinition;
import io.springforge.util.NamingUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.util.List;

public class ControllerGenerator extends AbstractGenerator {

    public ControllerGenerator(Log log) { super(log); }

    @Override
    public void generate(ForgeDefinition def, EntityDefinition entity, File outDir) throws MojoExecutionException {
        if (!entity.shouldGenerate("controller")) return;

        String pkg    = controllerPkg(def);
        String name   = entity.getName();
        String dtoPkg = dtoPkg(def);
        String svcPkg = servicePkg(def);

        String apiPath = entity.getApiPath() != null ? entity.getApiPath()
            : "/api/v1/" + NamingUtils.toSnakeCase(NamingUtils.toPlural(name)).replace("_", "-");

        CodeWriter w = new CodeWriter();

        w.imp("jakarta.validation.Valid")
         .imp("org.springframework.data.domain.Page")
         .imp("org.springframework.data.domain.Pageable")
         .imp("org.springframework.data.web.PageableDefault")
         .imp("org.springframework.http.HttpStatus")
         .imp("org.springframework.http.ResponseEntity")
         .imp("org.springframework.web.bind.annotation.*")
         .imp(dtoPkg + "." + name + "RequestDTO")
         .imp(dtoPkg + "." + name + "ResponseDTO")
         .imp(svcPkg + "." + name + "Service");

        boolean useSecurity = def.getProject().isGenerateSecurity();
        List<String> entityRoles = entity.getRoles();
        if (useSecurity && !entityRoles.isEmpty()) {
            w.imp("org.springframework.security.access.prepost.PreAuthorize");
        }

        // Imports DTOs de actions que têm endpoint
        for (ActionDefinition a : entity.getActions()) {
            if (a.getHttpMethod() == null) continue;
            if (a.hasRequest())  w.imp(dtoPkg + "." + a.getRequestDtoName());
            if (a.hasResponse()) w.imp(dtoPkg + "." + a.getResponseDtoName());
            if (useSecurity && !a.getRoles().isEmpty()) {
                w.imp("org.springframework.security.access.prepost.PreAuthorize");
            }
        }

        w.javadoc("Controller REST para " + name + ".\nBase URL: " + apiPath + "\nGerado pelo Spring Forge.");
        w.line("@RestController")
         .line("@RequestMapping(\"" + apiPath + "\")");

        // Anotação @PreAuthorize no nível de classe (se roles na entidade)
        if (useSecurity && !entityRoles.isEmpty()) {
            w.line(SecurityGenerator.preAuthorizeAnnotation(entityRoles));
        }
        w.line("public class " + name + "Controller {")
         .blank();
        w.indent();

        // Campo + construtor
        w.line("private final " + name + "Service service;")
         .blank()
         .line("public " + name + "Controller(" + name + "Service service) {")
         .indent()
         .line("this.service = service;")
         .unindent().line("}").blank();

        // GET /
        w.line("/** GET " + apiPath + " — lista com paginação */")
         .line("@GetMapping")
         .line("public ResponseEntity<Page<" + name + "ResponseDTO>> findAll(")
         .line("        @PageableDefault(size = 20, sort = \"id\") Pageable pageable) {")
         .indent()
         .line("return ResponseEntity.ok(service.findAll(pageable));")
         .unindent().line("}").blank();

        // POST /search (se entity tem filtros)
        if (entity.hasFilters()) {
            w.imp(dtoPkg + "." + name + "FilterDTO");
            w.line("/** POST " + apiPath + "/search — busca com filtros */")
             .line("@PostMapping(\"/search\")")
             .line("public ResponseEntity<Page<" + name + "ResponseDTO>> search(")
             .line("        @RequestBody " + name + "FilterDTO filter,")
             .line("        @PageableDefault(size = 20, sort = \"id\") Pageable pageable) {")
             .indent()
             .line("return ResponseEntity.ok(service.search(filter, pageable));")
             .unindent().line("}").blank();
        }

        // GET /{id}
        w.line("/** GET " + apiPath + "/{id} — busca por ID */")
         .line("@GetMapping(\"/{id}\")")
         .line("public ResponseEntity<" + name + "ResponseDTO> findById(@PathVariable Long id) {")
         .indent()
         .line("return ResponseEntity.ok(service.findById(id));")
         .unindent().line("}").blank();

        // POST /
        w.line("/** POST " + apiPath + " — cria novo registro */")
         .line("@PostMapping")
         .line("public ResponseEntity<" + name + "ResponseDTO> create(")
         .line("        @Valid @RequestBody " + name + "RequestDTO dto) {")
         .indent()
         .line("return ResponseEntity.status(HttpStatus.CREATED).body(service.create(dto));")
         .unindent().line("}").blank();

        // PUT /{id}
        w.line("/** PUT " + apiPath + "/{id} — atualiza registro */")
         .line("@PutMapping(\"/{id}\")")
         .line("public ResponseEntity<" + name + "ResponseDTO> update(")
         .line("        @PathVariable Long id,")
         .line("        @Valid @RequestBody " + name + "RequestDTO dto) {")
         .indent()
         .line("return ResponseEntity.ok(service.update(id, dto));")
         .unindent().line("}").blank();

        // DELETE /{id}
        w.line("/** DELETE " + apiPath + "/{id} — remove registro */")
         .line("@DeleteMapping(\"/{id}\")")
         .line("public ResponseEntity<Void> delete(@PathVariable Long id) {")
         .indent()
         .line("service.delete(id);")
         .line("return ResponseEntity.noContent().build();")
         .unindent().line("}").blank();

        // ── Actions com endpoint HTTP ─────────────────────────────────────────────
        boolean hasHttpActions = entity.getActions().stream()
            .anyMatch(a -> a.getHttpMethod() != null);

        if (hasHttpActions) {
            w.line("// ── Endpoints de actions customizadas ───────────────────────────────────────────")
             .blank();

            for (ActionDefinition a : entity.getActions()) {
                if (a.getHttpMethod() == null) continue;

                String method    = a.getHttpMethod().toUpperCase();
                String path      = a.getEffectiveApiPath();
                String retType   = a.hasResponse() ? "ResponseEntity<" + a.getResponseDtoName() + ">" : "ResponseEntity<Void>";
                String desc      = a.getDescription() != null ? a.getDescription() : "Action " + a.getName();

                w.line("/** " + method + " " + apiPath + path + " — " + desc + " */");
                // @PreAuthorize no nível de action (sobrescreve o da entidade)
                if (useSecurity && !a.getRoles().isEmpty()) {
                    w.line(SecurityGenerator.preAuthorizeAnnotation(a.getRoles()));
                }
                w.line("@" + toMappingAnnotation(method) + "(\"" + path + "\")")
                 .line("public " + retType + " " + a.getName() + "(");

                // Parâmetros do endpoint
                StringBuilder params = new StringBuilder();
                if (a.isRequiresId()) params.append("        @PathVariable Long id");
                if (a.hasRequest()) {
                    if (!params.isEmpty()) params.append(",\n");
                    params.append("        @Valid @RequestBody ").append(a.getRequestDtoName()).append(" dto");
                }
                if (!params.isEmpty()) {
                    for (String line : params.toString().split("\n")) w.line(line);
                }
                w.line(") {");
                w.indent();

                // Corpo do endpoint
                String serviceCall = a.getName() + "("
                    + (a.isRequiresId() ? "id" : "")
                    + (a.isRequiresId() && a.hasRequest() ? ", " : "")
                    + (a.hasRequest() ? "dto" : "")
                    + ")";

                if (a.hasResponse()) {
                    w.line("return ResponseEntity.ok(service." + serviceCall + ");");
                } else {
                    w.line("service." + serviceCall + ";")
                     .line("return ResponseEntity.noContent().build();");
                }

                w.unindent().line("}").blank();
            }
        }

        w.unindent().line("}");
        writeFile(w, javaFile(outDir, pkg, name + "Controller"), pkg);
    }

    private String toMappingAnnotation(String httpMethod) {
        return switch (httpMethod) {
            case "GET"    -> "GetMapping";
            case "POST"   -> "PostMapping";
            case "PUT"    -> "PutMapping";
            case "PATCH"  -> "PatchMapping";
            case "DELETE" -> "DeleteMapping";
            default       -> "PostMapping";
        };
    }
}
