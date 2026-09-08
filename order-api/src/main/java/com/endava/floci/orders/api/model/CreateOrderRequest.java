package com.endava.floci.orders.api.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record CreateOrderRequest(
        @NotBlank @Size(max = 100) String customerId,
        @NotEmpty @Size(max = 50) List<@Valid Item> items,
        @Size(min = 3, max = 3) String currency) {
    public record Item(
            @NotBlank @Size(max = 64) String sku,
            @Min(1) @Max(1000) int quantity,
            @DecimalMin("0.00") @DecimalMax("10000000.00") BigDecimal unitPrice) {}
}
