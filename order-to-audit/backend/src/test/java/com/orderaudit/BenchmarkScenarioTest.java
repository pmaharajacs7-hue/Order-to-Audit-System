package com.orderaudit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.orderaudit.TestSupport.bearer;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end test using the numbers from the assignment brief:
 *   flour 100 kg + 60 kg purchased - 40 kg used = 120 kg
 *   yield 188 of 200 = -6%, oil 10.5 of 10 = +5%  (both flagged, 3% tolerance)
 *   150 packets x Rs 50 = 7,500 + 2.5% CGST + 2.5% SGST = 7,875 ; paid 4,000 -> outstanding 3,875
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class BenchmarkScenarioTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    // ---------- tiny HTTP helpers ----------

    private JsonNode call(String method, String token, String url, Object body, int expectedStatus) throws Exception {
        var req = method.equals("POST") ? post(url) : get(url);
        req.header("Authorization", token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        String resp = mvc.perform(req).andExpect(status().is(expectedStatus)).andReturn().getResponse().getContentAsString();
        return resp.isEmpty() ? json.createObjectNode() : json.readTree(resp);
    }

    private JsonNode created(String token, String url, Object body) throws Exception { return call("POST", token, url, body, 201); }
    private JsonNode ok(String token, String url, Object body) throws Exception { return call("POST", token, url, body, 200); }
    private JsonNode read(String token, String url) throws Exception { return call("GET", token, url, null, 200); }

    private static BigDecimal dec(JsonNode n) { return new BigDecimal(n.asText()); }

    private static void assertMoney(String expected, JsonNode actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(dec(actual)),
                "expected " + expected + " but was " + actual);
    }

    private static String uniq(String prefix) { return prefix + "-" + UUID.randomUUID().toString().substring(0, 8); }

    // ---------- fixture: Murukku with a recipe, stocked from opening + purchase ----------

    private record Fixture(long flour, long oil, long spice, long pouch, long carton, long bom, long product) {}

    private Fixture buildMurukku(String admin, int packSizeG, String stateCodeUnused) throws Exception {
        long flour = material(admin, uniq("Flour"), "RAW", "kg");
        long oil = material(admin, uniq("Oil"), "RAW", "litre");
        long spice = material(admin, uniq("Spice"), "RAW", "kg");
        long pouch = material(admin, uniq("Pouch"), "PACKAGING", "piece");
        long carton = material(admin, uniq("Carton"), "PACKAGING", "piece");

        // recipe: one batch yields 40 kg of snack = 200 packets of 200 g
        long bom = created(admin, "/api/boms", Map.of("name", uniq("Murukku BOM"), "batchOutputKg", 40, "items", List.of(
                Map.of("materialId", flour, "quantity", 40, "basis", "PER_BATCH"),
                Map.of("materialId", oil, "quantity", 10, "basis", "PER_BATCH"),
                Map.of("materialId", spice, "quantity", 1.2, "basis", "PER_BATCH"),
                Map.of("materialId", pouch, "quantity", 200, "basis", "PER_BATCH"),
                Map.of("materialId", carton, "quantity", 10, "basis", "PER_BATCH")))).get("id").asLong();

        long product = created(admin, "/api/products", Map.of("name", "Murukku " + packSizeG + " g", "sku", uniq("MUR"),
                "packSizeG", packSizeG, "unitPrice", 50.00, "hsnCode", "19059090", "gstRatePct", 5.00,
                "bomId", bom)).get("id").asLong();
        return new Fixture(flour, oil, spice, pouch, carton, bom, product);
    }

    private long material(String admin, String name, String category, String unit) throws Exception {
        return created(admin, "/api/raw-materials", Map.of("name", name, "category", category, "unit", unit,
                "reorderLevel", 0)).get("id").asLong();
    }

    private void opening(String admin, long materialId, double qty, double unitCost) throws Exception {
        created(admin, "/api/inventory/adjustments", Map.of("itemType", "RAW_MATERIAL", "itemId", materialId,
                "movementType", "OPENING", "quantity", qty, "unitCost", unitCost));
    }

    private long supplier(String admin) throws Exception {
        return created(admin, "/api/suppliers", Map.of("name", uniq("Supplier"))).get("id").asLong();
    }

    private long customer(String admin, String name, String stateCode) throws Exception {
        return created(admin, "/api/customers", Map.of("name", name, "stateCode", stateCode,
                "gstin", "33ABCDE1234F1Z5")).get("id").asLong();
    }

    private BigDecimal stock(String admin, String type, long id) throws Exception {
        JsonNode ledger = read(admin, "/api/inventory/ledger?itemType=" + type + "&itemId=" + id);
        return ledger.size() == 0 ? BigDecimal.ZERO : dec(ledger.get(ledger.size() - 1).get("balanceAfter"));
    }

    /** Loads the exact benchmark opening balances and flour purchase from the brief. */
    private void stockUp(String admin, Fixture f) throws Exception {
        opening(admin, f.flour, 100, 42);
        opening(admin, f.oil, 30, 140);
        opening(admin, f.spice, 5, 200);
        opening(admin, f.pouch, 500, 2.50);
        opening(admin, f.carton, 30, 15);
        created(admin, "/api/inventory/adjustments", Map.of("itemType", "PRODUCT", "itemId", f.product,
                "movementType", "OPENING", "quantity", 50, "unitCost", 0));
        created(admin, "/api/purchases", Map.of("supplierId", supplier(admin), "invoiceNo", uniq("PUR"),
                "items", List.of(Map.of("materialId", f.flour, "quantity", 60, "unitPrice", 45))));
    }

    private JsonNode runBenchmarkProduction(String supervisor, Fixture f) throws Exception {
        return created(supervisor, "/api/production", Map.of(
                "productId", f.product, "batches", 1, "actualOutput", 188, "goodOutput", 188, "damagedUnits", 6,
                "labourCost", 1200, "utilitiesCost", 600,
                "materials", List.of(
                        Map.of("materialId", f.flour, "actualQty", 40),
                        Map.of("materialId", f.oil, "actualQty", 10.5),
                        Map.of("materialId", f.spice, "actualQty", 1.2),
                        Map.of("materialId", f.pouch, "actualQty", 194),
                        Map.of("materialId", f.carton, "actualQty", 9))));
    }

    // ---------- tests ----------

    @Test
    void fullOrderToAuditScenario() throws Exception {
        String admin = bearer(mvc, json, "admin");
        String supervisor = bearer(mvc, json, "supervisor");
        String salesman = bearer(mvc, json, "salesman");
        String accountant = bearer(mvc, json, "accountant");
        Fixture f = buildMurukku(admin, 200, null);
        stockUp(admin, f);

        // flour before production: 100 opening + 60 purchase = 160
        assertEquals(0, new BigDecimal("160").compareTo(stock(admin, "RAW_MATERIAL", f.flour)));

        // ---- production: the supervisor records what really happened ----
        JsonNode run = runBenchmarkProduction(supervisor, f);
        assertEquals(200, run.get("expectedOutput").asInt());
        assertEquals(188, run.get("goodOutput").asInt());
        assertMoney("-6.00", run.get("yieldVariancePct"));
        assertTrue(run.get("yieldFlagged").asBoolean());
        assertTrue(run.get("anyFlag").asBoolean());

        for (JsonNode m : run.get("materials")) {
            long id = m.get("materialId").asLong();
            if (id == f.oil) {
                assertMoney("5.00", m.get("variancePct"));
                assertTrue(m.get("flagged").asBoolean(), "oil +5% exceeds 3% tolerance");
            }
            if (id == f.flour) {
                assertMoney("0.00", m.get("variancePct"));
                assertFalse(m.get("flagged").asBoolean());
            }
        }

        // flour closing stock = 100 + 60 - 40 = 120 (the brief's example)
        assertEquals(0, new BigDecimal("120").compareTo(stock(admin, "RAW_MATERIAL", f.flour)));
        // Other benchmark closing balances: oil 30 - 10.5, spice 5 - 1.2,
        // pouches 500 - (188 good + 6 damaged), cartons 30 - 9.
        assertEquals(0, new BigDecimal("19.5").compareTo(stock(admin, "RAW_MATERIAL", f.oil)));
        assertEquals(0, new BigDecimal("3.8").compareTo(stock(admin, "RAW_MATERIAL", f.spice)));
        assertEquals(0, new BigDecimal("306").compareTo(stock(admin, "RAW_MATERIAL", f.pouch)));
        assertEquals(0, new BigDecimal("21").compareTo(stock(admin, "RAW_MATERIAL", f.carton)));
        // Opening finished goods 50 + 188 produced = 238.
        assertEquals(0, new BigDecimal("238").compareTo(stock(admin, "PRODUCT", f.product)));

        // Weighted-average flour cost: (100x42 + 60x45) / 160 = 43.125.
        // Material cost = flour 1725 + oil 1470 + spice 240 + pouches 485 + cartons 135 = 4055.
        // Add labour 1200 and gas/power 600: total 5855 / 188 good packets = 31.1436.
        assertMoney("4055.00", run.get("materialCost"));
        assertMoney("5855.00", run.get("totalCost"));
        assertMoney("31.1436", run.get("costPerPacket"));

        // ---- sales order: 150 packets at Rs 50 ----
        long cust = customer(admin, uniq("Retailer A"), "33");
        JsonNode order = created(salesman, "/api/orders", Map.of("customerId", cust,
                "items", List.of(Map.of("productId", f.product, "quantity", 150))));
        long orderId = order.get("id").asLong();
        assertEquals("SUBMITTED", order.get("status").asText());
        assertMoney("7500.00", order.get("subtotal"));

        // approve -> invoice
        assertEquals("APPROVED", ok(admin, "/api/orders/" + orderId + "/approve", null).get("status").asText());
        JsonNode invoice = created(admin, "/api/orders/" + orderId + "/invoice", null);
        long invoiceId = invoice.get("id").asLong();
        assertMoney("7500.00", invoice.get("subtotal"));
        assertMoney("187.50", invoice.get("cgst"));
        assertMoney("187.50", invoice.get("sgst"));
        assertMoney("0.00", invoice.get("igst"));
        assertMoney("7875.00", invoice.get("total"));
        assertMoney("7875.00", invoice.get("balance"));
        assertTrue(invoice.get("invoiceNo").asText().matches("INV-\\d{4}-\\d{2}-\\d{5}"),
                "invoice number should include financial-year start/end and a sequence");

        // creating the invoice twice must fail
        call("POST", admin, "/api/orders/" + orderId + "/invoice", null, 409);

        // stock is unchanged until dispatch: 50 opening + 188 produced = 238
        assertEquals(0, new BigDecimal("238").compareTo(stock(admin, "PRODUCT", f.product)));

        // ---- dispatch reduces finished goods: 238 - 150 = 88 ----
        assertEquals("DISPATCHED", ok(admin, "/api/invoices/" + invoiceId + "/dispatch", null).get("status").asText());
        assertEquals(0, new BigDecimal("88").compareTo(stock(admin, "PRODUCT", f.product)));
        call("POST", admin, "/api/invoices/" + invoiceId + "/dispatch", null, 409); // not twice

        // ---- Distributor B (Karnataka) orders 120; only 88 remain, so 32 are short ----
        long distributor = customer(admin, uniq("Distributor B"), "29");
        JsonNode distributorOrderResponse = created(salesman, "/api/orders", Map.of("customerId", distributor,
                "items", List.of(Map.of("productId", f.product, "quantity", 120))));
        long distributorOrder = distributorOrderResponse.get("id").asLong();
        assertMoney("88", distributorOrderResponse.get("items").get(0).get("availableStock"));
        assertFalse(distributorOrderResponse.get("items").get(0).get("stockSufficient").asBoolean());
        call("POST", admin, "/api/orders/" + distributorOrder + "/approve", null, 409);
        assertEquals("SUBMITTED", read(admin, "/api/orders/" + distributorOrder).get("status").asText());
        assertEquals(0, new BigDecimal("88").compareTo(stock(admin, "PRODUCT", f.product)));

        // ---- payment: Rs 4,000 of 7,875 -> outstanding 3,875 ----
        JsonNode pay = created(admin, "/api/payments", Map.of("invoiceId", invoiceId, "amount", 4000, "mode", "UPI"));
        assertMoney("3875.00", pay.get("invoiceBalanceAfter"));

        // paying more than is owed is rejected
        call("POST", admin, "/api/payments", Map.of("invoiceId", invoiceId, "amount", 3875.01), 409);
        call("POST", accountant, "/api/payments", Map.of("invoiceId", invoiceId, "amount", 1), 403);

        // ---- reports see the same numbers ----
        String retailer = order.get("customerName").asText();
        JsonNode outstanding = read(accountant, "/api/reports/outstanding");
        boolean found = false;
        for (JsonNode row : outstanding) {
            if (row.get("customerName").asText().equals(retailer)) {
                found = true;
                assertMoney("7875.00", row.get("invoiced"));
                assertMoney("4000.00", row.get("paid"));
                assertMoney("3875.00", row.get("outstanding"));
            }
        }
        assertTrue(found, "customer with a balance must appear in the outstanding report");

        // profitability: sells at 50, costs 31.14 -> margin 18.86
        boolean profitFound = false;
        for (JsonNode row : read(accountant, "/api/reports/profitability")) {
            if (row.get("productId").asLong() == f.product) {
                profitFound = true;
                assertMoney("31.14", row.get("costPerPacket"));
                assertMoney("18.86", row.get("marginPerPacket"));
            }
        }
        assertTrue(profitFound);

        // CSV export
        String csv = mvc.perform(get("/api/reports/outstanding?format=csv").header("Authorization", accountant))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(csv.contains("customer,invoiced,paid,outstanding"));
        assertTrue(csv.contains(retailer));

        // invoice PDF
        byte[] pdf = mvc.perform(get("/api/invoices/" + invoiceId + "/pdf").header("Authorization", accountant))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertEquals("%PDF", new String(pdf, 0, 4));
    }

    @Test
    void orderBiggerThanFinishedStockCannotBeApproved() throws Exception {
        String admin = bearer(mvc, json, "admin");
        String supervisor = bearer(mvc, json, "supervisor");
        String salesman = bearer(mvc, json, "salesman");
        Fixture f = buildMurukku(admin, 200, null);
        stockUp(admin, f);
        runBenchmarkProduction(supervisor, f); // 238 in stock (50 opening + 188 produced)

        long cust = customer(admin, uniq("Big Buyer"), "33");
        long orderId = created(salesman, "/api/orders", Map.of("customerId", cust,
                "items", List.of(Map.of("productId", f.product, "quantity", 239)))).get("id").asLong();
        call("POST", admin, "/api/orders/" + orderId + "/approve", null, 409);
        // still waiting, not approved
        assertEquals("SUBMITTED", read(admin, "/api/orders/" + orderId).get("status").asText());
    }

    @Test
    void productionWithoutEnoughMaterialChangesNothing() throws Exception {
        String admin = bearer(mvc, json, "admin");
        Fixture f = buildMurukku(admin, 200, null);
        // only flour is stocked; oil, spice, pouches and cartons are missing
        opening(admin, f.flour, 100, 40);

        call("POST", admin, "/api/production", Map.of("productId", f.product, "batches", 1,
                "actualOutput", 200, "goodOutput", 200), 409);

        // all-or-nothing: flour untouched, no finished goods created
        assertEquals(0, new BigDecimal("100").compareTo(stock(admin, "RAW_MATERIAL", f.flour)));
        assertEquals(0, BigDecimal.ZERO.compareTo(stock(admin, "PRODUCT", f.product)));
    }

    @Test
    void sameRecipeServesA500gProduct() throws Exception {
        String admin = bearer(mvc, json, "admin");
        Fixture f = buildMurukku(admin, 200, null);

        // second product, 500 g pack, linked to the SAME BOM
        long big = created(admin, "/api/products", Map.of("name", "Murukku 500 g", "sku", uniq("MUR5"),
                "packSizeG", 500, "unitPrice", 120.00, "hsnCode", "19059090", "gstRatePct", 5.00,
                "bomId", f.bom)).get("id").asLong();

        // one 40 kg batch = 200 packs of 200 g, or 80 packs of 500 g
        assertEquals(200, read(admin, "/api/boms/" + f.bom + "/plan?productId=" + f.product + "&batches=1").get("expectedPackets").asInt());
        assertEquals(80, read(admin, "/api/boms/" + f.bom + "/plan?productId=" + big + "&batches=1").get("expectedPackets").asInt());
        assertEquals(160, read(admin, "/api/boms/" + f.bom + "/plan?productId=" + big + "&batches=2").get("expectedPackets").asInt());
    }

    @Test
    void customerInAnotherStateGetsIgst() throws Exception {
        String admin = bearer(mvc, json, "admin");
        String supervisor = bearer(mvc, json, "supervisor");
        String salesman = bearer(mvc, json, "salesman");
        Fixture f = buildMurukku(admin, 200, null);
        stockUp(admin, f);
        runBenchmarkProduction(supervisor, f);

        long cust = customer(admin, uniq("Kerala Mart"), "32"); // seller is in state 33
        long orderId = created(salesman, "/api/orders", Map.of("customerId", cust,
                "items", List.of(Map.of("productId", f.product, "quantity", 150)))).get("id").asLong();
        ok(admin, "/api/orders/" + orderId + "/approve", null);
        JsonNode invoice = created(admin, "/api/orders/" + orderId + "/invoice", null);

        assertMoney("375.00", invoice.get("igst"));
        assertMoney("0.00", invoice.get("cgst"));
        assertMoney("0.00", invoice.get("sgst"));
        assertMoney("7875.00", invoice.get("total"));
    }

    @Test
    void invoiceKeepsOldPriceAfterProductPriceChanges() throws Exception {
        String admin = bearer(mvc, json, "admin");
        String supervisor = bearer(mvc, json, "supervisor");
        String salesman = bearer(mvc, json, "salesman");
        Fixture f = buildMurukku(admin, 200, null);
        stockUp(admin, f);
        runBenchmarkProduction(supervisor, f);

        long cust = customer(admin, uniq("Price Test"), "33");
        long orderId = created(salesman, "/api/orders", Map.of("customerId", cust,
                "items", List.of(Map.of("productId", f.product, "quantity", 10)))).get("id").asLong();

        // price goes up after the order was placed
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/products/" + f.product)
                .header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", "Murukku 200 g", "sku", uniq("MUR"), "packSizeG", 200,
                        "unitPrice", 99.00, "hsnCode", "19059090", "gstRatePct", 5.00, "bomId", f.bom))))
                .andExpect(status().isOk());

        ok(admin, "/api/orders/" + orderId + "/approve", null);
        JsonNode invoice = created(admin, "/api/orders/" + orderId + "/invoice", null);
        assertMoney("500.00", invoice.get("subtotal")); // 10 x 50, not 10 x 99
    }

    @Test
    void rolesGuardTheBusinessActions() throws Exception {
        String salesman = bearer(mvc, json, "salesman");
        String supervisor = bearer(mvc, json, "supervisor");
        String accountant = bearer(mvc, json, "accountant");

        call("POST", salesman, "/api/orders/1/approve", null, 403);          // only admin approves
        call("POST", supervisor, "/api/orders", Map.of("customerId", 1, "items", List.of(Map.of("productId", 1, "quantity", 1))), 403);
        call("POST", salesman, "/api/production", Map.of("productId", 1, "actualOutput", 1, "goodOutput", 1), 403);
        call("POST", accountant, "/api/production", Map.of("productId", 1, "actualOutput", 1, "goodOutput", 1), 403);
        call("POST", salesman, "/api/purchases", Map.of("supplierId", 1, "items", List.of(Map.of("materialId", 1, "quantity", 1, "unitPrice", 1))), 403);
        call("GET", salesman, "/api/reports/dashboard", null, 403);
        call("GET", supervisor, "/api/reports/outstanding", null, 403);
        call("GET", salesman, "/api/invoices", null, 403);
    }

    @Test
    void auditTrailRecordsWhoDidWhatAndIsReadableByAuditorsOnly() throws Exception {
        String admin = bearer(mvc, json, "admin");
        String accountant = bearer(mvc, json, "accountant");
        String salesman = bearer(mvc, json, "salesman");
        long flour = material(admin, uniq("Flour"), "RAW", "kg");

        JsonNode trail = read(accountant, "/api/audit?entityType=RAW_MATERIAL&entityId=" + flour);
        assertEquals(1, trail.size());
        assertEquals("CREATE", trail.get(0).get("action").asText());
        assertEquals("admin", trail.get(0).get("username").asText());

        assertTrue(read(accountant, "/api/audit").size() > 0);
        call("GET", salesman, "/api/audit", null, 403);
    }

    @Test
    void stockIsOnlyEverChangedThroughTheLedger() throws Exception {
        String admin = bearer(mvc, json, "admin");
        long flour = material(admin, uniq("Flour"), "RAW", "kg");
        opening(admin, flour, 100, 40);
        created(admin, "/api/purchases", Map.of("supplierId", supplier(admin),
                "items", List.of(Map.of("materialId", flour, "quantity", 60, "unitPrice", 42))));
        created(admin, "/api/inventory/adjustments", Map.of("itemType", "RAW_MATERIAL", "itemId", flour,
                "movementType", "ADJUSTMENT", "quantity", -5, "notes", "spillage"));

        JsonNode ledger = read(admin, "/api/inventory/ledger?itemType=RAW_MATERIAL&itemId=" + flour);
        assertEquals(3, ledger.size());
        assertEquals("OPENING", ledger.get(0).get("movementType").asText());
        assertEquals(0, new BigDecimal("100").compareTo(dec(ledger.get(0).get("balanceAfter"))));
        assertEquals(0, new BigDecimal("160").compareTo(dec(ledger.get(1).get("balanceAfter"))));
        assertEquals(0, new BigDecimal("155").compareTo(dec(ledger.get(2).get("balanceAfter"))));

        // cannot adjust stock below zero
        call("POST", admin, "/api/inventory/adjustments", Map.of("itemType", "RAW_MATERIAL", "itemId", flour,
                "movementType", "ADJUSTMENT", "quantity", -1000), 409);
    }
}
