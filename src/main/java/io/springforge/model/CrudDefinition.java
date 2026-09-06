package io.springforge.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuração avançada do CRUD gerado: paginação, filtros e ordenação permitidos.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public class CrudDefinition {

    private boolean enabled = true;
    private boolean pagination = true;
    private int defaultPageSize = 20;
    private int maxPageSize = 100;
    private String defaultSort = "id";
    private String defaultDirection = "ASC";

    /** Campos/caminhos liberados para ordenação. Ex: ["name", "price", "category.name"] */
    private List<String> sortable = new ArrayList<>();

    /** Filtros gerados. targetField aceita caminho aninhado, ex: category.name. */
    private List<FilterDefinition> filterable = new ArrayList<>();

    /** Se true, gera endpoints de operação em massa. */
    private boolean bulkOperations = false;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public boolean isPagination() { return pagination; }
    public void setPagination(boolean pagination) { this.pagination = pagination; }

    public int getDefaultPageSize() { return defaultPageSize; }
    public void setDefaultPageSize(int defaultPageSize) { this.defaultPageSize = defaultPageSize; }

    public int getMaxPageSize() { return maxPageSize; }
    public void setMaxPageSize(int maxPageSize) { this.maxPageSize = maxPageSize; }

    public String getDefaultSort() { return defaultSort; }
    public void setDefaultSort(String defaultSort) { this.defaultSort = defaultSort; }

    public String getDefaultDirection() { return defaultDirection; }
    public void setDefaultDirection(String defaultDirection) { this.defaultDirection = defaultDirection; }

    public List<String> getSortable() { return sortable; }
    public void setSortable(List<String> sortable) { this.sortable = sortable; }

    public List<FilterDefinition> getFilterable() { return filterable; }
    public void setFilterable(List<FilterDefinition> filterable) { this.filterable = filterable; }

    public boolean isBulkOperations() { return bulkOperations; }
    public void setBulkOperations(boolean bulkOperations) { this.bulkOperations = bulkOperations; }
}
