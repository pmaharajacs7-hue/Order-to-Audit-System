package com.orderaudit.sales;

import com.orderaudit.audit.AuditService;
import com.orderaudit.common.BusinessException;
import com.orderaudit.common.NotFoundException;
import com.orderaudit.customer.CustomerRepository;
import com.orderaudit.inventory.StockService;
import com.orderaudit.product.ProductRepository;
import com.orderaudit.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Order life cycle:  SUBMITTED -> APPROVED -> INVOICED -> DISPATCHED   (or SUBMITTED -> REJECTED)
 * A salesman only ever sees his own orders. Prices always come from the product, never from the client.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    public record OrderLineRequest(@NotNull Long productId, @Positive int quantity) {}

    public record OrderRequest(@NotNull Long customerId, @Size(max = 255) String notes,
                               @NotEmpty @Valid List<OrderLineRequest> items) {}

    public record OrderEditLineRequest(@NotNull Long productId, @Positive int quantity,
                                       @NotNull @DecimalMin("0.01") BigDecimal unitPrice) {}
    public record OrderEditRequest(@NotEmpty @Valid List<OrderEditLineRequest> items) {}

    public record RejectRequest(@Size(max = 255) String reason) {}

    public record OrderLineResponse(Long productId, String productName, int quantity,
                                    BigDecimal availableStock, boolean stockSufficient,
                                    BigDecimal unitPrice, BigDecimal lineTotal) {}

    public record OrderResponse(Long id, Long customerId, String customerName, String salesman, LocalDate orderDate,
                                String status, String notes, BigDecimal subtotal, List<OrderLineResponse> items) {}

    private final SalesOrderRepository orders;
    private final CustomerRepository customers;
    private final ProductRepository products;
    private final StockService stock;
    private final InvoiceService invoiceService;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public OrderController(SalesOrderRepository orders, CustomerRepository customers, ProductRepository products,
                           StockService stock, InvoiceService invoiceService, CurrentUser currentUser,
                           AuditService audit) {
        this.orders = orders;
        this.customers = customers;
        this.products = products;
        this.stock = stock;
        this.invoiceService = invoiceService;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','SALESMAN')")
    @Transactional
    public OrderResponse create(@Valid @RequestBody OrderRequest r) {
        var customer = customers.findById(r.customerId())
                .orElseThrow(() -> new NotFoundException("Customer", r.customerId()));
        if (!customer.isActive()) throw new IllegalArgumentException("Customer is inactive");

        // same product listed twice? merge the quantities
        Map<Long, Integer> qty = new LinkedHashMap<>();
        for (OrderLineRequest l : r.items()) qty.merge(l.productId(), l.quantity(), Integer::sum);

        SalesOrder order = new SalesOrder(customer, currentUser.get(), LocalDate.now(), r.notes());
        for (var e : qty.entrySet()) {
            var product = products.findById(e.getKey()).orElseThrow(() -> new NotFoundException("Product", e.getKey()));
            if (!product.isActive()) throw new IllegalArgumentException(product.getName() + " is inactive");
            order.addItem(new SalesOrderItem(product, e.getValue(), product.getUnitPrice()));
        }
        orders.saveAndFlush(order);
        audit.log("CREATE", "ORDER", order.getId(), "customer=" + customer.getName() + ", lines=" + qty.size());
        return toResponse(order);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','SALESMAN','ACCOUNTANT','OWNER')")
    @Transactional(readOnly = true)
    public List<OrderResponse> list() {
        var rows = currentUser.hasRole("SALESMAN")
                ? orders.findBySalesmanIdOrderByIdDesc(currentUser.id())
                : orders.findAllByOrderByIdDesc();
        return rows.stream().map(this::toResponse).toList();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','SALESMAN','ACCOUNTANT','OWNER')")
    @Transactional(readOnly = true)
    public OrderResponse get(@PathVariable Long id) {
        SalesOrder order = find(id);
        if (currentUser.hasRole("SALESMAN") && !order.getSalesman().getId().equals(currentUser.id())) {
            throw new AccessDeniedException("Not your order");
        }
        return toResponse(order);
    }

    /** Admin may correct quantities and prices before approving a submitted order. */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public OrderResponse edit(@PathVariable Long id, @Valid @RequestBody OrderEditRequest r) {
        SalesOrder order = find(id);
        requireStatus(order, SalesOrder.SUBMITTED, "edited");
        Map<Long, OrderEditLineRequest> lines = new LinkedHashMap<>();
        for (OrderEditLineRequest line : r.items()) {
            if (lines.put(line.productId(), line) != null) {
                throw new IllegalArgumentException("A product may appear only once in an edited order");
            }
        }
        String before = order.getItems().stream()
                .map(i -> i.getProduct().getName() + ":" + i.getQuantity() + "@" + i.getUnitPrice())
                .collect(java.util.stream.Collectors.joining(", "));
        List<SalesOrderItem> replacement = new java.util.ArrayList<>();
        for (OrderEditLineRequest line : lines.values()) {
            var product = products.findById(line.productId())
                    .orElseThrow(() -> new NotFoundException("Product", line.productId()));
            if (!product.isActive()) throw new IllegalArgumentException(product.getName() + " is inactive");
            replacement.add(new SalesOrderItem(product, line.quantity(), line.unitPrice()));
        }
        order.replaceItems(replacement);
        orders.saveAndFlush(order);
        String after = order.getItems().stream()
                .map(i -> i.getProduct().getName() + ":" + i.getQuantity() + "@" + i.getUnitPrice())
                .collect(java.util.stream.Collectors.joining(", "));
        audit.log("UPDATE", "ORDER", id, "before=[" + before + "], after=[" + after + "]");
        return toResponse(order);
    }

    /** Approval checks finished-goods stock covers the whole order. */
    @PostMapping("/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public OrderResponse approve(@PathVariable Long id) {
        SalesOrder order = find(id);
        requireStatus(order, SalesOrder.SUBMITTED, "approved");

        Map<Long, Integer> need = new LinkedHashMap<>();
        Map<Long, String> names = new LinkedHashMap<>();
        for (SalesOrderItem i : order.getItems()) {
            need.merge(i.getProduct().getId(), i.getQuantity(), Integer::sum);
            names.put(i.getProduct().getId(), i.getProduct().getName());
        }
        for (var e : need.entrySet()) {
            BigDecimal have = stock.balance(StockService.PRODUCT, e.getKey());
            if (have.compareTo(BigDecimal.valueOf(e.getValue())) < 0) {
                throw new BusinessException("Insufficient stock of " + names.get(e.getKey()) + ": ordered "
                        + e.getValue() + ", available " + have.stripTrailingZeros().toPlainString());
            }
        }
        order.approve(currentUser.id());
        audit.log("APPROVE", "ORDER", id, "customer=" + order.getCustomer().getName());
        return toResponse(order);
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public OrderResponse reject(@PathVariable Long id, @RequestBody(required = false) @Valid RejectRequest r) {
        SalesOrder order = find(id);
        requireStatus(order, SalesOrder.SUBMITTED, "rejected");
        order.reject(currentUser.id(), r == null ? null : r.reason());
        audit.log("REJECT", "ORDER", id, r == null ? "" : String.valueOf(r.reason()));
        return toResponse(order);
    }

    /** Creates the GST invoice for an approved order. */
    @PostMapping("/{id}/invoice")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public InvoiceService.InvoiceResponse invoice(@PathVariable Long id) {
        return invoiceService.createForOrder(id);
    }

    // ---------- helpers ----------

    private SalesOrder find(Long id) {
        return orders.findById(id).orElseThrow(() -> new NotFoundException("Order", id));
    }

    private void requireStatus(SalesOrder order, String expected, String action) {
        if (!expected.equals(order.getStatus())) {
            throw new BusinessException("Order " + order.getId() + " cannot be " + action
                    + " because it is " + order.getStatus());
        }
    }

    private OrderResponse toResponse(SalesOrder o) {
        List<OrderLineResponse> lines = o.getItems().stream().map(i -> {
            BigDecimal available = stock.balance(StockService.PRODUCT, i.getProduct().getId());
            return new OrderLineResponse(i.getProduct().getId(), i.getProduct().getName(), i.getQuantity(),
                    available, available.compareTo(BigDecimal.valueOf(i.getQuantity())) >= 0, i.getUnitPrice(),
                    i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())));
        }).toList();
        BigDecimal subtotal = lines.stream().map(OrderLineResponse::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new OrderResponse(o.getId(), o.getCustomer().getId(), o.getCustomer().getName(),
                o.getSalesman().getFullName(), o.getOrderDate(), o.getStatus(), o.getNotes(), subtotal, lines);
    }
}
