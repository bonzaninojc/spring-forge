package io.springforge.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.springforge.model.*;
import io.springforge.parser.ForgeJsonParser;
import org.apache.maven.plugin.logging.SystemStreamLog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SesiLaboralGeneratorTest {
    @TempDir Path temp;
    private final SystemStreamLog log = new SystemStreamLog();

    private ForgeDefinition definition() throws Exception {
        Path json = temp.resolve("forge.json");
        Files.writeString(json, """
            { "project": { "name": "Laboral", "basePackage": "com.example", "architectureStyle": "SESI_LABORAL",
                "generateFrontend": true },
              "entities": [ { "name": "Product", "schema": "laboral", "apiPath": "/api/products", "fields": [
                { "name": "title", "type": "String", "label": "Título", "placeholder": "Informe o título", "helperText": "Nome exibido na listagem", "required": true, "minLength": 3 },
                { "name": "price", "type": "BigDecimal" },
                { "name": "active", "type": "Boolean" },
                { "name": "status", "type": "Enum", "enumValues": ["ACTIVE", "INACTIVE"] },
                { "name": "documentCpf", "type": "String" },
                { "name": "startDate", "type": "LocalDate" },
                { "name": "createdOn", "type": "LocalDateTime", "readOnly": true }
              ], "filters": [
                { "name": "title", "label": "Título", "type": "String", "operator": "CONTAINS" },
                { "name": "status", "label": "Status", "type": "Enum", "enumValues": ["ACTIVE", "INACTIVE"] },
                { "name": "active", "label": "Ativo", "type": "Boolean" },
                { "name": "priceMin", "label": "Preço mínimo", "type": "BigDecimal", "targetField": "price" }
              ], "relations": [{ "type": "ManyToOne", "targetEntity": "Category", "fieldName": "category", "displayField": "name", "required": true }] },
              { "name": "Category", "schema": "catalog", "fields": [{ "name": "name", "type": "String" }] } ] }
            """);
        return new ForgeJsonParser().parse(json.toFile());
    }

    @Test void parsesAndSerializesNewArchitecture() throws Exception {
        ForgeDefinition def = definition();
        assertTrue(def.getProject().isSesiLaboral());
        assertTrue(def.getProject().isModular());
        String json = new ObjectMapper().writeValueAsString(def);
        assertTrue(json.contains("\"architectureStyle\":\"SESI_LABORAL\""));
        assertEquals(ArchitectureStyle.LAYERED, new ProjectConfig().getArchitectureStyle());
    }

    @Test void generatesConsistentModuleImportsAndRelations() throws Exception {
        ForgeDefinition def = definition();
        Path out = System.getProperty("forge.test.frontendRoot") == null ? temp.resolve("java")
            : Path.of(System.getProperty("forge.test.frontendRoot")).resolve("java");
        for (var entity : def.getEntities()) {
            for (var generator : List.of(new EntityGenerator(log), new RepositoryGenerator(log), new DtoGenerator(log),
                new FilterGenerator(log), new MapperGenerator(log), new ServiceGenerator(log), new ControllerGenerator(log),
                new ExceptionGenerator(log))) {
                generator.generate(def, entity, out.toFile());
            }
        }
        String entity = Files.readString(out.resolve("com/example/modules/product/entity/Product.java"));
        assertTrue(entity.contains("import com.example.modules.category.entity.Category;"));
        assertTrue(entity.contains("@Table(name = \"product\", schema = \"laboral\")"));
        String service = Files.readString(out.resolve("com/example/modules/product/service/impl/ProductServiceImpl.java"));
        assertTrue(service.contains("import com.example.modules.product.entity.Product;"));
        assertTrue(service.contains("import com.example.modules.category.repository.CategoryRepository;"));
        String controller = Files.readString(out.resolve("com/example/modules/product/controller/ProductController.java"));
        assertTrue(controller.contains("@RequestMapping(\"/api/products\")"));
        assertTrue(controller.contains("import com.example.modules.product.service.ProductService;"));
        assertTrue(controller.contains("service.search(filter, filter.toPageable())"));
        String filterDto = Files.readString(out.resolve("com/example/modules/product/dto/ProductFilterDTO.java"));
        assertTrue(filterDto.contains("private int page = 0;"));
        assertTrue(filterDto.contains("private String sortBy = \"id\";"));
        assertTrue(filterDto.contains("public Pageable toPageable()"));
        String requestDto = Files.readString(out.resolve("com/example/modules/product/dto/ProductRequestDTO.java"));
        assertTrue(requestDto.contains("Category é obrigatório"));
        assertFalse(Files.exists(out.resolve("com/example/modules/product/domain")));
    }

    @Test void qualifiesMigrationsWithEntitySchema() throws Exception {
        ForgeDefinition def = definition();
        def.getProject().setGenerateMigrations(true);
        Path out = temp.resolve("target/generated-sources/spring-forge");
        MigrationGenerator generator = new MigrationGenerator(log);
        generator.generate(def, def.getEntities().get(0), out.toFile());

        Path migrations = temp.resolve("target/generated-resources/spring-forge/db/migration");
        Path migration;
        try (var files = Files.list(migrations)) {
            migration = files.findFirst().orElseThrow();
        }
        String sql = Files.readString(migration);
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS laboral.product"));
        assertTrue(sql.contains("ALTER TABLE laboral.product"));
        assertTrue(sql.contains("REFERENCES catalog.category(id)"));
        assertTrue(sql.contains("COMMENT ON TABLE laboral.product"));
    }

    @Test void generatesSesiLaboralFeatureExtension() throws Exception {
        ForgeDefinition def = definition();
        Path root = System.getProperty("forge.test.frontendRoot") == null ? temp : Path.of(System.getProperty("forge.test.frontendRoot"));
        Path out = root.resolve("generated-sources/spring-forge");
        Path frontend = root.resolve("frontend");
        Files.createDirectories(frontend.resolve("src/router"));
        Files.createDirectories(frontend.resolve("src/navigation/vertical"));
        Path packageJson = frontend.resolve("package.json");
        Path appVue = frontend.resolve("src/App.vue");
        Path routerIndex = frontend.resolve("src/router/index.ts");
        Path navigationIndex = frontend.resolve("src/navigation/vertical/index.ts");
        if (!Files.exists(packageJson)) Files.writeString(packageJson, "{\"name\":\"existing-sesi-laboral\"}");
        if (!Files.exists(appVue)) Files.writeString(appVue, "<template>existing application</template>");
        if (!Files.exists(routerIndex)) Files.writeString(routerIndex, """
            import authRoutes from './routes/auth';
            const canNavigate = () => true;
            export default { routes: [...authRoutes, ...privateRoutes] };
            """);
        if (!Files.exists(navigationIndex)) Files.writeString(navigationIndex, """
            import type { NavMenuItem } from '@/types/nav-menu';
            import empresas from './empresas';

            const navMenuItems: NavMenuItem[] = [
              ...empresas,
            ];

            export default navMenuItems;
            """);
        String existingPackage = Files.readString(packageJson);
        String existingApp = Files.readString(appVue);
        FrontendGenerator generator = new FrontendGenerator(log);
        for (var entity : def.getEntities()) generator.generate(def, entity, out.toFile());
        generator.generateGlobalFiles(def, out.toFile());
        generator.generateGlobalFiles(def, out.toFile());
        new FrontendProjectGenerator(log).generate(def, out.toFile());
        assertTrue(Files.readString(frontend.resolve("src/services/productService.ts")).contains("const path = \"api/products\""));
        assertTrue(Files.readString(frontend.resolve("src/services/productService.ts")).contains("BaseService().post(`${path}/search`, payload)"));
        assertTrue(Files.readString(frontend.resolve("src/models/product.ts")).contains("\"categoryId\"?: number"));
        String tablePage = Files.readString(frontend.resolve("src/components/DashboardComponents/ProductsTable/ProductsTable.vue"));
        String listPage = Files.readString(frontend.resolve("src/views/pages/DashboardProducts/ListarProducts.vue"));
        String editPage = Files.readString(frontend.resolve("src/views/pages/DashboardProducts/EditarProducts.vue"));
        String createPage = Files.readString(frontend.resolve("src/views/pages/DashboardProducts/CadastrarProducts.vue"));
        String productService = Files.readString(frontend.resolve("src/services/productService.ts"));
        assertTrue(tablePage.contains("class=\"confirm-overlay\""));
        assertFalse(tablePage.contains("@/components/DashboardComponents/ConfirmModal/ConfirmModal.vue"));
        assertTrue(listPage.contains("@/views/pages/dashboardLayout/DashboardLayout.vue"));
        assertTrue(listPage.contains("debounceTimer"));
        assertTrue(listPage.contains(":filters=\"filters\""));
        assertTrue(editPage.startsWith("<template>\n  <DashboardLayout>"));
        assertTrue(editPage.contains("\n            <v-col cols=\"12\" md=\"4\">"));
        assertTrue(editPage.contains("\n<script setup lang=\"ts\">\n"));
        assertFalse(editPage.contains("<template><DashboardLayout>"));
        assertFalse(editPage.contains("; import "));
        assertTrue(listPage.contains("\n        <section class=\"filters-section\">"));
        assertTrue(tablePage.contains("\n      <thead>\n        <tr>"));
        assertTrue(listPage.contains("<style lang=\"scss\" scoped>"));
        assertTrue(listPage.contains("background-color: #f7f7f9;"));
        assertTrue(tablePage.contains(".custom-table-card {"));
        assertTrue(tablePage.contains("background: #002060;"));
        assertTrue(tablePage.contains("<div class=\"spinner\" />"));
        assertTrue(editPage.contains(".button-row-actions {"));
        assertTrue(createPage.contains("import CustomDate from"));
        assertTrue(createPage.contains("import CustomNumberInput from"));
        assertTrue(createPage.contains("id=\"status\""));
        assertTrue(createPage.contains("id=\"active\""));
        assertTrue(createPage.contains(":items=\"categoryOptions\""));
        assertTrue(createPage.contains("loadCategories({ page: 0, size: 100"));
        assertTrue(createPage.contains("function validateForm()"));
        assertTrue(createPage.contains("function buildPayload()"));
        assertTrue(createPage.contains("@update:model-value=\"handleAlertClose\""));
        assertTrue(productService.contains("normalizeFilterValue"));
        assertTrue(productService.contains("value !== undefined"));
        assertTrue(listPage.contains("{ title: 'Não', value: false }"));
        assertTrue(listPage.contains("active: null"));
        assertTrue(tablePage.contains("fieldType === 'Boolean'"));
        String routes = Files.readString(frontend.resolve("src/router/routes/generated.ts"));
        assertTrue(routes.contains("RouteRecordRaw"));
        assertTrue(routes.contains("ListarProducts"));
        assertTrue(routes.contains("ListarCategories"));
        assertTrue(routes.contains("{\n    path: \"/product/listar\",\n    name: \"listar-product\","));
        assertEquals(existingApp, Files.readString(appVue));
        assertEquals(existingPackage, Files.readString(packageJson));
        String router = Files.readString(routerIndex);
        assertTrue(router.contains("import generatedRoutes from './routes/generated';"));
        assertTrue(router.contains("routes: [...authRoutes, ...privateRoutes, ...generatedRoutes]"));
        String navigation = Files.readString(frontend.resolve("src/navigation/vertical/generated.ts"));
        assertTrue(navigation.contains("header: \"Products\""));
        assertTrue(navigation.contains("route: \"cadastrar-product\""));
        assertTrue(navigation.contains("route: \"listar-category\""));
        String navigationEntry = Files.readString(navigationIndex);
        assertTrue(navigationEntry.contains("import empresas from './empresas'"));
        assertTrue(navigationEntry.contains("import generatedNavMenuItems from './generated';"));
        assertTrue(navigationEntry.contains("...generatedNavMenuItems,"));
        assertEquals(navigationEntry.indexOf("...generatedNavMenuItems"), navigationEntry.lastIndexOf("...generatedNavMenuItems"));
    }

    @Test void honorsFrontendAndEntityGenerationFlags() throws Exception {
        ForgeDefinition def = definition();
        Path out = temp.resolve("generated-sources/spring-forge");
        def.getProject().setGenerateFrontend(false);
        new FrontendGenerator(log).generate(def, def.getEntities().get(0), out.toFile());
        new FrontendProjectGenerator(log).generate(def, out.toFile());
        assertFalse(Files.exists(temp.resolve("frontend")));
        def.getProject().setGenerateFrontend(true);
        def.getEntities().get(0).setGenerate(List.of("entity"));
        new FrontendGenerator(log).generate(def, def.getEntities().get(0), out.toFile());
        assertFalse(Files.exists(temp.resolve("frontend/src/models/product.ts")));
        assertFalse(Files.exists(temp.resolve("frontend/src/router/routes/generated.ts")));
    }
}
