package io.springforge.generator;

import io.springforge.model.*;
import io.springforge.parser.ForgeJsonParser;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CacheGeneratorTest {

    private final Log log = new SystemStreamLog();
    private CacheGenerator generator;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        generator = new CacheGenerator(log);
    }

    private ForgeDefinition buildDefinition(String provider) {
        ForgeDefinition def = new ForgeDefinition();
        ProjectConfig project = new ProjectConfig();
        project.setBasePackage("com.example");
        project.setName("TestApp");
        project.setGenerateCache(true);
        project.setGenerateMappers(true);
        def.setProject(project);

        EntityDefinition entity = new EntityDefinition();
        entity.setName("Product");
        CacheDefinition cache = new CacheDefinition();
        cache.setEnabled(true);
        cache.setTtlSeconds(600);
        cache.setMaxSize(1000);
        cache.setProvider(provider);
        entity.setCache(cache);

        FieldDefinition field = new FieldDefinition();
        field.setName("name");
        field.setType("String");
        entity.setFields(List.of(field));

        def.setEntities(List.of(entity));
        return def;
    }

    @Test
    void shouldGenerateRedisCacheConfig() throws Exception {
        ForgeDefinition def = buildDefinition("redis");
        File outDir = tempDir.toFile();

        generator.generateGlobalCacheConfig(def, outDir);

        File configFile = new File(outDir, "com/example/config/CacheConfig.java");
        assertTrue(configFile.exists(), "CacheConfig.java deve ser gerado");

        String content = Files.readString(configFile.toPath());
        assertTrue(content.contains("@EnableCaching"));
        assertTrue(content.contains("RedisCacheManager"));
        assertTrue(content.contains("PRODUCT_CACHE"));
        assertTrue(content.contains("Duration.ofSeconds(600)"));
    }

    @Test
    void shouldGenerateCaffeineCacheConfig() throws Exception {
        ForgeDefinition def = buildDefinition("caffeine");
        File outDir = tempDir.toFile();

        generator.generateGlobalCacheConfig(def, outDir);

        File configFile = new File(outDir, "com/example/config/CacheConfig.java");
        assertTrue(configFile.exists());

        String content = Files.readString(configFile.toPath());
        assertTrue(content.contains("CaffeineCacheManager"));
        assertTrue(content.contains("maximumSize(1000)"));
    }

    @Test
    void shouldGenerateCachedServiceImpl() throws Exception {
        ForgeDefinition def = buildDefinition("redis");
        File outDir = tempDir.toFile();

        generator.generateCachedService(def, def.getEntities().get(0), outDir);

        File serviceFile = new File(outDir, "com/example/service/impl/ProductCachedServiceImpl.java");
        assertTrue(serviceFile.exists());

        String content = Files.readString(serviceFile.toPath());
        assertTrue(content.contains("@Cacheable"));
        assertTrue(content.contains("@CacheEvict"));
        assertTrue(content.contains("@CachePut"));
        assertTrue(content.contains("@Primary"));
        assertTrue(content.contains("CacheConfig.PRODUCT_CACHE"));
    }

    @Test
    void shouldNotGenerateWhenDisabled() throws Exception {
        ForgeDefinition def = buildDefinition("redis");
        def.getProject().setGenerateCache(false);
        File outDir = tempDir.toFile();

        generator.generateGlobalCacheConfig(def, outDir);
        generator.generateCachedService(def, def.getEntities().get(0), outDir);

        File configFile = new File(outDir, "com/example/config/CacheConfig.java");
        assertFalse(configFile.exists());
    }
}
