package io.springforge.util;

/**
 * Utilitários de conversão de nomes para geração de código.
 */
public final class NamingUtils {

    private NamingUtils() {}

    /** "ProductCategory" → "product_category" */
    public static String toSnakeCase(String pascalOrCamel) {
        if (pascalOrCamel == null || pascalOrCamel.isBlank()) return pascalOrCamel;
        return pascalOrCamel
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .toLowerCase();
    }

    /** "product_category" → "ProductCategory" */
    public static String toPascalCase(String snakeOrCamel) {
        if (snakeOrCamel == null || snakeOrCamel.isBlank()) return snakeOrCamel;
        String[] parts = snakeOrCamel.split("[_\\-\\s]+");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0)));
                sb.append(part.substring(1).toLowerCase());
            }
        }
        return sb.toString();
    }

    /** "ProductCategory" → "productCategory" */
    public static String toCamelCase(String pascalOrSnake) {
        String pascal = toPascalCase(pascalOrSnake);
        if (pascal == null || pascal.isEmpty()) return pascal;
        return Character.toLowerCase(pascal.charAt(0)) + pascal.substring(1);
    }

    /** "Product" → "products" (pluralização simples) */
    public static String toPlural(String name) {
        if (name == null || name.isBlank()) return name;
        String lower = name.toLowerCase();
        if (lower.endsWith("y") && !lower.endsWith("ay") && !lower.endsWith("ey") && !lower.endsWith("oy") && !lower.endsWith("uy")) {
            return name.substring(0, name.length() - 1) + "ies";
        }
        if (lower.endsWith("s") || lower.endsWith("x") || lower.endsWith("z") ||
            lower.endsWith("ch") || lower.endsWith("sh")) {
            return name + "es";
        }
        return name + "s";
    }

    /** "com.myapp" + "model" → "com.myapp.model" */
    public static String joinPackage(String base, String sub) {
        if (base == null || base.isBlank()) return sub;
        if (sub == null || sub.isBlank()) return base;
        return base + "." + sub;
    }

    /** "com.myapp.model" → "com/myapp/model" */
    public static String packageToPath(String packageName) {
        if (packageName == null) return "";
        return packageName.replace('.', '/');
    }

    /**
     * Mapeia tipo do JSON para tipo Java completo (com import se necessário)
     */
    public static String toJavaType(String type) {
        if (type == null) return "String";
        return switch (type.toLowerCase()) {
            case "string"        -> "String";
            case "integer", "int"-> "Integer";
            case "long"          -> "Long";
            case "double"        -> "Double";
            case "float"         -> "Float";
            case "bigdecimal"    -> "BigDecimal";
            case "boolean"       -> "Boolean";
            case "localdate"     -> "LocalDate";
            case "localdatetime" -> "LocalDateTime";
            case "uuid"          -> "UUID";
            default              -> type; // Enum ou tipo customizado
        };
    }

    /**
     * Retorna o import necessário para o tipo Java
     */
    public static String toJavaImport(String type) {
        if (type == null) return null;
        return switch (type.toLowerCase()) {
            case "bigdecimal"    -> "java.math.BigDecimal";
            case "localdate"     -> "java.time.LocalDate";
            case "localdatetime" -> "java.time.LocalDateTime";
            case "uuid"          -> "java.util.UUID";
            default              -> null;
        };
    }

    /**
     * Converte camelCase/PascalCase em label legível.
     * "emailCliente" → "Email Cliente"
     * "nomeCompleto" → "Nome Completo"
     * "status" → "Status"
     */
    public static String toHumanLabel(String name) {
        if (name == null || name.isBlank()) return name;
        String spaced = name.replaceAll("([a-z])([A-Z])", "$1 $2");
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }
}
