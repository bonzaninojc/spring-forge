package io.springforge.generator;

import io.springforge.model.ActionDefinition;
import io.springforge.model.EntityDefinition;
import io.springforge.model.ForgeDefinition;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Gera infraestrutura Spring Security com RBAC:
 *   1. SecurityConfig — @Configuration com SecurityFilterChain (stateless JWT-ready)
 *   2. Adiciona @PreAuthorize nos controllers quando roles são configuradas
 *
 * Também gera (uma vez por projeto):
 *   - SecurityConfig.java
 *   - AppRole.java (enum com todas as roles usadas no forge.json)
 */
public class SecurityGenerator extends AbstractGenerator {

    public SecurityGenerator(Log log) { super(log); }

    @Override
    public void generate(ForgeDefinition def, EntityDefinition entity, File outDir) throws MojoExecutionException {
        // Geração por entidade não aplicável — tudo é gerado no global
    }

    /** Gera SecurityConfig + AppRole enum (chamado uma vez pelo Mojo) */
    public void generateGlobalSecurity(ForgeDefinition def, File outDir) throws MojoExecutionException {
        if (!def.getProject().isGenerateSecurity()) return;

        String pkg = securityPkg(def);

        // Coleta todas as roles usadas no projeto
        Set<String> allRoles = collectAllRoles(def);

        writeFile(buildAppRole(pkg, allRoles), javaFile(outDir, pkg, "AppRole"), pkg);
        writeFile(buildSecurityConfig(def, pkg), javaFile(outDir, pkg, "SecurityConfig"), pkg);
    }

    private Set<String> collectAllRoles(ForgeDefinition def) {
        Set<String> roles = new LinkedHashSet<>();
        for (EntityDefinition e : def.getEntities()) {
            roles.addAll(e.getRoles());
            for (ActionDefinition a : e.getActions()) {
                roles.addAll(a.getRoles());
            }
        }
        if (roles.isEmpty()) {
            roles.add("ADMIN");
            roles.add("USER");
        }
        return roles;
    }

    private CodeWriter buildAppRole(String pkg, Set<String> roles) {
        CodeWriter w = new CodeWriter();
        w.javadoc("Enum de roles da aplicação.\nGerado pelo Spring Forge — adicione novas roles conforme necessário.");
        w.line("public enum AppRole {").blank();
        w.indent();
        int i = 0;
        for (String role : roles) {
            String suffix = (++i < roles.size()) ? "," : ";";
            w.line(role.toUpperCase() + suffix);
        }
        w.blank();
        w.line("public String authority() { return \"ROLE_\" + name(); }");
        w.unindent().line("}");
        return w;
    }

    private CodeWriter buildSecurityConfig(ForgeDefinition def, String pkg) {
        CodeWriter w = new CodeWriter();

        w.imp("org.springframework.context.annotation.Bean")
         .imp("org.springframework.context.annotation.Configuration")
         .imp("org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity")
         .imp("org.springframework.security.config.annotation.web.builders.HttpSecurity")
         .imp("org.springframework.security.config.annotation.web.configuration.EnableWebSecurity")
         .imp("org.springframework.security.config.http.SessionCreationPolicy")
         .imp("org.springframework.security.web.SecurityFilterChain");

        w.javadoc("Configuração Spring Security com RBAC.\n"
                + "Gerado pelo Spring Forge — personalize conforme sua estratégia de autenticação (JWT, OAuth2, etc).");
        w.line("@Configuration")
         .line("@EnableWebSecurity")
         .line("@EnableMethodSecurity")
         .line("public class SecurityConfig {").blank();
        w.indent();

        w.line("@Bean")
         .line("public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {")
         .indent()
         .line("http")
         .line("    .csrf(csrf -> csrf.disable())")
         .line("    .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))")
         .line("    .authorizeHttpRequests(auth -> auth");

        // Swagger/OpenAPI endpoints liberados
        if (def.getProject().isGenerateOpenApi()) {
            w.line("        .requestMatchers(\"/swagger-ui/**\", \"/v3/api-docs/**\").permitAll()");
        }

        w.line("        .anyRequest().authenticated()")
         .line("    );");

        w.blank()
         .line("// TODO: Adicione seu filtro JWT ou OAuth2 resource server aqui")
         .line("// http.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));")
         .blank()
         .line("return http.build();")
         .unindent().line("}").blank();

        w.unindent().line("}");
        return w;
    }

    /**
     * Gera a expressão @PreAuthorize para um conjunto de roles.
     * Ex: @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
     */
    public static String preAuthorizeAnnotation(List<String> roles) {
        if (roles == null || roles.isEmpty()) return null;
        if (roles.size() == 1) {
            return "@PreAuthorize(\"hasRole('" + roles.get(0).toUpperCase() + "')\")";
        }
        StringBuilder sb = new StringBuilder("@PreAuthorize(\"hasAnyRole(");
        for (int i = 0; i < roles.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("'").append(roles.get(i).toUpperCase()).append("'");
        }
        sb.append(")\")");
        return sb.toString();
    }

    private String securityPkg(ForgeDefinition def) {
        return def.getProject().getBasePackage() + ".security";
    }
}
