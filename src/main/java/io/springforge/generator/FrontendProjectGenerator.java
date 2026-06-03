package io.springforge.generator;

import io.springforge.model.EntityDefinition;
import io.springforge.model.ForgeDefinition;
import io.springforge.util.NamingUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Gera arquivos de configuração do projeto frontend Vite+React+TS.
 * package.json, vite.config.ts, tsconfig*.json, index.html, main.tsx
 */
public class FrontendProjectGenerator {

    private final Log log;

    public FrontendProjectGenerator(Log log) { this.log = log; }

    public void generate(ForgeDefinition def, File outDir) throws MojoExecutionException {
        if (!def.getProject().isGenerateFrontend()) return;

        File frontendRoot = new File(outDir.getParentFile().getParentFile(),
                def.getProject().getFrontendDir()).getParentFile();

        generatePackageJson(def, frontendRoot);
        generateViteConfig(frontendRoot);
        generateTsConfig(frontendRoot);
        generateTsConfigApp(frontendRoot);
        generateTsConfigNode(frontendRoot);
        generateIndexHtml(def, frontendRoot);
        generateMain(def, frontendRoot);
        generatePackageLock(def, frontendRoot);
    }

    private void generatePackageJson(ForgeDefinition def, File root) throws MojoExecutionException {
        String name = NamingUtils.toSnakeCase(def.getProject().getName()).replace("_", "-");
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"name\": \"").append(name).append("\",\n");
        sb.append("  \"private\": true,\n");
        sb.append("  \"version\": \"0.1.0\",\n");
        sb.append("  \"type\": \"module\",\n");
        sb.append("  \"scripts\": {\n");
        sb.append("    \"dev\": \"vite\",\n");
        sb.append("    \"build\": \"tsc -b && vite build\",\n");
        sb.append("    \"preview\": \"vite preview\"\n");
        sb.append("  },\n");
        sb.append("  \"dependencies\": {\n");
        sb.append("    \"@emotion/react\": \"^11.13.5\",\n");
        sb.append("    \"@emotion/styled\": \"^11.13.5\",\n");
        sb.append("    \"@mui/icons-material\": \"^6.3.0\",\n");
        sb.append("    \"@mui/material\": \"^6.3.0\",\n");
        sb.append("    \"@reduxjs/toolkit\": \"^2.5.0\",\n");
        sb.append("    \"axios\": \"^1.7.9\",\n");
        sb.append("    \"notistack\": \"^3.0.1\",\n");
        sb.append("    \"react\": \"^19.0.0\",\n");
        sb.append("    \"react-dom\": \"^19.0.0\",\n");
        sb.append("    \"react-redux\": \"^9.2.0\",\n");
        sb.append("    \"react-router-dom\": \"^7.1.1\",\n");
        sb.append("    \"react-hook-form\": \"^7.54.0\",\n");
        sb.append("    \"@hookform/resolvers\": \"^3.9.0\",\n");
        sb.append("    \"zod\": \"^3.23.0\"\n");
        sb.append("  },\n");
        sb.append("  \"devDependencies\": {\n");
        sb.append("    \"@types/react\": \"^19.0.0\",\n");
        sb.append("    \"@types/react-dom\": \"^19.0.0\",\n");
        sb.append("    \"@vitejs/plugin-react\": \"^4.3.4\",\n");
        sb.append("    \"typescript\": \"~5.7.0\",\n");
        sb.append("    \"vite\": \"^6.0.0\"\n");
        sb.append("  }\n");
        sb.append("}\n");
        writeFile(sb.toString(), new File(root, "package.json"));
    }

    private void generateViteConfig(File root) throws MojoExecutionException {
        StringBuilder sb = new StringBuilder();
        sb.append("import { defineConfig } from 'vite';\n");
        sb.append("import react from '@vitejs/plugin-react';\n\n");
        sb.append("export default defineConfig({\n");
        sb.append("  plugins: [react()],\n");
        sb.append("  server: {\n");
        sb.append("    port: 5173,\n");
        sb.append("    proxy: {\n");
        sb.append("      '/api': {\n");
        sb.append("        target: 'http://localhost:8080',\n");
        sb.append("        changeOrigin: true,\n");
        sb.append("      },\n");
        sb.append("    },\n");
        sb.append("  },\n");
        sb.append("});\n");
        writeFile(sb.toString(), new File(root, "vite.config.ts"));
    }

    private void generateTsConfig(File root) throws MojoExecutionException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"files\": [],\n");
        sb.append("  \"references\": [\n");
        sb.append("    { \"path\": \"./tsconfig.app.json\" },\n");
        sb.append("    { \"path\": \"./tsconfig.node.json\" }\n");
        sb.append("  ]\n");
        sb.append("}\n");
        writeFile(sb.toString(), new File(root, "tsconfig.json"));
    }

    private void generateTsConfigApp(File root) throws MojoExecutionException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"compilerOptions\": {\n");
        sb.append("    \"target\": \"ES2020\",\n");
        sb.append("    \"useDefineForClassFields\": true,\n");
        sb.append("    \"lib\": [\"ES2020\", \"DOM\", \"DOM.Iterable\"],\n");
        sb.append("    \"module\": \"ESNext\",\n");
        sb.append("    \"skipLibCheck\": true,\n");
        sb.append("    \"moduleResolution\": \"bundler\",\n");
        sb.append("    \"allowImportingTsExtensions\": true,\n");
        sb.append("    \"isolatedModules\": true,\n");
        sb.append("    \"moduleDetection\": \"force\",\n");
        sb.append("    \"noEmit\": true,\n");
        sb.append("    \"jsx\": \"react-jsx\",\n");
        sb.append("    \"strict\": true,\n");
        sb.append("    \"noUnusedLocals\": true,\n");
        sb.append("    \"noUnusedParameters\": true,\n");
        sb.append("    \"noFallthroughCasesInSwitch\": true,\n");
        sb.append("    \"noUncheckedSideEffectImports\": true\n");
        sb.append("  },\n");
        sb.append("  \"include\": [\"src\"]\n");
        sb.append("}\n");
        writeFile(sb.toString(), new File(root, "tsconfig.app.json"));
    }

    private void generateTsConfigNode(File root) throws MojoExecutionException {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"compilerOptions\": {\n");
        sb.append("    \"target\": \"ES2022\",\n");
        sb.append("    \"lib\": [\"ES2023\"],\n");
        sb.append("    \"module\": \"ESNext\",\n");
        sb.append("    \"skipLibCheck\": true,\n");
        sb.append("    \"moduleResolution\": \"bundler\",\n");
        sb.append("    \"allowImportingTsExtensions\": true,\n");
        sb.append("    \"isolatedModules\": true,\n");
        sb.append("    \"moduleDetection\": \"force\",\n");
        sb.append("    \"noEmit\": true,\n");
        sb.append("    \"strict\": true,\n");
        sb.append("    \"noUnusedLocals\": true,\n");
        sb.append("    \"noUnusedParameters\": true,\n");
        sb.append("    \"noFallthroughCasesInSwitch\": true,\n");
        sb.append("    \"noUncheckedSideEffectImports\": true\n");
        sb.append("  },\n");
        sb.append("  \"include\": [\"vite.config.ts\"]\n");
        sb.append("}\n");
        writeFile(sb.toString(), new File(root, "tsconfig.node.json"));
    }

    private void generateIndexHtml(ForgeDefinition def, File root) throws MojoExecutionException {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html>\n");
        sb.append("<html lang=\"pt-BR\">\n");
        sb.append("  <head>\n");
        sb.append("    <meta charset=\"UTF-8\" />\n");
        sb.append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\" />\n");
        sb.append("    <link rel=\"preconnect\" href=\"https://fonts.googleapis.com\" />\n");
        sb.append("    <link rel=\"preconnect\" href=\"https://fonts.gstatic.com\" crossorigin />\n");
        sb.append("    <link href=\"https://fonts.googleapis.com/css2?family=Inter:wght@300;400;500;600;700&display=swap\" rel=\"stylesheet\" />\n");
        sb.append("    <title>").append(def.getProject().getName()).append("</title>\n");
        sb.append("  </head>\n");
        sb.append("  <body>\n");
        sb.append("    <div id=\"root\"></div>\n");
        sb.append("    <script type=\"module\" src=\"/src/main.tsx\"></script>\n");
        sb.append("  </body>\n");
        sb.append("</html>\n");
        writeFile(sb.toString(), new File(root, "index.html"));
    }

    private void generateMain(ForgeDefinition def, File root) throws MojoExecutionException {
        File srcDir = new File(root, "src");
        StringBuilder sb = new StringBuilder();
        sb.append("import React from 'react';\n");
        sb.append("import ReactDOM from 'react-dom/client';\n");
        sb.append("import App from './App';\n\n");
        sb.append("ReactDOM.createRoot(document.getElementById('root')!).render(\n");
        sb.append("  <React.StrictMode>\n");
        sb.append("    <App />\n");
        sb.append("  </React.StrictMode>\n");
        sb.append(");\n");
        writeFile(sb.toString(), new File(srcDir, "main.tsx"));
    }

    private void generatePackageLock(ForgeDefinition def, File root) throws MojoExecutionException {
        String name = NamingUtils.toSnakeCase(def.getProject().getName()).replace("_", "-");
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"name\": \"").append(name).append("\",\n");
        sb.append("  \"version\": \"0.1.0\",\n");
        sb.append("  \"lockfileVersion\": 3,\n");
        sb.append("  \"requires\": true,\n");
        sb.append("  \"packages\": {\n");
        sb.append("    \"\": {\n");
        sb.append("      \"name\": \"").append(name).append("\",\n");
        sb.append("      \"version\": \"0.1.0\",\n");
        sb.append("      \"dependencies\": {\n");
        sb.append("        \"@emotion/react\": \"^11.13.5\",\n");
        sb.append("        \"@emotion/styled\": \"^11.13.5\",\n");
        sb.append("        \"@mui/icons-material\": \"^6.3.0\",\n");
        sb.append("        \"@mui/material\": \"^6.3.0\",\n");
        sb.append("        \"@reduxjs/toolkit\": \"^2.5.0\",\n");
        sb.append("        \"axios\": \"^1.7.9\",\n");
        sb.append("        \"notistack\": \"^3.0.1\",\n");
        sb.append("        \"react\": \"^19.0.0\",\n");
        sb.append("        \"react-dom\": \"^19.0.0\",\n");
        sb.append("        \"react-redux\": \"^9.2.0\",\n");
        sb.append("        \"react-router-dom\": \"^7.1.1\"\n");
        sb.append("      },\n");
        sb.append("      \"devDependencies\": {\n");
        sb.append("        \"@types/react\": \"^19.0.0\",\n");
        sb.append("        \"@types/react-dom\": \"^19.0.0\",\n");
        sb.append("        \"@vitejs/plugin-react\": \"^4.3.4\",\n");
        sb.append("        \"typescript\": \"~5.7.0\",\n");
        sb.append("        \"vite\": \"^6.0.0\"\n");
        sb.append("      }\n");
        sb.append("    }\n");
        sb.append("  }\n");
        sb.append("}\n");
        writeFile(sb.toString(), new File(root, "package-lock.json"));
    }

    private void writeFile(String content, File target) throws MojoExecutionException {
        try {
            Files.createDirectories(target.getParentFile().toPath());
            try (Writer w = new OutputStreamWriter(new FileOutputStream(target), StandardCharsets.UTF_8)) {
                w.write(content);
            }
            log.info("  [GERADO] " + target.getPath());
        } catch (Exception e) {
            throw new MojoExecutionException("Erro ao gravar " + target.getName() + ": " + e.getMessage(), e);
        }
    }
}
