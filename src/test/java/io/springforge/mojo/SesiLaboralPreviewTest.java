package io.springforge.mojo;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SesiLaboralPreviewTest {
    @TempDir Path temp;

    @Test void previewsVueFromUnsavedProjectSelection() throws Exception {
        ForgeDashboardMojo mojo = new ForgeDashboardMojo();
        ObjectMapper mapper = new ObjectMapper();
        String payload = mapper.writeValueAsString(Map.of("entity", "Product", "layer", "frontend", "forge", Map.of(
            "project", Map.of("name", "Preview", "basePackage", "com.example", "architectureStyle", "SESI_LABORAL",
                "generateFrontend", true, "frontendDir", temp.resolve("must-not-write/src").toString()),
            "entities", new Object[]{Map.of("name", "Product", "fields", new Object[]{Map.of("name", "title", "type", "String")})}
        )));
        Exchange exchange = new Exchange(payload);
        var preview = ForgeDashboardMojo.class.getDeclaredMethod("handlePreview", HttpExchange.class);
        preview.setAccessible(true);
        preview.invoke(mojo, exchange);
        String response = exchange.output.toString(StandardCharsets.UTF_8);
        assertEquals(200, exchange.status, response);
        var files = mapper.readTree(response).get("files");
        assertTrue(files.has("frontend/src/views/pages/DashboardProducts/ListarProducts.vue"), response);
        assertTrue(files.has("frontend/src/router/routes/generated.ts"), response);
        assertTrue(files.has("frontend/src/navigation/vertical/generated.ts"), response);
        assertFalse(files.has("frontend/package.json"), response);
        assertFalse(Files.exists(temp.resolve("must-not-write")));
    }

    @Test void exposesEntitySchemaFieldInDashboard() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/dashboard/index.html")) {
            assertNotNull(input);
            String dashboard = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(dashboard.contains("<label>Schema</label><input value={entity.schema||''}"));
            assertTrue(dashboard.contains("set('schema',e.target.value)"));
        }
    }

    private static class Exchange extends HttpExchange {
        final ByteArrayOutputStream output = new ByteArrayOutputStream();
        final InputStream input;
        final Headers headers = new Headers();
        int status;
        Exchange(String body) { input = new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)); }
        public Headers getRequestHeaders() { return headers; }
        public Headers getResponseHeaders() { return headers; }
        public URI getRequestURI() { return URI.create("/api/preview"); }
        public String getRequestMethod() { return "POST"; }
        public HttpContext getHttpContext() { return null; }
        public void close() {}
        public InputStream getRequestBody() { return input; }
        public OutputStream getResponseBody() { return output; }
        public void sendResponseHeaders(int code, long length) { status = code; }
        public InetSocketAddress getRemoteAddress() { return new InetSocketAddress(0); }
        public int getResponseCode() { return status; }
        public InetSocketAddress getLocalAddress() { return new InetSocketAddress(0); }
        public String getProtocol() { return "HTTP/1.1"; }
        public Object getAttribute(String name) { return null; }
        public void setAttribute(String name, Object value) {}
        public void setStreams(InputStream input, OutputStream output) {}
        public HttpPrincipal getPrincipal() { return null; }
    }
}
