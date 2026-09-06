package io.springforge.generator;

import java.io.File;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import io.springforge.model.ActionDefinition;
import io.springforge.model.EntityDefinition;
import io.springforge.model.ForgeDefinition;
import io.springforge.util.NamingUtils;

/**
 * Gerador — Arquitetura Hexagonal: REST Adapter (In Adapter).
 *
 * Gera o controller REST em:
 *   {basePackage}.adapter.in.rest.{EntityName}RestAdapter
 *
 * O adapter de entrada:
 * - Recebe requisições HTTP e delega ao Use-Case ({EntityName}UseCase)
 * - Nunca acessa o domínio ou persistência diretamente
 * - Segue o mesmo contrato de endpoints do ControllerGenerator do estilo Layered
 */
public class HexagonalRestAdapterGenerator extends AbstractGenerator {

    public HexagonalRestAdapterGenerator(Log log) { super(log); }

    @Override
    public void generate(ForgeDefinition def, EntityDefinition entity, File outDir)
            throws MojoExecutionException {

        if (!def.getProject().isHexagonal()) return;
        if (!entity.shouldGenerate("controller")) return;

        String pkg  = hexRestAdapterPkg(def);
        String name = entity.getName();

        writeFile(buildRestAdapter(def, entity, pkg),
                  javaFile(outDir, pkg, name + "RestAdapter"), pkg);
    }

    private CodeWriter buildRestAdapter(ForgeDefinition def, EntityDefinition entity, String pkg) {
        String name    = entity.getName();
        String dtoPkg  = hexDtoPkg(def);
        String inPkg   = hexPortInPkg(def);

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
         .imp(inPkg  + "." + name + "UseCase");

        if (entity.hasFilters()) {
            w.imp(dtoPkg + "." + name + "FilterDTO");
        }

        for (ActionDefinition a : entity.getActions()) {
            if (a.getHttpMethod() == null) continue;
            if (a.hasRequest())  w.imp(dtoPkg + "." + a.getRequestDtoName());
            if (a.hasResponse()) w.imp(dtoPkg + "." + a.getResponseDtoName());
        }

        w.javadoc(
            "Adapter REST (entrada) para " + name + ".\n" +
            "Base URL: " + apiPath + "\n" +
            "Delega todas as operações ao use-case " + name + "UseCase.\n" +
            "Gerado pelo Spring Forge — Arquitetura Hexagonal."
        );

        w.line("@RestController")
         .line("@RequestMapping(\"" + apiPath + "\")");

        w.line("public class " + name + "RestAdapter {")
         .blank();
        w.indent();

        // Campo + construtor
        w.line("private final " + name + "UseCase useCase;")
         .blank()
         .line("public " + name + "RestAdapter(" + name + "UseCase useCase) {")
         .indent()
         .line("this.useCase = useCase;")
         .unindent().line("}").blank();

        // GET /
        w.line("/** GET " + apiPath + " — lista com paginação */")
         .line("@GetMapping")
         .line("public ResponseEntity<Page<" + name + "ResponseDTO>> findAll(")
         .line("        @PageableDefault(size = 20, sort = \"id\") Pageable pageable) {")
         .indent()
         .line("return ResponseEntity.ok(useCase.findAll(pageable));")
         .unindent().line("}").blank();

        // POST /search (filtros)
        if (entity.hasFilters()) {
            w.line("/** POST " + apiPath + "/search — busca com filtros */")
             .line("@PostMapping(\"/search\")")
             .line("public ResponseEntity<Page<" + name + "ResponseDTO>> search(")
             .line("        @RequestBody " + name + "FilterDTO filter,")
             .line("        @PageableDefault(size = 20, sort = \"id\") Pageable pageable) {")
             .indent()
             .line("return ResponseEntity.ok(useCase.search(filter, pageable));")
             .unindent().line("}").blank();
        }

        // GET /{id}
        w.line("/** GET " + apiPath + "/{id} — busca por ID */")
         .line("@GetMapping(\"/{id}\")")
         .line("public ResponseEntity<" + name + "ResponseDTO> findById(@PathVariable Long id) {")
         .indent()
         .line("return ResponseEntity.ok(useCase.findById(id));")
         .unindent().line("}").blank();

        // POST /
        w.line("/** POST " + apiPath + " — cria novo registro */")
         .line("@PostMapping")
         .line("@ResponseStatus(HttpStatus.CREATED)")
         .line("public ResponseEntity<" + name + "ResponseDTO> create(@Valid @RequestBody " + name + "RequestDTO dto) {")
         .indent()
         .line("return ResponseEntity.status(HttpStatus.CREATED).body(useCase.create(dto));")
         .unindent().line("}").blank();

        // PUT /{id}
        w.line("/** PUT " + apiPath + "/{id} — atualiza registro existente */")
         .line("@PutMapping(\"/{id}\")")
         .line("public ResponseEntity<" + name + "ResponseDTO> update(@PathVariable Long id, @Valid @RequestBody " + name + "RequestDTO dto) {")
         .indent()
         .line("return ResponseEntity.ok(useCase.update(id, dto));")
         .unindent().line("}").blank();

        // DELETE /{id}
        w.line("/** DELETE " + apiPath + "/{id} — remove registro */")
         .line("@DeleteMapping(\"/{id}\")")
         .line("@ResponseStatus(HttpStatus.NO_CONTENT)")
         .line("public ResponseEntity<Void> delete(@PathVariable Long id) {")
         .indent()
         .line("useCase.delete(id);")
         .line("return ResponseEntity.noContent().build();")
         .unindent().line("}").blank();

        // ── Actions com endpoint HTTP ─────────────────────────────────────────────
        for (ActionDefinition a : entity.getActions()) {
            if (a.getHttpMethod() == null) continue;

            String method  = a.getHttpMethod().toUpperCase();
            String path    = a.getApiPath() != null ? a.getApiPath() : "/" + a.getName().toLowerCase();
            String retType = a.hasResponse()
                ? "ResponseEntity<" + a.getResponseDtoName() + ">"
                : "ResponseEntity<Void>";

            w.line("/** " + method + " " + apiPath + path + " — " + a.getName() + " */");

            switch (method) {
                case "GET"    -> w.line("@GetMapping(\"" + path + "\")");
                case "POST"   -> w.line("@PostMapping(\"" + path + "\")");
                case "PUT"    -> w.line("@PutMapping(\"" + path + "\")");
                case "PATCH"  -> w.line("@PatchMapping(\"" + path + "\")");
                case "DELETE" -> w.line("@DeleteMapping(\"" + path + "\")");
                default       -> w.line("@PostMapping(\"" + path + "\")");
            }

            StringBuilder methodSig = new StringBuilder("public " + retType + " " + a.getName() + "(");
            if (a.isRequiresId())  methodSig.append("@PathVariable Long id");
            if (a.hasRequest()) {
                if (a.isRequiresId()) methodSig.append(", ");
                methodSig.append("@Valid @RequestBody ").append(a.getRequestDtoName()).append(" dto");
            }
            methodSig.append(") {");

            w.line(methodSig.toString())
             .indent();

            if (a.hasResponse()) {
                String callArgs = buildCallArgs(a);
                w.line("return ResponseEntity.ok(useCase." + a.getName() + "(" + callArgs + "));");
            } else {
                String callArgs = buildCallArgs(a);
                w.line("useCase." + a.getName() + "(" + callArgs + ");")
                 .line("return ResponseEntity.noContent().build();");
            }

            w.unindent().line("}").blank();
        }

        w.unindent().line("}");
        return w;
    }

    private String buildCallArgs(ActionDefinition a) {
        StringBuilder sb = new StringBuilder();
        if (a.isRequiresId()) sb.append("id");
        if (a.hasRequest()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append("dto");
        }
        return sb.toString();
    }
}
