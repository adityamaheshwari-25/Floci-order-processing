package com.endava.floci.orders.api.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.endava.floci.orders.api.model.OrderResponse;
import com.endava.floci.orders.api.service.OrderExceptions;
import com.endava.floci.orders.api.service.OrderService;
import com.endava.floci.orders.domain.OrderItem;
import com.endava.floci.orders.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class OrderControllerTest {
    private OrderService service;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = Mockito.mock(OrderService.class);
        mvc =
                MockMvcBuilders.standaloneSetup(new OrderController(service))
                        .setControllerAdvice(new ApiExceptionHandler())
                        .build();
    }

    @Test
    void acceptsAValidOrder() throws Exception {
        OrderResponse response = response();
        when(service.create(any(), eq("idem-1"), eq("corr-1"))).thenReturn(response);

        mvc.perform(
                        post("/api/v1/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-Key", "idem-1")
                                .header("X-Correlation-Id", "corr-1")
                                .content(
                                        """
                                        {"customerId":"customer","items":[{"sku":"SKU","quantity":1,"unitPrice":10.00}]}
                                        """))
                .andExpect(status().isAccepted())
                .andExpect(header().string("X-Correlation-Id", "corr-1"))
                .andExpect(jsonPath("$.status").value("RECEIVED"));
    }

    @Test
    void mapsValidationErrorsToProblemDetails() throws Exception {
        mvc.perform(
                        post("/api/v1/orders")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-Key", "idem-1")
                                .content("{\"customerId\":\"\",\"items\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.fieldErrors.customerId").exists())
                .andExpect(jsonPath("$.fieldErrors.items").exists());
    }

    @Test
    void mapsUnknownOrdersToProblemDetails() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(id)).thenThrow(new OrderExceptions.NotFound("missing"));

        mvc.perform(get("/api/v1/orders/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    private static OrderResponse response() {
        Instant now = Instant.parse("2026-08-25T12:00:00Z");
        return new OrderResponse(
                UUID.randomUUID(),
                "customer",
                OrderStatus.RECEIVED,
                List.of(new OrderItem("SKU", 1, BigDecimal.TEN)),
                "INR",
                BigDecimal.TEN,
                null,
                "corr-1",
                now,
                now);
    }
}
