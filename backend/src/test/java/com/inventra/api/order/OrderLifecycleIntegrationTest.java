package com.inventra.api.order;

import com.fasterxml.jackson.databind.JsonNode;
import com.inventra.api.BaseIntegrationTest;
import com.inventra.api.auth.dto.TokenResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Order lifecycle integration tests.
 *
 * <p>Validates the full order state machine end-to-end:
 *   DRAFT -> SUBMITTED -> APPROVED -> PICKING -> SHIPPED -> DELIVERED
 *   plus REJECTED and CANCELLED branches.
 *
 * <p>Also verifies that stock is reserved on submit, released on reject/cancel,
 * and deducted on deliver — the most consequential business rule in the system.
 */
@DisplayName("Order Lifecycle")
class OrderLifecycleIntegrationTest extends BaseIntegrationTest {

    private TokenResponse token;
    private String productId;
    private String customerId;
    private final String suffix = UUID.randomUUID().toString().substring(0, 8);

    @BeforeEach
    void setUp() throws Exception {
        token = registerTenant("ord-" + suffix, "ord-" + suffix + "@test.com", "Password1!");

        String categoryId = createCategory("Hardware-" + suffix);
        productId = createProduct("SKU-" + suffix, "Widget-" + suffix, categoryId);
        customerId = createCustomer("Acme " + suffix);

        receiveStock(productId, 100);
    }

    @Nested
    @DisplayName("Happy path: DRAFT -> SUBMITTED -> APPROVED -> PICKING -> SHIPPED -> DELIVERED")
    class HappyPath {

        @Test
        @DisplayName("Order traverses every valid state and ends DELIVERED")
        void fullLifecycle() throws Exception {
            String orderId = createOrder(customerId, productId, 5);

            assertStatus(orderId, "DRAFT");

            transition(orderId, "submit");
            assertStatus(orderId, "SUBMITTED");
            assertAvailableStock(productId, 95); // 100 on hand, 5 reserved

            transition(orderId, "approve");
            assertStatus(orderId, "APPROVED");

            transition(orderId, "start-picking");
            assertStatus(orderId, "PICKING");

            transition(orderId, "ship");
            assertStatus(orderId, "SHIPPED");

            transition(orderId, "deliver");
            assertStatus(orderId, "DELIVERED");

            // Stock has now been deducted, not just reserved
            assertOnHandStock(productId, 95);
        }

        @Test
        @DisplayName("History records every transition")
        void historyHasEntryPerTransition() throws Exception {
            String orderId = createOrder(customerId, productId, 1);
            transition(orderId, "submit");
            transition(orderId, "approve");

            mockMvc.perform(get("/api/v1/orders/" + orderId + "/history")
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isOk())
                    // initial create + submit + approve = 3 entries
                    .andExpect(jsonPath("$.length()").value(3));
        }
    }

    @Nested
    @DisplayName("Stock reservation")
    class StockReservation {

        @Test
        @DisplayName("Submit reserves; reject releases the reservation")
        void rejectReleasesReservation() throws Exception {
            String orderId = createOrder(customerId, productId, 10);

            transition(orderId, "submit");
            assertAvailableStock(productId, 90);

            transition(orderId, "reject");
            assertAvailableStock(productId, 100); // Released
            assertOnHandStock(productId, 100);    // Never deducted
        }

        @Test
        @DisplayName("Submit reserves; cancel releases the reservation")
        void cancelReleasesReservation() throws Exception {
            String orderId = createOrder(customerId, productId, 7);

            transition(orderId, "submit");
            assertAvailableStock(productId, 93);

            transition(orderId, "cancel");
            assertAvailableStock(productId, 100);
            assertOnHandStock(productId, 100);
        }

        @Test
        @DisplayName("Cannot submit when stock is insufficient")
        void cannotSubmitWithoutEnoughStock() throws Exception {
            // Order requests 200 but only 100 are on hand
            String orderId = createOrder(customerId, productId, 200);

            mockMvc.perform(post("/api/v1/orders/" + orderId + "/submit")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isConflict()); // Insufficient stock -> 409
        }

        @Test
        @DisplayName("Deliver deducts on-hand stock")
        void deliverDeductsStock() throws Exception {
            String orderId = createOrder(customerId, productId, 25);

            transition(orderId, "submit");
            transition(orderId, "approve");
            transition(orderId, "start-picking");
            transition(orderId, "ship");

            assertOnHandStock(productId, 100); // Not deducted yet
            transition(orderId, "deliver");
            assertOnHandStock(productId, 75);  // Now deducted
        }
    }

    @Nested
    @DisplayName("Invalid transitions are rejected")
    class InvalidTransitions {

        @Test
        @DisplayName("Cannot approve a DRAFT order (must be SUBMITTED first)")
        void cannotApproveDraft() throws Exception {
            String orderId = createOrder(customerId, productId, 1);

            mockMvc.perform(post("/api/v1/orders/" + orderId + "/approve")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Cannot ship an APPROVED order without PICKING first")
        void cannotShipBeforePicking() throws Exception {
            String orderId = createOrder(customerId, productId, 1);
            transition(orderId, "submit");
            transition(orderId, "approve");

            mockMvc.perform(post("/api/v1/orders/" + orderId + "/ship")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Cannot transition from a terminal state (DELIVERED)")
        void cannotTransitionFromDelivered() throws Exception {
            String orderId = createOrder(customerId, productId, 1);
            transition(orderId, "submit");
            transition(orderId, "approve");
            transition(orderId, "start-picking");
            transition(orderId, "ship");
            transition(orderId, "deliver");

            // All these should fail because DELIVERED is terminal
            for (String op : List.of("submit", "approve", "reject", "start-picking",
                                      "ship", "deliver", "cancel")) {
                mockMvc.perform(post("/api/v1/orders/" + orderId + "/" + op)
                                .header("Authorization", bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                        .andExpect(status().isBadRequest());
            }
        }

        @Test
        @DisplayName("Cannot reject an APPROVED order (only SUBMITTED can be rejected)")
        void cannotRejectApproved() throws Exception {
            String orderId = createOrder(customerId, productId, 1);
            transition(orderId, "submit");
            transition(orderId, "approve");

            mockMvc.perform(post("/api/v1/orders/" + orderId + "/reject")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Cannot submit an empty order")
        void cannotSubmitEmptyOrder() throws Exception {
            // Create an order then update it to have no items
            String orderId = createOrder(customerId, productId, 1);

            mockMvc.perform(put("/api/v1/orders/" + orderId)
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "items", List.of()))))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/v1/orders/" + orderId + "/submit")
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("Edit and delete rules")
    class EditAndDelete {

        @Test
        @DisplayName("DRAFT order can be updated")
        void draftCanBeUpdated() throws Exception {
            String orderId = createOrder(customerId, productId, 1);

            mockMvc.perform(put("/api/v1/orders/" + orderId)
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("notes", "Updated notes"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.notes").value("Updated notes"));
        }

        @Test
        @DisplayName("SUBMITTED order cannot be updated")
        void submittedCannotBeUpdated() throws Exception {
            String orderId = createOrder(customerId, productId, 1);
            transition(orderId, "submit");

            mockMvc.perform(put("/api/v1/orders/" + orderId)
                            .header("Authorization", bearer(token))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("notes", "Should fail"))))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("DRAFT order can be deleted")
        void draftCanBeDeleted() throws Exception {
            String orderId = createOrder(customerId, productId, 1);

            mockMvc.perform(delete("/api/v1/orders/" + orderId)
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isNoContent());

            mockMvc.perform(get("/api/v1/orders/" + orderId)
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("SUBMITTED order cannot be deleted")
        void submittedCannotBeDeleted() throws Exception {
            String orderId = createOrder(customerId, productId, 1);
            transition(orderId, "submit");

            mockMvc.perform(delete("/api/v1/orders/" + orderId)
                            .header("Authorization", bearer(token)))
                    .andExpect(status().isBadRequest());
        }
    }

    private String createCategory(String name) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    private String createProduct(String sku, String name, String categoryId) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "sku", sku,
                                "name", name,
                                "categoryId", categoryId,
                                "unitPrice", new BigDecimal("19.99")))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    private String createCustomer(String name) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "name", name,
                                "email", name.toLowerCase().replace(" ", "") + "@example.com"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    private void receiveStock(String productId, int quantity) throws Exception {
        mockMvc.perform(put("/api/v1/inventory/" + productId + "/receive")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantity", quantity))))
                .andExpect(status().isOk());
    }

    private String createOrder(String customerId, String productId, int quantity) throws Exception {
        MvcResult r = mockMvc.perform(post("/api/v1/orders")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "customerId", customerId,
                                "items", List.of(Map.of(
                                        "productId", productId,
                                        "quantity", quantity))))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(r.getResponse().getContentAsString()).get("id").asText();
    }

    private void transition(String orderId, String operation) throws Exception {
        mockMvc.perform(post("/api/v1/orders/" + orderId + "/" + operation)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    private void assertStatus(String orderId, String expected) throws Exception {
        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(expected));
    }

    private void assertAvailableStock(String productId, int expected) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/inventory/" + productId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode node = objectMapper.readTree(r.getResponse().getContentAsString());
        int onHand = node.get("quantityOnHand").asInt();
        int reserved = node.get("quantityReserved").asInt();
        int available = onHand - reserved;
        if (available != expected) {
            throw new AssertionError(
                    "Expected available stock " + expected + " but was " + available
                            + " (onHand=" + onHand + ", reserved=" + reserved + ")");
        }
    }

    private void assertOnHandStock(String productId, int expected) throws Exception {
        mockMvc.perform(get("/api/v1/inventory/" + productId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantityOnHand").value(expected));
    }
}
