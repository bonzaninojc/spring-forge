package io.springforge.model;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public class EntityDefinition {

    private String name;
    private String tableName;
    private String schema;
    private List<FieldDefinition> fields = new ArrayList<>();
    private List<RelationDefinition> relations = new ArrayList<>();
    private List<ActionDefinition> actions = new ArrayList<>();   // ← NOVO
    private boolean auditable = true;
    private boolean softDelete = false;
    private List<String> generate = new ArrayList<>();
    private String apiPath;

    /** Filas RabbitMQ globais da entidade (não ligadas a uma action específica) */
    private List<QueueDefinition> queues = new ArrayList<>();

    /** Eventos Spring globais da entidade */
    private List<EventDefinition> events = new ArrayList<>();

    /** Tags OpenAPI para todos os endpoints desta entidade */
    private List<String> openApiTags = new ArrayList<>();

    /** Filtros de busca para a entidade (endpoint POST /search) */
    private List<FilterDefinition> filters = new ArrayList<>();

    /** Configuração de cache para a entidade */
    private CacheDefinition cache;

    /** Configuração de export/import para a entidade */
    private ExportImportDefinition exportImport;

    /** Configuração avançada do CRUD: filtros/ordenação/paginação. */
    private CrudDefinition crud;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public String getSchema() { return schema; }
    public void setSchema(String schema) { this.schema = schema; }

    public List<FieldDefinition> getFields() { return fields; }
    public void setFields(List<FieldDefinition> fields) { this.fields = fields; }

    public List<RelationDefinition> getRelations() { return relations; }
    public void setRelations(List<RelationDefinition> relations) { this.relations = relations; }

    public List<ActionDefinition> getActions() { return actions; }
    public void setActions(List<ActionDefinition> actions) { this.actions = actions; }

    public boolean isAuditable() { return auditable; }
    public void setAuditable(boolean auditable) { this.auditable = auditable; }

    public boolean isSoftDelete() { return softDelete; }
    public void setSoftDelete(boolean softDelete) { this.softDelete = softDelete; }

    public List<String> getGenerate() { return generate; }
    public void setGenerate(List<String> generate) { this.generate = generate; }

    public String getApiPath() { return apiPath; }
    public void setApiPath(String apiPath) { this.apiPath = apiPath; }

    public boolean shouldGenerate(String layer) {
        return generate == null || generate.isEmpty() || generate.contains(layer);
    }

    public boolean hasActions() {
        return actions != null && !actions.isEmpty();
    }

    public List<QueueDefinition> getQueues() { return queues; }
    public void setQueues(List<QueueDefinition> queues) { this.queues = queues; }

    public List<EventDefinition> getEvents() { return events; }
    public void setEvents(List<EventDefinition> events) { this.events = events; }

    public List<String> getOpenApiTags() { return openApiTags; }
    public void setOpenApiTags(List<String> openApiTags) { this.openApiTags = openApiTags; }

    public List<FilterDefinition> getFilters() { return filters; }
    public void setFilters(List<FilterDefinition> filters) { this.filters = filters; }

    /**
     * Filtros efetivos: mantém compatibilidade com entity.filters e adiciona crud.filterable.
     */
    public List<FilterDefinition> getEffectiveFilters() {
        List<FilterDefinition> all = new ArrayList<>();
        if (filters != null) all.addAll(filters);
        if (crud != null && crud.getFilterable() != null) {
            for (FilterDefinition filter : crud.getFilterable()) {
                boolean alreadyDefined = all.stream().anyMatch(existing -> existing.getName().equals(filter.getName()));
                if (!alreadyDefined) all.add(filter);
            }
        }
        return all;
    }

    public boolean hasFilters() { return !getEffectiveFilters().isEmpty(); }

    public CacheDefinition getCache() { return cache; }
    public void setCache(CacheDefinition cache) { this.cache = cache; }
    public boolean hasCache() { return cache != null && cache.isEnabled(); }

    public ExportImportDefinition getExportImport() { return exportImport; }
    public void setExportImport(ExportImportDefinition exportImport) { this.exportImport = exportImport; }
    public boolean hasExportImport() { return exportImport != null && exportImport.isEnabled(); }

    public CrudDefinition getCrud() { return crud; }
    public void setCrud(CrudDefinition crud) { this.crud = crud; }
    public boolean hasAdvancedCrud() {
        return crud != null && (crud.isBulkOperations()
            || (crud.getSortable() != null && !crud.getSortable().isEmpty())
            || (crud.getFilterable() != null && !crud.getFilterable().isEmpty()));
    }

    /** Retorna todas as queues: as da entidade + as das actions */
    public List<QueueDefinition> getAllQueues() {
        List<QueueDefinition> all = new ArrayList<>(queues);
        for (ActionDefinition a : actions) {
            if (a.hasQueues()) all.addAll(a.getQueues());
        }
        return all;
    }

    /** Retorna todos os eventos: os da entidade + os das actions */
    public List<EventDefinition> getAllEvents() {
        List<EventDefinition> all = new ArrayList<>(events);
        for (ActionDefinition a : actions) {
            if (a.hasEvent()) all.add(a.getEvent());
        }
        return all;
    }

    public boolean hasQueues() { return !getAllQueues().isEmpty(); }
    public boolean hasEvents() { return !getAllEvents().isEmpty(); }
}
