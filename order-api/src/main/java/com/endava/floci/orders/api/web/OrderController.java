package com.endava.floci.orders.api.web;

import com.endava.floci.orders.api.model.CreateOrderRequest;
import com.endava.floci.orders.api.model.OrderResponse;
import com.endava.floci.orders.api.service.OrderService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/orders")
public class OrderController {
    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> create(
            @Valid @RequestBody CreateOrderRequest request,
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 128) String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) @Size(max = 128)
                    String correlationId) {
        String effectiveCorrelation =
                correlationId == null || correlationId.isBlank()
                        ? UUID.randomUUID().toString()
                        : correlationId;
        OrderResponse response = service.create(request, idempotencyKey, effectiveCorrelation);
        return ResponseEntity.accepted()
                .header("X-Correlation-Id", response.correlationId())
                .body(response);
    }

    @GetMapping("/{orderId}")
    public OrderResponse get(@PathVariable UUID orderId) {
        return service.get(orderId);
    }
}
