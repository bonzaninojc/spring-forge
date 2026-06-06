package io.springforge.mojo;

import io.springforge.model.FilterDefinition;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ForgeReverseMojoTest {

    /**
     * Testa a lógica do FilterDefinition.resolveOperator() (convenções automáticas)
     * e FilterDefinition.resolveTargetField().
     */
    @Test
    void filterDefinitionResolveOperatorConventions() {
        FilterDefinition f1 = new FilterDefinition();
        f1.setName("name");
        f1.setType("String");
        assertEquals("CONTAINS", f1.resolveOperator());

        FilterDefinition f2 = new FilterDefinition();
        f2.setName("priceMin");
        f2.setType("BigDecimal");
        assertEquals("GREATER_THAN_OR_EQUAL", f2.resolveOperator());
        assertEquals("price", f2.resolveTargetField());

        FilterDefinition f3 = new FilterDefinition();
        f3.setName("priceMax");
        f3.setType("BigDecimal");
        assertEquals("LESS_THAN_OR_EQUAL", f3.resolveOperator());
        assertEquals("price", f3.resolveTargetField());

        FilterDefinition f4 = new FilterDefinition();
        f4.setName("status");
        f4.setType("Enum");
        assertEquals("EQUALS", f4.resolveOperator());

        FilterDefinition f5 = new FilterDefinition();
        f5.setName("active");
        f5.setType("Boolean");
        assertEquals("EQUALS", f5.resolveOperator());
    }

    @Test
    void filterDefinitionExplicitOperatorOverridesConvention() {
        FilterDefinition f = new FilterDefinition();
        f.setName("name");
        f.setType("String");
        f.setOperator("STARTS_WITH");
        assertEquals("STARTS_WITH", f.resolveOperator());
    }

    @Test
    void filterDefinitionTargetFieldExplicit() {
        FilterDefinition f = new FilterDefinition();
        f.setName("valorMinimo");
        f.setType("BigDecimal");
        f.setTargetField("price");
        assertEquals("price", f.resolveTargetField());
    }
}
