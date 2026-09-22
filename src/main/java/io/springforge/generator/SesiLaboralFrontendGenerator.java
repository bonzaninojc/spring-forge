package io.springforge.generator;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.springforge.model.EntityDefinition;
import io.springforge.model.FieldDefinition;
import io.springforge.model.FilterDefinition;
import io.springforge.model.ForgeDefinition;
import io.springforge.model.RelationDefinition;
import io.springforge.util.NamingUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Generates feature files that extend an existing Sesi Laboral Vue application. */
public class SesiLaboralFrontendGenerator extends AbstractGenerator {
    public SesiLaboralFrontendGenerator(Log log) { super(log); }

    private File sourceDir(ForgeDefinition def, File out) {
        return new File(out.getParentFile().getParentFile(), def.getProject().getFrontendDir());
    }

    private String key(EntityDefinition entity) { return NamingUtils.toCamelCase(entity.getName()); }
    private String plural(EntityDefinition entity) { return NamingUtils.toPlural(entity.getName()); }

    private String apiPath(EntityDefinition entity) {
        String path = entity.getApiPath() != null ? entity.getApiPath()
            : "/api/v1/" + NamingUtils.toSnakeCase(NamingUtils.toPlural(entity.getName())).replace("_", "-");
        return path.startsWith("/") ? path.substring(1) : path;
    }

    private String quote(String value) {
        try { return new ObjectMapper().writeValueAsString(value).replace("<", "\\u003c"); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException(e); }
    }

    private String type(FieldDefinition field) {
        return switch (field.getType().toLowerCase()) {
            case "boolean" -> "boolean";
            case "long", "integer", "int", "double", "float", "bigdecimal", "short" -> "number";
            default -> "string";
        };
    }

    @Override
    public void generate(ForgeDefinition def, EntityDefinition entity, File out) throws MojoExecutionException {
        if (!def.getProject().isGenerateFrontend() || !entity.shouldGenerate("frontend")) return;
        File src = sourceDir(def, out);
        String plural = plural(entity);
        String key = key(entity);

        write(src, "models/" + key + ".ts", model(entity));
        write(src, "services/" + key + "Service.ts", service(entity));
        write(src, "components/DashboardComponents/" + plural + "Table/" + plural + "Table.vue", table(entity));
        write(src, "views/pages/Dashboard" + plural + "/Listar" + plural + ".vue", listPage(entity));
        write(src, "views/pages/Dashboard" + plural + "/Cadastrar" + plural + ".vue", formPage(def, entity, false));
        write(src, "views/pages/Dashboard" + plural + "/Editar" + plural + ".vue", formPage(def, entity, true));
    }

    /** Application infrastructure is owned by the target Sesi Laboral application. */
    public void generateGlobalFiles(ForgeDefinition def, File out) throws MojoExecutionException {
        if (!def.getProject().isGenerateFrontend()) return;
        File sourceDir = sourceDir(def, out);
        write(sourceDir, "router/routes/generated.ts", routes(def));
        write(sourceDir, "navigation/vertical/generated.ts", navigation(def));
        integrateRoutes(sourceDir);
        integrateNavigation(sourceDir);
    }
    public void generateProject(ForgeDefinition def, File out) { }

    private String model(EntityDefinition entity) {
        String name = entity.getName();
        StringBuilder output = new StringBuilder("export interface " + name + " {\n  id: number;\n");
        for (FieldDefinition field : entity.getFields()) if (field.isInResponse()) {
            output.append("  ").append(quote(field.getName())).append("?: ").append(type(field)).append(" | null;\n");
        }
        for (var relation : entity.getRelations()) if ("ManyToOne".equals(relation.getType()) && relation.isInResponse()) {
            output.append("  ").append(quote(relation.getFieldName() + "Id")).append("?: number | null;\n");
        }
        output.append("}\n\nexport interface ").append(name).append("Request {\n");
        for (FieldDefinition field : entity.getFields()) if (field.isInRequest()) {
            output.append("  ").append(quote(field.getName())).append("?: ").append(type(field)).append(" | null;\n");
        }
        for (var relation : entity.getRelations()) if ("ManyToOne".equals(relation.getType()) && relation.isInRequest()) {
            output.append("  ").append(quote(relation.getFieldName() + "Id")).append("?: number | null;\n");
        }
        return output.append("}\n").toString();
    }

    private String service(EntityDefinition entity) {
        String name = entity.getName();
        String plural = plural(entity);
        String key = key(entity);
        String filterFields = filterTypeFields(entity);
        String listCall = entity.hasFilters()
            ? """
                const payload = Object.fromEntries(
                  Object.entries(params)
                    .map(([key, value]) => [key, normalizeFilterValue(key, value)])
                    .filter(([, value]) => value !== undefined),
                );
                return (await BaseService().post(`${path}/search`, payload)).data;
                """.stripTrailing()
            : """
                const query = new URLSearchParams({
                  page: String(params.page),
                  size: String(params.size),
                  sort: `${params.sortBy ?? 'id'},${params.ascending === false ? 'desc' : 'asc'}`,
                });
                return (await BaseService().get(`${path}?${query}`)).data;
                """.stripTrailing();
        String filterNormalizer = entity.hasFilters() ? """
            function normalizeFilterValue(key: string, value: unknown): unknown {
              if (value === '' || value === null || value === undefined) return undefined;
              if (Array.isArray(value)) {
                const values = value.filter((item) => item !== '' && item !== null && item !== undefined);
                return values.length === value.length && values.length > 0 ? values : undefined;
              }
              if (/cpf|cep/i.test(key)) return String(value).replace(/\\D/g, '');
              if (/cnpj/i.test(key)) return String(value).replace(/[^a-zA-Z0-9]/g, '');
              return value;
            }
            """.stripTrailing() : "";
        return """
            import { BaseService } from './baseService';
            import type { %s, %sRequest } from '@/models/%s';

            export interface %sPageRequest {
              page: number;
              size: number;
              sortBy?: string;
              ascending?: boolean;
            %s
            }

            export interface %sPageResponse {
              content: %s[];
              totalElements: number;
              totalPages: number;
            }

            const path = %s;

            %s

            export async function load%s(params: %sPageRequest): Promise<%sPageResponse> {
            %s
            }

            export async function find%s(id: number): Promise<%s> {
              return (await BaseService().get(`${path}/${id}`)).data;
            }

            export function register%s(payload: %sRequest) {
              return BaseService().post(path, payload);
            }

            export function update%s(id: number, payload: %sRequest) {
              return BaseService().put(`${path}/${id}`, payload);
            }

            export function remove%s(id: number) {
              return BaseService().del(`${path}/${id}`);
            }
            """.formatted(name, name, key, name, filterFields, name, name, quote(apiPath(entity)), filterNormalizer,
                plural, name, name, listCall.indent(2).stripTrailing(),
                name, name, plural, name, plural, name, plural);
    }

    private String table(EntityDefinition entity) {
        String name = entity.getName();
        String plural = plural(entity);
        String key = key(entity);
        String props = entity.hasFilters()
            ? "const props = defineProps<{ filters: Record<string, unknown> }>();\n"
            : "";
        String filterPayload = entity.hasFilters() ? ",\n      ...props.filters" : "";
        return """
            <template>
              <div v-if="isLoading" class="custom-table-card loading-overlay">
                <div class="spinner" />
                <span class="loading-text">Carregando...</span>
              </div>

              <div v-else class="custom-table-card">
                <div class="table-controls">
                  <label>
                    Mostrar
                    <select v-model.number="rowsPerPage" @change="applyFilters">
                      <option
                        v-for="size in [10, 25, 50, 100]"
                        :key="size"
                        :value="size"
                      >
                        {{ size }}
                      </option>
                    </select>
                    resultados
                  </label>
                </div>

                <table class="custom-table">
                  <thead>
                    <tr>
            %s
                      <th class="central">Ações</th>
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="item in items" :key="item.id">
            %s
                      <td class="central">
                        <button class="icon-btn" @click="edit(item.id)">
                          <i class="fa-regular fa-pen-to-square" />
                        </button>
                        <button class="icon-btn" @click="requestRemoval(item.id)">
                          <i class="fa-regular fa-trash-can" />
                        </button>
                      </td>
                    </tr>
                    <tr v-if="items.length === 0">
                      <td :colspan="%d" class="empty">
                        Nenhum resultado encontrado
                      </td>
                    </tr>
                  </tbody>
                </table>

                <div class="table-footer">
                  <div>
                    Mostrando {{ startItem }} a {{ endItem }} de {{ totalResults }} resultados
                  </div>
                  <div class="pagination">
                    <button :disabled="currentPage === 1" @click="goToFirst">&lt;&lt;</button>
                    <button :disabled="currentPage === 1" @click="prevPage">&lt;</button>
                    <button
                      v-for="page in visiblePages"
                      :key="page"
                      :class="{ active: currentPage === page }"
                      @click="changePage(page)"
                    >
                      {{ page }}
                    </button>
                    <button :disabled="totalPages <= 1 || currentPage >= totalPages" @click="nextPage">&gt;</button>
                    <button :disabled="totalPages <= 1 || currentPage >= totalPages" @click="goToLast">&gt;&gt;</button>
                  </div>
                </div>
              </div>

              <div
                v-if="confirmRemoval"
                class="confirm-overlay"
                role="dialog"
                aria-modal="true"
                aria-labelledby="confirm-removal-title"
                @click.self="confirmRemoval = false"
              >
                <div class="confirm-dialog">
                  <h2 id="confirm-removal-title">Excluir registro</h2>
                  <p>Tem certeza que deseja excluir este registro?</p>
                  <div class="confirm-actions">
                    <button class="confirm-cancel" @click="confirmRemoval = false">
                      Cancelar
                    </button>
                    <button class="confirm-delete" @click="remove">
                      Excluir
                    </button>
                  </div>
                </div>
              </div>
              <FeedbackModal
                v-model="alert.show"
                :title="alert.title"
                :message="alert.message"
                :severity="alert.severity"
              />
            </template>

            <script setup lang="ts">
            import { onMounted, ref } from 'vue';
            import { useRouter } from 'vue-router';
            import { usePagination } from '@/composables/usePagination';
            import FeedbackModal from '@/components/DashboardComponents/FeedbackModal/FeedbackModal.vue';
            import { load%s, remove%s } from '@/services/%sService';
            import type { %s } from '@/models/%s';

            %s
            const router = useRouter();
            const items = ref<%s[]>([]);
            const isLoading = ref(false);
            const confirmRemoval = ref(false);
            const selectedId = ref<number | null>(null);
            const alert = ref({ show: false, title: '', message: '', severity: 'success' });

            const showAlert = (title: string, message: string, severity: string) => {
              alert.value = { show: true, title, message, severity };
            };

            function formatValue(value: unknown, fieldType: string): string {
              if (value === null || value === undefined || value === '') return '-';
              if (fieldType === 'Boolean') return value ? 'Sim' : 'Não';
              if (fieldType === 'LocalDate') {
                const [year, month, day] = String(value).split('-');
                return year && month && day ? `${day}/${month}/${year}` : String(value);
              }
              if (fieldType === 'LocalDateTime') {
                const date = new Date(String(value));
                return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString('pt-BR');
              }
              return String(value);
            }

            const {
              currentPage,
              rowsPerPage,
              totalResults,
              totalPages,
              startItem,
              endItem,
              visiblePages,
              changePage,
              prevPage,
              nextPage,
              goToFirst,
              goToLast,
            } = usePagination(fetchItems);

            async function fetchItems() {
              isLoading.value = true;
              try {
                const page = await load%s({
                  page: currentPage.value - 1,
                  size: rowsPerPage.value,
                  sortBy: %s,
                  ascending: %s%s,
                });
                items.value = page.content || [];
                totalResults.value = page.totalElements;
                totalPages.value = page.totalPages;
              } catch (error) {
                console.error(error);
                showAlert('Erro', 'Não foi possível carregar os registros.', 'error');
              } finally {
                isLoading.value = false;
              }
            }

            async function applyFilters() {
              currentPage.value = 1;
              await fetchItems();
            }

            function edit(id: number) {
              router.push({ name: 'editar-%s', params: { id } });
            }

            function requestRemoval(id: number) {
              selectedId.value = id;
              confirmRemoval.value = true;
            }

            async function remove() {
              if (selectedId.value === null) return;

              const id = selectedId.value;
              confirmRemoval.value = false;
              selectedId.value = null;

              try {
                await remove%s(id);
                showAlert('Sucesso', 'Registro excluído com sucesso.', 'success');
                await applyFilters();
              } catch (error) {
                console.error(error);
                showAlert('Erro', 'Não foi possível excluir o registro.', 'error');
              }
            }

            onMounted(fetchItems);
            defineExpose({ applyFilters });
            </script>

            %s
            """.formatted(headers(entity), cells(entity), responseCount(entity) + 2, plural, plural, key, name, key,
                props, name, plural, quote(defaultSort(entity)), defaultAscending(entity), filterPayload, key, plural, tableStyles());
    }

    private String defaultSort(EntityDefinition entity) {
        return entity.getCrud() != null && entity.getCrud().getDefaultSort() != null
            && !entity.getCrud().getDefaultSort().isBlank() ? entity.getCrud().getDefaultSort() : "id";
    }

    private boolean defaultAscending(EntityDefinition entity) {
        return entity.getCrud() == null || !"DESC".equalsIgnoreCase(entity.getCrud().getDefaultDirection());
    }

    private String listPage(EntityDefinition entity) {
        String plural = plural(entity);
        String key = key(entity);
        String filtersSection = entity.hasFilters()
            ? """
                <section class="filters-section">
                  <h3>Filtros</h3>
                  <div class="form-row">
                %s
                  </div>
                </section>
                """.formatted(filterMarkup(entity).indent(-2).stripTrailing())
                .indent(8)
                .stripTrailing()
            : "";
        String tableBinding = entity.hasFilters() ? " :filters=\"filters\"" : "";
        String imports = entity.hasFilters()
            ? "import { nextTick, reactive, ref, watch } from 'vue';\n" + filterComponentImports(entity)
            : "import { ref } from 'vue';";
        String filterState = entity.hasFilters()
            ? """
                const filters = reactive<Record<string, any>>(%s);
                const booleanOptions = [
                  { title: 'Sim', value: true },
                  { title: 'Não', value: false },
                ];
                const nullFilterOptions = [{ title: 'Aplicar filtro', value: true }];
                let debounceTimer: ReturnType<typeof setTimeout> | null = null;

                watch(
                  filters,
                  () => {
                    if (debounceTimer) clearTimeout(debounceTimer);
                    debounceTimer = setTimeout(async () => {
                      await nextTick();
                      table.value?.applyFilters();
                    }, 600);
                  },
                  { deep: true },
                );
                """.formatted(filterInitialState(entity)).stripTrailing()
            : "";
        return """
            <template>
              <DashboardLayout>
                <LoadingOverlay v-if="isLoading" />

                <div class="page-wrapper w-full">
                  <div class="padding-page">
                    <div class="page-header">
                      <div>
                        <h1 class="header-title">%s</h1>
                        <p class="header-subtitle">Gerencie os registros cadastrados</p>
                      </div>
                      <button
                        class="custom-import-button"
                        @click="router.push({ name: 'cadastrar-%s' })"
                      >
                        <v-icon icon="fa-solid fa-plus" />
                        Cadastrar
                      </button>
                    </div>

            %s

                    <%sTable ref="table"%s />
                  </div>
                </div>

                <FeedbackModal
                  v-model="alert.show"
                  :title="alert.title"
                  :message="alert.message"
                  :severity="alert.severity"
                />
              </DashboardLayout>
            </template>

            <script setup lang="ts">
            %s
            import { useRouter } from 'vue-router';
            import DashboardLayout from '@/views/pages/dashboardLayout/DashboardLayout.vue';
            import LoadingOverlay from '@/components/DashboardComponents/LoadingOverlay/LoadingOverlay.vue';
            import FeedbackModal from '@/components/DashboardComponents/FeedbackModal/FeedbackModal.vue';
            import %sTable from '@/components/DashboardComponents/%sTable/%sTable.vue';

            const router = useRouter();
            const isLoading = ref(false);
            const table = ref();
            const alert = ref({ show: false, title: '', message: '', severity: 'success' });

            %s
            </script>

            %s
            """.formatted(plural, key, filtersSection, plural, tableBinding, imports, plural, plural, plural, filterState,
                listPageStyles());
    }

    private String formPage(ForgeDefinition def, EntityDefinition entity, boolean edit) {
        String name = entity.getName();
        String plural = plural(entity);
        String key = key(entity);
        String mode = edit ? "Editar" : "Cadastrar";
        List<RelationDefinition> relations = requestRelations(entity, true);
        boolean mounted = edit || !relations.isEmpty();
        String vueImports = mounted ? "onMounted, reactive, ref" : "reactive, ref";
        String routerImports = edit ? "useRoute, useRouter" : "useRouter";
        String routeDeclaration = edit ? "const route = useRoute();\n" : "";
        List<String> serviceFunctions = new ArrayList<>();
        if (edit) {
            serviceFunctions.add("find" + name);
            serviceFunctions.add("update" + plural);
        } else {
            serviceFunctions.add("register" + plural);
        }
        for (RelationDefinition relation : relations) {
            EntityDefinition target = findEntity(def, relation.getTargetEntity());
            if (key(target).equals(key) && !serviceFunctions.contains("load" + plural(target))) {
                serviceFunctions.add("load" + plural(target));
            }
        }
        String serviceImport = String.join(", ", serviceFunctions);
        String save = edit
            ? "await update" + plural + "(Number(route.params.id), payload);"
            : "await register" + plural + "(payload);";
        String mountedHook = mountedHook(def, entity, edit, relations);
        return """
            <template>
              <DashboardLayout>
                <LoadingOverlay v-if="isLoading" />

                <div class="page-wrapper w-full">
                  <div class="padding-page">
                    <div class="page-header">
                      <div>
                        <h1 class="header-title">%s %s</h1>
                        <p class="header-subtitle">Preencha os dados do registro</p>
                      </div>
                    </div>

                    <div class="form-spacing">
                      <v-row>
            %s
                      </v-row>

                      <v-row>
                        <span><strong>*</strong> Campos obrigatórios</span>
                      </v-row>

                      <v-row>
                        <div class="separator" />
                      </v-row>

                      <v-row class="button-row-actions">
                        <button
                          class="custom-cancel-button-outlined"
                          @click="router.push({ name: 'listar-%s' })"
                        >
                          <v-icon icon="fa-solid fa-xmark" />
                          Cancelar
                        </button>
                        <button class="custom-import-button" @click="submit">
                          <v-icon icon="fa-solid fa-check" />
                          Salvar
                        </button>
                      </v-row>
                    </div>
                  </div>
                </div>

                <FeedbackModal
                  v-model="alert.show"
                  :title="alert.title"
                  :message="alert.message"
                  :severity="alert.severity"
                  @update:model-value="handleAlertClose"
                />
              </DashboardLayout>
            </template>

            <script setup lang="ts">
            import { %s } from 'vue';
            import { %s } from 'vue-router';
            import DashboardLayout from '@/views/pages/dashboardLayout/DashboardLayout.vue';
            %s
            import LoadingOverlay from '@/components/DashboardComponents/LoadingOverlay/LoadingOverlay.vue';
            import FeedbackModal from '@/components/DashboardComponents/FeedbackModal/FeedbackModal.vue';
            import { %s } from '@/services/%sService';
            %s
            import type { %sRequest } from '@/models/%s';

            %sconst router = useRouter();
            const isLoading = ref(false);
            const form = reactive<%sRequest>(%s);
            %s
            const alert = ref({ show: false, title: '', message: '', severity: 'success' });
            const redirectAfterAlert = ref(false);

            const showAlert = (title: string, message: string, severity: string) => {
              alert.value = { show: true, title, message, severity };
            };

            function handleAlertClose(show: boolean) {
              if (!show && redirectAfterAlert.value) {
                redirectAfterAlert.value = false;
                router.push({ name: 'listar-%s' });
              }
            }

            %s

            %s

            async function submit() {
              const validationMessage = validateForm();
              if (validationMessage) {
                showAlert('Verifique os campos', validationMessage, 'error');
                return;
              }

              isLoading.value = true;
              try {
                const payload = buildPayload();
                %s
                redirectAfterAlert.value = true;
                showAlert('Sucesso', 'Registro salvo com sucesso.', 'success');
              } catch (error) {
                console.error(error);
                const message = (error as any)?.response?.data?.detail
                  ?? 'Não foi possível salvar o registro.';
                showAlert('Erro', message, 'error');
              } finally {
                isLoading.value = false;
              }
            }

            %s
            </script>

            %s
            """.formatted(mode, name, formFields(entity), key, vueImports, routerImports, formComponentImports(entity),
                serviceImport, key, relationServiceImports(def, entity, relations), name, key, routeDeclaration,
                name, formInitialState(entity, relations), relationState(relations), key, dateHelpers(entity, edit),
                validationFunction(entity, relations), save, mountedHook, formPageStyles());
    }

    private String headers(EntityDefinition entity) {
        StringBuilder output = new StringBuilder("          <th>ID</th>\n");
        for (FieldDefinition field : entity.getFields()) {
            if (field.isInResponse()) {
                output.append("          <th>")
                    .append(fieldLabel(field))
                    .append("</th>\n");
            }
        }
        for (RelationDefinition relation : requestRelations(entity, false)) {
            output.append("          <th>").append(label(relation.getFieldName())).append("</th>\n");
        }
        return output.toString().stripTrailing();
    }

    private String cells(EntityDefinition entity) {
        StringBuilder output = new StringBuilder("          <td>{{ item.id }}</td>\n");
        for (FieldDefinition field : entity.getFields()) {
            if (field.isInResponse()) {
                output.append("          <td>{{ formatValue(item[")
                    .append(quote(field.getName()))
                    .append("], ").append(quote(field.getType())).append(") }}</td>\n");
            }
        }
        for (RelationDefinition relation : requestRelations(entity, false)) {
            output.append("          <td>{{ item[")
                .append(quote(relation.getFieldName() + "Id"))
                .append("] ?? '-' }}</td>\n");
        }
        return output.toString().stripTrailing();
    }
    private int responseCount(EntityDefinition entity) {
        return (int) entity.getFields().stream().filter(FieldDefinition::isInResponse).count()
            + requestRelations(entity, false).size();
    }
    private String filterTypeFields(EntityDefinition entity) {
        StringBuilder output = new StringBuilder();
        for (FilterDefinition filter : entity.getEffectiveFilters()) {
            String type = filterBaseType(filter);
            String operator = filter.resolveOperator();
            if ("IN".equals(operator) || "BETWEEN".equals(operator)) type += "[]";
            if ("IS_NULL".equals(operator) || "IS_NOT_NULL".equals(operator)) type = "boolean";
            output.append("  ").append(filter.getName()).append("?: ").append(type).append(";\n");
        }
        return output.toString().stripTrailing();
    }

    private String filterBaseType(FilterDefinition filter) {
        return switch (filter.getType().toLowerCase()) {
            case "boolean" -> "boolean";
            case "long", "integer", "int", "double", "float", "bigdecimal", "short" -> "number";
            default -> "string";
        };
    }

    private String filterInitialState(EntityDefinition entity) {
        StringBuilder output = new StringBuilder("{\n");
        for (FilterDefinition filter : entity.getEffectiveFilters()) {
            String operator = filter.resolveOperator();
            String value = "IN".equals(operator) ? "[]" : "BETWEEN".equals(operator) ? "[null, null]"
                : "IS_NULL".equals(operator) || "IS_NOT_NULL".equals(operator) || "boolean".equals(filterBaseType(filter)) ? "null" : "''";
            output.append("  ").append(filter.getName()).append(": ").append(value).append(",\n");
        }
        return output.append("}").toString();
    }

    private String filterMarkup(EntityDefinition entity) {
        StringBuilder output = new StringBuilder();
        for (FilterDefinition filter : entity.getEffectiveFilters()) {
            String name = filter.getName();
            String title = escapeHtml(filter.getLabel());
            String operator = filter.resolveOperator();
            output.append("      <div class=\"form-group\">\n")
                .append("        <label for=\"filter-").append(name).append("\">")
                .append(title).append("</label>\n");
            if ("IS_NULL".equals(operator) || "IS_NOT_NULL".equals(operator)) {
                output.append("        <v-select\n")
                    .append("          id=\"filter-").append(name).append("\"\n")
                    .append("          v-model=\"filters.").append(name).append("\"\n")
                    .append("          :items=\"nullFilterOptions\"\n")
                    .append("          clearable\n          hide-details\n")
                    .append("        />\n");
            } else if ("boolean".equals(filterBaseType(filter))) {
                output.append("        <v-select\n")
                    .append("          id=\"filter-").append(name).append("\"\n")
                    .append("          v-model=\"filters.").append(name).append("\"\n")
                    .append("          :items=\"booleanOptions\"\n")
                    .append("          clearable\n          hide-details\n")
                    .append("        />\n");
            } else if ("BETWEEN".equals(operator)) {
                output.append("        <div class=\"filter-range\">\n")
                    .append("          <CustomInput\n")
                    .append("            v-model").append("number".equals(filterBaseType(filter)) ? ".number" : "").append("=\"filters.").append(name).append("[0]\"\n")
                    .append("            placeholder=\"De\"\n")
                    .append(filterInputType(filter))
                    .append("            clearable\n")
                    .append("          />\n")
                    .append("          <CustomInput\n")
                    .append("            v-model").append("number".equals(filterBaseType(filter)) ? ".number" : "").append("=\"filters.").append(name).append("[1]\"\n")
                    .append("            placeholder=\"Até\"\n")
                    .append(filterInputType(filter))
                    .append("            clearable\n")
                    .append("          />\n")
                    .append("        </div>\n");
            } else if ("IN".equals(operator)) {
                output.append("        <v-combobox\n")
                    .append("          id=\"filter-").append(name).append("\"\n")
                    .append("          v-model=\"filters.").append(name).append("\"\n")
                    .append("          :items='").append(jsonArray(filter)).append("'\n")
                    .append("          multiple\n          chips\n          clearable\n          hide-details\n")
                    .append("        />\n");
            } else if ("Enum".equalsIgnoreCase(filter.getType())) {
                output.append("        <v-select\n")
                    .append("          id=\"filter-").append(name).append("\"\n")
                    .append("          v-model=\"filters.").append(name).append("\"\n")
                    .append("          :items='").append(jsonArray(filter)).append("'\n")
                    .append("          clearable\n          hide-details\n")
                    .append("        />\n");
            } else if (mask(name) != null) {
                output.append("        <CustomNumberInput\n")
                    .append("          id=\"filter-").append(name).append("\"\n")
                    .append("          v-model=\"filters.").append(name).append("\"\n")
                    .append("          mask=\"").append(mask(name)).append("\"\n")
                    .append("          placeholder=\"\"\n")
                    .append("          :show-error=\"false\"\n")
                    .append("          clearable\n")
                    .append("        />\n");
            } else {
                output.append("        <CustomInput\n")
                    .append("          id=\"filter-").append(name).append("\"\n")
                    .append("          v-model").append("number".equals(filterBaseType(filter)) ? ".number" : "").append("=\"filters.").append(name).append("\"\n")
                    .append("          placeholder=\"\"\n");
                if ("number".equals(filterBaseType(filter))) output.append("          type=\"number\"\n");
                if ("LocalDate".equalsIgnoreCase(filter.getType())) output.append("          type=\"date\"\n");
                if ("LocalDateTime".equalsIgnoreCase(filter.getType())) output.append("          type=\"datetime-local\"\n");
                output.append("          clearable\n")
                    .append("        />\n");
            }
            output.append("      </div>\n");
        }
        return output.toString().stripIndent().stripTrailing();
    }

    private String jsonArray(FilterDefinition filter) {
        StringBuilder output = new StringBuilder("[");
        for (int index = 0; index < filter.getEnumValues().size(); index++) {
            if (index > 0) output.append(", ");
            output.append(quote(filter.getEnumValues().get(index)));
        }
        return output.append(']').toString();
    }

    private String filterInputType(FilterDefinition filter) {
        if ("number".equals(filterBaseType(filter))) return "            type=\"number\"\n";
        if ("LocalDate".equalsIgnoreCase(filter.getType())) return "            type=\"date\"\n";
        if ("LocalDateTime".equalsIgnoreCase(filter.getType())) return "            type=\"datetime-local\"\n";
        return "";
    }

    private String filterComponentImports(EntityDefinition entity) {
        boolean customInput = entity.getEffectiveFilters().stream().anyMatch(filter -> {
            String operator = filter.resolveOperator();
            return "BETWEEN".equals(operator) || (!"IN".equals(operator) && !"IS_NULL".equals(operator)
                && !"IS_NOT_NULL".equals(operator) && !"Enum".equalsIgnoreCase(filter.getType())
                && !"boolean".equals(filterBaseType(filter)) && mask(filter.getName()) == null);
        });
        boolean numberInput = entity.getEffectiveFilters().stream().anyMatch(filter -> mask(filter.getName()) != null);
        StringBuilder imports = new StringBuilder();
        if (customInput) imports.append("import CustomInput from '@/components/DashboardComponents/CustomInput/CustomInput.vue';\n");
        if (numberInput) imports.append("import CustomNumberInput from '@/components/DashboardComponents/CustomNumberInput/CustomNumberInput.vue';\n");
        return imports.toString().stripTrailing();
    }

    private String mask(String name) {
        String lower = name.toLowerCase();
        if (lower.contains("cpf")) return "cpf";
        if (lower.contains("cnpj")) return "cnpj";
        if (lower.contains("cep")) return "cep";
        return null;
    }

    private String escapeHtml(String value) {
        return value == null ? "" : value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String fieldLabel(FieldDefinition field) {
        return escapeHtml(field.getLabel() == null || field.getLabel().isBlank() ? label(field.getName()) : field.getLabel());
    }

    private List<RelationDefinition> requestRelations(EntityDefinition entity, boolean request) {
        return entity.getRelations().stream()
            .filter(relation -> "ManyToOne".equals(relation.getType()))
            .filter(relation -> request ? relation.isInRequest() : relation.isInResponse())
            .toList();
    }

    private String formFields(EntityDefinition entity) {
        StringBuilder output = new StringBuilder();
        for (FieldDefinition field : entity.getFields()) {
            if (!field.isInRequest()) continue;

            output.append("          <v-col cols=\"12\" md=\"4\">\n")
                .append("            <label for=\"").append(field.getName()).append("\">\n")
                .append("              ").append(fieldLabel(field));
            if (field.isRequired()) output.append(" <strong>*</strong>");
            output.append("\n            </label>\n");
            appendFieldControl(output, field);
            if (field.getHelperText() != null && !field.getHelperText().isBlank()) {
                output.append("            <small class=\"field-helper\">")
                    .append(escapeHtml(field.getHelperText())).append("</small>\n");
            }
            output.append("          </v-col>\n");
        }
        for (RelationDefinition relation : requestRelations(entity, true)) {
            String fieldName = relation.getFieldName() + "Id";
            output.append("          <v-col cols=\"12\" md=\"4\">\n")
                .append("            <label for=\"").append(fieldName).append("\">\n")
                .append("              ").append(label(relation.getFieldName()));
            if (relation.isRequired()) output.append(" <strong>*</strong>");
            output.append("\n            </label>\n")
                .append("            <v-select\n")
                .append("              id=\"").append(fieldName).append("\"\n")
                .append("              v-model=\"form.").append(fieldName).append("\"\n")
                .append("              :items=\"").append(relation.getFieldName()).append("Options\"\n")
                .append("              placeholder=\"Selecione\"\n")
                .append("              clearable\n")
                .append("              hide-details\n")
                .append("            />\n")
                .append("          </v-col>\n");
        }
        return output.toString().indent(2).stripTrailing();
    }

    private void appendFieldControl(StringBuilder output, FieldDefinition field) {
        String name = field.getName();
        String placeholder = field.getPlaceholder() == null ? "" : field.getPlaceholder();
        String tooltip = field.getHelperText() != null && !field.getHelperText().isBlank()
            ? "              tooltip=" + quote(field.getHelperText()) + "\n" : "";
        String disabled = field.isReadOnly() ? "              disabled\n" : "";
        if ("Enum".equalsIgnoreCase(field.getType())) {
            output.append("            <v-select\n")
                .append("              id=\"").append(name).append("\"\n")
                .append("              v-model=\"form.").append(name).append("\"\n")
                .append("              :items='").append(jsonArray(field.getEnumValues())).append("'\n")
                .append("              placeholder=").append(quote(placeholder)).append("\n")
                .append(disabled).append("              clearable\n              hide-details\n            />\n");
        } else if ("Boolean".equalsIgnoreCase(field.getType())) {
            output.append("            <v-switch\n")
                .append("              id=\"").append(name).append("\"\n")
                .append("              v-model=\"form.").append(name).append("\"\n")
                .append("              color=\"primary\"\n")
                .append(disabled).append("              hide-details\n            />\n");
        } else if ("LocalDate".equalsIgnoreCase(field.getType())) {
            output.append("            <CustomDate\n")
                .append("              id=\"").append(name).append("\"\n")
                .append("              v-model=\"form.").append(name).append("\"\n")
                .append("              placeholder=").append(quote(placeholder)).append("\n")
                .append(tooltip).append(disabled).append("              clearable\n            />\n");
        } else if (mask(name) != null) {
            output.append("            <CustomNumberInput\n")
                .append("              id=\"").append(name).append("\"\n")
                .append("              v-model=\"form.").append(name).append("\"\n")
                .append("              mask=\"").append(mask(name)).append("\"\n")
                .append("              placeholder=").append(quote(placeholder)).append("\n")
                .append(tooltip).append(disabled).append("              clearable\n            />\n");
        } else {
            output.append("            <CustomInput\n")
                .append("              id=\"").append(name).append("\"\n")
                .append("              v-model").append("number".equals(type(field)) ? ".number" : "")
                .append("=\"form.").append(name).append("\"\n")
                .append("              placeholder=").append(quote(placeholder)).append("\n");
            if ("number".equals(type(field))) output.append("              type=\"number\"\n");
            if ("LocalDateTime".equalsIgnoreCase(field.getType())) output.append("              type=\"datetime-local\"\n");
            output.append(tooltip).append(disabled).append("              clearable\n            />\n");
        }
    }

    private String jsonArray(List<String> values) {
        StringBuilder output = new StringBuilder("[");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) output.append(", ");
            output.append(quote(values.get(index)));
        }
        return output.append(']').toString();
    }

    private String formComponentImports(EntityDefinition entity) {
        boolean customInput = entity.getFields().stream().anyMatch(field -> field.isInRequest()
            && !"Enum".equalsIgnoreCase(field.getType()) && !"Boolean".equalsIgnoreCase(field.getType())
            && !"LocalDate".equalsIgnoreCase(field.getType()) && mask(field.getName()) == null);
        boolean numberInput = entity.getFields().stream().anyMatch(field -> field.isInRequest() && mask(field.getName()) != null);
        boolean dateInput = entity.getFields().stream().anyMatch(field -> field.isInRequest() && "LocalDate".equalsIgnoreCase(field.getType()));
        StringBuilder imports = new StringBuilder();
        if (customInput) imports.append("import CustomInput from '@/components/DashboardComponents/CustomInput/CustomInput.vue';\n");
        if (numberInput) imports.append("import CustomNumberInput from '@/components/DashboardComponents/CustomNumberInput/CustomNumberInput.vue';\n");
        if (dateInput) imports.append("import CustomDate from '@/components/DashboardComponents/CustomData/CustomDate.vue';\n");
        return imports.toString().stripTrailing();
    }

    private String formInitialState(EntityDefinition entity, List<RelationDefinition> relations) {
        StringBuilder state = new StringBuilder("{\n");
        for (FieldDefinition field : entity.getFields()) {
            if (!field.isInRequest()) continue;
            state.append("  ").append(field.getName()).append(": ").append(initialValue(field)).append(",\n");
        }
        for (RelationDefinition relation : relations) {
            state.append("  ").append(relation.getFieldName()).append("Id: null,\n");
        }
        return state.append("}").toString();
    }

    private String initialValue(FieldDefinition field) {
        String value = field.getDefaultValue();
        if (value != null && !value.isBlank()) {
            if ("boolean".equals(type(field)) && ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value))) {
                return value.toLowerCase();
            }
            if ("number".equals(type(field)) && value.matches("-?\\d+(\\.\\d+)?")) return value;
            return quote(value);
        }
        if ("Boolean".equalsIgnoreCase(field.getType())) return "false";
        if ("number".equals(type(field))) return "null";
        return "''";
    }

    private String relationState(List<RelationDefinition> relations) {
        StringBuilder state = new StringBuilder();
        for (RelationDefinition relation : relations) {
            state.append("const ").append(relation.getFieldName())
                .append("Options = ref<Array<{ title: string; value: number }>>([]);\n");
        }
        return state.toString().stripTrailing();
    }

    private String relationServiceImports(ForgeDefinition def, EntityDefinition source, List<RelationDefinition> relations) {
        StringBuilder imports = new StringBuilder();
        List<String> imported = new ArrayList<>();
        for (RelationDefinition relation : relations) {
            EntityDefinition target = findEntity(def, relation.getTargetEntity());
            String function = "load" + plural(target);
            String targetKey = key(target);
            if (targetKey.equals(key(source))) continue;
            String signature = function + "@" + targetKey;
            if (imported.add(signature)) {
                imports.append("import { ").append(function).append(" } from '@/services/")
                    .append(targetKey).append("Service';\n");
            }
        }
        return imports.toString().stripTrailing();
    }

    private String mountedHook(ForgeDefinition def, EntityDefinition entity, boolean edit,
                               List<RelationDefinition> relations) {
        if (!edit && relations.isEmpty()) return "";
        StringBuilder loaders = new StringBuilder();
        for (RelationDefinition relation : relations) {
            EntityDefinition target = findEntity(def, relation.getTargetEntity());
            String displayField = relation.getDisplayField();
            if (displayField == null || displayField.isBlank()) {
                displayField = target.getFields().stream().filter(FieldDefinition::isInResponse)
                    .map(FieldDefinition::getName).findFirst().orElse("id");
            }
            loaders.append("    const ").append(relation.getFieldName()).append("Page = await load")
                .append(plural(target)).append("({ page: 0, size: 100, sortBy: 'id', ascending: true });\n")
                .append("    ").append(relation.getFieldName()).append("Options.value = (")
                .append(relation.getFieldName()).append("Page.content ?? []).map((item) => ({\n")
                .append("      title: String(item[").append(quote(displayField)).append("] ?? item.id),\n")
                .append("      value: item.id,\n")
                .append("    }));\n");
        }
        StringBuilder editLoad = new StringBuilder();
        if (edit) {
            editLoad.append("    const item = await find").append(entity.getName())
                .append("(Number(route.params.id));\n    Object.assign(form, item);\n");
            for (FieldDefinition field : entity.getFields()) {
                if (field.isInRequest() && "LocalDate".equalsIgnoreCase(field.getType())) {
                    editLoad.append("    form.").append(field.getName()).append(" = toDisplayDate(item.")
                        .append(field.getName()).append(");\n");
                }
            }
        }
        return """
            onMounted(async () => {
              isLoading.value = true;
              try {
            %s%s
              } catch (error) {
                console.error(error);
                showAlert('Erro', 'Não foi possível carregar os dados da página.', 'error');
              } finally {
                isLoading.value = false;
              }
            });
            """.formatted(loaders.toString(), editLoad.toString()).stripTrailing();
    }

    private EntityDefinition findEntity(ForgeDefinition def, String name) {
        return def.getEntities().stream().filter(entity -> entity.getName().equals(name)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Entidade relacionada não encontrada: " + name));
    }

    private String dateHelpers(EntityDefinition entity, boolean edit) {
        boolean hasDate = entity.getFields().stream().anyMatch(field -> field.isInRequest()
            && "LocalDate".equalsIgnoreCase(field.getType()));
        StringBuilder helpers = new StringBuilder();
        if (hasDate) {
            helpers.append("""
                function toIsoDate(value: unknown): string | null {
                  if (!value) return null;
                  const text = String(value);
                  const parts = text.split('/');
                  return parts.length === 3 ? `${parts[2]}-${parts[1]}-${parts[0]}` : text;
                }

                """);
            if (edit) {
                helpers.append("""
                    function toDisplayDate(value: unknown): string {
                      if (!value) return '';
                      const text = String(value);
                      const parts = text.split('-');
                      return parts.length === 3 ? `${parts[2]}/${parts[1]}/${parts[0]}` : text;
                    }

                    """);
            }
        }
        helpers.append("function buildPayload(): ").append(entity.getName()).append("Request {\n")
            .append("  const payload: ").append(entity.getName()).append("Request = { ...form };\n");
        for (FieldDefinition field : entity.getFields()) {
            if (!field.isInRequest()) continue;
            if (field.isReadOnly()) {
                helpers.append("  delete payload.").append(field.getName()).append(";\n");
            } else if ("LocalDate".equalsIgnoreCase(field.getType())) {
                helpers.append("  payload.").append(field.getName()).append(" = toIsoDate(form.")
                    .append(field.getName()).append(");\n");
            } else if (mask(field.getName()) != null) {
                String regex = "cnpj".equals(mask(field.getName())) ? "/[^a-zA-Z0-9]/g" : "/\\D/g";
                helpers.append("  payload.").append(field.getName()).append(" = form.").append(field.getName())
                    .append(" ? String(form.").append(field.getName()).append(").replace(").append(regex)
                    .append(", '') : form.").append(field.getName()).append(";\n");
            }
        }
        return helpers.append("  return payload;\n}").toString();
    }

    private String validationFunction(EntityDefinition entity, List<RelationDefinition> relations) {
        StringBuilder body = new StringBuilder("function validateForm(): string | null {\n  const missing: string[] = [];\n");
        for (FieldDefinition field : entity.getFields()) {
            if (!field.isInRequest() || field.isReadOnly()) continue;
            String label = field.getLabel() == null || field.getLabel().isBlank() ? label(field.getName()) : field.getLabel();
            if (field.isRequired()) {
                body.append("  if (form.").append(field.getName()).append(" === null || form.")
                    .append(field.getName()).append(" === undefined || String(form.").append(field.getName())
                    .append(").trim() === '') missing.push(").append(quote(label)).append(");\n");
            }
            if (field.getMinLength() != null) {
                body.append("  if (form.").append(field.getName()).append(" && String(form.").append(field.getName())
                    .append(").length < ").append(field.getMinLength()).append(") return ")
                    .append(quote(label + " deve ter pelo menos " + field.getMinLength() + " caracteres.")).append(";\n");
            }
            if (field.getMaxLength() != null) {
                body.append("  if (form.").append(field.getName()).append(" && String(form.").append(field.getName())
                    .append(").length > ").append(field.getMaxLength()).append(") return ")
                    .append(quote(label + " deve ter no máximo " + field.getMaxLength() + " caracteres.")).append(";\n");
            }
            boolean email = "email".equalsIgnoreCase(field.getName()) || field.getValidations().stream().anyMatch(v -> v.contains("Email"));
            if (email) {
                body.append("  if (form.").append(field.getName()).append(" && !/^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$/.test(String(form.")
                    .append(field.getName()).append("))) return ").append(quote("Informe um email válido.")).append(";\n");
            }
        }
        for (RelationDefinition relation : relations) {
            if (relation.isRequired()) {
                body.append("  if (form.").append(relation.getFieldName()).append("Id == null) missing.push(")
                    .append(quote(label(relation.getFieldName()))).append(");\n");
            }
        }
        body.append("  return missing.length ? `Preencha os campos obrigatórios: ${missing.join(', ')}.` : null;\n}");
        return body.toString();
    }

    private String tableStyles() {
        return """
            <style lang="scss" scoped>
            .custom-table-card {
              background: #ffffff;
              border-radius: 0.6rem;
              box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
              padding: 1.5rem;
              margin: 1rem 0;
              overflow-x: auto;
            }

            .table-controls {
              margin-bottom: 1rem;
              font-size: 1.2rem;
              color: #002060;

              select {
                margin: 0 0.3rem;
                padding: 0.4rem 0.6rem;
                border: 1px solid #cccccc;
                border-radius: 8px;
                background: #ffffff;
                color: #002060;
                font-size: 1rem;
              }
            }

            .custom-table {
              width: 100%;
              border-collapse: separate;
              border-spacing: 0 6px;
              font-size: 1.3rem;

              thead {
                background: #002060;
                color: #ffffff;

                th {
                  padding: 1rem;
                  text-align: left;
                  white-space: nowrap;
                }
              }

              tbody {
                tr {
                  background: #f5f5f5;
                  transition: background 0.2s ease;

                  &:hover {
                    background: #eaeaea;
                  }
                }

                td {
                  padding: 1rem;
                  color: #002060;
                  border-bottom: 2px solid #ffffff;
                }
              }

              .empty {
                padding: 1rem;
                color: #666666;
                text-align: center;
              }
            }

            .icon-btn {
              width: 36px;
              height: 36px;
              border: 1px solid #002060;
              border-radius: 50%;
              background: transparent;
              color: #002060;
              display: inline-flex;
              align-items: center;
              justify-content: center;
              margin: 0 0.2rem;
              cursor: pointer;
              transition: all 0.2s ease;

              &:hover {
                background: #002060;
                color: #ffffff;
              }
            }

            .central {
              text-align: center !important;
            }

            .table-footer {
              display: flex;
              justify-content: space-between;
              align-items: center;
              gap: 1rem;
              margin-top: 2rem;
              color: #002060;
              font-size: 1rem;
            }

            .pagination {
              display: flex;
              gap: 0.4rem;

              button {
                min-width: 34px;
                padding: 0.4rem 0.8rem;
                border: 1px solid #cccccc;
                border-radius: 8px;
                background: #ffffff;
                color: #002060;
                cursor: pointer;
                font-size: 1.1rem;

                &.active {
                  background: #002060;
                  color: #ffffff;
                  font-weight: 700;
                }

                &:disabled {
                  opacity: 0.5;
                  cursor: not-allowed;
                }
              }
            }

            .loading-overlay {
              min-height: 220px;
              display: flex;
              flex-direction: column;
              align-items: center;
              justify-content: center;
            }

            .spinner {
              width: 60px;
              height: 60px;
              border: 6px solid #e5e7eb;
              border-top-color: #002060;
              border-radius: 50%;
              animation: spin 1s linear infinite;
            }

            .loading-text {
              margin-top: 1rem;
              color: #002060;
              font-size: 1.3rem;
              font-weight: 600;
            }

            .confirm-overlay {
              position: fixed;
              inset: 0;
              z-index: 9999;
              padding: 1.5rem;
              background: rgba(0, 0, 0, 0.45);
              display: flex;
              align-items: center;
              justify-content: center;
            }

            .confirm-dialog {
              width: min(440px, 100%);
              padding: 2rem;
              border-radius: 12px;
              background: #ffffff;
              box-shadow: 0 6px 20px rgba(0, 0, 0, 0.25);
              color: #002060;

              h2 {
                margin: 0 0 1rem;
                font-size: 1.8rem;
              }

              p {
                margin: 0;
                color: #4b5563;
                font-size: 1.2rem;
              }
            }

            .confirm-actions {
              display: flex;
              justify-content: flex-end;
              gap: 0.8rem;
              margin-top: 2rem;

              button {
                height: 36px;
                padding: 0 16px;
                border-radius: 4px;
                cursor: pointer;
                font-weight: 600;
                text-transform: uppercase;
              }
            }

            .confirm-cancel {
              border: 1px solid #6b7280;
              background: transparent;
              color: #4b5563;
            }

            .confirm-delete {
              border: 1px solid #b91c1c;
              background: #b91c1c;
              color: #ffffff;
            }

            @keyframes spin {
              to { transform: rotate(360deg); }
            }

            @media (max-width: 768px) {
              .table-footer {
                align-items: flex-start;
                flex-direction: column;
              }

              .pagination {
                flex-wrap: wrap;
              }
            }
            </style>
            """;
    }

    private String listPageStyles() {
        return """
            <style lang="scss" scoped>
            $primary-color: #041538;
            $hover-color: #4aff00;

            .page-wrapper {
              flex: 1;
              width: 100%;
              min-height: 100%;
              background-color: #f7f7f9;
              color: #374151;
            }

            .padding-page {
              padding: 4rem 6rem 3rem 4rem;
            }

            .page-header {
              display: flex;
              align-items: baseline;
              justify-content: space-between;
              gap: 1rem;
              margin-bottom: 2rem;
            }

            .header-title {
              margin: 0;
              color: #374151;
              font-size: 3.25rem;
              font-weight: 600;
            }

            .header-subtitle {
              margin-top: 0.25rem;
              color: #6b7280;
              font-size: 1.4rem;
            }

            .custom-import-button {
              height: 36px;
              padding: 0 16px;
              border: none;
              border-radius: 4px;
              background-color: $primary-color;
              color: #ffffff;
              display: inline-flex;
              align-items: center;
              justify-content: center;
              cursor: pointer;
              font-weight: 500;
              letter-spacing: 0.089em;
              text-transform: uppercase;
              transition: all 0.2s ease;

              .v-icon {
                margin-right: 10px;
                color: #ffffff !important;
                font-size: 14px !important;
              }

              &:hover {
                background-color: $hover-color;
                color: $primary-color;
                transform: translateY(-2px);
                box-shadow: 0 4px 8px rgba(0, 0, 0, 0.2);

                .v-icon { color: $primary-color !important; }
              }
            }

            .filters-section {
              margin-bottom: 1rem;

              h3 {
                margin-bottom: 2rem;
                color: #374151;
              }
            }

            .form-row {
              display: flex;
              flex-wrap: wrap;
              gap: 1rem;
              margin-bottom: 2.5rem;
            }

            .form-group {
              min-width: 220px;
              flex: 1;
              display: flex;
              flex-direction: column;
            }

            label {
              margin-bottom: 0.4rem;
              color: #555555;
              font-size: 1.2rem;
              font-weight: 500;
            }

            @media (max-width: 768px) {
              .padding-page { padding: 2rem 1.5rem; }
              .page-header { align-items: flex-start; flex-direction: column; }
              .header-title { font-size: 2.4rem; }
              .custom-import-button { align-self: stretch; }
            }
            </style>
            """;
    }

    private String formPageStyles() {
        return """
            <style lang="scss" scoped>
            $primary-color: #041538;
            $hover-color: #4aff00;
            $color-gray-500: #6b7280;
            $color-gray-600: #4b5563;

            .page-wrapper {
              flex: 1;
              width: 100%;
              min-height: 100%;
              overflow-x: hidden;
              background-color: #f7f7f9;
              box-sizing: border-box;
            }

            .padding-page {
              padding: 4rem 6rem 3rem 4rem;
            }

            .page-header {
              display: flex;
              align-items: baseline;
              justify-content: space-between;
              margin-bottom: 3rem;
              color: #374151;
            }

            .header-title {
              margin: 0;
              font-size: 3.25rem;
              font-weight: 600;
            }

            .header-subtitle {
              margin-top: 0.25rem;
              color: $color-gray-500;
              font-size: 1.4rem;
            }

            .form-spacing {
              width: 100%;

              > .v-row {
                margin-bottom: 0.5rem;

                &:last-of-type { margin-bottom: 0; }
              }
            }

            label {
              display: block;
              margin-bottom: 0.4rem;
              color: #555555;
              font-size: 1.2rem;
              font-weight: 500;
            }

            .field-helper {
              display: block;
              margin-top: 0.35rem;
              color: $color-gray-500;
              font-size: 0.9rem;
            }

            .separator {
              width: 100%;
              height: 1px;
              margin: 1rem 0;
              background: #d1d5db;
            }

            .button-row-actions {
              display: flex;
              justify-content: flex-end;
              gap: 1rem;
              margin-top: 1rem;
            }

            .custom-import-button,
            .custom-cancel-button-outlined {
              height: 36px;
              padding: 0 16px;
              border-radius: 4px;
              display: inline-flex;
              align-items: center;
              justify-content: center;
              cursor: pointer;
              font-weight: 500;
              letter-spacing: 0.089em;
              text-transform: uppercase;
              transition: all 0.2s ease;

              .v-icon {
                margin-right: 10px;
                font-size: 14px !important;
              }
            }

            .custom-import-button {
              border: none;
              background-color: $primary-color;
              color: #ffffff;

              .v-icon { color: #ffffff !important; }

              &:hover {
                background-color: $hover-color;
                color: $primary-color;
                transform: translateY(-2px);
                box-shadow: 0 4px 8px rgba(0, 0, 0, 0.2);

                .v-icon { color: $primary-color !important; }
              }
            }

            .custom-cancel-button-outlined {
              border: 1px solid $color-gray-500;
              background-color: transparent;
              color: $color-gray-600;

              .v-icon { color: $color-gray-600 !important; }
            }

            @media (max-width: 768px) {
              .padding-page { padding: 2rem 1.5rem; }
              .header-title { font-size: 2.4rem; }
              .button-row-actions { flex-direction: column-reverse; }
              .custom-import-button,
              .custom-cancel-button-outlined { width: 100%; }
            }
            </style>
            """;
    }

    private String routes(ForgeDefinition def) {
        StringBuilder output = new StringBuilder("import type { RouteRecordRaw } from 'vue-router';\n");
        for (EntityDefinition entity : def.getEntities()) {
            if (!entity.shouldGenerate("frontend")) continue;
            String plural = plural(entity);
            output.append("import Listar").append(plural).append(" from '@/views/pages/Dashboard").append(plural).append("/Listar").append(plural).append(".vue';\n")
                .append("import Cadastrar").append(plural).append(" from '@/views/pages/Dashboard").append(plural).append("/Cadastrar").append(plural).append(".vue';\n")
                .append("import Editar").append(plural).append(" from '@/views/pages/Dashboard").append(plural).append("/Editar").append(plural).append(".vue';\n");
        }
        output.append("\n/**\n * Rotas das entidades geradas. No router/index.ts do Sesi Laboral, importe\n * generatedRoutes de './routes/generated' e inclua ...generatedRoutes em routes.\n */\n")
            .append("const generatedRoutes: Array<RouteRecordRaw> = [\n");
        for (EntityDefinition entity : def.getEntities()) {
            if (!entity.shouldGenerate("frontend")) continue;
            String plural = plural(entity);
            String key = key(entity);
            output.append(route("/" + key + "/listar", "listar-" + key, "Listar" + plural, "Lista de " + plural))
                .append(route("/" + key + "/cadastrar", "cadastrar-" + key, "Cadastrar" + plural, "Cadastrar " + plural))
                .append(route("/" + key + "/editar/:id", "editar-" + key, "Editar" + plural, "Editar " + plural));
        }
        return output.append("];\n\nexport default generatedRoutes;\n").toString();
    }

    private String route(String path, String name, String component, String title) {
        return """
              {
                path: %s,
                name: %s,
                component: %s,
                meta: {
                  requiresAuth: true,
                  requiresAdmin: true,
                  title: %s,
                },
              },
            """.formatted(quote(path), quote(name), component, quote(title));
    }

    private String navigation(ForgeDefinition def) {
        StringBuilder output = new StringBuilder("""
            import { $regraAcao, $regraEntidade } from '@/custom-enum/regras-enum';
            import type { NavMenuItem } from '@/types/nav-menu';

            const generatedNavMenuItems: NavMenuItem[] = [
            """);
        for (EntityDefinition entity : def.getEntities()) {
            if (!entity.shouldGenerate("frontend")) continue;
            String plural = plural(entity);
            String key = key(entity);
            output.append("""
              {
                header: %s,
                resource: $regraEntidade.menuLateral.header.powerbi,
                action: $regraAcao.visualizar,
              },
              {
                title: 'Cadastrar',
                route: %s,
                icon: 'fa-solid fa-plus',
                resource: $regraEntidade.menuLateral.powerbi.dashboard,
                action: $regraAcao.visualizar,
              },
              {
                title: 'Listar',
                route: %s,
                icon: 'fa-solid fa-list',
                resource: $regraEntidade.menuLateral.powerbi.dashboard,
                action: $regraAcao.visualizar,
              },
            """.formatted(quote(plural), quote("cadastrar-" + key), quote("listar-" + key)));
        }
        return output.append("];\n\nexport default generatedNavMenuItems;\n").toString();
    }

    private String label(String value) { return NamingUtils.toHumanLabel(value); }

    /**
     * Connects the generated route collection to the Sesi Laboral router without
     * replacing its imports, guards or existing route declarations.
     */
    private void integrateRoutes(File sourceDir) throws MojoExecutionException {
        File routerIndex = new File(sourceDir, "router/index.ts");
        if (!routerIndex.isFile()) return;
        try {
            String content = Files.readString(routerIndex.toPath(), StandardCharsets.UTF_8);
            String updated = content;
            if (!updated.contains("from './routes/generated'")) {
                String anchor = "const canNavigate";
                int anchorIndex = updated.indexOf(anchor);
                if (anchorIndex < 0) {
                    log.warn("  [AVISO] router/index.ts não segue o padrão Sesi Laboral; importe './routes/generated' manualmente.");
                    return;
                }
                updated = updated.substring(0, anchorIndex)
                    + "import generatedRoutes from './routes/generated';\n\n"
                    + updated.substring(anchorIndex);
            }
            if (!updated.contains("...generatedRoutes")) {
                String routeList = "routes: [...authRoutes, ...privateRoutes]";
                if (!updated.contains(routeList)) {
                    log.warn("  [AVISO] Não foi possível incluir as rotas geradas automaticamente; adicione ...generatedRoutes ao router/index.ts.");
                    return;
                }
                updated = updated.replace(routeList, "routes: [...authRoutes, ...privateRoutes, ...generatedRoutes]");
            }
            if (!updated.equals(content)) {
                Files.writeString(routerIndex.toPath(), updated, StandardCharsets.UTF_8);
                log.info("  [ATUALIZADO] " + routerIndex);
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Erro ao integrar rotas Sesi Laboral em router/index.ts", e);
        }
    }

    /** Adds the generated entity sections to the existing administrative sidebar. */
    private void integrateNavigation(File sourceDir) throws MojoExecutionException {
        File navigationIndex = new File(sourceDir, "navigation/vertical/index.ts");
        if (!navigationIndex.isFile()) return;
        try {
            String content = Files.readString(navigationIndex.toPath(), StandardCharsets.UTF_8);
            String updated = content;
            if (!updated.contains("from './generated'")) {
                String anchor = "const navMenuItems";
                int anchorIndex = updated.indexOf(anchor);
                if (anchorIndex < 0) {
                    log.warn("  [AVISO] navigation/vertical/index.ts não segue o padrão Sesi Laboral; importe './generated' manualmente.");
                    return;
                }
                updated = updated.substring(0, anchorIndex)
                    + "import generatedNavMenuItems from './generated';\n\n"
                    + updated.substring(anchorIndex);
            }
            if (!updated.contains("...generatedNavMenuItems")) {
                String listStart = "const navMenuItems: NavMenuItem[] = [";
                if (!updated.contains(listStart)) {
                    log.warn("  [AVISO] Não foi possível incluir o menu gerado automaticamente; adicione ...generatedNavMenuItems ao navigation/vertical/index.ts.");
                    return;
                }
                updated = updated.replace(listStart, listStart + "\n  ...generatedNavMenuItems,");
            }
            if (!updated.equals(content)) {
                Files.writeString(navigationIndex.toPath(), updated, StandardCharsets.UTF_8);
                log.info("  [ATUALIZADO] " + navigationIndex);
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Erro ao integrar menu lateral Sesi Laboral em navigation/vertical/index.ts", e);
        }
    }

    private void write(File root, String path, String content) throws MojoExecutionException {
        try { File target = new File(root, path); Files.createDirectories(target.getParentFile().toPath()); Files.writeString(target.toPath(), content, StandardCharsets.UTF_8); log.info("  [GERADO] " + target); }
        catch (Exception e) { throw new MojoExecutionException("Erro ao gerar extensão frontend Sesi Laboral: " + path, e); }
    }
}
