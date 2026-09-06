package io.springforge.generator;

import java.io.File;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import io.springforge.model.ForgeDefinition;
import io.springforge.model.PomConfig;

/**
 * Enriquecedor do pom.xml do projeto alvo.
 *
 * A classe é propositalmente conservadora: ela adiciona somente dependências
 * ausentes, não remove nada e cria backup por padrão. Dependências gerenciadas
 * pelo Spring Boot são adicionadas sem versão. Dependências fora do BOM, como
 * MapStruct e SpringDoc, recebem versões via properties.
 */
public class PomDependencyEnricher {

    private final Log log;

    public PomDependencyEnricher(Log log) {
        this.log = log;
    }

    public List<DependencySpec> requiredDependencies(ForgeDefinition def) {
        List<DependencySpec> deps = new ArrayList<>();
        String database = def.getProject().getDatabase() == null ? "postgres" : def.getProject().getDatabase().toLowerCase();

        // Base comum para CRUD REST gerado.
        add(deps, "org.springframework.boot", "spring-boot-starter-web", null, null, "REST controllers gerados");
        add(deps, "org.springframework.boot", "spring-boot-starter-validation", null, null, "validações Jakarta em DTOs");

        if ("mongodb".equals(database)) {
            add(deps, "org.springframework.boot", "spring-boot-starter-data-mongodb", null, null, "MongoDB");
        } else {
            add(deps, "org.springframework.boot", "spring-boot-starter-data-jpa", null, null, "JPA repositories/specifications");
            if ("postgres".equals(database)) add(deps, "org.postgresql", "postgresql", null, "runtime", "driver PostgreSQL");
            if ("mysql".equals(database)) add(deps, "com.mysql", "mysql-connector-j", null, "runtime", "driver MySQL");
            if ("h2".equals(database)) add(deps, "com.h2database", "h2", null, "runtime", "driver H2");
        }

        if (def.getProject().isGenerateMappers()) {
            add(deps, "org.mapstruct", "mapstruct", "${mapstruct.version}", null, "mappers MapStruct");
        }

        if (def.getProject().isGenerateOpenApi()) {
            add(deps, "org.springdoc", "springdoc-openapi-starter-webmvc-ui", "${springdoc.version}", null, "Swagger/OpenAPI");
        }

        if (def.getProject().isGenerateRabbitMQ()) {
            add(deps, "org.springframework.boot", "spring-boot-starter-amqp", null, null, "RabbitMQ gerado");
        }

        if (def.getProject().isGenerateSpringEvents()) {
            add(deps, "org.springframework", "spring-context", null, null, "ApplicationEvent gerado");
        }

        if (def.getProject().isGenerateCache()) {
            add(deps, "org.springframework.boot", "spring-boot-starter-cache", null, null, "Spring Cache gerado");
            String provider = def.getEntities().stream()
                    .filter(e -> e.getCache() != null && e.getCache().isEnabled())
                    .map(e -> e.getCache().getProvider())
                    .filter(p -> p != null && !p.isBlank())
                    .findFirst().orElse("redis");
            if ("redis".equalsIgnoreCase(provider)) {
                add(deps, "org.springframework.boot", "spring-boot-starter-data-redis", null, null, "Redis cache");
            } else if ("caffeine".equalsIgnoreCase(provider)) {
                add(deps, "com.github.ben-manes.caffeine", "caffeine", null, null, "Caffeine cache");
            }
        }

        if (def.getProject().isGenerateMigrations() && !"mongodb".equals(database)) {
            add(deps, "org.flywaydb", "flyway-core", null, null, "Flyway migrations geradas");
            if ("mysql".equals(database)) {
                add(deps, "org.flywaydb", "flyway-mysql", null, null, "Flyway MySQL support");
            }
        }

        if (def.getProject().isGenerateTests()) {
            add(deps, "org.springframework.boot", "spring-boot-starter-test", null, "test", "testes gerados");
        }

        return dedupe(deps);
    }

    public Map<String, String> requiredProperties(ForgeDefinition def) {
        Map<String, String> props = new LinkedHashMap<>();
        if (def.getProject().isGenerateMappers()) props.put("mapstruct.version", "1.5.5.Final");
        if (def.getProject().isGenerateOpenApi()) props.put("springdoc.version", "2.5.0");
        return props;
    }

    public void enrich(File projectBaseDir, ForgeDefinition def) throws MojoExecutionException {
        PomConfig cfg = def.getProject().getPom();
        if (cfg == null || !cfg.isAutoUpdate()) {
            log.info("  [POM] autoUpdate=false — pom.xml não será alterado.");
            return;
        }

        File pomFile = new File(projectBaseDir, "pom.xml");
        if (!pomFile.exists()) {
            log.warn("  [POM] pom.xml não encontrado em " + pomFile.getPath());
            return;
        }

        try {
            if (cfg.isCreateBackup()) {
                File backup = new File(projectBaseDir, "pom.xml.spring-forge.bak");
                if (!backup.exists()) {
                    Files.copy(pomFile.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    log.info("  [POM] Backup criado: " + backup.getName());
                }
            }

            Document doc = parsePom(pomFile);
            Element root = doc.getDocumentElement();
            String ns = root.getNamespaceURI();
            Element properties = child(root, "properties", ns);
            if (properties == null) {
                properties = doc.createElementNS(ns, "properties");
                insertBeforeBuildOrAppend(root, properties);
            }

            for (Map.Entry<String, String> entry : requiredProperties(def).entrySet()) {
                if (child(properties, entry.getKey(), ns) == null) {
                    Element p = doc.createElementNS(ns, entry.getKey());
                    p.setTextContent(entry.getValue());
                    properties.appendChild(p);
                    log.info("  [POM] property adicionada: " + entry.getKey() + "=" + entry.getValue());
                }
            }

            Element dependencies = child(root, "dependencies", ns);
            if (dependencies == null) {
                dependencies = doc.createElementNS(ns, "dependencies");
                insertBeforeBuildOrAppend(root, dependencies);
            }

            Set<String> existing = dependencyKeys(dependencies);
            for (DependencySpec dep : requiredDependencies(def)) {
                if (!existing.contains(dep.key())) {
                    if (cfg.isAddComments()) dependencies.appendChild(doc.createTextNode("\n        "));
                    if (cfg.isAddComments()) dependencies.appendChild(doc.createComment(" Spring Forge: " + dep.reason + " "));
                    dependencies.appendChild(doc.createTextNode("\n        "));
                    dependencies.appendChild(toElement(doc, ns, dep));
                    existing.add(dep.key());
                    log.info("  [POM] dependency adicionada: " + dep.groupId + ":" + dep.artifactId);
                }
            }

            if (cfg.isManagePlugins() && def.getProject().isGenerateMappers()) {
                ensureMapStructCompilerPlugin(doc, root, ns);
            }

            writePom(doc, pomFile);
        } catch (Exception e) {
            throw new MojoExecutionException("Erro ao atualizar pom.xml: " + e.getMessage(), e);
        }
    }

    private static void add(List<DependencySpec> deps, String groupId, String artifactId, String version, String scope, String reason) {
        deps.add(new DependencySpec(groupId, artifactId, version, scope, reason));
    }

    private static List<DependencySpec> dedupe(List<DependencySpec> deps) {
        Map<String, DependencySpec> map = new LinkedHashMap<>();
        for (DependencySpec d : deps) map.putIfAbsent(d.key(), d);
        return new ArrayList<>(map.values());
    }

    private Document parsePom(File pomFile) throws Exception {
        String xml = Files.readString(pomFile.toPath(), StandardCharsets.UTF_8);
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        try { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); } catch (Exception ignored) {}
        Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        doc.setXmlStandalone(false);
        return doc;
    }

    private void writePom(Document doc, File pomFile) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        tf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        var transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        transformer.transform(new DOMSource(doc), new StreamResult(pomFile));
        log.info("  [POM] pom.xml atualizado com dependências Spring Forge.");
    }

    private Element toElement(Document doc, String ns, DependencySpec dep) {
        Element d = doc.createElementNS(ns, "dependency");
        appendText(doc, d, ns, "groupId", dep.groupId);
        appendText(doc, d, ns, "artifactId", dep.artifactId);
        if (dep.version != null && !dep.version.isBlank()) appendText(doc, d, ns, "version", dep.version);
        if (dep.scope != null && !dep.scope.isBlank()) appendText(doc, d, ns, "scope", dep.scope);
        return d;
    }

    private void appendText(Document doc, Element parent, String ns, String name, String value) {
        Element e = doc.createElementNS(ns, name);
        e.setTextContent(value);
        parent.appendChild(e);
    }

    private Set<String> dependencyKeys(Element dependencies) {
        Set<String> keys = new LinkedHashSet<>();
        NodeList nodes = dependencies.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element dep && "dependency".equals(dep.getLocalName())) {
                String groupId = text(dep, "groupId");
                String artifactId = text(dep, "artifactId");
                if (!groupId.isBlank() && !artifactId.isBlank()) keys.add(groupId + ":" + artifactId);
            }
        }
        return keys;
    }

    private String text(Element parent, String name) {
        Element child = child(parent, name, parent.getNamespaceURI());
        return child == null ? "" : child.getTextContent().trim();
    }

    private Element child(Element parent, String localName, String ns) {
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n instanceof Element e && localName.equals(e.getLocalName())) return e;
        }
        return null;
    }

    private void insertBeforeBuildOrAppend(Element root, Element child) {
        NodeList nodes = root.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n instanceof Element e && "build".equals(e.getLocalName())) {
                root.insertBefore(child, n);
                return;
            }
        }
        root.appendChild(child);
    }

    private void ensureMapStructCompilerPlugin(Document doc, Element root, String ns) {
        Element build = child(root, "build", ns);
        if (build == null) {
            build = doc.createElementNS(ns, "build");
            root.appendChild(build);
        }
        Element plugins = child(build, "plugins", ns);
        if (plugins == null) {
            plugins = doc.createElementNS(ns, "plugins");
            build.appendChild(plugins);
        }
        Element compiler = findPlugin(plugins, "org.apache.maven.plugins", "maven-compiler-plugin");
        if (compiler == null) {
            plugins.appendChild(doc.createTextNode("\n            "));
            Comment comment = doc.createComment(" Spring Forge: MapStruct annotation processor ");
            plugins.appendChild(comment);
            plugins.appendChild(doc.createTextNode("\n            "));
            compiler = doc.createElementNS(ns, "plugin");
            appendText(doc, compiler, ns, "groupId", "org.apache.maven.plugins");
            appendText(doc, compiler, ns, "artifactId", "maven-compiler-plugin");
            Element configuration = doc.createElementNS(ns, "configuration");
            Element paths = doc.createElementNS(ns, "annotationProcessorPaths");
            paths.appendChild(mapStructProcessorPath(doc, ns));
            configuration.appendChild(paths);
            compiler.appendChild(configuration);
            plugins.appendChild(compiler);
            log.info("  [POM] maven-compiler-plugin configurado para MapStruct.");
            return;
        }

        Element configuration = child(compiler, "configuration", ns);
        if (configuration == null) {
            configuration = doc.createElementNS(ns, "configuration");
            compiler.appendChild(configuration);
        }
        Element paths = child(configuration, "annotationProcessorPaths", ns);
        if (paths == null) {
            paths = doc.createElementNS(ns, "annotationProcessorPaths");
            configuration.appendChild(paths);
        }
        if (findProcessorPath(paths, "org.mapstruct", "mapstruct-processor") == null) {
            paths.appendChild(mapStructProcessorPath(doc, ns));
            log.info("  [POM] mapstruct-processor adicionado ao annotationProcessorPaths.");
        }
    }

    private Element findPlugin(Element plugins, String groupId, String artifactId) {
        NodeList nodes = plugins.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element plugin && "plugin".equals(plugin.getLocalName())) {
                if (groupId.equals(text(plugin, "groupId")) && artifactId.equals(text(plugin, "artifactId"))) return plugin;
            }
        }
        return null;
    }

    private Element findProcessorPath(Element paths, String groupId, String artifactId) {
        NodeList nodes = paths.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element path && "path".equals(path.getLocalName())) {
                if (groupId.equals(text(path, "groupId")) && artifactId.equals(text(path, "artifactId"))) return path;
            }
        }
        return null;
    }

    private Element mapStructProcessorPath(Document doc, String ns) {
        Element path = doc.createElementNS(ns, "path");
        appendText(doc, path, ns, "groupId", "org.mapstruct");
        appendText(doc, path, ns, "artifactId", "mapstruct-processor");
        appendText(doc, path, ns, "version", "${mapstruct.version}");
        return path;
    }

    public record DependencySpec(String groupId, String artifactId, String version, String scope, String reason) {
        public String key() { return groupId + ":" + artifactId; }
    }
}
