package io.springforge.mojo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.springforge.generator.*;
import io.springforge.model.EntityDefinition;
import io.springforge.model.ForgeDefinition;
import io.springforge.parser.ForgeJsonParser;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Goal "ui" — sobe um dashboard web local para editar o forge.json visualmente.
 *
 * Uso:
 *   mvn spring-forge:ui
 *   mvn spring-forge:ui -Dforge.ui.port=4200
 */
@Mojo(
        name = "ui",
        defaultPhase = LifecyclePhase.NONE,
        requiresProject = true,
        threadSafe = true
)
public class ForgeDashboardMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    private MavenProject project;

    @Parameter(property = "forge.input", defaultValue = "${project.basedir}/forge.json")
    private File inputFile;

    @Parameter(property = "forge.ui.port", defaultValue = "4200")
    private int port;

    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private final AtomicLong lastHeartbeat = new AtomicLong(System.currentTimeMillis());
    private static final long HEARTBEAT_TIMEOUT_MS = 10_000;
    private volatile Thread mainThread;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("╔══════════════════════════════════════╗");
        getLog().info("║    Spring Forge — Dashboard UI       ║");
        getLog().info("╚══════════════════════════════════════╝");
        getLog().info("  Porta: " + port);
        getLog().info("  forge.json: " + inputFile.getPath());

        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

            // API endpoints
            server.createContext("/api/forge", this::handleForgeApi);
            server.createContext("/api/generate", this::handleGenerate);
            server.createContext("/api/validate", this::handleValidate);
            server.createContext("/api/reverse", this::handleReverse);
            server.createContext("/api/preview", this::handlePreview);
            server.createContext("/api/heartbeat", this::handleHeartbeat);

            // Serve frontend estático
            server.createContext("/", this::handleStatic);

            server.setExecutor(null);
            server.start();

            // Garante que o server para e libera a porta ao encerrar
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                getLog().info("Encerrando dashboard e liberando porta " + port + "...");
                server.stop(0);
            }));

            // Watchdog: encerra se o browser parar de enviar heartbeat
            Thread watchdog = new Thread(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(5_000);
                    } catch (InterruptedException e) {
                        break;
                    }
                    if (System.currentTimeMillis() - lastHeartbeat.get() > HEARTBEAT_TIMEOUT_MS) {
                        getLog().info("Browser desconectado — encerrando dashboard.");
                        server.stop(0);
                        Thread.currentThread().interrupt();
                        // Interrompe a main thread para sair
                        ForgeDashboardMojo.this.mainThread.interrupt();
                    }
                }
            });
            watchdog.setDaemon(true);
            watchdog.start();

            getLog().info("");
            getLog().info("  ✔ Dashboard rodando em: http://localhost:" + port);
            getLog().info("  Pressione Ctrl+C para parar.");
            getLog().info("");

            // Tenta abrir no browser
            openBrowser("http://localhost:" + port);

            // Bloqueia até Ctrl+C ou browser desconectar
            mainThread = Thread.currentThread();
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            getLog().info("Dashboard encerrado.");
        } catch (Exception e) {
            throw new MojoExecutionException("Erro ao iniciar dashboard: " + e.getMessage(), e);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // GET/PUT /api/forge — lê e salva o forge.json
    // ═══════════════════════════════════════════════════════════════

    private void handleForgeApi(HttpExchange ex) throws IOException {
        setCors(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(204, -1); return; }

        if ("GET".equals(ex.getRequestMethod())) {
            if (!inputFile.exists()) {
                sendJson(ex, 404, Map.of("error", "forge.json não encontrado"));
                return;
            }
            String content = Files.readString(inputFile.toPath(), StandardCharsets.UTF_8);
            sendRaw(ex, 200, "application/json", content);
        } else if ("PUT".equals(ex.getRequestMethod())) {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            // Valida JSON
            try {
                Object parsed = mapper.readValue(body, Object.class);
                String formatted = mapper.writeValueAsString(parsed);
                Files.writeString(inputFile.toPath(), formatted, StandardCharsets.UTF_8);
                sendJson(ex, 200, Map.of("success", true, "message", "forge.json salvo com sucesso"));
            } catch (Exception e) {
                sendJson(ex, 400, Map.of("error", "JSON inválido: " + e.getMessage()));
            }
        } else {
            ex.sendResponseHeaders(405, -1);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // POST /api/generate — executa a geração
    // ═══════════════════════════════════════════════════════════════

    private void handleGenerate(HttpExchange ex) throws IOException {
        setCors(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(204, -1); return; }
        if (!"POST".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405, -1); return; }

        try {
            // Lê body para obter filtro de entidades (opcional)
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String entitiesFilter = "";
            if (body != null && !body.isBlank()) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> reqBody = mapper.readValue(body, Map.class);
                    Object ent = reqBody.get("entities");
                    if (ent instanceof List<?> list && !list.isEmpty()) {
                        entitiesFilter = list.stream()
                                .map(Object::toString)
                                .collect(Collectors.joining(","));
                    }
                } catch (Exception ignored) {}
            }

            ForgeJsonParser parser = new ForgeJsonParser();
            ForgeDefinition def = parser.parse(inputFile);
            int entityCount = entitiesFilter.isEmpty()
                    ? def.getEntities().size()
                    : entitiesFilter.split(",").length;

            // Executa geração via ProcessBuilder
            List<String> cmd = new ArrayList<>(List.of("mvn", "spring-forge:generate", "-q"));
            if (!entitiesFilter.isEmpty()) {
                cmd.add("-Dforge.entities=" + entitiesFilter);
            }
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(project.getBasedir());
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = proc.waitFor();

            if (exitCode == 0) {
                sendJson(ex, 200, Map.of(
                        "success", true,
                        "message", entityCount + " entidade(s) gerada(s) com sucesso",
                        "output", output
                ));
            } else {
                sendJson(ex, 500, Map.of("error", "Falha na geração", "output", output));
            }
        } catch (Exception e) {
            sendJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // POST /api/reverse — reverse engineering de banco de dados
    // ═══════════════════════════════════════════════════════════════

    private void handleReverse(HttpExchange ex) throws IOException {
        setCors(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(204, -1); return; }
        if (!"POST".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405, -1); return; }

        try {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> params = mapper.readValue(body, Map.class);

            String jdbcUrl = (String) params.get("jdbcUrl");
            String jdbcUser = (String) params.get("jdbcUser");
            String jdbcPassword = (String) params.getOrDefault("jdbcPassword", "");
            String basePackage = (String) params.getOrDefault("basePackage", "com.myapp");
            String schema = (String) params.getOrDefault("schema", "");

            if (jdbcUrl == null || jdbcUrl.isBlank() || jdbcUser == null || jdbcUser.isBlank()) {
                sendJson(ex, 400, Map.of("error", "jdbcUrl e jdbcUser são obrigatórios"));
                return;
            }

            // Executa via ProcessBuilder para reutilizar o Mojo existente
            List<String> cmd = new ArrayList<>(List.of(
                    "mvn", "spring-forge:reverse", "-q",
                    "-Dforge.jdbcUrl=" + jdbcUrl,
                    "-Dforge.jdbcUser=" + jdbcUser,
                    "-Dforge.jdbcPassword=" + jdbcPassword,
                    "-Dforge.basePackage=" + basePackage,
                    "-Dforge.output=" + inputFile.getPath()
            ));
            if (schema != null && !schema.isBlank()) {
                cmd.add("-Dforge.schema=" + schema);
            }

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(project.getBasedir());
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = proc.waitFor();

            if (exitCode == 0) {
                // Relê o forge.json gerado para devolver ao frontend
                String content = Files.readString(inputFile.toPath(), StandardCharsets.UTF_8);
                sendJson(ex, 200, Map.of(
                        "success", true,
                        "message", "Reverse engineering concluído! forge.json atualizado.",
                        "forge", mapper.readValue(content, Object.class)
                ));
            } else {
                sendJson(ex, 500, Map.of("error", "Falha no reverse engineering", "output", output));
            }
        } catch (Exception e) {
            sendJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // POST /api/validate — valida o forge.json sem gerar
    // ═══════════════════════════════════════════════════════════════

    private void handleValidate(HttpExchange ex) throws IOException {
        setCors(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(204, -1); return; }
        if (!"POST".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405, -1); return; }

        try {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            // Tenta parsear e validar
            File tempFile = File.createTempFile("forge-validate-", ".json");
            Files.writeString(tempFile.toPath(), body, StandardCharsets.UTF_8);
            try {
                ForgeJsonParser parser = new ForgeJsonParser();
                ForgeDefinition def = parser.parse(tempFile);
                sendJson(ex, 200, Map.of(
                        "valid", true,
                        "entities", def.getEntities().size(),
                        "message", "forge.json válido — " + def.getEntities().size() + " entidade(s)"
                ));
            } catch (MojoExecutionException e) {
                sendJson(ex, 200, Map.of("valid", false, "message", e.getMessage()));
            } finally {
                tempFile.delete();
            }
        } catch (Exception e) {
            sendJson(ex, 500, Map.of("error", e.getMessage()));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // POST /api/preview — gera preview do código para uma entidade
    // ═══════════════════════════════════════════════════════════════

    private void handlePreview(HttpExchange ex) throws IOException {
        setCors(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(204, -1); return; }
        if (!"POST".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(405, -1); return; }

        try {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            @SuppressWarnings("unchecked")
            Map<String, Object> req = mapper.readValue(body, Map.class);

            String entityName = (String) req.get("entity");
            String layer = (String) req.getOrDefault("layer", "entity");

            // Carrega o forge.json atual
            File tempForge = inputFile.exists() ? inputFile : null;
            if (tempForge == null) {
                sendJson(ex, 404, Map.of("error", "forge.json não encontrado"));
                return;
            }

            ForgeJsonParser parser = new ForgeJsonParser();
            ForgeDefinition def = parser.parse(tempForge);

            EntityDefinition targetEntity = def.getEntities().stream()
                    .filter(e -> e.getName().equals(entityName))
                    .findFirst().orElse(null);

            if (targetEntity == null) {
                sendJson(ex, 404, Map.of("error", "Entidade não encontrada: " + entityName));
                return;
            }

            // Gera em diretório temporário e lê o arquivo
            Path tempDir = Files.createTempDirectory("forge-preview-");
            try {
                AbstractGenerator gen = switch (layer) {
                    case "entity"     -> new EntityGenerator(getLog());
                    case "controller" -> new ControllerGenerator(getLog());
                    case "service"    -> new ServiceGenerator(getLog());
                    case "repository" -> new RepositoryGenerator(getLog());
                    case "dto"        -> new DtoGenerator(getLog());
                    case "mapper"     -> new MapperGenerator(getLog());
                    default           -> new EntityGenerator(getLog());
                };
                gen.generate(def, targetEntity, tempDir.toFile());

                // Coleta todos os .java gerados
                Map<String, String> files = new LinkedHashMap<>();
                try (var walk = Files.walk(tempDir)) {
                    walk.filter(p -> p.toString().endsWith(".java"))
                            .sorted()
                            .forEach(p -> {
                                try {
                                    String relativeName = tempDir.relativize(p).toString()
                                            .replace(File.separatorChar, '/');
                                    files.put(relativeName, Files.readString(p, StandardCharsets.UTF_8));
                                } catch (IOException ignored) {}
                            });
                }

                sendJson(ex, 200, Map.of("files", files, "entity", entityName, "layer", layer));
            } finally {
                // Limpa temp dir
                try (var walk = Files.walk(tempDir)) {
                    walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
                }
            }
        } catch (Exception e) {
            sendJson(ex, 500, Map.of("error", e.getMessage() != null ? e.getMessage() : "Erro interno"));
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // GET /api/heartbeat — browser envia periodicamente
    // ═══════════════════════════════════════════════════════════════

    private void handleHeartbeat(HttpExchange ex) throws IOException {
        setCors(ex);
        if ("OPTIONS".equals(ex.getRequestMethod())) { ex.sendResponseHeaders(204, -1); return; }
        lastHeartbeat.set(System.currentTimeMillis());
        sendJson(ex, 200, Map.of("ok", true));
    }

    // ═══════════════════════════════════════════════════════════════
    // Serve frontend estático (embutido no JAR)
    // ═══════════════════════════════════════════════════════════════

    private void handleStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if ("/".equals(path)) path = "/index.html";

        // Tenta carregar do classpath (embutido no JAR)
        InputStream is = getClass().getResourceAsStream("/dashboard" + path);
        if (is == null) {
            // SPA fallback — retorna index.html para rotas do React
            is = getClass().getResourceAsStream("/dashboard/index.html");
        }
        if (is == null) {
            ex.sendResponseHeaders(404, -1);
            return;
        }

        String contentType = guessContentType(path);
        byte[] data = is.readAllBytes();
        is.close();
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(200, data.length);
        ex.getResponseBody().write(data);
        ex.getResponseBody().close();
    }

    // ═══════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════

    private void setCors(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
    }

    private void sendJson(HttpExchange ex, int status, Object obj) throws IOException {
        String json = mapper.writeValueAsString(obj);
        sendRaw(ex, status, "application/json", json);
    }

    private void sendRaw(HttpExchange ex, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType + "; charset=utf-8");
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.getResponseBody().close();
    }

    private String guessContentType(String path) {
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".js")) return "application/javascript";
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".json")) return "application/json";
        if (path.endsWith(".svg")) return "image/svg+xml";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".ico")) return "image/x-icon";
        return "application/octet-stream";
    }

    private void openBrowser(String url) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                Runtime.getRuntime().exec(new String[]{"rundll32", "url.dll,FileProtocolHandler", url});
            } else if (os.contains("mac")) {
                Runtime.getRuntime().exec(new String[]{"open", url});
            } else {
                Runtime.getRuntime().exec(new String[]{"xdg-open", url});
            }
        } catch (Exception e) {
            getLog().debug("Não foi possível abrir o browser: " + e.getMessage());
        }
    }
}