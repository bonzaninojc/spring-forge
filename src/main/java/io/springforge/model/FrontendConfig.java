package io.springforge.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Configuração declarativa do frontend gerado pelo Spring Forge.
 * Mantém React/MUI como implementação atual, mas deixa o forge.json preparado
 * para evoluir para outros stacks sem quebrar compatibilidade.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public class FrontendConfig {

    /** Ativa a geração de frontend. Equivale a project.generateFrontend=true. */
    private boolean enabled = false;

    /** Framework alvo. Hoje suportado: react. */
    private String framework = "react";

    /** Biblioteca visual. Hoje suportado: mui. */
    private String ui = "mui";

    /** Gerenciador de estado. Hoje suportado: redux-toolkit. */
    private String state = "redux-toolkit";

    /** Biblioteca de formulários. Hoje suportado: react-hook-form. */
    private String forms = "react-hook-form";

    /** Biblioteca de validação. Hoje suportado: zod. */
    private String validation = "zod";

    /** Gera api/client.ts com interceptor JWT. */
    private boolean apiClient = true;

    /** Gera página de login quando security está ativo. */
    private boolean authPages = true;

    /** Gera ProtectedRoute quando security está ativo. */
    private boolean protectedRoutes = true;

    /** Gera App shell com menu/header. */
    private boolean appShell = true;

    /** Gera painel de filtros baseado em crud.filterable/entity.filters. */
    private boolean filterPanel = true;

    /** Gera ordenação no grid/lista baseada em crud.sortable. */
    private boolean tableSorting = true;

    /** Gera diálogos de confirmação reutilizáveis. */
    private boolean confirmDialogs = true;

    /** Gera provider de notificações/snackbar. */
    private boolean notifications = true;

    /** Reserva para tema escuro/claro do frontend gerado. */
    private boolean darkMode = false;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getFramework() { return framework; }
    public void setFramework(String framework) { this.framework = framework; }

    public String getUi() { return ui; }
    public void setUi(String ui) { this.ui = ui; }

    public String getState() { return state; }
    public void setState(String state) { this.state = state; }

    public String getForms() { return forms; }
    public void setForms(String forms) { this.forms = forms; }

    public String getValidation() { return validation; }
    public void setValidation(String validation) { this.validation = validation; }

    public boolean isApiClient() { return apiClient; }
    public void setApiClient(boolean apiClient) { this.apiClient = apiClient; }

    public boolean isAuthPages() { return authPages; }
    public void setAuthPages(boolean authPages) { this.authPages = authPages; }

    public boolean isProtectedRoutes() { return protectedRoutes; }
    public void setProtectedRoutes(boolean protectedRoutes) { this.protectedRoutes = protectedRoutes; }

    public boolean isAppShell() { return appShell; }
    public void setAppShell(boolean appShell) { this.appShell = appShell; }

    public boolean isFilterPanel() { return filterPanel; }
    public void setFilterPanel(boolean filterPanel) { this.filterPanel = filterPanel; }

    public boolean isTableSorting() { return tableSorting; }
    public void setTableSorting(boolean tableSorting) { this.tableSorting = tableSorting; }

    public boolean isConfirmDialogs() { return confirmDialogs; }
    public void setConfirmDialogs(boolean confirmDialogs) { this.confirmDialogs = confirmDialogs; }

    public boolean isNotifications() { return notifications; }
    public void setNotifications(boolean notifications) { this.notifications = notifications; }

    public boolean isDarkMode() { return darkMode; }
    public void setDarkMode(boolean darkMode) { this.darkMode = darkMode; }
}
