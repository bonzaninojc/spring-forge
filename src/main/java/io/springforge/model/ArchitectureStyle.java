package io.springforge.model;

/**
 * Estilo de arquitetura a ser gerado pelo Spring Forge.
 *
 * <ul>
 *   <li>{@code LAYERED}   – Arquitetura em camadas clássica.</li>
 *   <li>{@code HEXAGONAL} – Arquitetura Hexagonal (Ports &amp; Adapters).</li>
 *   <li>{@code MODULAR}   – Arquitetura modular por domínio/feature.</li>
 * </ul>
 */
public enum ArchitectureStyle {

    /**
     * Arquitetura em camadas tradicional Spring Boot.
     * Pacotes: entity, repository, service, service.impl, controller, dto, mapper, exception.
     */
    LAYERED,

    /**
     * Arquitetura Hexagonal — Ports &amp; Adapters.
     */
    HEXAGONAL,

    /**
     * Arquitetura modular por domínio/feature.
     *
     * Estrutura gerada por entidade:
     * <pre>
     * {basePackage}
     *  └── modules
     *      └── product
     *          ├── domain       ← entidade JPA e enums do módulo
     *          ├── repository   ← Spring Data repository do módulo
     *          ├── service      ← contrato de serviço
     *          │   └── impl     ← implementação
     *          ├── dto          ← DTOs do módulo
     *          ├── mapper       ← MapStruct mapper do módulo
     *          ├── specification← filtros/specifications do módulo
     *          ├── exception    ← exceções do módulo
     *          └── web          ← controller REST do módulo
     * </pre>
     */
    MODULAR,

    /** Módulos Spring Boot e frontend Vue/Vuetify inspirado no Sesi Laboral. */
    SESI_LABORAL
}
