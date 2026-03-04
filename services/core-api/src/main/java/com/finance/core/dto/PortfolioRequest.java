package com.finance.core.dto;

import lombok.Data;

@Data
public class PortfolioRequest {
    private String name;
    private String ownerId; // Temporary, in real app comes from token
    private String description;
    private String visibility; // PUBLIC or PRIVATE (defaults to PRIVATE)
}
