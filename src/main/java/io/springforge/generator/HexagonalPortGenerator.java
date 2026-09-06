package io.springforge.generator;

import io.springforge.model.ActionDefinition;
import io.springforge.model.EntityDefinition;
import io.springforge.model.ForgeDefinition;
import io.springforge.util.NamingUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;

/**
 * Gerador — Arquitetura Hexagonal: Ports (In e Out).
 *
 * Gera dois tipos de porta:
 *
 * <b>Port de Entrada (In Port / Use-Case Interface)</b>
 *   Pacote: {basePackage}.application.port.in
 *   Arquivo: {EntityName}UseCase.java
 *   Define o contrato de operações que o adapter de entrada (REST) pode invocar.
 *
 * <b>Port de Saída (Out Port / Repository Interface)</b>
 *   Pacote: {basePackage}.application.port.out
 *   Arquivo: {EntityName}RepositoryPort.java
 *   Define o contrato de persistência que o adapter de saída (JPA) deve implementar.
 */
public class HexagonalPortGenerator extends AbstractGenerator {

    public HexagonalPortGenerator(Log log) { super(log); }

    @Override
    public void generate(ForgeDefinition def, EntityDefinition entity, File outDir)
            throws MojoExecutionException {

        if (!def.getProject().isHexagonal()) return;
        if (!entity.shouldGenerate("service")) return;

        String name = entity.getName();

        // Port de Entrada
        String inPkg = hexPortInPkg(def);
        writeFile(buildInPort(def, entity, inPkg),
                  javaFile(outDir, inPkg, name + "UseCase"), inPkg);

        // Port de Saída
        String outPkg = hexPortOutPkg(def);
        writeFile(buildOutPort(def, entity, outPkg),
                  javaFile(outDir, outPkg, name + "RepositoryPort"), outPkg);
    }

    // ── Port de Entrada ─────────────────────────────────────────────────────────

    private CodeWriter buildInPort(ForgeDefinition def, EntityDefinition entity, String pkg) {
        String name    = entity.getName();
        String dtoPkg  = hexDtoPkg(def);
        String excPkg  = exceptionPkg(def);
        CodeWriter w   = new CodeWriter();

        w.imp("org.springframework.data.domain.Page")
         .imp("org.springframework.data.domain.Pageable")
         .imp(dtoPkg + "." + name + "RequestDTO")
         .imp(dtoPkg + "." + name + "ResponseDTO");

        if (entity.hasFilters()) {
            w.imp(dtoPkg + "." + name + "FilterDTO");
        }

        for (ActionDefinition a : entity.getActions()) {
            if (a.hasRequest())  w.imp(dtoPkg + "." + a.getRequestDtoName());
            if (a.hasResponse()) w.imp(dtoPkg + "." + a.getResponseDtoName());
        }

        w.javadoc(
            "Port de entrada (Use-Case) para " + name + ".\n" +
            "Define as operações disponíveis para os adapters de entrada (ex: REST).\n" +
            "Gerado pelo Spring Forge — Arquitetura Hexagonal."
        );

        w.line("public interface " + name + "UseCase {").blank();
        w.indent();

        w.javadoc("Lista todos com paginação.")
         .line("Page<" + name + "ResponseDTO> findAll(Pageable pageable);")
         .blank();

        if (entity.hasFilters()) {
            w.javadoc("Busca com filtros via DTO.")
             .line("Page<" + name + "ResponseDTO> search(" + name + "FilterDTO filter, Pageable pageable);")
             .blank();
        }

        w.javadoc("Busca pelo ID.\n@throws " + excPkg + "." + name + "NotFoundException se não encontrado")
         .line(name + "ResponseDTO findById(Long id);")
         .blank();

        w.javadoc("Cria um novo registro.")
         .line(name + "ResponseDTO create(" + name + "RequestDTO dto);")
         .blank();

        w.javadoc("Atualiza um registro existente.\n@throws " + excPkg + "." + name + "NotFoundException se não encontrado")
         .line(name + "ResponseDTO update(Long id, " + name + "RequestDTO dto);")
         .blank();

        w.javadoc("Remove" + (entity.isSoftDelete() ? " (soft delete)" : "") + " pelo ID.\n@throws " + excPkg + "." + name + "NotFoundException se não encontrado")
         .line("void delete(Long id);")
         .blank();

        if (entity.hasActions()) {
            w.line("// ── Actions customizadas ─────────────────────────────────────────────────────────")
             .blank();

            for (ActionDefinition a : entity.getActions()) {
                String ret   = a.hasResponse() ? a.getResponseDtoName() : "void";
                String param = buildActionParams(a);
                String desc  = a.getDescription() != null ? a.getDescription()
                    : "Action '" + a.getName() + "' — implemente no Use-Case.";

                w.javadoc(desc + "\n@implSpec Implemente em " + name + "UseCaseImpl.")
                 .line(ret + " " + a.getName() + "(" + param + ");")
                 .blank();
            }
        }

        w.unindent().line("}");
        return w;
    }

    // ── Port de Saída ────────────────────────────────────────────────────────────

    private CodeWriter buildOutPort(ForgeDefinition def, EntityDefinition entity, String pkg) {
        String name       = entity.getName();
        String domainPkg  = hexDomainModelPkg(def);
        CodeWriter w      = new CodeWriter();

        w.imp("java.util.Optional")
         .imp("java.util.List")
         .imp("org.springframework.data.domain.Page")
         .imp("org.springframework.data.domain.Pageable")
         .imp(domainPkg + "." + name);

        w.javadoc(
            "Port de saída (Repository Port) para " + name + ".\n" +
            "Define o contrato de persistência que o adapter de saída (JPA) deve implementar.\n" +
            "O núcleo da aplicação depende apenas desta interface, nunca do JPA diretamente.\n" +
            "Gerado pelo Spring Forge — Arquitetura Hexagonal."
        );

        w.line("public interface " + name + "RepositoryPort {").blank();
        w.indent();

        w.javadoc("Salva (cria ou atualiza) um registro de domínio.")
         .line(name + " save(" + name + " domain);")
         .blank();

        w.javadoc("Busca pelo ID. Retorna Optional vazio se não encontrado.")
         .line("Optional<" + name + "> findById(Long id);")
         .blank();

        if (entity.isSoftDelete()) {
            w.javadoc("Busca pelo ID ignorando registros excluídos logicamente.")
             .line("Optional<" + name + "> findByIdActive(Long id);")
             .blank();
        }

        w.javadoc("Lista todos com paginação" + (entity.isSoftDelete() ? " (ignora excluídos logicamente)" : "") + ".")
         .line("Page<" + name + "> findAll(Pageable pageable);")
         .blank();

        w.javadoc("Remove fisicamente o registro pelo ID.")
         .line("void deleteById(Long id);")
         .blank();

        w.javadoc("Verifica se existe um registro com o ID informado.")
         .line("boolean existsById(Long id);")
         .blank();

        w.unindent().line("}");
        return w;
    }

    private String buildActionParams(ActionDefinition a) {
        StringBuilder sb = new StringBuilder();
        if (a.isRequiresId()) sb.append("Long id");
        if (a.hasRequest()) {
            if (!sb.isEmpty()) sb.append(", ");
            sb.append(a.getRequestDtoName()).append(" dto");
        }
        return sb.toString();
    }
}
