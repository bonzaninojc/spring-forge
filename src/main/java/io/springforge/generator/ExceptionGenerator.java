package io.springforge.generator;

import io.springforge.model.EntityDefinition;
import io.springforge.model.ForgeDefinition;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;

public class ExceptionGenerator extends AbstractGenerator {

    public ExceptionGenerator(Log log) { super(log); }

    @Override
    public void generate(ForgeDefinition def, EntityDefinition entity, File outDir) throws MojoExecutionException {
        String pkg  = exceptionPkg(def);
        String name = entity.getName();

        // NotFoundException por entidade
        File notFoundFile = javaFile(outDir, pkg, name + "NotFoundException");
        if (!notFoundFile.exists()) {
            writeFile(buildNotFoundException(name), notFoundFile, pkg);
        }

        // GlobalExceptionHandler — apenas uma vez por projeto
        File handlerFile = javaFile(outDir, pkg, "GlobalExceptionHandler");
        if (!handlerFile.exists()) {
            writeFile(buildGlobalHandler(pkg), handlerFile, pkg);
        }
    }

    private CodeWriter buildNotFoundException(String name) {
        CodeWriter w = new CodeWriter();
        w.javadoc("Lançada quando " + name + " não é encontrado.");
        w.line("public class " + name + "NotFoundException extends RuntimeException {")
         .blank();
        w.indent();
        w.line("public " + name + "NotFoundException(Long id) {")
         .indent()
         .line("super(\"" + name + " com ID \" + id + \" não encontrado.\");")
         .unindent().line("}").blank();
        w.line("public " + name + "NotFoundException(String message) {")
         .indent()
         .line("super(message);")
         .unindent().line("}").blank();
        w.unindent().line("}");
        return w;
    }

    private CodeWriter buildGlobalHandler(String pkg) {
        CodeWriter w = new CodeWriter();

        w.imp("org.springframework.http.HttpStatus")
         .imp("org.springframework.http.ResponseEntity")
         .imp("org.springframework.validation.FieldError")
         .imp("org.springframework.web.bind.MethodArgumentNotValidException")
         .imp("org.springframework.web.bind.annotation.ExceptionHandler")
         .imp("org.springframework.web.bind.annotation.RestControllerAdvice")
         .imp("java.time.LocalDateTime")
         .imp("java.util.HashMap")
         .imp("java.util.Map");

        w.javadoc("Handler global de exceções para a API REST.\nGerado pelo Spring Forge.");
        w.line("@RestControllerAdvice")
         .line("public class GlobalExceptionHandler {")
         .blank();
        w.indent();

        // RuntimeException → 404 se mensagem contiver "não encontrado"
        w.line("@ExceptionHandler(RuntimeException.class)")
         .line("public ResponseEntity<ErrorResponse> handleRuntime(RuntimeException ex) {")
         .indent()
         .line("String msg = ex.getMessage();")
         .line("if (msg != null && msg.contains(\"não encontrado\")) {")
         .indent()
         .line("return ResponseEntity.status(HttpStatus.NOT_FOUND)")
         .line("        .body(new ErrorResponse(404, msg));")
         .unindent().line("}")
         .line("return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)")
         .line("        .body(new ErrorResponse(500, \"Erro interno do servidor\"));")
         .unindent().line("}").blank();

        // UnsupportedOperationException → 501 Not Implemented
        w.line("@ExceptionHandler(UnsupportedOperationException.class)")
         .line("public ResponseEntity<ErrorResponse> handleNotImplemented(UnsupportedOperationException ex) {")
         .indent()
         .line("return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)")
         .line("        .body(new ErrorResponse(501, ex.getMessage()));")
         .unindent().line("}").blank();

        // MethodArgumentNotValidException → 400
        w.line("@ExceptionHandler(MethodArgumentNotValidException.class)")
         .line("public ResponseEntity<ValidationErrorResponse> handleValidation(MethodArgumentNotValidException ex) {")
         .indent()
         .line("Map<String, String> errors = new HashMap<>();")
         .line("ex.getBindingResult().getAllErrors().forEach(e -> {")
         .indent()
         .line("String field = ((FieldError) e).getField();")
         .line("errors.put(field, e.getDefaultMessage());")
         .unindent().line("});")
         .line("return ResponseEntity.status(HttpStatus.BAD_REQUEST)")
         .line("        .body(new ValidationErrorResponse(400, \"Erro de validação\", errors));")
         .unindent().line("}").blank();

        // ── Classes internas de resposta ─────────────────────────────────────────
        w.line("// ── Payloads de erro ─────────────────────────────────────────────────────────────")
         .blank();

        w.line("public static class ErrorResponse {")
         .indent()
         .line("private final int status;")
         .line("private final String message;")
         .line("private final LocalDateTime timestamp = LocalDateTime.now();")
         .blank()
         .line("public ErrorResponse(int status, String message) {")
         .indent()
         .line("this.status = status; this.message = message;")
         .unindent().line("}")
         .blank()
         .line("public int getStatus() { return status; }")
         .line("public String getMessage() { return message; }")
         .line("public LocalDateTime getTimestamp() { return timestamp; }")
         .unindent().line("}").blank();

        w.line("public static class ValidationErrorResponse extends ErrorResponse {")
         .indent()
         .line("private final Map<String, String> errors;")
         .blank()
         .line("public ValidationErrorResponse(int status, String message, Map<String, String> errors) {")
         .indent()
         .line("super(status, message); this.errors = errors;")
         .unindent().line("}")
         .blank()
         .line("public Map<String, String> getErrors() { return errors; }")
         .unindent().line("}").blank();

        w.unindent().line("}");
        return w;
    }
}
