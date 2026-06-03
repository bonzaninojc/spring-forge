package io.springforge.generator;

import io.springforge.model.ActionDefinition;
import io.springforge.model.EntityDefinition;
import io.springforge.model.FieldDefinition;
import io.springforge.model.ForgeDefinition;
import io.springforge.util.NamingUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;

/**
 * Gera testes unitários (JUnit 5 + Mockito) para Service e Controller.
 *
 * Para cada entidade gera:
 *   1. ${Entity}ServiceImplTest  — testa CRUD + actions com mocks
 *   2. ${Entity}ControllerTest   — testa endpoints com @WebMvcTest
 */
public class TestGenerator extends AbstractGenerator {

    public TestGenerator(Log log) { super(log); }

    @Override
    public void generate(ForgeDefinition def, EntityDefinition entity, File outDir) throws MojoExecutionException {
        if (!def.getProject().isGenerateTests()) return;

        String name = entity.getName();

        if (entity.shouldGenerate("service")) {
            String testPkg = serviceImplPkg(def);
            writeFile(buildServiceTest(def, entity),
                      testJavaFile(outDir, testPkg, name + "ServiceImplTest"), testPkg);
        }

        if (entity.shouldGenerate("controller")) {
            String testPkg = controllerPkg(def);
            writeFile(buildControllerTest(def, entity),
                      testJavaFile(outDir, testPkg, name + "ControllerTest"), testPkg);
        }
    }

    private File testJavaFile(File outDir, String packageName, String className) {
        File testDir = new File(outDir.getParentFile(), outDir.getName() + "-test");
        String path = packageName.replace('.', '/');
        return new File(testDir, path + "/" + className + ".java");
    }

    // ── Service Test ─────────────────────────────────────────────────────────────

    private CodeWriter buildServiceTest(ForgeDefinition def, EntityDefinition entity) {
        String name    = entity.getName();
        String entPkg  = entityPkg(def);
        String dtoPkg  = dtoPkg(def);
        String repoPkg = repoPkg(def);
        String excPkg  = exceptionPkg(def);
        boolean useMapper = def.getProject().isGenerateMappers();

        CodeWriter w = new CodeWriter();

        w.imp("org.junit.jupiter.api.BeforeEach")
         .imp("org.junit.jupiter.api.Test")
         .imp("org.junit.jupiter.api.extension.ExtendWith")
         .imp("org.mockito.InjectMocks")
         .imp("org.mockito.Mock")
         .imp("org.mockito.junit.jupiter.MockitoExtension")
         .imp("org.springframework.data.domain.Page")
         .imp("org.springframework.data.domain.PageImpl")
         .imp("org.springframework.data.domain.PageRequest")
         .imp("org.springframework.data.domain.Pageable")
         .imp("java.util.List")
         .imp("java.util.Optional")
         .imp(entPkg  + "." + name)
         .imp(repoPkg + "." + name + "Repository")
         .imp(dtoPkg  + "." + name + "RequestDTO")
         .imp(dtoPkg  + "." + name + "ResponseDTO")
         .imp(excPkg  + "." + name + "NotFoundException");

        if (useMapper) w.imp(mapperPkg(def) + "." + name + "Mapper");

        w.imp("static org.mockito.Mockito.*")
         .imp("static org.junit.jupiter.api.Assertions.*");

        w.javadoc("Testes unitários para " + name + "ServiceImpl.\nGerado pelo Spring Forge.");
        w.line("@ExtendWith(MockitoExtension.class)")
         .line("class " + name + "ServiceImplTest {").blank();
        w.indent();

        // Mocks
        w.line("@Mock")
         .line("private " + name + "Repository repository;").blank();
        if (useMapper) {
            w.line("@Mock")
             .line("private " + name + "Mapper mapper;").blank();
        }

        w.line("@InjectMocks")
         .line("private " + name + "ServiceImpl service;").blank();

        // Fixtures
        w.line("private " + name + " entity;")
         .line("private " + name + "RequestDTO requestDTO;")
         .line("private " + name + "ResponseDTO responseDTO;").blank();

        // setUp
        w.line("@BeforeEach")
         .line("void setUp() {")
         .indent()
         .line("entity = new " + name + "();")
         .line("entity.setId(1L);")
         .line("requestDTO = new " + name + "RequestDTO();")
         .line("responseDTO = new " + name + "ResponseDTO();")
         .line("responseDTO.setId(1L);")
         .unindent().line("}").blank();

        // test findAll
        w.line("@Test")
         .line("void findAll_ShouldReturnPage() {")
         .indent()
         .line("Pageable pageable = PageRequest.of(0, 20);")
         .line("Page<" + name + "> page = new PageImpl<>(List.of(entity));");
        if (entity.isSoftDelete()) {
            w.line("when(repository.findAllActive(pageable)).thenReturn(page);");
        } else {
            w.line("when(repository.findAll(pageable)).thenReturn(page);");
        }
        if (useMapper) {
            w.line("when(mapper.toResponseDTO(entity)).thenReturn(responseDTO);");
        }
        w.blank()
         .line("Page<" + name + "ResponseDTO> result = service.findAll(pageable);")
         .blank()
         .line("assertNotNull(result);")
         .line("assertEquals(1, result.getTotalElements());")
         .unindent().line("}").blank();

        // test findById
        w.line("@Test")
         .line("void findById_WhenExists_ShouldReturn() {");
        w.indent();
        if (entity.isSoftDelete()) {
            w.line("when(repository.findByIdActive(1L)).thenReturn(Optional.of(entity));");
        } else {
            w.line("when(repository.findById(1L)).thenReturn(Optional.of(entity));");
        }
        if (useMapper) {
            w.line("when(mapper.toResponseDTO(entity)).thenReturn(responseDTO);");
        }
        w.blank()
         .line(name + "ResponseDTO result = service.findById(1L);")
         .blank()
         .line("assertNotNull(result);")
         .line("assertEquals(1L, result.getId());")
         .unindent().line("}").blank();

        // test findById not found
        w.line("@Test")
         .line("void findById_WhenNotExists_ShouldThrow() {")
         .indent();
        if (entity.isSoftDelete()) {
            w.line("when(repository.findByIdActive(99L)).thenReturn(Optional.empty());");
        } else {
            w.line("when(repository.findById(99L)).thenReturn(Optional.empty());");
        }
        w.blank()
         .line("assertThrows(" + name + "NotFoundException.class, () -> service.findById(99L));")
         .unindent().line("}").blank();

        // test create
        w.line("@Test")
         .line("void create_ShouldSaveAndReturn() {")
         .indent();
        if (useMapper) {
            w.line("when(mapper.toEntity(requestDTO)).thenReturn(entity);");
        }
        w.line("when(repository.save(any())).thenReturn(entity);");
        if (useMapper) {
            w.line("when(mapper.toResponseDTO(entity)).thenReturn(responseDTO);");
        }
        w.blank()
         .line(name + "ResponseDTO result = service.create(requestDTO);")
         .blank()
         .line("assertNotNull(result);")
         .line("verify(repository).save(any());")
         .unindent().line("}").blank();

        // test delete
        w.line("@Test")
         .line("void delete_WhenExists_ShouldDelete() {")
         .indent();
        if (entity.isSoftDelete()) {
            w.line("when(repository.findByIdActive(1L)).thenReturn(Optional.of(entity));");
        } else {
            w.line("when(repository.findById(1L)).thenReturn(Optional.of(entity));");
        }
        w.blank()
         .line("assertDoesNotThrow(() -> service.delete(1L));");
        if (entity.isSoftDelete()) {
            w.line("verify(repository).save(entity);");
        } else {
            w.line("verify(repository).delete(entity);");
        }
        w.unindent().line("}").blank();

        w.unindent().line("}");
        return w;
    }

    // ── Controller Test ──────────────────────────────────────────────────────────

    private CodeWriter buildControllerTest(ForgeDefinition def, EntityDefinition entity) {
        String name    = entity.getName();
        String dtoPkg  = dtoPkg(def);
        String svcPkg  = servicePkg(def);
        String ctrlPkg = controllerPkg(def);

        String apiPath = entity.getApiPath() != null ? entity.getApiPath()
            : "/api/v1/" + NamingUtils.toSnakeCase(NamingUtils.toPlural(name)).replace("_", "-");

        CodeWriter w = new CodeWriter();

        w.imp("org.junit.jupiter.api.Test")
         .imp("org.springframework.beans.factory.annotation.Autowired")
         .imp("org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest")
         .imp("org.springframework.boot.test.mock.mockito.MockBean")
         .imp("org.springframework.data.domain.Page")
         .imp("org.springframework.data.domain.PageImpl")
         .imp("org.springframework.http.MediaType")
         .imp("org.springframework.test.web.servlet.MockMvc")
         .imp("java.util.List")
         .imp(dtoPkg + "." + name + "RequestDTO")
         .imp(dtoPkg + "." + name + "ResponseDTO")
         .imp(svcPkg + "." + name + "Service")
         .imp(ctrlPkg + "." + name + "Controller")
         .imp("static org.mockito.Mockito.*")
         .imp("static org.mockito.ArgumentMatchers.any")
         .imp("static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*")
         .imp("static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*");

        if (def.getProject().isGenerateSecurity()) {
            w.imp("org.springframework.security.test.context.support.WithMockUser")
             .imp("org.springframework.context.annotation.Import");
        }

        w.javadoc("Testes de integração para " + name + "Controller.\nGerado pelo Spring Forge.");
        w.line("@WebMvcTest(" + name + "Controller.class)");
        if (def.getProject().isGenerateSecurity()) {
            w.line("@WithMockUser(roles = \"ADMIN\")");
        }
        w.line("class " + name + "ControllerTest {").blank();
        w.indent();

        w.line("@Autowired")
         .line("private MockMvc mockMvc;").blank();

        w.line("@MockBean")
         .line("private " + name + "Service service;").blank();

        // test GET /
        w.line("@Test")
         .line("void findAll_ShouldReturn200() throws Exception {")
         .indent()
         .line(name + "ResponseDTO dto = new " + name + "ResponseDTO();")
         .line("dto.setId(1L);")
         .line("Page<" + name + "ResponseDTO> page = new PageImpl<>(List.of(dto));")
         .line("when(service.findAll(any())).thenReturn(page);")
         .blank()
         .line("mockMvc.perform(get(\"" + apiPath + "\")")
         .line("        .contentType(MediaType.APPLICATION_JSON))")
         .line("        .andExpect(status().isOk())")
         .line("        .andExpect(jsonPath(\"$.content\").isArray());")
         .unindent().line("}").blank();

        // test GET /{id}
        w.line("@Test")
         .line("void findById_ShouldReturn200() throws Exception {")
         .indent()
         .line(name + "ResponseDTO dto = new " + name + "ResponseDTO();")
         .line("dto.setId(1L);")
         .line("when(service.findById(1L)).thenReturn(dto);")
         .blank()
         .line("mockMvc.perform(get(\"" + apiPath + "/1\")")
         .line("        .contentType(MediaType.APPLICATION_JSON))")
         .line("        .andExpect(status().isOk())")
         .line("        .andExpect(jsonPath(\"$.id\").value(1));")
         .unindent().line("}").blank();

        // test POST /
        w.line("@Test")
         .line("void create_ShouldReturn201() throws Exception {")
         .indent()
         .line(name + "ResponseDTO dto = new " + name + "ResponseDTO();")
         .line("dto.setId(1L);")
         .line("when(service.create(any())).thenReturn(dto);")
         .blank()
         .line("mockMvc.perform(post(\"" + apiPath + "\")")
         .line("        .contentType(MediaType.APPLICATION_JSON)")
         .line("        .content(\"{}\"))")
         .line("        .andExpect(status().isCreated())")
         .line("        .andExpect(jsonPath(\"$.id\").value(1));")
         .unindent().line("}").blank();

        // test DELETE /{id}
        w.line("@Test")
         .line("void delete_ShouldReturn204() throws Exception {")
         .indent()
         .line("doNothing().when(service).delete(1L);")
         .blank()
         .line("mockMvc.perform(delete(\"" + apiPath + "/1\")")
         .line("        .contentType(MediaType.APPLICATION_JSON))")
         .line("        .andExpect(status().isNoContent());")
         .unindent().line("}").blank();

        w.unindent().line("}");
        return w;
    }
}
