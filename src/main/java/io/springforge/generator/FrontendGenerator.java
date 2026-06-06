package io.springforge.generator;

import io.springforge.model.ActionDefinition;
import io.springforge.model.EntityDefinition;
import io.springforge.model.FieldDefinition;
import io.springforge.model.FilterDefinition;
import io.springforge.model.ForgeDefinition;
import io.springforge.model.RelationDefinition;
import io.springforge.util.NamingUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;

import java.io.File;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * Gera frontend React (Vite + MUI + Redux Toolkit) para cada entidade.
 * Componentizado com:
 *  - components/shared/PageHeader.tsx
 *  - components/shared/ConfirmDialog.tsx
 *  - components/shared/EmptyState.tsx
 *  - components/shared/EntitySelect.tsx  (autocomplete para relacionamentos)
 *  - components/shared/FormTextField.tsx
 *  - components/shared/StatusChip.tsx
 *  - store/slices/{entity}Slice.ts
 *  - pages/{entity}/List{Entity}Page.tsx
 *  - pages/{entity}/{Entity}FormPage.tsx
 *  - store/store.ts
 *  - routes.tsx
 *  - components/AppMenu.tsx
 *  - App.tsx
 *  - theme.ts
 */
public class FrontendGenerator extends AbstractGenerator {

    public FrontendGenerator(Log log) { super(log); }

    @Override
    public void generate(ForgeDefinition def, EntityDefinition entity, File outDir) throws MojoExecutionException {
        if (!def.getProject().isGenerateFrontend()) return;
        if (!entity.shouldGenerate("frontend")) return;

        File frontendDir = resolveFrontendDir(def, outDir);

        generateSlice(def, entity, frontendDir);
        generateListPage(def, entity, frontendDir);
        generateFormPage(def, entity, frontendDir);
        generateDetailPage(def, entity, frontendDir);
        if (entity.hasFilters()) {
            generateFilterPanel(def, entity, frontendDir);
        }
        generateValidationSchema(def, entity, frontendDir);
    }

    public void generateGlobalFiles(ForgeDefinition def, File outDir) throws MojoExecutionException {
        if (!def.getProject().isGenerateFrontend()) return;

        File frontendDir = resolveFrontendDir(def, outDir);

        generateTheme(def, frontendDir);
        generateStore(def, frontendDir);
        generateRoutes(def, frontendDir);
        generateAppMenu(def, frontendDir);
        generateApp(def, frontendDir);
        generateSharedComponents(frontendDir);
        generateAppHeader(def, frontendDir);
    }

    private File resolveFrontendDir(ForgeDefinition def, File outDir) {
        return new File(outDir.getParentFile().getParentFile(), def.getProject().getFrontendDir());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Theme
    // ═══════════════════════════════════════════════════════════════════════

    private void generateTheme(ForgeDefinition def, File frontendDir) throws MojoExecutionException {
        StringBuilder sb = new StringBuilder();
        sb.append("import { createTheme } from '@mui/material/styles';\n\n");
        sb.append("const theme = createTheme({\n");
        sb.append("  typography: {\n");
        sb.append("    fontFamily: '\"Inter\", \"Roboto\", \"Helvetica\", sans-serif',\n");
        sb.append("    h4: { fontWeight: 700 },\n");
        sb.append("    h5: { fontWeight: 600 },\n");
        sb.append("    h6: { fontWeight: 600 },\n");
        sb.append("  },\n");
        sb.append("  palette: {\n");
        sb.append("    primary: { main: '#1976d2', light: '#42a5f5', dark: '#1565c0' },\n");
        sb.append("    secondary: { main: '#0288d1', light: '#03a9f4', dark: '#01579b' },\n");
        sb.append("    success: { main: '#2e7d32' },\n");
        sb.append("    warning: { main: '#ed6c02' },\n");
        sb.append("    error: { main: '#d32f2f' },\n");
        sb.append("    background: { default: '#f0f7ff', paper: '#ffffff' },\n");
        sb.append("  },\n");
        sb.append("  shape: { borderRadius: 12 },\n");
        sb.append("  components: {\n");
        sb.append("    MuiButton: {\n");
        sb.append("      styleOverrides: {\n");
        sb.append("        root: { textTransform: 'none', fontWeight: 600, borderRadius: 10, padding: '8px 20px' },\n");
        sb.append("        contained: { boxShadow: '0 2px 8px rgba(25, 118, 210, 0.3)' },\n");
        sb.append("      },\n");
        sb.append("    },\n");
        sb.append("    MuiPaper: {\n");
        sb.append("      styleOverrides: {\n");
        sb.append("        root: { boxShadow: '0 1px 3px rgba(0,0,0,0.04), 0 1px 2px rgba(0,0,0,0.06)', border: '1px solid #f1f5f9' },\n");
        sb.append("      },\n");
        sb.append("    },\n");
        sb.append("    MuiTextField: {\n");
        sb.append("      defaultProps: { variant: 'outlined', size: 'medium' },\n");
        sb.append("    },\n");
        sb.append("    MuiTableHead: {\n");
        sb.append("      styleOverrides: { root: { '& .MuiTableCell-root': { fontWeight: 600, backgroundColor: '#f8fafc', borderBottom: '2px solid #e2e8f0' } } },\n");
        sb.append("    },\n");
        sb.append("    MuiTableRow: {\n");
        sb.append("      styleOverrides: { root: { '&:hover': { backgroundColor: '#f8fafc' }, '&:last-child td': { border: 0 } } },\n");
        sb.append("    },\n");
        sb.append("    MuiChip: {\n");
        sb.append("      styleOverrides: { root: { fontWeight: 500 } },\n");
        sb.append("    },\n");
        sb.append("  },\n");
        sb.append("});\n\n");
        sb.append("export default theme;\n");
        writeTs(sb.toString(), new File(frontendDir, "theme.ts"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Shared Components
    // ═══════════════════════════════════════════════════════════════════════

    private void generateSharedComponents(File frontendDir) throws MojoExecutionException {
        generatePageHeader(frontendDir);
        generateConfirmDialog(frontendDir);
        generateEmptyState(frontendDir);
        generateEntitySelect(frontendDir);
        generateFormTextField(frontendDir);
        generateStatusChip(frontendDir);
        generateLoadingOverlay(frontendDir);
        generateNotificationProvider(frontendDir);
    }

    private void generatePageHeader(File frontendDir) throws MojoExecutionException {
        StringBuilder sb = new StringBuilder();
        sb.append("import { Box, Button, Stack, Typography } from '@mui/material';\n");
        sb.append("import { Add } from '@mui/icons-material';\n");
        sb.append("import { ReactNode } from 'react';\n\n");
        sb.append("interface PageHeaderProps {\n");
        sb.append("  title: string;\n");
        sb.append("  subtitle?: string;\n");
        sb.append("  actionLabel?: string;\n");
        sb.append("  onAction?: () => void;\n");
        sb.append("  actionIcon?: ReactNode;\n");
        sb.append("}\n\n");
        sb.append("export default function PageHeader({ title, subtitle, actionLabel, onAction, actionIcon }: PageHeaderProps) {\n");
        sb.append("  return (\n");
        sb.append("    <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent=\"space-between\" alignItems={{ xs: 'flex-start', sm: 'center' }} spacing={2} mb={3}>\n");
        sb.append("      <Box>\n");
        sb.append("        <Typography variant=\"h4\" sx={{ fontSize: { xs: '1.5rem', sm: '2.125rem' } }}>{title}</Typography>\n");
        sb.append("        {subtitle && <Typography variant=\"body2\" color=\"text.secondary\" mt={0.5}>{subtitle}</Typography>}\n");
        sb.append("      </Box>\n");
        sb.append("      {actionLabel && onAction && (\n");
        sb.append("        <Button variant=\"contained\" size=\"large\" startIcon={actionIcon ?? <Add />} onClick={onAction}>\n");
        sb.append("          {actionLabel}\n");
        sb.append("        </Button>\n");
        sb.append("      )}\n");
        sb.append("    </Stack>\n");
        sb.append("  );\n");
        sb.append("}\n");
        writeTs(sb.toString(), new File(frontendDir, "components/shared/PageHeader.tsx"));
    }

    private void generateConfirmDialog(File frontendDir) throws MojoExecutionException {
        StringBuilder sb = new StringBuilder();
        sb.append("import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle } from '@mui/material';\n\n");
        sb.append("interface ConfirmDialogProps {\n");
        sb.append("  open: boolean;\n");
        sb.append("  title?: string;\n");
        sb.append("  message: string;\n");
        sb.append("  confirmLabel?: string;\n");
        sb.append("  cancelLabel?: string;\n");
        sb.append("  onConfirm: () => void;\n");
        sb.append("  onCancel: () => void;\n");
        sb.append("}\n\n");
        sb.append("export default function ConfirmDialog({ open, title = 'Confirmar', message, confirmLabel = 'Confirmar', cancelLabel = 'Cancelar', onConfirm, onCancel }: ConfirmDialogProps) {\n");
        sb.append("  return (\n");
        sb.append("    <Dialog open={open} onClose={onCancel} PaperProps={{ sx: { borderRadius: 3, p: 1 } }}>\n");
        sb.append("      <DialogTitle fontWeight={600}>{title}</DialogTitle>\n");
        sb.append("      <DialogContent><DialogContentText>{message}</DialogContentText></DialogContent>\n");
        sb.append("      <DialogActions sx={{ px: 3, pb: 2 }}>\n");
        sb.append("        <Button onClick={onCancel} color=\"inherit\">{cancelLabel}</Button>\n");
        sb.append("        <Button onClick={onConfirm} variant=\"contained\" color=\"error\">{confirmLabel}</Button>\n");
        sb.append("      </DialogActions>\n");
        sb.append("    </Dialog>\n");
        sb.append("  );\n");
        sb.append("}\n");
        writeTs(sb.toString(), new File(frontendDir, "components/shared/ConfirmDialog.tsx"));
    }

    private void generateEmptyState(File frontendDir) throws MojoExecutionException {
        StringBuilder sb = new StringBuilder();
        sb.append("import { Box, Paper, Typography, Button } from '@mui/material';\n");
        sb.append("import { Inbox, Add } from '@mui/icons-material';\n\n");
        sb.append("interface EmptyStateProps {\n");
        sb.append("  title?: string;\n");
        sb.append("  description?: string;\n");
        sb.append("  actionLabel?: string;\n");
        sb.append("  onAction?: () => void;\n");
        sb.append("}\n\n");
        sb.append("export default function EmptyState({ title = 'Nenhum registro encontrado', description = 'Comece criando um novo registro.', actionLabel, onAction }: EmptyStateProps) {\n");
        sb.append("  return (\n");
        sb.append("    <Paper sx={{ p: 6, textAlign: 'center', borderRadius: 3 }}>\n");
        sb.append("      <Inbox sx={{ fontSize: 64, color: 'text.disabled', mb: 2 }} />\n");
        sb.append("      <Typography variant=\"h6\" color=\"text.secondary\" gutterBottom>{title}</Typography>\n");
        sb.append("      <Typography variant=\"body2\" color=\"text.disabled\" mb={3}>{description}</Typography>\n");
        sb.append("      {actionLabel && onAction && (\n");
        sb.append("        <Button variant=\"contained\" startIcon={<Add />} onClick={onAction}>{actionLabel}</Button>\n");
        sb.append("      )}\n");
        sb.append("    </Paper>\n");
        sb.append("  );\n");
        sb.append("}\n");
        writeTs(sb.toString(), new File(frontendDir, "components/shared/EmptyState.tsx"));
    }

    private void generateEntitySelect(File frontendDir) throws MojoExecutionException {
        StringBuilder sb = new StringBuilder();
        sb.append("import { useEffect, useState } from 'react';\n");
        sb.append("import { Autocomplete, TextField, CircularProgress, Box, Typography } from '@mui/material';\n");
        sb.append("import axios from 'axios';\n\n");
        sb.append("interface EntityOption {\n");
        sb.append("  id: number;\n");
        sb.append("  label: string;\n");
        sb.append("}\n\n");
        sb.append("interface EntitySelectProps {\n");
        sb.append("  label: string;\n");
        sb.append("  apiPath: string;\n");
        sb.append("  value: number | null;\n");
        sb.append("  onChange: (id: number | null) => void;\n");
        sb.append("  labelField?: string;\n");
        sb.append("  required?: boolean;\n");
        sb.append("  disabled?: boolean;\n");
        sb.append("}\n\n");
        sb.append("export default function EntitySelect({ label, apiPath, value, onChange, labelField = 'name', required = false, disabled = false }: EntitySelectProps) {\n");
        sb.append("  const [options, setOptions] = useState<EntityOption[]>([]);\n");
        sb.append("  const [loading, setLoading] = useState(false);\n");
        sb.append("  const [inputValue, setInputValue] = useState('');\n\n");
        sb.append("  useEffect(() => {\n");
        sb.append("    let active = true;\n");
        sb.append("    setLoading(true);\n");
        sb.append("    axios.get(apiPath, { params: { size: 100 } })\n");
        sb.append("      .then((res) => {\n");
        sb.append("        if (!active) return;\n");
        sb.append("        const data = res.data.content ?? res.data;\n");
        sb.append("        const mapped = (Array.isArray(data) ? data : []).map((item: Record<string, unknown>) => ({\n");
        sb.append("          id: item.id as number,\n");
        sb.append("          label: String(item[labelField] ?? item['name'] ?? `#${item.id}`),\n");
        sb.append("        }));\n");
        sb.append("        setOptions(mapped);\n");
        sb.append("      })\n");
        sb.append("      .catch(() => setOptions([]))\n");
        sb.append("      .finally(() => setLoading(false));\n");
        sb.append("    return () => { active = false; };\n");
        sb.append("  }, [apiPath, labelField]);\n\n");
        sb.append("  const selected = options.find((o) => o.id === value) ?? null;\n\n");
        sb.append("  return (\n");
        sb.append("    <Autocomplete\n");
        sb.append("      options={options}\n");
        sb.append("      value={selected}\n");
        sb.append("      onChange={(_, newVal) => onChange(newVal?.id ?? null)}\n");
        sb.append("      inputValue={inputValue}\n");
        sb.append("      onInputChange={(_, newInput) => setInputValue(newInput)}\n");
        sb.append("      getOptionLabel={(opt) => opt.label}\n");
        sb.append("      isOptionEqualToValue={(opt, val) => opt.id === val.id}\n");
        sb.append("      loading={loading}\n");
        sb.append("      disabled={disabled}\n");
        sb.append("      noOptionsText=\"Nenhum resultado\"\n");
        sb.append("      loadingText=\"Carregando...\"\n");
        sb.append("      renderOption={(props, option) => (\n");
        sb.append("        <Box component=\"li\" {...props} key={option.id}>\n");
        sb.append("          <Typography variant=\"body2\">{option.label}</Typography>\n");
        sb.append("        </Box>\n");
        sb.append("      )}\n");
        sb.append("      renderInput={(params) => (\n");
        sb.append("        <TextField\n");
        sb.append("          {...params}\n");
        sb.append("          label={label}\n");
        sb.append("          required={required}\n");
        sb.append("          margin=\"normal\"\n");
        sb.append("          placeholder={`Selecione ${label.toLowerCase()}...`}\n");
        sb.append("          InputProps={{\n");
        sb.append("            ...params.InputProps,\n");
        sb.append("            endAdornment: (\n");
        sb.append("              <>\n");
        sb.append("                {loading ? <CircularProgress color=\"inherit\" size={20} /> : null}\n");
        sb.append("                {params.InputProps.endAdornment}\n");
        sb.append("              </>\n");
        sb.append("            ),\n");
        sb.append("          }}\n");
        sb.append("        />\n");
        sb.append("      )}\n");
        sb.append("    />\n");
        sb.append("  );\n");
        sb.append("}\n");
        writeTs(sb.toString(), new File(frontendDir, "components/shared/EntitySelect.tsx"));
    }

    private void generateFormTextField(File frontendDir) throws MojoExecutionException {
        StringBuilder sb = new StringBuilder();
        sb.append("import { TextField, MenuItem } from '@mui/material';\n\n");
        sb.append("interface FormTextFieldProps {\n");
        sb.append("  name: string;\n");
        sb.append("  label: string;\n");
        sb.append("  value: unknown;\n");
        sb.append("  onChange: (e: React.ChangeEvent<HTMLInputElement>) => void;\n");
        sb.append("  required?: boolean;\n");
        sb.append("  type?: string;\n");
        sb.append("  multiline?: boolean;\n");
        sb.append("  rows?: number;\n");
        sb.append("  options?: { value: string; label: string }[];\n");
        sb.append("}\n\n");
        sb.append("export default function FormTextField({ name, label, value, onChange, required, type = 'text', multiline, rows, options }: FormTextFieldProps) {\n");
        sb.append("  if (options && options.length > 0) {\n");
        sb.append("    return (\n");
        sb.append("      <TextField\n");
        sb.append("        select fullWidth margin=\"normal\" label={label} name={name}\n");
        sb.append("        value={String(value ?? '')} onChange={onChange} required={required}\n");
        sb.append("      >\n");
        sb.append("        <MenuItem value=\"\"><em>Selecione...</em></MenuItem>\n");
        sb.append("        {options.map((opt) => <MenuItem key={opt.value} value={opt.value}>{opt.label}</MenuItem>)}\n");
        sb.append("      </TextField>\n");
        sb.append("    );\n");
        sb.append("  }\n");
        sb.append("  return (\n");
        sb.append("    <TextField\n");
        sb.append("      fullWidth margin=\"normal\" label={label} name={name}\n");
        sb.append("      value={String(value ?? '')} onChange={onChange}\n");
        sb.append("      required={required} type={type} multiline={multiline} rows={rows}\n");
        sb.append("    />\n");
        sb.append("  );\n");
        sb.append("}\n");
        writeTs(sb.toString(), new File(frontendDir, "components/shared/FormTextField.tsx"));
    }

    private void generateStatusChip(File frontendDir) throws MojoExecutionException {
        StringBuilder sb = new StringBuilder();
        sb.append("import { Chip } from '@mui/material';\n\n");
        sb.append("const colorMap: Record<string, 'success' | 'error' | 'warning' | 'info' | 'default'> = {\n");
        sb.append("  ACTIVE: 'success', INACTIVE: 'error', PENDING: 'warning',\n");
        sb.append("  CONFIRMED: 'info', CANCELLED: 'error', SHIPPED: 'info',\n");
        sb.append("  DELIVERED: 'success', OUT_OF_STOCK: 'warning',\n");
        sb.append("};\n\n");
        sb.append("interface StatusChipProps {\n");
        sb.append("  value: string | null | undefined;\n");
        sb.append("}\n\n");
        sb.append("export default function StatusChip({ value }: StatusChipProps) {\n");
        sb.append("  if (!value) return null;\n");
        sb.append("  const color = colorMap[value.toUpperCase()] ?? 'default';\n");
        sb.append("  return <Chip label={value} color={color} size=\"small\" variant=\"outlined\" />;\n");
        sb.append("}\n");
        writeTs(sb.toString(), new File(frontendDir, "components/shared/StatusChip.tsx"));
    }

    private void generateLoadingOverlay(File frontendDir) throws MojoExecutionException {
        StringBuilder sb = new StringBuilder();
        sb.append("import { Backdrop, CircularProgress, Typography, Stack } from '@mui/material';\n\n");
        sb.append("interface LoadingOverlayProps {\n");
        sb.append("  open: boolean;\n");
        sb.append("  message?: string;\n");
        sb.append("}\n\n");
        sb.append("export default function LoadingOverlay({ open, message = 'Carregando...' }: LoadingOverlayProps) {\n");
        sb.append("  return (\n");
        sb.append("    <Backdrop open={open} sx={{ zIndex: 9999, color: '#fff', flexDirection: 'column' }}>\n");
        sb.append("      <Stack alignItems=\"center\" spacing={2}>\n");
        sb.append("        <CircularProgress color=\"inherit\" size={48} />\n");
        sb.append("        <Typography variant=\"body1\" fontWeight={500}>{message}</Typography>\n");
        sb.append("      </Stack>\n");
        sb.append("    </Backdrop>\n");
        sb.append("  );\n");
        sb.append("}\n");
        writeTs(sb.toString(), new File(frontendDir, "components/shared/LoadingOverlay.tsx"));
    }

    private void generateNotificationProvider(File frontendDir) throws MojoExecutionException {
        StringBuilder sb = new StringBuilder();
        sb.append("import { createContext, useContext, useState, useCallback, ReactNode } from 'react';\n");
        sb.append("import { Snackbar, Alert, AlertColor } from '@mui/material';\n\n");
        sb.append("interface Notification {\n");
        sb.append("  message: string;\n");
        sb.append("  severity: AlertColor;\n");
        sb.append("}\n\n");
        sb.append("interface NotificationContextType {\n");
        sb.append("  notify: (message: string, severity?: AlertColor) => void;\n");
        sb.append("}\n\n");
        sb.append("const NotificationContext = createContext<NotificationContextType>({ notify: () => {} });\n\n");
        sb.append("export const useNotification = () => useContext(NotificationContext);\n\n");
        sb.append("export default function NotificationProvider({ children }: { children: ReactNode }) {\n");
        sb.append("  const [notification, setNotification] = useState<Notification | null>(null);\n\n");
        sb.append("  const notify = useCallback((message: string, severity: AlertColor = 'success') => {\n");
        sb.append("    setNotification({ message, severity });\n");
        sb.append("  }, []);\n\n");
        sb.append("  return (\n");
        sb.append("    <NotificationContext.Provider value={{ notify }}>\n");
        sb.append("      {children}\n");
        sb.append("      <Snackbar\n");
        sb.append("        open={notification != null}\n");
        sb.append("        autoHideDuration={4000}\n");
        sb.append("        onClose={() => setNotification(null)}\n");
        sb.append("        anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}\n");
        sb.append("      >\n");
        sb.append("        <Alert\n");
        sb.append("          severity={notification?.severity ?? 'info'}\n");
        sb.append("          variant=\"filled\"\n");
        sb.append("          onClose={() => setNotification(null)}\n");
        sb.append("          sx={{ borderRadius: 2, minWidth: 300 }}\n");
        sb.append("        >\n");
        sb.append("          {notification?.message}\n");
        sb.append("        </Alert>\n");
        sb.append("      </Snackbar>\n");
        sb.append("    </NotificationContext.Provider>\n");
        sb.append("  );\n");
        sb.append("}\n");
        writeTs(sb.toString(), new File(frontendDir, "components/shared/NotificationProvider.tsx"));
    }


    // ═══════════════════════════════════════════════════════════════════════
    // Redux Slice
    // ═══════════════════════════════════════════════════════════════════════

    private void generateSlice(ForgeDefinition def, EntityDefinition entity, File frontendDir) throws MojoExecutionException {
        String name = entity.getName();
        String camel = NamingUtils.toCamelCase(name);
        String plural = NamingUtils.toPlural(camel);
        String apiPath = entity.getApiPath() != null ? entity.getApiPath()
                : "/api/v1/" + NamingUtils.toPlural(NamingUtils.toSnakeCase(name)).replace("_", "-");

        StringBuilder sb = new StringBuilder();
        sb.append("import { createSlice, createAsyncThunk } from '@reduxjs/toolkit';\n");
        sb.append("import axios from 'axios';\n\n");

        // Interface
        sb.append("export interface ").append(name).append(" {\n");
        sb.append("  id?: number;\n");
        for (FieldDefinition f : entity.getFields()) {
            if (f.isInResponse()) {
                sb.append("  ").append(f.getName()).append("?: ").append(tsType(f)).append(";\n");
            }
        }
        // Relation fields in response
        for (RelationDefinition r : entity.getRelations()) {
            if (r.isInResponse() && "ManyToOne".equals(r.getType())) {
                sb.append("  ").append(r.getFieldName()).append("?: { id?: number; [key: string]: unknown };\n");
                sb.append("  ").append(r.getFieldName()).append("Id?: number;\n");
            }
        }
        sb.append("}\n\n");

        // State
        sb.append("interface ").append(name).append("State {\n");
        sb.append("  items: ").append(name).append("[];\n");
        sb.append("  totalElements: number;\n");
        sb.append("  totalPages: number;\n");
        sb.append("  current: ").append(name).append(" | null;\n");
        sb.append("  loading: boolean;\n");
        sb.append("  saving: boolean;\n");
        sb.append("  error: string | null;\n");
        sb.append("}\n\n");

        sb.append("const initialState: ").append(name).append("State = {\n");
        sb.append("  items: [],\n");
        sb.append("  totalElements: 0,\n");
        sb.append("  totalPages: 0,\n");
        sb.append("  current: null,\n");
        sb.append("  loading: false,\n");
        sb.append("  saving: false,\n");
        sb.append("  error: null,\n");
        sb.append("};\n\n");

        String pluralPascal = NamingUtils.toPascalCase(plural);

        // Thunks
        sb.append("export const fetch").append(pluralPascal).append(" = createAsyncThunk(\n");
        sb.append("  '").append(camel).append("/fetchAll',\n");
        sb.append("  async (params?: { page?: number; size?: number }) => {\n");
        sb.append("    const res = await axios.get('").append(apiPath).append("', { params });\n");
        sb.append("    return res.data;\n");
        sb.append("  }\n");
        sb.append(");\n\n");

        sb.append("export const fetch").append(name).append("ById = createAsyncThunk(\n");
        sb.append("  '").append(camel).append("/fetchById',\n");
        sb.append("  async (id: number) => {\n");
        sb.append("    const res = await axios.get(`").append(apiPath).append("/${id}`);\n");
        sb.append("    return res.data;\n");
        sb.append("  }\n");
        sb.append(");\n\n");

        sb.append("export const create").append(name).append(" = createAsyncThunk(\n");
        sb.append("  '").append(camel).append("/create',\n");
        sb.append("  async (data: Partial<").append(name).append(">) => {\n");
        sb.append("    const res = await axios.post('").append(apiPath).append("', data);\n");
        sb.append("    return res.data;\n");
        sb.append("  }\n");
        sb.append(");\n\n");

        sb.append("export const update").append(name).append(" = createAsyncThunk(\n");
        sb.append("  '").append(camel).append("/update',\n");
        sb.append("  async ({ id, data }: { id: number; data: Partial<").append(name).append("> }) => {\n");
        sb.append("    const res = await axios.put(`").append(apiPath).append("/${id}`, data);\n");
        sb.append("    return res.data;\n");
        sb.append("  }\n");
        sb.append(");\n\n");

        sb.append("export const delete").append(name).append(" = createAsyncThunk(\n");
        sb.append("  '").append(camel).append("/delete',\n");
        sb.append("  async (id: number) => {\n");
        sb.append("    await axios.delete(`").append(apiPath).append("/${id}`);\n");
        sb.append("    return id;\n");
        sb.append("  }\n");
        sb.append(");\n\n");

        // Slice
        sb.append("const ").append(camel).append("Slice = createSlice({\n");
        sb.append("  name: '").append(camel).append("',\n");
        sb.append("  initialState,\n");
        sb.append("  reducers: {\n");
        sb.append("    clearCurrent(state) { state.current = null; },\n");
        sb.append("    clearError(state) { state.error = null; },\n");
        sb.append("    searchFulfilled(state, action) {\n");
        sb.append("      state.loading = false;\n");
        sb.append("      state.items = action.payload.content ?? action.payload;\n");
        sb.append("      state.totalElements = action.payload.totalElements ?? 0;\n");
        sb.append("      state.totalPages = action.payload.totalPages ?? 0;\n");
        sb.append("    },\n");
        sb.append("  },\n");
        sb.append("  extraReducers: (builder) => {\n");
        sb.append("    builder\n");
        sb.append("      .addCase(fetch").append(pluralPascal).append(".pending, (state) => { state.loading = true; state.error = null; })\n");
        sb.append("      .addCase(fetch").append(pluralPascal).append(".fulfilled, (state, action) => {\n");
        sb.append("        state.loading = false;\n");
        sb.append("        state.items = action.payload.content ?? action.payload;\n");
        sb.append("        state.totalElements = action.payload.totalElements ?? 0;\n");
        sb.append("        state.totalPages = action.payload.totalPages ?? 0;\n");
        sb.append("      })\n");
        sb.append("      .addCase(fetch").append(pluralPascal).append(".rejected, (state, action) => { state.loading = false; state.error = action.error.message ?? 'Erro ao carregar'; })\n");
        sb.append("      .addCase(fetch").append(name).append("ById.fulfilled, (state, action) => { state.current = action.payload; })\n");
        sb.append("      .addCase(create").append(name).append(".pending, (state) => { state.saving = true; })\n");
        sb.append("      .addCase(create").append(name).append(".fulfilled, (state) => { state.saving = false; state.totalElements += 1; })\n");
        sb.append("      .addCase(create").append(name).append(".rejected, (state, action) => { state.saving = false; state.error = action.error.message ?? 'Erro ao salvar'; })\n");
        sb.append("      .addCase(update").append(name).append(".pending, (state) => { state.saving = true; })\n");
        sb.append("      .addCase(update").append(name).append(".fulfilled, (state) => { state.saving = false; })\n");
        sb.append("      .addCase(update").append(name).append(".rejected, (state, action) => { state.saving = false; state.error = action.error.message ?? 'Erro ao atualizar'; })\n");
        sb.append("      .addCase(delete").append(name).append(".fulfilled, (state, action) => {\n");
        sb.append("        state.items = state.items.filter(i => i.id !== action.payload);\n");
        sb.append("        state.totalElements = Math.max(0, state.totalElements - 1);\n");
        sb.append("      });\n");
        sb.append("  },\n");
        sb.append("});\n\n");

        sb.append("export const { clearCurrent, clearError, searchFulfilled } = ").append(camel).append("Slice.actions;\n");
        sb.append("export default ").append(camel).append("Slice.reducer;\n");

        writeTs(sb.toString(), new File(frontendDir, "store/slices/" + camel + "Slice.ts"));
    }


    // ═══════════════════════════════════════════════════════════════════════
    // List Page
    // ═══════════════════════════════════════════════════════════════════════

    private void generateListPage(ForgeDefinition def, EntityDefinition entity, File frontendDir) throws MojoExecutionException {
        String name = entity.getName();
        String camel = NamingUtils.toCamelCase(name);
        String plural = NamingUtils.toPlural(camel);
        String pluralPascal = NamingUtils.toPascalCase(plural);
        String apiPath = entity.getApiPath() != null ? entity.getApiPath()
                : "/api/v1/" + NamingUtils.toPlural(NamingUtils.toSnakeCase(name)).replace("_", "-");

        List<FieldDefinition> responseFields = entity.getFields().stream()
                .filter(FieldDefinition::isInResponse).toList();

        boolean hasFilters = entity.hasFilters();
        boolean hasExportImport = entity.getExportImport() != null && entity.getExportImport().isEnabled();
        List<ActionDefinition> httpActions = entity.getActions().stream()
                .filter(a -> a.getHttpMethod() != null && a.isRequiresId()).toList();

        StringBuilder sb = new StringBuilder();
        sb.append("import { useEffect, useState } from 'react';\n");
        sb.append("import { useNavigate, useLocation } from 'react-router-dom';\n");
        sb.append("import { useAppDispatch, useAppSelector } from '../../store/hooks';\n");
        if (hasFilters) {
            sb.append("import { fetch").append(pluralPascal).append(", delete").append(name).append(", searchFulfilled } from '../../store/slices/").append(camel).append("Slice';\n");
        } else {
            sb.append("import { fetch").append(pluralPascal).append(", delete").append(name).append(" } from '../../store/slices/").append(camel).append("Slice';\n");
        }
        sb.append("import {\n");
        sb.append("  Box, Button, Chip, IconButton, Menu, MenuItem, Paper, Stack, Table, TableBody,\n");
        sb.append("  TableCell, TableContainer, TableHead, TableRow,\n");
        sb.append("  Tooltip, Snackbar, Alert, Fade, Skeleton, TablePagination,\n");
        sb.append("} from '@mui/material';\n");
        sb.append("import { Edit, Delete, Visibility, MoreVert");
        if (hasExportImport) {
            sb.append(", FileDownload, FileUpload");
        }
        sb.append(" } from '@mui/icons-material';\n");
        sb.append("import axios from 'axios';\n");
        sb.append("import PageHeader from '../../components/shared/PageHeader';\n");
        sb.append("import ConfirmDialog from '../../components/shared/ConfirmDialog';\n");
        sb.append("import EmptyState from '../../components/shared/EmptyState';\n");
        sb.append("import StatusChip from '../../components/shared/StatusChip';\n");
        if (hasFilters) {
            sb.append("import ").append(name).append("FilterPanel from '../../components/").append(camel).append("/").append(name).append("FilterPanel';\n");
        }
        sb.append("\n");

        sb.append("export default function List").append(name).append("Page() {\n");
        sb.append("  const dispatch = useAppDispatch();\n");
        sb.append("  const navigate = useNavigate();\n");
        sb.append("  const location = useLocation();\n");
        sb.append("  const { items, loading, totalElements } = useAppSelector((s) => s.").append(camel).append(");\n");
        sb.append("  const [page, setPage] = useState(0);\n");
        sb.append("  const [rowsPerPage, setRowsPerPage] = useState(10);\n");
        sb.append("  const [deleteId, setDeleteId] = useState<number | null>(null);\n");
        sb.append("  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: 'success' | 'error' }>({ open: false, message: '', severity: 'success' });\n");
        if (!httpActions.isEmpty()) {
            sb.append("  const [actionMenuAnchor, setActionMenuAnchor] = useState<null | HTMLElement>(null);\n");
            sb.append("  const [actionMenuId, setActionMenuId] = useState<number | null>(null);\n");
        }
        sb.append("\n");

        sb.append("  const handleDelete = async () => {\n");
        sb.append("    if (deleteId == null) return;\n");
        sb.append("    try {\n");
        sb.append("      await dispatch(delete").append(name).append("(deleteId)).unwrap();\n");
        sb.append("      setSnackbar({ open: true, message: 'Registro excluído com sucesso!', severity: 'success' });\n");
        sb.append("    } catch {\n");
        sb.append("      setSnackbar({ open: true, message: 'Erro ao excluir registro.', severity: 'error' });\n");
        sb.append("    }\n");
        sb.append("    setDeleteId(null);\n");
        sb.append("  };\n\n");

        // Filter search handler
        if (hasFilters) {
            sb.append("  const [filterActive, setFilterActive] = useState<Record<string, unknown> | null>(null);\n\n");
            sb.append("  const handleSearch = (filter: Record<string, unknown>) => {\n");
            sb.append("    setFilterActive(Object.keys(filter).length > 0 ? filter : null);\n");
            sb.append("    setPage(0);\n");
            sb.append("  };\n\n");
        }

        // Single useEffect for data fetching
        sb.append("  useEffect(() => {\n");
        if (hasFilters) {
            sb.append("    if (filterActive) {\n");
            sb.append("      axios.post('").append(apiPath).append("/search', filterActive, { params: { page, size: rowsPerPage } })\n");
            sb.append("        .then(res => dispatch(searchFulfilled(res.data))).catch(() => {});\n");
            sb.append("    } else {\n");
            sb.append("      dispatch(fetch").append(pluralPascal).append("({ page, size: rowsPerPage }));\n");
            sb.append("    }\n");
            sb.append("  }, [dispatch, filterActive, page, rowsPerPage, location.key]);\n\n");
        } else {
            sb.append("    dispatch(fetch").append(pluralPascal).append("({ page, size: rowsPerPage }));\n");
            sb.append("  }, [dispatch, page, rowsPerPage, location.key]);\n\n");
        }

        // Export handlers
        if (hasExportImport) {
            List<String> formats = entity.getExportImport().getFormats();
            for (String fmt : formats) {
                sb.append("  const handleExport").append(fmt.substring(0,1).toUpperCase()).append(fmt.substring(1)).append(" = () => {\n");
                sb.append("    window.open('").append(apiPath).append("/export/").append(fmt).append("', '_blank');\n");
                sb.append("  };\n\n");

                sb.append("  const handleImport").append(fmt.substring(0,1).toUpperCase()).append(fmt.substring(1)).append(" = async (file: File) => {\n");
                sb.append("    const formData = new FormData();\n");
                sb.append("    formData.append('file', file);\n");
                sb.append("    try {\n");
                sb.append("      const res = await axios.post('").append(apiPath).append("/import/").append(fmt).append("', formData, { headers: { 'Content-Type': 'multipart/form-data' } });\n");
                sb.append("      setSnackbar({ open: true, message: res.data.message ?? 'Importado com sucesso!', severity: 'success' });\n");
                sb.append("      dispatch(fetch").append(pluralPascal).append("({ page, size: rowsPerPage }));\n");
                sb.append("    } catch {\n");
                sb.append("      setSnackbar({ open: true, message: 'Erro ao importar arquivo.', severity: 'error' });\n");
                sb.append("    }\n");
                sb.append("  };\n\n");
            }
        }

        // Action execution handlers
        if (!httpActions.isEmpty()) {
            sb.append("  const handleActionClick = (event: React.MouseEvent<HTMLElement>, id: number) => {\n");
            sb.append("    setActionMenuAnchor(event.currentTarget);\n");
            sb.append("    setActionMenuId(id);\n");
            sb.append("  };\n\n");
            sb.append("  const handleActionClose = () => {\n");
            sb.append("    setActionMenuAnchor(null);\n");
            sb.append("    setActionMenuId(null);\n");
            sb.append("  };\n\n");

            sb.append("  const executeAction = async (actionPath: string, method: string) => {\n");
            sb.append("    if (actionMenuId == null) return;\n");
            sb.append("    try {\n");
            sb.append("      const url = `").append(apiPath).append("${actionPath.replace('{id}', String(actionMenuId))}`;\n");
            sb.append("      await axios({ method, url });\n");
            sb.append("      setSnackbar({ open: true, message: 'Ação executada com sucesso!', severity: 'success' });\n");
            sb.append("      dispatch(fetch").append(pluralPascal).append("({ page, size: rowsPerPage }));\n");
            sb.append("    } catch {\n");
            sb.append("      setSnackbar({ open: true, message: 'Erro ao executar ação.', severity: 'error' });\n");
            sb.append("    }\n");
            sb.append("    handleActionClose();\n");
            sb.append("  };\n\n");
        }

        sb.append("  return (\n");
        sb.append("    <Fade in>\n");
        sb.append("      <Box>\n");
        sb.append("        <PageHeader\n");
        sb.append("          title=\"").append(name).append("\"\n");
        sb.append("          subtitle={`${totalElements} registro(s)`}\n");
        sb.append("          actionLabel=\"Novo ").append(name).append("\"\n");
        sb.append("          onAction={() => navigate('/").append(plural).append("/new')}\n");
        sb.append("        />\n\n");

        // Export/Import toolbar
        if (hasExportImport) {
            List<String> formats = entity.getExportImport().getFormats();
            sb.append("        <Paper variant=\"outlined\" sx={{ p: 1.5, mb: 2, borderRadius: 2, display: 'flex', gap: 1, flexWrap: 'wrap', alignItems: 'center' }}>\n");
            for (String fmt : formats) {
                String fmtLabel = fmt.substring(0,1).toUpperCase() + fmt.substring(1);
                String fmtUpper = fmt.toUpperCase();
                sb.append("          <Button size=\"small\" startIcon={<FileDownload />} onClick={handleExport").append(fmtLabel).append("}>").append(fmtUpper).append("</Button>\n");
                sb.append("          <Button size=\"small\" color=\"secondary\" startIcon={<FileUpload />} component=\"label\">Importar ").append(fmtUpper).append("<input type=\"file\" hidden accept=\"").append("csv".equals(fmt) ? ".csv" : ".xlsx,.xls").append("\" onChange={(e) => { if (e.target.files?.[0]) handleImport").append(fmtLabel).append("(e.target.files[0]); e.target.value = ''; }} /></Button>\n");
            }
            sb.append("        </Paper>\n\n");
        }

        // Filter panel
        if (hasFilters) {
            sb.append("        <").append(name).append("FilterPanel onSearch={handleSearch} />\n\n");
        }

        sb.append("        {!loading && (!items || items.length === 0) ? (\n");
        sb.append("          <EmptyState actionLabel=\"Criar ").append(name).append("\" onAction={() => navigate('/").append(plural).append("/new')} />\n");
        sb.append("        ) : (\n");
        sb.append("        <TableContainer component={Paper} sx={{ borderRadius: 3, boxShadow: '0 4px 20px rgba(25, 118, 210, 0.08)', overflowX: 'auto' }}>\n");
        sb.append("          <Table sx={{ minWidth: 650 }}>\n");
        sb.append("            <TableHead>\n");
        sb.append("              <TableRow>\n");
        sb.append("                <TableCell>ID</TableCell>\n");
        for (FieldDefinition f : responseFields) {
            sb.append("                <TableCell>").append(NamingUtils.toHumanLabel(f.getName())).append("</TableCell>\n");
        }
        for (RelationDefinition r : entity.getRelations()) {
            if (r.isInResponse() && "ManyToOne".equals(r.getType())) {
                sb.append("                <TableCell>").append(NamingUtils.toHumanLabel(r.getFieldName())).append("</TableCell>\n");
            }
        }
        sb.append("                <TableCell align=\"center\">Ações</TableCell>\n");
        sb.append("              </TableRow>\n");
        sb.append("            </TableHead>\n");
        sb.append("            <TableBody>\n");
        sb.append("              {loading ? (\n");
        sb.append("                Array.from({ length: 5 }).map((_, i) => (\n");
        sb.append("                  <TableRow key={i}>\n");
        int colCount = 2 + responseFields.size() + (int) entity.getRelations().stream().filter(r -> r.isInResponse() && "ManyToOne".equals(r.getType())).count();
        sb.append("                    {Array.from({ length: ").append(colCount).append(" }).map((_, j) => <TableCell key={j}><Skeleton /></TableCell>)}\n");
        sb.append("                  </TableRow>\n");
        sb.append("                ))\n");
        sb.append("              ) : (\n");
        sb.append("                items.map((item) => (\n");
        sb.append("                  <TableRow key={item.id}>\n");
        sb.append("                    <TableCell><Chip label={`#${item.id}`} size=\"small\" variant=\"outlined\" /></TableCell>\n");
        for (FieldDefinition f : responseFields) {
            if ("Enum".equalsIgnoreCase(f.getType())) {
                sb.append("                    <TableCell><StatusChip value={item.").append(f.getName()).append(" as unknown as string} /></TableCell>\n");
            } else {
                sb.append("                    <TableCell sx={{ whiteSpace: 'nowrap' }}>{String(item.").append(f.getName()).append(" ?? '-')}</TableCell>\n");
            }
        }
        for (RelationDefinition r : entity.getRelations()) {
            if (r.isInResponse() && "ManyToOne".equals(r.getType())) {
                sb.append("                    <TableCell>{(item.").append(r.getFieldName()).append(" as Record<string, unknown>)?.name as string ?? '-'}</TableCell>\n");
            }
        }
        sb.append("                    <TableCell align=\"center\">\n");
        sb.append("                      <Stack direction=\"row\" spacing={0} justifyContent=\"center\" flexWrap=\"nowrap\">\n");
        sb.append("                        <Tooltip title=\"Visualizar\">\n");
        sb.append("                          <IconButton size=\"small\" color=\"info\" onClick={() => navigate(`/").append(plural).append("/${item.id}/view`)}><Visibility fontSize=\"small\" /></IconButton>\n");
        sb.append("                        </Tooltip>\n");
        sb.append("                        <Tooltip title=\"Editar\">\n");
        sb.append("                          <IconButton size=\"small\" color=\"primary\" onClick={() => navigate(`/").append(plural).append("/${item.id}`)}><Edit fontSize=\"small\" /></IconButton>\n");
        sb.append("                        </Tooltip>\n");
        sb.append("                        <Tooltip title=\"Excluir\">\n");
        sb.append("                          <IconButton size=\"small\" color=\"error\" onClick={() => setDeleteId(item.id!)}><Delete fontSize=\"small\" /></IconButton>\n");
        sb.append("                        </Tooltip>\n");
        if (!httpActions.isEmpty()) {
            sb.append("                        <Tooltip title=\"Mais ações\">\n");
            sb.append("                          <IconButton size=\"small\" onClick={(e) => handleActionClick(e, item.id!)}><MoreVert fontSize=\"small\" /></IconButton>\n");
            sb.append("                        </Tooltip>\n");
        }
        sb.append("                      </Stack>\n");
        sb.append("                    </TableCell>\n");
        sb.append("                  </TableRow>\n");
        sb.append("                ))\n");
        sb.append("              )}\n");
        sb.append("            </TableBody>\n");
        sb.append("          </Table>\n");
        sb.append("          <TablePagination\n");
        sb.append("            component=\"div\" count={totalElements} page={page}\n");
        sb.append("            rowsPerPage={rowsPerPage}\n");
        sb.append("            onPageChange={(_, p) => setPage(p)}\n");
        sb.append("            onRowsPerPageChange={(e) => { setRowsPerPage(parseInt(e.target.value, 10)); setPage(0); }}\n");
        sb.append("            labelRowsPerPage=\"Por página\"\n");
        sb.append("          />\n");
        sb.append("        </TableContainer>\n");
        sb.append("        )}\n\n");

        // Actions menu
        if (!httpActions.isEmpty()) {
            sb.append("        <Menu anchorEl={actionMenuAnchor} open={Boolean(actionMenuAnchor)} onClose={handleActionClose}>\n");
            for (ActionDefinition a : httpActions) {
                String label = NamingUtils.toHumanLabel(a.getName());
                String path = a.getApiPath() != null ? a.getApiPath() : "/{id}/" + a.getName();
                sb.append("          <MenuItem onClick={() => executeAction('").append(path).append("', '").append(a.getHttpMethod().toLowerCase()).append("')}>").append(label).append("</MenuItem>\n");
            }
            sb.append("        </Menu>\n\n");
        }

        sb.append("        <ConfirmDialog\n");
        sb.append("          open={deleteId != null}\n");
        sb.append("          title=\"Excluir registro\"\n");
        sb.append("          message=\"Tem certeza que deseja excluir este registro? Esta ação não pode ser desfeita.\"\n");
        sb.append("          confirmLabel=\"Excluir\"\n");
        sb.append("          onConfirm={handleDelete}\n");
        sb.append("          onCancel={() => setDeleteId(null)}\n");
        sb.append("        />\n\n");

        sb.append("        <Snackbar open={snackbar.open} autoHideDuration={4000} onClose={() => setSnackbar({ ...snackbar, open: false })} anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}>\n");
        sb.append("          <Alert severity={snackbar.severity} variant=\"filled\" onClose={() => setSnackbar({ ...snackbar, open: false })}>{snackbar.message}</Alert>\n");
        sb.append("        </Snackbar>\n");
        sb.append("      </Box>\n");
        sb.append("    </Fade>\n");
        sb.append("  );\n");
        sb.append("}\n");

        writeTs(sb.toString(), new File(frontendDir, "pages/" + camel + "/List" + name + "Page.tsx"));
    }


    // ═══════════════════════════════════════════════════════════════════════
    // Form Page (with EntitySelect for relations)
    // ═══════════════════════════════════════════════════════════════════════

    private void generateFormPage(ForgeDefinition def, EntityDefinition entity, File frontendDir) throws MojoExecutionException {
        String name = entity.getName();
        String camel = NamingUtils.toCamelCase(name);
        String plural = NamingUtils.toPlural(camel);

        List<FieldDefinition> requestFields = entity.getFields().stream()
                .filter(FieldDefinition::isInRequest).toList();

        List<RelationDefinition> manyToOneRelations = entity.getRelations().stream()
                .filter(r -> "ManyToOne".equals(r.getType())).toList();

        StringBuilder sb = new StringBuilder();
        sb.append("import { useEffect, useState } from 'react';\n");
        sb.append("import { useNavigate, useParams } from 'react-router-dom';\n");
        sb.append("import { useAppDispatch, useAppSelector } from '../../store/hooks';\n");
        sb.append("import { create").append(name).append(", update").append(name).append(", fetch").append(name).append("ById, clearCurrent } from '../../store/slices/").append(camel).append("Slice';\n");
        sb.append("import { Box, Button, Paper, Typography, Stack, Snackbar, Alert, Divider, Fade } from '@mui/material';\n");
        sb.append("import { Save, ArrowBack } from '@mui/icons-material';\n");
        sb.append("import FormTextField from '../../components/shared/FormTextField';\n");
        if (!manyToOneRelations.isEmpty()) {
            sb.append("import EntitySelect from '../../components/shared/EntitySelect';\n");
        }
        sb.append("\n");

        sb.append("export default function ").append(name).append("FormPage() {\n");
        sb.append("  const { id } = useParams<{ id: string }>();\n");
        sb.append("  const isEdit = Boolean(id);\n");
        sb.append("  const dispatch = useAppDispatch();\n");
        sb.append("  const navigate = useNavigate();\n");
        sb.append("  const { current, saving } = useAppSelector((s) => s.").append(camel).append(");\n");
        sb.append("  const [snackbar, setSnackbar] = useState<{ open: boolean; message: string; severity: 'success' | 'error' }>({ open: false, message: '', severity: 'success' });\n\n");

        // Form state
        sb.append("  const [form, setForm] = useState<Record<string, unknown>>({\n");
        for (FieldDefinition f : requestFields) {
            sb.append("    ").append(f.getName()).append(": '',\n");
        }
        for (RelationDefinition r : manyToOneRelations) {
            sb.append("    ").append(r.getFieldName()).append("Id: null as number | null,\n");
        }
        sb.append("  });\n\n");

        // Load on edit
        sb.append("  useEffect(() => {\n");
        sb.append("    if (isEdit && id) dispatch(fetch").append(name).append("ById(Number(id)));\n");
        sb.append("    return () => { dispatch(clearCurrent()); };\n");
        sb.append("  }, [id, isEdit, dispatch]);\n\n");

        // Populate form from current
        sb.append("  useEffect(() => {\n");
        sb.append("    if (current) {\n");
        sb.append("      setForm({\n");
        for (FieldDefinition f : requestFields) {
            sb.append("        ").append(f.getName()).append(": current.").append(f.getName()).append(" ?? '',\n");
        }
        for (RelationDefinition r : manyToOneRelations) {
            sb.append("        ").append(r.getFieldName()).append("Id: (current as Record<string, unknown>).").append(r.getFieldName()).append("Id ?? ((current as Record<string, unknown>).").append(r.getFieldName()).append(" as Record<string, unknown>)?.id ?? null,\n");
        }
        sb.append("      });\n");
        sb.append("    }\n");
        sb.append("  }, [current]);\n\n");

        sb.append("  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {\n");
        sb.append("    setForm({ ...form, [e.target.name]: e.target.value });\n");
        sb.append("  };\n\n");

        sb.append("  const handleSubmit = async (e: React.FormEvent) => {\n");
        sb.append("    e.preventDefault();\n");
        sb.append("    try {\n");
        sb.append("      if (isEdit && id) {\n");
        sb.append("        await dispatch(update").append(name).append("({ id: Number(id), data: form })).unwrap();\n");
        sb.append("        setSnackbar({ open: true, message: 'Atualizado com sucesso!', severity: 'success' });\n");
        sb.append("      } else {\n");
        sb.append("        await dispatch(create").append(name).append("(form)).unwrap();\n");
        sb.append("        setSnackbar({ open: true, message: 'Criado com sucesso!', severity: 'success' });\n");
        sb.append("      }\n");
        sb.append("      setTimeout(() => navigate('/").append(plural).append("'), 1000);\n");
        sb.append("    } catch {\n");
        sb.append("      setSnackbar({ open: true, message: 'Erro ao salvar. Verifique os campos.', severity: 'error' });\n");
        sb.append("    }\n");
        sb.append("  };\n\n");

        sb.append("  return (\n");
        sb.append("    <Fade in>\n");
        sb.append("      <Box>\n");
        sb.append("        <Stack direction=\"row\" alignItems=\"center\" spacing={2} mb={3}>\n");
        sb.append("          <Button variant=\"text\" startIcon={<ArrowBack />} onClick={() => navigate('/").append(plural).append("')} color=\"inherit\">\n");
        sb.append("            Voltar\n");
        sb.append("          </Button>\n");
        sb.append("          <Typography variant=\"h4\">{isEdit ? 'Editar' : 'Novo'} ").append(name).append("</Typography>\n");
        sb.append("        </Stack>\n\n");

        sb.append("        <Paper component=\"form\" onSubmit={handleSubmit} sx={{ p: { xs: 2, sm: 3, md: 4 }, maxWidth: { sm: '100%', md: 700 }, borderRadius: 3, boxShadow: '0 4px 20px rgba(25, 118, 210, 0.08)' }}>\n");
        sb.append("          <Typography variant=\"h6\" color=\"primary\" fontWeight={600} mb={2}>Dados</Typography>\n");

        // Regular fields
        for (FieldDefinition f : requestFields) {
            String label = NamingUtils.toHumanLabel(f.getName());
            if ("Enum".equalsIgnoreCase(f.getType()) && f.getEnumValues() != null && !f.getEnumValues().isEmpty()) {
                sb.append("          <FormTextField\n");
                sb.append("            name=\"").append(f.getName()).append("\" label=\"").append(label).append("\"\n");
                sb.append("            value={form.").append(f.getName()).append("} onChange={handleChange}\n");
                sb.append("            required={").append(f.isRequired()).append("}\n");
                sb.append("            options={[");
                for (int i = 0; i < f.getEnumValues().size(); i++) {
                    String ev = f.getEnumValues().get(i);
                    sb.append("{ value: '").append(ev).append("', label: '").append(ev).append("' }");
                    if (i < f.getEnumValues().size() - 1) sb.append(", ");
                }
                sb.append("]}\n");
                sb.append("          />\n");
            } else if ("Boolean".equalsIgnoreCase(f.getType())) {
                sb.append("          <FormTextField\n");
                sb.append("            name=\"").append(f.getName()).append("\" label=\"").append(label).append("\"\n");
                sb.append("            value={form.").append(f.getName()).append("} onChange={handleChange}\n");
                sb.append("            options={[{ value: 'true', label: 'Sim' }, { value: 'false', label: 'Não' }]}\n");
                sb.append("          />\n");
            } else {
                String inputType = "text";
                if ("Integer".equalsIgnoreCase(f.getType()) || "Long".equalsIgnoreCase(f.getType()) ||
                    "Double".equalsIgnoreCase(f.getType()) || "Float".equalsIgnoreCase(f.getType()) ||
                    "BigDecimal".equalsIgnoreCase(f.getType())) {
                    inputType = "number";
                } else if ("LocalDate".equalsIgnoreCase(f.getType())) {
                    inputType = "date";
                } else if ("LocalDateTime".equalsIgnoreCase(f.getType())) {
                    inputType = "datetime-local";
                }
                sb.append("          <FormTextField\n");
                sb.append("            name=\"").append(f.getName()).append("\" label=\"").append(label).append("\"\n");
                sb.append("            value={form.").append(f.getName()).append("} onChange={handleChange}\n");
                sb.append("            required={").append(f.isRequired()).append("} type=\"").append(inputType).append("\"\n");
                if (f.getMaxLength() != null && f.getMaxLength() > 200) {
                    sb.append("            multiline rows={3}\n");
                }
                sb.append("          />\n");
            }
        }

        // Relation fields with EntitySelect (integrado, sem separador)
        if (!manyToOneRelations.isEmpty()) {
            for (RelationDefinition r : manyToOneRelations) {
                String targetName = r.getTargetEntity();
                String targetCamel = NamingUtils.toCamelCase(targetName);
                String targetPlural = NamingUtils.toPlural(NamingUtils.toSnakeCase(targetName)).replace("_", "-");

                // Try to find the target entity's apiPath
                String targetApiPath = "/api/v1/" + targetPlural;
                for (EntityDefinition e : def.getEntities()) {
                    if (e.getName().equals(targetName) && e.getApiPath() != null) {
                        targetApiPath = e.getApiPath();
                        break;
                    }
                }

                sb.append("          <EntitySelect\n");
                sb.append("            label=\"").append(NamingUtils.toHumanLabel(r.getFieldName())).append("\"\n");
                sb.append("            apiPath=\"").append(targetApiPath).append("\"\n");
                sb.append("            labelField=\"name\"\n");
                sb.append("            value={form.").append(r.getFieldName()).append("Id as number | null}\n");
                sb.append("            onChange={(id) => setForm({ ...form, ").append(r.getFieldName()).append("Id: id })}\n");
                sb.append("            required\n");
                sb.append("          />\n");
            }
        }

        sb.append("\n          <Stack direction=\"row\" spacing={2} mt={4}>\n");
        sb.append("            <Button type=\"submit\" variant=\"contained\" size=\"large\" startIcon={<Save />} disabled={saving}>\n");
        sb.append("              {saving ? 'Salvando...' : 'Salvar'}\n");
        sb.append("            </Button>\n");
        sb.append("            <Button variant=\"outlined\" size=\"large\" onClick={() => navigate('/").append(plural).append("')} disabled={saving}>\n");
        sb.append("              Cancelar\n");
        sb.append("            </Button>\n");
        sb.append("          </Stack>\n");
        sb.append("        </Paper>\n\n");

        sb.append("        <Snackbar open={snackbar.open} autoHideDuration={4000} onClose={() => setSnackbar({ ...snackbar, open: false })} anchorOrigin={{ vertical: 'bottom', horizontal: 'right' }}>\n");
        sb.append("          <Alert severity={snackbar.severity} variant=\"filled\" onClose={() => setSnackbar({ ...snackbar, open: false })}>{snackbar.message}</Alert>\n");
        sb.append("        </Snackbar>\n");
        sb.append("      </Box>\n");
        sb.append("    </Fade>\n");
        sb.append("  );\n");
        sb.append("}\n");

        writeTs(sb.toString(), new File(frontendDir, "pages/" + camel + "/" + name + "FormPage.tsx"));
    }


    // ═══════════════════════════════════════════════════════════════════════
    // Store (global)
    // ═══════════════════════════════════════════════════════════════════════

    private void generateStore(ForgeDefinition def, File frontendDir) throws MojoExecutionException {
        List<EntityDefinition> entities = def.getEntities();

        StringBuilder sb = new StringBuilder();
        sb.append("import { configureStore } from '@reduxjs/toolkit';\n");
        for (EntityDefinition e : entities) {
            String camel = NamingUtils.toCamelCase(e.getName());
            sb.append("import ").append(camel).append("Reducer from './slices/").append(camel).append("Slice';\n");
        }
        sb.append("\nexport const store = configureStore({\n");
        sb.append("  reducer: {\n");
        for (EntityDefinition e : entities) {
            String camel = NamingUtils.toCamelCase(e.getName());
            sb.append("    ").append(camel).append(": ").append(camel).append("Reducer,\n");
        }
        sb.append("  },\n});\n\n");
        sb.append("export type RootState = ReturnType<typeof store.getState>;\n");
        sb.append("export type AppDispatch = typeof store.dispatch;\n");
        writeTs(sb.toString(), new File(frontendDir, "store/store.ts"));

        // hooks.ts
        StringBuilder hooks = new StringBuilder();
        hooks.append("import { useDispatch, useSelector, TypedUseSelectorHook } from 'react-redux';\n");
        hooks.append("import type { RootState, AppDispatch } from './store';\n\n");
        hooks.append("export const useAppDispatch = () => useDispatch<AppDispatch>();\n");
        hooks.append("export const useAppSelector: TypedUseSelectorHook<RootState> = useSelector;\n");
        writeTs(hooks.toString(), new File(frontendDir, "store/hooks.ts"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Routes
    // ═══════════════════════════════════════════════════════════════════════

    private void generateRoutes(ForgeDefinition def, File frontendDir) throws MojoExecutionException {
        List<EntityDefinition> entities = def.getEntities();
        String firstPlural = NamingUtils.toPlural(NamingUtils.toCamelCase(entities.get(0).getName()));

        StringBuilder sb = new StringBuilder();
        sb.append("import { RouteObject, Navigate } from 'react-router-dom';\n");
        for (EntityDefinition e : entities) {
            String n = e.getName();
            String c = NamingUtils.toCamelCase(n);
            sb.append("import List").append(n).append("Page from './pages/").append(c).append("/List").append(n).append("Page';\n");
            sb.append("import ").append(n).append("FormPage from './pages/").append(c).append("/").append(n).append("FormPage';\n");
            sb.append("import ").append(n).append("DetailPage from './pages/").append(c).append("/").append(n).append("DetailPage';\n");
        }
        sb.append("\nconst routes: RouteObject[] = [\n");
        sb.append("  { path: '/', element: <Navigate to=\"/").append(firstPlural).append("\" replace /> },\n");
        for (EntityDefinition e : entities) {
            String n = e.getName();
            String c = NamingUtils.toCamelCase(n);
            String p = NamingUtils.toPlural(c);
            sb.append("  { path: '/").append(p).append("', element: <List").append(n).append("Page /> },\n");
            sb.append("  { path: '/").append(p).append("/new', element: <").append(n).append("FormPage /> },\n");
            sb.append("  { path: '/").append(p).append("/:id', element: <").append(n).append("FormPage /> },\n");
            sb.append("  { path: '/").append(p).append("/:id/view', element: <").append(n).append("DetailPage /> },\n");
        }
        sb.append("];\n\nexport default routes;\n");
        writeTs(sb.toString(), new File(frontendDir, "routes.tsx"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // App Menu
    // ═══════════════════════════════════════════════════════════════════════

    private void generateAppMenu(ForgeDefinition def, File frontendDir) throws MojoExecutionException {
        List<EntityDefinition> entities = def.getEntities();

        StringBuilder sb = new StringBuilder();
        sb.append("import { Drawer, List, ListItemButton, ListItemIcon, ListItemText, Toolbar, Box, Typography, Avatar, useMediaQuery } from '@mui/material';\n");
        sb.append("import { useNavigate, useLocation } from 'react-router-dom';\n");
        sb.append("import { ViewList, Dashboard } from '@mui/icons-material';\n\n");

        sb.append("export const DRAWER_WIDTH = 280;\n\n");

        sb.append("const menuItems = [\n");
        for (EntityDefinition e : entities) {
            String c = NamingUtils.toCamelCase(e.getName());
            String p = NamingUtils.toPlural(c);
            sb.append("  { label: '").append(NamingUtils.toHumanLabel(e.getName())).append("', path: '/").append(p).append("' },\n");
        }
        sb.append("];\n\n");

        sb.append("interface AppMenuProps {\n");
        sb.append("  mobileOpen: boolean;\n");
        sb.append("  onClose: () => void;\n");
        sb.append("}\n\n");

        sb.append("export default function AppMenu({ mobileOpen, onClose }: AppMenuProps) {\n");
        sb.append("  const navigate = useNavigate();\n");
        sb.append("  const location = useLocation();\n");
        sb.append("  const isMobile = useMediaQuery('(max-width:900px)');\n\n");

        sb.append("  const drawerContent = (\n");
        sb.append("    <>\n");
        sb.append("      <Toolbar sx={{ px: 3 }}>\n");
        sb.append("        <Avatar sx={{ bgcolor: 'primary.main', width: 36, height: 36, mr: 1.5, fontSize: 16 }}>\n");
        sb.append("          <Dashboard fontSize=\"small\" />\n");
        sb.append("        </Avatar>\n");
        sb.append("        <Typography variant=\"subtitle1\" fontWeight={700} noWrap>").append(def.getProject().getName()).append("</Typography>\n");
        sb.append("      </Toolbar>\n");
        sb.append("      <Box sx={{ px: 2, pt: 2, pb: 1 }}>\n");
        sb.append("        <Typography variant=\"overline\" color=\"text.disabled\" fontSize={11} letterSpacing={1.2}>Cadastros</Typography>\n");
        sb.append("      </Box>\n");
        sb.append("      <List sx={{ px: 1.5 }}>\n");
        sb.append("        {menuItems.map((item) => {\n");
        sb.append("          const active = location.pathname.startsWith(item.path);\n");
        sb.append("          return (\n");
        sb.append("            <ListItemButton\n");
        sb.append("              key={item.path}\n");
        sb.append("              onClick={() => { navigate(item.path); if (isMobile) onClose(); }}\n");
        sb.append("              sx={{\n");
        sb.append("                borderRadius: 2.5,\n");
        sb.append("                mb: 0.5,\n");
        sb.append("                py: 1.2,\n");
        sb.append("                bgcolor: active ? 'primary.main' : 'transparent',\n");
        sb.append("                color: active ? '#fff' : 'text.primary',\n");
        sb.append("                '&:hover': { bgcolor: active ? 'primary.dark' : 'rgba(25, 118, 210, 0.06)' },\n");
        sb.append("                transition: 'all 0.2s ease',\n");
        sb.append("              }}\n");
        sb.append("            >\n");
        sb.append("              <ListItemIcon sx={{ color: active ? '#fff' : 'text.secondary', minWidth: 40 }}>\n");
        sb.append("                <ViewList fontSize=\"small\" />\n");
        sb.append("              </ListItemIcon>\n");
        sb.append("              <ListItemText primary={item.label} primaryTypographyProps={{ fontWeight: active ? 600 : 500, fontSize: 14 }} />\n");
        sb.append("            </ListItemButton>\n");
        sb.append("          );\n");
        sb.append("        })}\n");
        sb.append("      </List>\n");
        sb.append("    </>\n");
        sb.append("  );\n\n");

        sb.append("  if (isMobile) {\n");
        sb.append("    return (\n");
        sb.append("      <Drawer variant=\"temporary\" open={mobileOpen} onClose={onClose}\n");
        sb.append("        sx={{ '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box' } }}>\n");
        sb.append("        {drawerContent}\n");
        sb.append("      </Drawer>\n");
        sb.append("    );\n");
        sb.append("  }\n\n");

        sb.append("  return (\n");
        sb.append("    <Drawer variant=\"permanent\"\n");
        sb.append("      sx={{ width: DRAWER_WIDTH, flexShrink: 0,\n");
        sb.append("        '& .MuiDrawer-paper': { width: DRAWER_WIDTH, boxSizing: 'border-box', borderRight: 'none', boxShadow: '1px 0 12px rgba(0,0,0,0.03)' } }}>\n");
        sb.append("      {drawerContent}\n");
        sb.append("    </Drawer>\n");
        sb.append("  );\n");
        sb.append("}\n");
        writeTs(sb.toString(), new File(frontendDir, "components/AppMenu.tsx"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // App.tsx
    // ═══════════════════════════════════════════════════════════════════════

    private void generateApp(ForgeDefinition def, File frontendDir) throws MojoExecutionException {
        StringBuilder sb = new StringBuilder();
        sb.append("import { useState, useMemo } from 'react';\n");
        sb.append("import { BrowserRouter, useRoutes } from 'react-router-dom';\n");
        sb.append("import { Provider } from 'react-redux';\n");
        sb.append("import { store } from './store/store';\n");
        sb.append("import { ThemeProvider, createTheme } from '@mui/material/styles';\n");
        sb.append("import { Box, CssBaseline, useMediaQuery } from '@mui/material';\n");
        sb.append("import AppMenu, { DRAWER_WIDTH } from './components/AppMenu';\n");
        sb.append("import AppHeader from './components/AppHeader';\n");
        sb.append("import routes from './routes';\n");
        sb.append("import baseTheme from './theme';\n\n");

        sb.append("function AppRoutes() {\n");
        sb.append("  const element = useRoutes(routes);\n");
        sb.append("  return <>{element}</>;\n");
        sb.append("}\n\n");

        sb.append("export default function App() {\n");
        sb.append("  const prefersDark = useMediaQuery('(prefers-color-scheme: dark)');\n");
        sb.append("  const [darkMode, setDarkMode] = useState(() => {\n");
        sb.append("    const saved = localStorage.getItem('darkMode');\n");
        sb.append("    return saved !== null ? saved === 'true' : prefersDark;\n");
        sb.append("  });\n");
        sb.append("  const [menuOpen, setMenuOpen] = useState(false);\n");
        sb.append("  const isMobile = useMediaQuery('(max-width:900px)');\n\n");

        sb.append("  const theme = useMemo(() => createTheme({\n");
        sb.append("    ...baseTheme,\n");
        sb.append("    palette: { ...baseTheme.palette, mode: darkMode ? 'dark' : 'light' },\n");
        sb.append("  }), [darkMode]);\n\n");

        sb.append("  const toggleDark = () => {\n");
        sb.append("    setDarkMode((prev) => { localStorage.setItem('darkMode', String(!prev)); return !prev; });\n");
        sb.append("  };\n\n");

        sb.append("  return (\n");
        sb.append("    <Provider store={store}>\n");
        sb.append("      <ThemeProvider theme={theme}>\n");
        sb.append("        <BrowserRouter>\n");
        sb.append("          <CssBaseline />\n");
        sb.append("          <Box sx={{ display: 'flex', minHeight: '100vh' }}>\n");
        sb.append("            <AppMenu mobileOpen={menuOpen} onClose={() => setMenuOpen(false)} />\n");
        sb.append("            <AppHeader darkMode={darkMode} onToggleDark={toggleDark} onToggleMenu={() => setMenuOpen(true)} />\n");
        sb.append("            <Box\n");
        sb.append("              component=\"main\"\n");
        sb.append("              sx={{\n");
        sb.append("                flexGrow: 1,\n");
        sb.append("                minWidth: 0,\n");
        sb.append("                px: { xs: 2, sm: 3, md: 4 },\n");
        sb.append("                pb: { xs: 2, sm: 3, md: 4 },\n");
        sb.append("                pt: { xs: 10, sm: 12 },\n");
        sb.append("                ml: isMobile ? 0 : `${DRAWER_WIDTH}px`,\n");
        sb.append("                bgcolor: 'background.default',\n");
        sb.append("                minHeight: '100vh',\n");
        sb.append("                transition: 'margin 0.3s',\n");
        sb.append("              }}\n");
        sb.append("            >\n");
        sb.append("              <AppRoutes />\n");
        sb.append("            </Box>\n");
        sb.append("          </Box>\n");
        sb.append("        </BrowserRouter>\n");
        sb.append("      </ThemeProvider>\n");
        sb.append("    </Provider>\n");
        sb.append("  );\n");
        sb.append("}\n");
        writeTs(sb.toString(), new File(frontendDir, "App.tsx"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════════

    private String tsType(FieldDefinition f) {
        return switch (f.getType().toLowerCase()) {
            case "integer", "int", "long", "double", "float", "bigdecimal" -> "number";
            case "boolean" -> "boolean";
            default -> "string";
        };
    }

    // ═══════════════════════════════════════════════════════════════════════
    // App Header (Dark Mode toggle + responsivo)
    // ═══════════════════════════════════════════════════════════════════════

    private void generateAppHeader(ForgeDefinition def, File frontendDir) throws MojoExecutionException {
        StringBuilder sb = new StringBuilder();
        sb.append("import { AppBar, IconButton, Toolbar, Typography, useMediaQuery } from '@mui/material';\n");
        sb.append("import { DarkMode, LightMode, Menu as MenuIcon } from '@mui/icons-material';\n");
        sb.append("import { DRAWER_WIDTH } from './AppMenu';\n\n");
        sb.append("interface AppHeaderProps {\n");
        sb.append("  darkMode: boolean;\n");
        sb.append("  onToggleDark: () => void;\n");
        sb.append("  onToggleMenu: () => void;\n");
        sb.append("}\n\n");
        sb.append("export default function AppHeader({ darkMode, onToggleDark, onToggleMenu }: AppHeaderProps) {\n");
        sb.append("  const isMobile = useMediaQuery('(max-width:900px)');\n\n");
        sb.append("  return (\n");
        sb.append("    <AppBar\n");
        sb.append("      position=\"fixed\"\n");
        sb.append("      elevation={0}\n");
        sb.append("      sx={{\n");
        sb.append("        ml: isMobile ? 0 : `${DRAWER_WIDTH}px`,\n");
        sb.append("        width: isMobile ? '100%' : `calc(100% - ${DRAWER_WIDTH}px)`,\n");
        sb.append("        bgcolor: 'background.paper',\n");
        sb.append("        borderBottom: '1px solid',\n");
        sb.append("        borderColor: 'divider',\n");
        sb.append("      }}\n");
        sb.append("    >\n");
        sb.append("      <Toolbar>\n");
        sb.append("        {isMobile && (\n");
        sb.append("          <IconButton edge=\"start\" onClick={onToggleMenu} sx={{ mr: 2 }}>\n");
        sb.append("            <MenuIcon />\n");
        sb.append("          </IconButton>\n");
        sb.append("        )}\n");
        sb.append("        <Typography variant=\"subtitle1\" color=\"text.primary\" sx={{ flexGrow: 1 }} />\n");
        sb.append("        <IconButton onClick={onToggleDark} color=\"default\">\n");
        sb.append("          {darkMode ? <LightMode /> : <DarkMode />}\n");
        sb.append("        </IconButton>\n");
        sb.append("      </Toolbar>\n");
        sb.append("    </AppBar>\n");
        sb.append("  );\n");
        sb.append("}\n");
        writeTs(sb.toString(), new File(frontendDir, "components/AppHeader.tsx"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Detail Page
    // ═══════════════════════════════════════════════════════════════════════

    private void generateDetailPage(ForgeDefinition def, EntityDefinition entity, File frontendDir) throws MojoExecutionException {
        String name = entity.getName();
        String camel = NamingUtils.toCamelCase(name);
        String plural = NamingUtils.toPlural(camel);

        List<FieldDefinition> responseFields = entity.getFields().stream()
                .filter(FieldDefinition::isInResponse).toList();

        StringBuilder sb = new StringBuilder();
        sb.append("import { useEffect, useState } from 'react';\n");
        sb.append("import { useNavigate, useParams } from 'react-router-dom';\n");
        sb.append("import { useAppDispatch, useAppSelector } from '../../store/hooks';\n");
        sb.append("import { fetch").append(name).append("ById, delete").append(name).append(", clearCurrent } from '../../store/slices/").append(camel).append("Slice';\n");
        sb.append("import {\n");
        sb.append("  Box, Button, Chip, Divider, Grid, Paper, Stack, Typography, Fade,\n");
        sb.append("} from '@mui/material';\n");
        sb.append("import { ArrowBack, Edit, Delete } from '@mui/icons-material';\n");
        sb.append("import StatusChip from '../../components/shared/StatusChip';\n");
        sb.append("import ConfirmDialog from '../../components/shared/ConfirmDialog';\n\n");

        sb.append("export default function ").append(name).append("DetailPage() {\n");
        sb.append("  const { id } = useParams<{ id: string }>();\n");
        sb.append("  const dispatch = useAppDispatch();\n");
        sb.append("  const navigate = useNavigate();\n");
        sb.append("  const { current } = useAppSelector((s) => s.").append(camel).append(");\n");
        sb.append("  const [confirmDelete, setConfirmDelete] = useState(false);\n\n");

        sb.append("  useEffect(() => {\n");
        sb.append("    if (id) dispatch(fetch").append(name).append("ById(Number(id)));\n");
        sb.append("    return () => { dispatch(clearCurrent()); };\n");
        sb.append("  }, [id, dispatch]);\n\n");

        sb.append("  const handleDelete = async () => {\n");
        sb.append("    if (!id) return;\n");
        sb.append("    await dispatch(delete").append(name).append("(Number(id))).unwrap();\n");
        sb.append("    navigate('/").append(plural).append("');\n");
        sb.append("  };\n\n");

        sb.append("  if (!current) return <Typography>Carregando...</Typography>;\n\n");

        sb.append("  return (\n");
        sb.append("    <Fade in>\n");
        sb.append("      <Box>\n");
        sb.append("        <Stack direction={{ xs: 'column', sm: 'row' }} alignItems={{ xs: 'flex-start', sm: 'center' }} justifyContent=\"space-between\" spacing={2} mb={3}>\n");
        sb.append("          <Stack direction=\"row\" alignItems=\"center\" spacing={2}>\n");
        sb.append("            <Button startIcon={<ArrowBack />} onClick={() => navigate('/").append(plural).append("')} color=\"inherit\">Voltar</Button>\n");
        sb.append("            <Typography variant=\"h4\">").append(name).append(" #{current.id}</Typography>\n");
        sb.append("          </Stack>\n");
        sb.append("          <Stack direction=\"row\" spacing={1} flexWrap=\"wrap\" useFlexGap>\n");
        sb.append("            <Button variant=\"contained\" startIcon={<Edit />} onClick={() => navigate(`/").append(plural).append("/${id}`)}>Editar</Button>\n");
        sb.append("            <Button variant=\"outlined\" color=\"error\" startIcon={<Delete />} onClick={() => setConfirmDelete(true)}>Excluir</Button>\n");
        sb.append("          </Stack>\n");
        sb.append("        </Stack>\n\n");

        sb.append("        <Paper sx={{ p: 4, borderRadius: 3, boxShadow: '0 4px 20px rgba(25, 118, 210, 0.08)' }}>\n");
        sb.append("          <Typography variant=\"h6\" color=\"primary\" fontWeight={600} mb={2}>Detalhes</Typography>\n");
        sb.append("          <Grid container spacing={3}>\n");

        for (FieldDefinition f : responseFields) {
            String label = NamingUtils.toHumanLabel(f.getName());
            sb.append("            <Grid item xs={12} sm={6} md={4}>\n");
            sb.append("              <Typography variant=\"caption\" color=\"text.disabled\">").append(label).append("</Typography>\n");
            if ("Enum".equalsIgnoreCase(f.getType())) {
                sb.append("              <Box mt={0.5}><StatusChip value={current.").append(f.getName()).append(" as unknown as string} /></Box>\n");
            } else if ("BigDecimal".equalsIgnoreCase(f.getType()) || "Double".equalsIgnoreCase(f.getType()) || "Float".equalsIgnoreCase(f.getType())) {
                sb.append("              <Typography variant=\"body1\" fontWeight={500}>\n");
                sb.append("                {current.").append(f.getName()).append(" != null ? `R$ ${Number(current.").append(f.getName()).append(").toFixed(2)}` : '-'}\n");
                sb.append("              </Typography>\n");
            } else if ("LocalDate".equalsIgnoreCase(f.getType()) || "LocalDateTime".equalsIgnoreCase(f.getType())) {
                sb.append("              <Typography variant=\"body1\" fontWeight={500}>\n");
                sb.append("                {current.").append(f.getName()).append(" ? new Date(current.").append(f.getName()).append(" as string).toLocaleString('pt-BR') : '-'}\n");
                sb.append("              </Typography>\n");
            } else if ("Boolean".equalsIgnoreCase(f.getType())) {
                sb.append("              <Box mt={0.5}><Chip label={current.").append(f.getName()).append(" ? 'Sim' : 'Não'} size=\"small\" color={current.").append(f.getName()).append(" ? 'success' : 'default'} /></Box>\n");
            } else {
                sb.append("              <Typography variant=\"body1\" fontWeight={500}>{String(current.").append(f.getName()).append(" ?? '-')}</Typography>\n");
            }
            sb.append("            </Grid>\n");
        }

        // Audit fields
        if (entity.isAuditable()) {
            sb.append("\n            <Grid item xs={12}><Divider sx={{ my: 1 }} /></Grid>\n");
            sb.append("            <Grid item xs={12} sm={6} md={4}>\n");
            sb.append("              <Typography variant=\"caption\" color=\"text.disabled\">Criado em</Typography>\n");
            sb.append("              <Typography variant=\"body2\">{(current as Record<string,unknown>).createdAt ? new Date(String((current as Record<string,unknown>).createdAt)).toLocaleString('pt-BR') : '-'}</Typography>\n");
            sb.append("            </Grid>\n");
            sb.append("            <Grid item xs={12} sm={6} md={4}>\n");
            sb.append("              <Typography variant=\"caption\" color=\"text.disabled\">Atualizado em</Typography>\n");
            sb.append("              <Typography variant=\"body2\">{(current as Record<string,unknown>).updatedAt ? new Date(String((current as Record<string,unknown>).updatedAt)).toLocaleString('pt-BR') : '-'}</Typography>\n");
            sb.append("            </Grid>\n");
        }

        sb.append("          </Grid>\n");
        sb.append("        </Paper>\n\n");

        // Action buttons
        List<ActionDefinition> httpActions = entity.getActions().stream()
                .filter(a -> a.getHttpMethod() != null).toList();
        if (!httpActions.isEmpty()) {
            sb.append("        <Paper sx={{ p: 3, mt: 3, borderRadius: 3 }}>\n");
            sb.append("          <Typography variant=\"h6\" color=\"text.secondary\" mb={2}>Ações</Typography>\n");
            sb.append("          <Stack direction=\"row\" spacing={2} flexWrap=\"wrap\">\n");
            for (ActionDefinition a : httpActions) {
                String label = NamingUtils.toHumanLabel(a.getName());
                sb.append("            <Button variant=\"outlined\" onClick={() => navigate(`/").append(plural).append("/${id}/").append(a.getName()).append("`)}>").append(label).append("</Button>\n");
            }
            sb.append("          </Stack>\n");
            sb.append("        </Paper>\n\n");
        }

        sb.append("        <ConfirmDialog\n");
        sb.append("          open={confirmDelete}\n");
        sb.append("          title=\"Excluir registro\"\n");
        sb.append("          message=\"Tem certeza? Esta ação não pode ser desfeita.\"\n");
        sb.append("          confirmLabel=\"Excluir\"\n");
        sb.append("          onConfirm={handleDelete}\n");
        sb.append("          onCancel={() => setConfirmDelete(false)}\n");
        sb.append("        />\n");
        sb.append("      </Box>\n");
        sb.append("    </Fade>\n");
        sb.append("  );\n");
        sb.append("}\n");

        writeTs(sb.toString(), new File(frontendDir, "pages/" + camel + "/" + name + "DetailPage.tsx"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Filter Panel
    // ═══════════════════════════════════════════════════════════════════════

    private void generateFilterPanel(ForgeDefinition def, EntityDefinition entity, File frontendDir) throws MojoExecutionException {
        String name = entity.getName();
        String camel = NamingUtils.toCamelCase(name);

        StringBuilder sb = new StringBuilder();
        sb.append("import { useState } from 'react';\n");
        sb.append("import { Box, Button, Grid, Paper, TextField, MenuItem, Stack, Collapse, IconButton } from '@mui/material';\n");
        sb.append("import { FilterList, Clear } from '@mui/icons-material';\n\n");

        sb.append("export interface ").append(name).append("Filter {\n");
        for (FilterDefinition f : entity.getFilters()) {
            String tsType = switch (f.getType().toLowerCase()) {
                case "integer", "long", "double", "float", "bigdecimal" -> "number | ''";
                default -> "string";
            };
            sb.append("  ").append(f.getName()).append(": ").append(tsType).append(";\n");
        }
        sb.append("}\n\n");

        sb.append("const emptyFilter: ").append(name).append("Filter = {\n");
        for (FilterDefinition f : entity.getFilters()) {
            sb.append("  ").append(f.getName()).append(": '',\n");
        }
        sb.append("};\n\n");

        sb.append("interface ").append(name).append("FilterPanelProps {\n");
        sb.append("  onSearch: (filter: ").append(name).append("Filter) => void;\n");
        sb.append("}\n\n");

        sb.append("export default function ").append(name).append("FilterPanel({ onSearch }: ").append(name).append("FilterPanelProps) {\n");
        sb.append("  const [open, setOpen] = useState(false);\n");
        sb.append("  const [filter, setFilter] = useState<").append(name).append("Filter>(emptyFilter);\n\n");

        sb.append("  const handleChange = (e: React.ChangeEvent<HTMLInputElement>) => {\n");
        sb.append("    setFilter({ ...filter, [e.target.name]: e.target.value });\n");
        sb.append("  };\n\n");

        sb.append("  const handleSearch = () => {\n");
        sb.append("    const cleaned = Object.fromEntries(\n");
        sb.append("      Object.entries(filter).filter(([, v]) => v !== '' && v != null)\n");
        sb.append("    );\n");
        sb.append("    onSearch(cleaned as ").append(name).append("Filter);\n");
        sb.append("  };\n\n");

        sb.append("  const handleClear = () => {\n");
        sb.append("    setFilter(emptyFilter);\n");
        sb.append("    onSearch({} as ").append(name).append("Filter);\n");
        sb.append("  };\n\n");

        sb.append("  return (\n");
        sb.append("    <Paper sx={{ mb: 3, borderRadius: 3 }}>\n");
        sb.append("      <Stack direction=\"row\" alignItems=\"center\" sx={{ px: 2, py: 1 }}>\n");
        sb.append("        <Button startIcon={<FilterList />} onClick={() => setOpen(!open)} size=\"small\">\n");
        sb.append("          {open ? 'Ocultar filtros' : 'Filtros'}\n");
        sb.append("        </Button>\n");
        sb.append("      </Stack>\n");
        sb.append("      <Collapse in={open}>\n");
        sb.append("        <Box sx={{ px: 3, pb: 2 }}>\n");
        sb.append("          <Grid container spacing={2}>\n");

        for (FilterDefinition f : entity.getFilters()) {
            String label = f.getLabel() != null ? f.getLabel() : NamingUtils.toHumanLabel(f.getName());
            sb.append("            <Grid item xs={12} sm={6} md={3}>\n");

            if ("Enum".equalsIgnoreCase(f.getType()) && f.getEnumValues() != null && !f.getEnumValues().isEmpty()) {
                sb.append("              <TextField select fullWidth size=\"small\" label=\"").append(label).append("\" name=\"").append(f.getName()).append("\" value={filter.").append(f.getName()).append("} onChange={handleChange}>\n");
                sb.append("                <MenuItem value=\"\"><em>Todos</em></MenuItem>\n");
                for (String ev : f.getEnumValues()) {
                    sb.append("                <MenuItem value=\"").append(ev).append("\">").append(ev).append("</MenuItem>\n");
                }
                sb.append("              </TextField>\n");
            } else {
                String inputType = switch (f.getType().toLowerCase()) {
                    case "integer", "long", "double", "float", "bigdecimal" -> "number";
                    case "localdate" -> "date";
                    case "localdatetime" -> "datetime-local";
                    default -> "text";
                };
                sb.append("              <TextField fullWidth size=\"small\" label=\"").append(label).append("\" name=\"").append(f.getName()).append("\" type=\"").append(inputType).append("\" value={filter.").append(f.getName()).append("} onChange={handleChange} />\n");
            }

            sb.append("            </Grid>\n");
        }

        sb.append("          </Grid>\n");
        sb.append("          <Stack direction=\"row\" spacing={1} mt={2}>\n");
        sb.append("            <Button variant=\"contained\" size=\"small\" onClick={handleSearch}>Buscar</Button>\n");
        sb.append("            <Button variant=\"text\" size=\"small\" startIcon={<Clear />} onClick={handleClear}>Limpar</Button>\n");
        sb.append("          </Stack>\n");
        sb.append("        </Box>\n");
        sb.append("      </Collapse>\n");
        sb.append("    </Paper>\n");
        sb.append("  );\n");
        sb.append("}\n");

        writeTs(sb.toString(), new File(frontendDir, "components/" + camel + "/" + name + "FilterPanel.tsx"));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Validation Schema (Zod)
    // ═══════════════════════════════════════════════════════════════════════

    private void generateValidationSchema(ForgeDefinition def, EntityDefinition entity, File frontendDir) throws MojoExecutionException {
        String name = entity.getName();
        String camel = NamingUtils.toCamelCase(name);

        List<FieldDefinition> requestFields = entity.getFields().stream()
                .filter(FieldDefinition::isInRequest).toList();

        StringBuilder sb = new StringBuilder();
        sb.append("import { z } from 'zod';\n\n");

        sb.append("export const ").append(camel).append("Schema = z.object({\n");

        for (FieldDefinition f : requestFields) {
            sb.append("  ").append(f.getName()).append(": ");

            String baseType = switch (f.getType().toLowerCase()) {
                case "integer", "long" -> "z.coerce.number().int()";
                case "double", "float", "bigdecimal" -> "z.coerce.number()";
                case "boolean" -> "z.coerce.boolean()";
                default -> "z.string()";
            };

            StringBuilder chain = new StringBuilder(baseType);

            if ("String".equalsIgnoreCase(f.getType()) || "Enum".equalsIgnoreCase(f.getType())) {
                if (f.isRequired()) {
                    chain.append(".min(1, 'Campo obrigatório')");
                }
                if (f.getMinLength() != null) {
                    chain.append(".min(").append(f.getMinLength()).append(", 'Mínimo ").append(f.getMinLength()).append(" caracteres')");
                }
                if (f.getMaxLength() != null) {
                    chain.append(".max(").append(f.getMaxLength()).append(", 'Máximo ").append(f.getMaxLength()).append(" caracteres')");
                }
            } else if (f.isRequired()) {
                // numeric required — just needs to exist
                chain.append("");
            }

            if (!f.isRequired() && !"boolean".equalsIgnoreCase(f.getType())) {
                chain.append(".optional().or(z.literal(''))");
            }

            sb.append(chain).append(",\n");
        }

        sb.append("});\n\n");
        sb.append("export type ").append(name).append("FormData = z.infer<typeof ").append(camel).append("Schema>;\n");

        writeTs(sb.toString(), new File(frontendDir, "validation/" + camel + "Schema.ts"));
    }

    private void writeTs(String content, File target) throws MojoExecutionException {
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
