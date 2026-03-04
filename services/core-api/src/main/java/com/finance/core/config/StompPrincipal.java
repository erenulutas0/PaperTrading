package com.finance.core.config;

import java.security.Principal;
import java.util.Objects;

public class StompPrincipal implements Principal {

    private final String name;

    public StompPrincipal(String name) {
        this.name = Objects.requireNonNull(name, "name must not be null");
    }

    @Override
    public String getName() {
        return name;
    }
}

