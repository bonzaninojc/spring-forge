package io.springforge.generator;

import io.springforge.model.ActionDefinition;
import io.springforge.model.CacheDefinition;
import io.springforge.model.EntityDefinition;
import io.springforge.model.ForgeDefinition;
import io.springforge.util.NamingUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Gera configuração de cache (Spring Cache) para entidades que declaram "cache" no forge.json.
 *
 * Gera:
 *   - CacheConfig.java (configuração global com Redis ou Caffeine)
 *   - Anotações @Cacheable/@CacheEvict no ServiceImpl (via decoração)
 *
 * Requer generateCache: true no project.
 */
public class CacheGenerator extends AbstractGenerator {

    public CacheGenerator(Log log) { super(log); }

    @Override
    public void generate(ForgeDefinition def, EntityDefinition entity, File outDir) throws MojoExecutionException {
        // Gera nada por entidade — apenas adiciona anotações no Service
        // A config global é gerada pelo generateGlobalCacheConfig()
    }

    /**
     * Gera a classe CacheConfig global (chamado uma vez pelo Mojo).
     */
    public void generateGlobalCacheConfig(ForgeDefinition def, File outDir) throws MojoExecutionException {
        if (!def.getProject().isGenerateCache()) return;

        List<EntityDefinition> cachedEntities = def.getEntities().stream()
            .filter(EntityDefinition::hasCache)
            .collect(Collectors.toList());

        if (cachedEntities.isEmpty()) return;

        String pkg = def.getProject().getBasePackage() + ".config";
        String provider = cachedEntities.stream()
            .map(e -> e.getCache().getProvider())
            .findFirst().orElse("redis");

        CodeWriter w = buildCacheConfig(def, cachedEntities, pkg, provider);
        writeFile(w, javaFile(outDir, pkg, "CacheConfig"), pkg);
    }

    /**
     * Gera o ServiceImpl com cache (CachedXxxServiceImpl) para uma entidade.
     */
    public void generateCachedService(ForgeDefinition def, EntityDefinition entity, File outDir) throws MojoExecutionException {
        if (!def.getProject().isGenerateCache()) return;
        if (!entity.hasCache()) return;

        String pkg = serviceImplPkg(def);
        CodeWriter w = buildCachedServiceImpl(def, entity, pkg);
        writeFile(w, javaFile(outDir, pkg, entity.getName() + "CachedServiceImpl"), pkg);
    }

    private CodeWriter buildCacheConfig(ForgeDefinition def, List<EntityDefinition> cachedEntities, String pkg, String provider) {
        CodeWriter w = new CodeWriter();

        w.imp("org.springframework.cache.CacheManager")
         .imp("org.springframework.cache.annotation.EnableCaching")
         .imp("org.springframework.context.annotation.Bean")
         .imp("org.springframework.context.annotation.Configuration");

        if ("redis".equalsIgnoreCase(provider)) {
            w.imp("org.springframework.data.redis.cache.RedisCacheConfiguration")
             .imp("org.springframework.data.redis.cache.RedisCacheManager")
             .imp("org.springframework.data.redis.connection.RedisConnectionFactory")
             .imp("org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer")
             .imp("org.springframework.data.redis.serializer.RedisSerializationContext")
             .imp("java.time.Duration")
             .imp("java.util.HashMap")
             .imp("java.util.Map");
        } else {
            w.imp("com.github.benmanes.caffeine.cache.Caffeine")
             .imp("org.springframework.cache.caffeine.CaffeineCacheManager")
             .imp("java.time.Duration")
             .imp("java.util.Arrays");
        }

        w.javadoc("Configuração de cache gerada pelo Spring Forge.\nProvider: " + provider);
        w.line("@Configuration")
         .line("@EnableCaching")
         .line("public class CacheConfig {").blank();
        w.indent();

        // Constantes de nomes de cache
        for (EntityDefinition e : cachedEntities) {
            String constant = NamingUtils.toSnakeCase(e.getName()).toUpperCase() + "_CACHE";
            w.line("public static final String " + constant + " = \"" + NamingUtils.toCamelCase(e.getName()) + "\";");
        }
        w.blank();

        if ("redis".equalsIgnoreCase(provider)) {
            buildRedisCacheManager(w, cachedEntities);
        } else {
            buildCaffeineCacheManager(w, cachedEntities);
        }

        w.unindent().line("}");
        return w;
    }

    private void buildRedisCacheManager(CodeWriter w, List<EntityDefinition> cachedEntities) {
        w.line("@Bean")
         .line("public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {")
         .indent()
         .line("RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()")
         .line("    .serializeValuesWith(RedisSerializationContext.SerializationPair")
         .line("        .fromSerializer(new GenericJackson2JsonRedisSerializer()))")
         .line("    .disableCachingNullValues();").blank()
         .line("Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();").blank();

        for (EntityDefinition e : cachedEntities) {
            CacheDefinition cache = e.getCache();
            String constant = NamingUtils.toSnakeCase(e.getName()).toUpperCase() + "_CACHE";
            w.line("cacheConfigs.put(" + constant + ", defaultConfig.entryTtl(Duration.ofSeconds(" + cache.getTtlSeconds() + ")));");
        }

        w.blank()
         .line("return RedisCacheManager.builder(connectionFactory)")
         .line("    .cacheDefaults(defaultConfig.entryTtl(Duration.ofSeconds(300)))")
         .line("    .withInitialCacheConfigurations(cacheConfigs)")
         .line("    .build();")
         .unindent().line("}").blank();
    }

    private void buildCaffeineCacheManager(CodeWriter w, List<EntityDefinition> cachedEntities) {
        // Usa o TTL/maxSize da primeira entidade como default (simplificação)
        CacheDefinition first = cachedEntities.get(0).getCache();

        w.line("@Bean")
         .line("public CacheManager cacheManager() {")
         .indent()
         .line("CaffeineCacheManager manager = new CaffeineCacheManager();")
         .line("manager.setCacheNames(Arrays.asList(");

        for (int i = 0; i < cachedEntities.size(); i++) {
            String constant = NamingUtils.toSnakeCase(cachedEntities.get(i).getName()).toUpperCase() + "_CACHE";
            String comma = (i < cachedEntities.size() - 1) ? "," : "";
            w.line("    " + constant + comma);
        }

        w.line("));")
         .line("manager.setCaffeine(Caffeine.newBuilder()")
         .line("    .expireAfterWrite(Duration.ofSeconds(" + first.getTtlSeconds() + "))")
         .line("    .maximumSize(" + first.getMaxSize() + "));")
         .line("return manager;")
         .unindent().line("}").blank();
    }

    private CodeWriter buildCachedServiceImpl(ForgeDefinition def, EntityDefinition entity, String pkg) {
        String name = entity.getName();
        String svcPkg = servicePkg(def);
        String dtoPkg = dtoPkg(def);
        String entityPkg = entityPkg(def);
        String configPkg = def.getProject().getBasePackage() + ".config";
        String excPkg = exceptionPkg(def);
        String constant = NamingUtils.toSnakeCase(name).toUpperCase() + "_CACHE";

        CodeWriter w = new CodeWriter();

        w.imp("org.springframework.cache.annotation.CacheEvict")
         .imp("org.springframework.cache.annotation.Cacheable")
         .imp("org.springframework.cache.annotation.CachePut")
         .imp("org.springframework.context.annotation.Primary")
         .imp("org.springframework.data.domain.Page")
         .imp("org.springframework.data.domain.Pageable")
         .imp("org.springframework.stereotype.Service")
         .imp(svcPkg + "." + name + "Service")
         .imp(dtoPkg + "." + name + "RequestDTO")
         .imp(dtoPkg + "." + name + "ResponseDTO")
         .imp(configPkg + ".CacheConfig");

        // Import action DTOs
        for (ActionDefinition a : entity.getActions()) {
            if (a.hasRequest()) w.imp(dtoPkg + "." + a.getRequestDtoName());
            if (a.hasResponse()) w.imp(dtoPkg + "." + a.getResponseDtoName());
        }

        // Import FilterDTO if entity has filters
        if (entity.hasFilters()) {
            w.imp(dtoPkg + "." + name + "FilterDTO");
        }

        w.javadoc("Service com cache para " + name + ".\nGerado pelo Spring Forge.\n\nDecorador @Primary que delega para o ServiceImpl e adiciona cache nos métodos CRUDL.");
        w.line("@Service")
         .line("@Primary")
         .line("public class " + name + "CachedServiceImpl implements " + name + "Service {").blank();
        w.indent();

        w.line("private final " + name + "ServiceImpl delegate;").blank();

        w.line("public " + name + "CachedServiceImpl(" + name + "ServiceImpl delegate) {")
         .indent()
         .line("this.delegate = delegate;")
         .unindent().line("}").blank();

        // findAll — delega sem cache
        w.line("@Override")
         .line("public Page<" + name + "ResponseDTO> findAll(Pageable pageable) {")
         .indent()
         .line("return delegate.findAll(pageable);")
         .unindent().line("}").blank();

        // search — delega sem cache (se tem filtros)
        if (entity.hasFilters()) {
            w.line("@Override")
             .line("public Page<" + name + "ResponseDTO> search(" + name + "FilterDTO filter, Pageable pageable) {")
             .indent()
             .line("return delegate.search(filter, pageable);")
             .unindent().line("}").blank();
        }

        // findById — @Cacheable
        w.line("@Override")
         .line("@Cacheable(value = CacheConfig." + constant + ", key = \"#id\")")
         .line("public " + name + "ResponseDTO findById(Long id) {")
         .indent()
         .line("return delegate.findById(id);")
         .unindent().line("}").blank();

        // create — @CacheEvict
        w.line("@Override")
         .line("@CacheEvict(value = CacheConfig." + constant + ", allEntries = true)")
         .line("public " + name + "ResponseDTO create(" + name + "RequestDTO dto) {")
         .indent()
         .line("return delegate.create(dto);")
         .unindent().line("}").blank();

        // update — @CachePut
        w.line("@Override")
         .line("@CachePut(value = CacheConfig." + constant + ", key = \"#id\")")
         .line("public " + name + "ResponseDTO update(Long id, " + name + "RequestDTO dto) {")
         .indent()
         .line("return delegate.update(id, dto);")
         .unindent().line("}").blank();

        // delete — @CacheEvict
        w.line("@Override")
         .line("@CacheEvict(value = CacheConfig." + constant + ", allEntries = true)")
         .line("public void delete(Long id) {")
         .indent()
         .line("delegate.delete(id);")
         .unindent().line("}").blank();

        // Delegate all action methods
        for (ActionDefinition a : entity.getActions()) {
            String returnType = a.hasResponse() ? a.getResponseDtoName() : "void";
            StringBuilder params = new StringBuilder();
            StringBuilder args = new StringBuilder();
            if (a.isRequiresId()) {
                params.append("Long id");
                args.append("id");
            }
            if (a.hasRequest()) {
                if (!params.isEmpty()) { params.append(", "); args.append(", "); }
                params.append(a.getRequestDtoName()).append(" dto");
                args.append("dto");
            }
            w.line("@Override")
             .line("public " + returnType + " " + a.getName() + "(" + params + ") {")
             .indent()
             .line((a.hasResponse() ? "return " : "") + "delegate." + a.getName() + "(" + args + ");")
             .unindent().line("}").blank();
        }

        w.unindent().line("}");
        return w;
    }
}
