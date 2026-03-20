package com.finance.core.repository;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.EntityGraph;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;

import static org.junit.jupiter.api.Assertions.assertFalse;

class PortfolioRepositoryStructureTest {

    @Test
    void shouldNotExposePagedPortfolioMethodsWithEntityGraphItemsHydration() {
        for (Method method : PortfolioRepository.class.getDeclaredMethods()) {
            boolean returnsPagedPortfolio = false;
            if (Page.class.equals(method.getReturnType()) && method.getGenericReturnType() instanceof ParameterizedType parameterizedType) {
                returnsPagedPortfolio = parameterizedType.getActualTypeArguments().length == 1
                        && parameterizedType.getActualTypeArguments()[0].getTypeName().equals("com.finance.core.domain.Portfolio");
            }

            EntityGraph entityGraph = method.getAnnotation(EntityGraph.class);
            boolean hydratesItems = entityGraph != null
                    && java.util.Arrays.asList(entityGraph.attributePaths()).contains("items");

            assertFalse(
                    returnsPagedPortfolio && hydratesItems,
                    () -> "Paged Portfolio repository method must not use @EntityGraph(items): " + method.getName());
        }
    }
}
