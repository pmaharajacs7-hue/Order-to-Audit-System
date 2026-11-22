package com.orderaudit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderaudit.audit.AuditLogRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static com.orderaudit.TestSupport.bearer;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
class MasterDataApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AuditLogRepository auditLogs;

    // ---------- helpers ----------

    private Map<String, Object> product(String sku) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", "Murukku 200 g");
        m.put("sku", sku);
        m.put("packSizeG", 200);
        m.put("unitPrice", 50.00);
        m.put("hsnCode", "19059090");
        m.put("gstRatePct", 5.00);
        return m;
    }

    private long createProduct(String token, String sku) throws Exception {
        String resp = mvc.perform(post("/api/products").header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(product(sku))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(resp).get("id").asLong();
    }

    private static String sku() {
        return "MUR-" + UUID.randomUUID().toString().substring(0, 8);
    }

    // ---------- products ----------

    @Test
    void adminCreatesProductAndOthersCanRead() throws Exception {
        String admin = bearer(mvc, json, "admin");
        String salesman = bearer(mvc, json, "salesman");
        long id = createProduct(admin, sku());

        mvc.perform(get("/api/products/" + id).header("Authorization", salesman))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gstRatePct").value(5.0))
                .andExpect(jsonPath("$.packSizeG").value(200));
    }

    @Test
    void salesmanCannotCreateOrChangeProducts() throws Exception {
        String salesman = bearer(mvc, json, "salesman");
        mvc.perform(post("/api/products").header("Authorization", salesman)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(product(sku()))))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/products/1").header("Authorization", salesman))
                .andExpect(status().isForbidden());
    }

    @Test
    void productsRequireLogin() throws Exception {
        mvc.perform(get("/api/products")).andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateSkuIs409() throws Exception {
        String admin = bearer(mvc, json, "admin");
        String sku = sku();
        createProduct(admin, sku);
        mvc.perform(post("/api/products").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(product(sku))))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidProductFieldsAre400() throws Exception {
        String admin = bearer(mvc, json, "admin");
        Map<String, Object> bad = product(sku());
        bad.put("gstRatePct", 150);        // over 100
        bad.put("hsnCode", "12");          // too short
        bad.put("packSizeG", 0);           // not positive
        mvc.perform(post("/api/products").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(bad)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unknownProductIs404() throws Exception {
        mvc.perform(get("/api/products/999999").header("Authorization", bearer(mvc, json, "admin")))
                .andExpect(status().isNotFound());
    }

    @Test
    void deactivatedProductDisappearsFromDefaultListButKeepsHistory() throws Exception {
        String admin = bearer(mvc, json, "admin");
        long id = createProduct(admin, sku());

        mvc.perform(delete("/api/products/" + id).header("Authorization", admin))
                .andExpect(status().isNoContent());

        String active = mvc.perform(get("/api/products").header("Authorization", admin))
                .andReturn().getResponse().getContentAsString();
        String all = mvc.perform(get("/api/products?includeInactive=true").header("Authorization", admin))
                .andReturn().getResponse().getContentAsString();
        assertFalse(active.contains("\"id\":" + id + ","));
        assertEquals(true, all.contains("\"id\":" + id + ","));
    }

    @Test
    void priceChangeIsAudited() throws Exception {
        String admin = bearer(mvc, json, "admin");
        long id = createProduct(admin, sku());

        Map<String, Object> changed = product(sku());
        changed.put("unitPrice", 55.00);
        mvc.perform(put("/api/products/" + id).header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(changed)))
                .andExpect(status().isOk());

        var trail = auditLogs.findByEntityTypeAndEntityIdOrderByIdAsc("PRODUCT", id);
        assertEquals(2, trail.size());
        assertEquals("CREATE", trail.get(0).getAction());
        assertEquals("UPDATE", trail.get(1).getAction());
        assertEquals(true, trail.get(1).getDetails().contains("55"));
        assertEquals(true, trail.get(1).getUserId() != null);
    }

    // ---------- customers ----------

    private Map<String, Object> customer(String gstin) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", "Sri Murugan Stores");
        if (gstin != null) m.put("gstin", gstin);
        m.put("phone", "9876543210");
        m.put("address", "Main Road, Madurai");
        m.put("stateCode", "33");
        return m;
    }

    @Test
    void customerCrudAndRoles() throws Exception {
        String admin = bearer(mvc, json, "admin");
        String salesman = bearer(mvc, json, "salesman");
        String supervisor = bearer(mvc, json, "supervisor");

        String resp = mvc.perform(post("/api/customers").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(customer("33ABCDE1234F1Z5"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(resp).get("id").asLong();

        // salesman can view (needs it to place orders) but not create
        mvc.perform(get("/api/customers/" + id).header("Authorization", salesman)).andExpect(status().isOk());
        mvc.perform(post("/api/customers").header("Authorization", salesman)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(customer(null))))
                .andExpect(status().isForbidden());
        // production supervisor has no business with customers
        mvc.perform(get("/api/customers").header("Authorization", supervisor)).andExpect(status().isForbidden());
    }

    @Test
    void badGstinIs400() throws Exception {
        mvc.perform(post("/api/customers").header("Authorization", bearer(mvc, json, "admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(customer("NOT-A-GSTIN"))))
                .andExpect(status().isBadRequest());
    }

    // ---------- suppliers ----------

    @Test
    void supplierVisibilityIsLimited() throws Exception {
        String admin = bearer(mvc, json, "admin");
        String owner = bearer(mvc, json, "owner");
        String salesman = bearer(mvc, json, "salesman");

        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", "Annapurna Flour Mills");
        s.put("phone", "9000000000");
        mvc.perform(post("/api/suppliers").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(s)))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/suppliers").header("Authorization", owner)).andExpect(status().isOk());
        mvc.perform(get("/api/suppliers").header("Authorization", salesman)).andExpect(status().isForbidden());
    }
}
