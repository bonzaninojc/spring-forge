package io.springforge.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;

/**
 * Configuração de export/import para uma entidade.
 *
 * Exemplo no forge.json:
 * {
 *   "exportImport": {
 *     "enabled": true,
 *     "formats": ["csv", "excel"],
 *     "exportFields": ["name", "price", "stock"],
 *     "importFields": ["name", "price", "stock"]
 *   }
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExportImportDefinition {

    private boolean enabled = true;
    private List<String> formats = List.of("csv");
    private List<String> exportFields = new ArrayList<>();
    private List<String> importFields = new ArrayList<>();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public List<String> getFormats() { return formats; }
    public void setFormats(List<String> formats) { this.formats = formats; }

    public List<String> getExportFields() { return exportFields; }
    public void setExportFields(List<String> exportFields) { this.exportFields = exportFields; }

    public List<String> getImportFields() { return importFields; }
    public void setImportFields(List<String> importFields) { this.importFields = importFields; }

    public boolean supportsCsv() { return formats.stream().anyMatch(f -> "csv".equalsIgnoreCase(f)); }
    public boolean supportsExcel() { return formats.stream().anyMatch(f -> "excel".equalsIgnoreCase(f)); }
}
