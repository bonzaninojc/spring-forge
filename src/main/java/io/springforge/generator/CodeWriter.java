package io.springforge.generator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Wrapper fluente sobre StringBuilder para geração de código Java.
 * Zero dependências externas — puro Java.
 *
 * Uso:
 *   CodeWriter w = new CodeWriter();
 *   w.line("package com.foo;")
 *    .blank()
 *    .line("public class Foo {")
 *    .indent()
 *      .line("private String bar;")
 *    .unindent()
 *    .line("}");
 */
public class CodeWriter {

    private final StringBuilder sb = new StringBuilder(4096);
    private final Set<String> imports = new LinkedHashSet<>();
    private int indentLevel = 0;
    private static final String INDENT = "    "; // 4 espaços

    // ===================== Imports =====================

    /** Registra um import que será inserido pelo método toSource() */
    public CodeWriter imp(String fullClass) {
        if (fullClass != null && !fullClass.isBlank() && fullClass.contains(".")) {
            imports.add(fullClass);
        }
        return this;
    }

    /** Registra imports condicionalmente */
    public CodeWriter impIf(boolean condition, String fullClass) {
        if (condition) imp(fullClass);
        return this;
    }

    // ===================== Escrita =====================

    /** Escreve uma linha com indentação atual + \n */
    public CodeWriter line(String text) {
        if (indentLevel > 0) {
            sb.append(INDENT.repeat(indentLevel));
        }
        sb.append(text).append('\n');
        return this;
    }

    /** Linha em branco */
    public CodeWriter blank() {
        sb.append('\n');
        return this;
    }

    /** Linha de comentário Javadoc */
    public CodeWriter javadoc(String text) {
        line("/**");
        for (String l : text.split("\n")) {
            line(" * " + l);
        }
        line(" */");
        return this;
    }

    /** Abre bloco (adiciona " {" na linha anterior implícito — use após line()) */
    public CodeWriter openBlock() {
        // Já foi escrito na line anterior: "public class Foo {"
        indentLevel++;
        return this;
    }

    /** Fecha bloco com "}" */
    public CodeWriter closeBlock() {
        indentLevel = Math.max(0, indentLevel - 1);
        line("}");
        return this;
    }

    /** Fecha bloco com "}" + linha em branco */
    public CodeWriter closeBlockBlank() {
        closeBlock();
        blank();
        return this;
    }

    /** Aumenta indentação manualmente */
    public CodeWriter indent() {
        indentLevel++;
        return this;
    }

    /** Diminui indentação manualmente */
    public CodeWriter unindent() {
        indentLevel = Math.max(0, indentLevel - 1);
        return this;
    }

    // ===================== Saída =====================

    /**
     * Retorna o fonte completo.
     * NÃO inclui package/imports — use toSource(packageName) para isso.
     */
    public String build() {
        return sb.toString();
    }

    /**
     * Retorna o fonte completo com package + imports + corpo.
     * Os imports são ordenados: java.* → jakarta.* → org.* → resto.
     */
    public String toSource(String packageName) {
        StringBuilder out = new StringBuilder(sb.length() + 512);
        out.append("package ").append(packageName).append(";\n\n");

        if (!imports.isEmpty()) {
            imports.stream()
                .sorted(CodeWriter::importOrder)
                .forEach(imp -> out.append("import ").append(imp).append(";\n"));
            out.append('\n');
        }

        out.append(sb);
        return out.toString();
    }

    /** Grava o fonte em arquivo, criando diretórios necessários */
    public void writeTo(File file, String packageName) throws Exception {
        Files.createDirectories(file.getParentFile().toPath());
        try (Writer w = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            w.write(toSource(packageName));
        }
    }

    // ===================== Privado =====================

    private static int importOrder(String a, String b) {
        return importGroup(a) != importGroup(b)
            ? Integer.compare(importGroup(a), importGroup(b))
            : a.compareTo(b);
    }

    private static int importGroup(String imp) {
        if (imp.startsWith("java."))    return 0;
        if (imp.startsWith("jakarta.")) return 1;
        if (imp.startsWith("org."))     return 2;
        if (imp.startsWith("com."))     return 3;
        return 4;
    }
}
