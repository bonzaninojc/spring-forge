package io.springforge.generator;

import io.springforge.model.EntityDefinition;
import io.springforge.model.ForgeDefinition;
import io.springforge.util.NamingUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;

public class RepositoryGenerator extends AbstractGenerator {

    public RepositoryGenerator(Log log) { super(log); }

    @Override
    public void generate(ForgeDefinition def, EntityDefinition entity, File outDir) throws MojoExecutionException {
        if (!entity.shouldGenerate("repository")) return;

        String pkg = repoPkg(def);
        String name = entity.getName();
        CodeWriter w = new CodeWriter();

        w.imp("org.springframework.data.domain.Page")
         .imp("org.springframework.data.domain.Pageable")
         .imp("org.springframework.data.jpa.repository.JpaRepository")
         .imp("org.springframework.data.jpa.repository.JpaSpecificationExecutor")
         .imp("org.springframework.stereotype.Repository")
         .imp(entityPkg(def) + "." + name);

        if (entity.isSoftDelete()) {
            w.imp("org.springframework.data.jpa.repository.Query")
             .imp("java.util.Optional");
        }

        w.javadoc("Repositório Spring Data JPA para " + name + ".\nGerado pelo Spring Forge.");
        w.line("@Repository")
         .line("public interface " + name + "Repository")
         .line("        extends JpaRepository<" + name + ", Long>, JpaSpecificationExecutor<" + name + "> {")
         .blank();
        w.indent();

        if (entity.isSoftDelete()) {
            w.line("@Query(\"SELECT e FROM " + name + " e WHERE e.id = :id AND e.deletedAt IS NULL\")")
             .line("java.util.Optional<" + name + "> findByIdActive(Long id);")
             .blank()
             .line("@Query(\"SELECT e FROM " + name + " e WHERE e.deletedAt IS NULL\")")
             .line("Page<" + name + "> findAllActive(Pageable pageable);")
             .blank();
        }

        // Finder por campos únicos
        entity.getFields().stream()
            .filter(f -> f.isUnique())
            .forEach(f -> {
                String cap = NamingUtils.toPascalCase(f.getName());
                String type = NamingUtils.toJavaType(f.getType());
                w.line("java.util.Optional<" + name + "> findBy" + cap + "(" + type + " " + f.getName() + ");")
                 .blank();
            });

        w.unindent().line("}");

        writeFile(w, javaFile(outDir, pkg, name + "Repository"), pkg);
    }
}
