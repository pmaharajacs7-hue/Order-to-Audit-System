package com.orderaudit.report;

import com.orderaudit.customer.CustomerRepository;
import com.orderaudit.payment.PaymentRepository;
import com.orderaudit.production.ProductionRun;
import com.orderaudit.production.ProductionRunRepository;
import com.orderaudit.product.ProductRepository;
import com.orderaudit.purchase.PurchaseRepository;
import com.orderaudit.sales.Invoice;
import com.orderaudit.sales.InvoiceRepository;
import com.orderaudit.inventory.StockService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/** Read-only calculations over the transaction tables. Nothing here changes data. */
@Service
@Transactional(readOnly = true)
public class ReportService {

    public record OutstandingRow(Long customerId, String customerName, BigDecimal invoiced, BigDecimal paid,
                                 BigDecimal outstanding, int openInvoices) {}

    public record GstRow(String period, BigDecimal taxableValue, BigDecimal cgst, BigDecimal sgst,
                         BigDecimal igst, BigDecimal totalTax, BigDecimal invoiceTotal) {}

    public record SalesRow(Long productId, String productName, long packets, BigDecimal taxableValue,
                           BigDecimal invoiceValue) {}

    public record PurchaseRow(Long materialId, String materialName, String unit, BigDecimal quantity,
                              BigDecimal amount, BigDecimal avgPrice) {}

    public record ProductionRow(Long runId, LocalDate runDate, String productName, int expected, int good,
                                BigDecimal yieldVariancePct, boolean flagged, BigDecimal totalCost,
                                BigDecimal costPerPacket) {}

    public record ProfitRow(Long productId, String productName, BigDecimal sellingPrice, BigDecimal costPerPacket,
                            BigDecimal marginPerPacket, BigDecimal marginPct) {}

    public record Dashboard(BigDecimal salesInvoiced, BigDecimal collected, BigDecimal outstanding,
                            BigDecimal purchasesTotal, int lowStockItems, int flaggedProductionRuns,
                            int ordersAwaitingApproval) {}

    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final PurchaseRepository purchases;
    private final ProductionRunRepository runs;
    private final ProductRepository products;
    private final CustomerRepository customers;
    private final StockService stock;
    private final com.orderaudit.inventory.RawMaterialRepository materials;
    private final com.orderaudit.sales.SalesOrderRepository orders;

    public ReportService(InvoiceRepository invoices, PaymentRepository payments, PurchaseRepository purchases,
                         ProductionRunRepository runs, ProductRepository products, CustomerRepository customers,
                         StockService stock, com.orderaudit.inventory.RawMaterialRepository materials,
                         com.orderaudit.sales.SalesOrderRepository orders) {
        this.invoices = invoices;
        this.payments = payments;
        this.purchases = purchases;
        this.runs = runs;
        this.products = products;
        this.customers = customers;
        this.stock = stock;
        this.materials = materials;
        this.orders = orders;
    }

    /** Per customer: total invoiced, total paid, still owed. Only customers who owe something are returned unless all=true. */
    public List<OutstandingRow> outstanding(boolean all) {
        Map<Long, BigDecimal> paidByInvoice = new HashMap<>();
        payments.findAll().forEach(p -> paidByInvoice.merge(p.getInvoice().getId(), p.getAmount(), BigDecimal::add));

        Map<Long, OutstandingRow> byCustomer = new LinkedHashMap<>();
        for (Invoice inv : invoices.findAll()) {
            var c = inv.getOrder().getCustomer();
            BigDecimal paid = paidByInvoice.getOrDefault(inv.getId(), BigDecimal.ZERO);
            BigDecimal due = inv.getTotal().subtract(paid);
            OutstandingRow old = byCustomer.get(c.getId());
            byCustomer.put(c.getId(), old == null
                    ? new OutstandingRow(c.getId(), c.getName(), inv.getTotal(), paid, due, due.signum() > 0 ? 1 : 0)
                    : new OutstandingRow(c.getId(), c.getName(), old.invoiced().add(inv.getTotal()),
                    old.paid().add(paid), old.outstanding().add(due), old.openInvoices() + (due.signum() > 0 ? 1 : 0)));
        }
        return byCustomer.values().stream()
                .filter(r -> all || r.outstanding().signum() > 0)
                .sorted(Comparator.comparing(OutstandingRow::outstanding).reversed()).toList();
    }

    /** GST collected per month (yyyy-MM) between two dates, inclusive. */
    public List<GstRow> gstSummary(LocalDate from, LocalDate to) {
        Map<String, BigDecimal[]> months = new TreeMap<>();
        for (Invoice inv : invoices.findAll()) {
            if (outside(inv.getInvoiceDate(), from, to)) continue;
            String key = inv.getInvoiceDate().toString().substring(0, 7);
            BigDecimal[] a = months.computeIfAbsent(key, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            a[0] = a[0].add(inv.getSubtotal());
            a[1] = a[1].add(inv.getCgst());
            a[2] = a[2].add(inv.getSgst());
            a[3] = a[3].add(inv.getIgst());
            a[4] = a[4].add(inv.getTotal());
        }
        List<GstRow> rows = new ArrayList<>();
        months.forEach((k, a) -> rows.add(new GstRow(k, a[0], a[1], a[2], a[3], a[1].add(a[2]).add(a[3]), a[4])));
        return rows;
    }

    public List<SalesRow> salesByProduct(LocalDate from, LocalDate to) {
        Map<Long, Object[]> acc = new LinkedHashMap<>();
        for (Invoice inv : invoices.findAll()) {
            if (outside(inv.getInvoiceDate(), from, to)) continue;
            inv.getItems().forEach(i -> {
                Object[] a = acc.computeIfAbsent(i.getProduct().getId(),
                        k -> new Object[]{i.getProduct().getName(), 0L, BigDecimal.ZERO, BigDecimal.ZERO});
                a[1] = (Long) a[1] + i.getQuantity();
                a[2] = ((BigDecimal) a[2]).add(i.getTaxableValue());
                a[3] = ((BigDecimal) a[3]).add(i.getTaxableValue()).add(i.getCgst()).add(i.getSgst()).add(i.getIgst());
            });
        }
        List<SalesRow> rows = new ArrayList<>();
        acc.forEach((id, a) -> rows.add(new SalesRow(id, (String) a[0], (Long) a[1], (BigDecimal) a[2], (BigDecimal) a[3])));
        return rows;
    }

    public List<PurchaseRow> purchasesByMaterial(LocalDate from, LocalDate to) {
        Map<Long, Object[]> acc = new LinkedHashMap<>();
        for (var p : purchases.findAll()) {
            if (outside(p.getPurchaseDate(), from, to)) continue;
            p.getItems().forEach(i -> {
                Object[] a = acc.computeIfAbsent(i.getMaterial().getId(), k -> new Object[]{
                        i.getMaterial().getName(), i.getMaterial().getUnit(), BigDecimal.ZERO, BigDecimal.ZERO});
                a[2] = ((BigDecimal) a[2]).add(i.getQuantity());
                a[3] = ((BigDecimal) a[3]).add(i.getLineTotal());
            });
        }
        List<PurchaseRow> rows = new ArrayList<>();
        acc.forEach((id, a) -> {
            BigDecimal qty = (BigDecimal) a[2];
            BigDecimal amt = (BigDecimal) a[3];
            BigDecimal avg = qty.signum() == 0 ? BigDecimal.ZERO : amt.divide(qty, 2, RoundingMode.HALF_UP);
            rows.add(new PurchaseRow(id, (String) a[0], (String) a[1], qty, amt, avg));
        });
        return rows;
    }

    public List<ProductionRow> production(LocalDate from, LocalDate to) {
        List<ProductionRow> rows = new ArrayList<>();
        for (ProductionRun r : runs.findAllByOrderByIdDesc()) {
            if (outside(r.getRunDate(), from, to)) continue;
            boolean flagged = r.isYieldFlagged() || r.getMaterials().stream().anyMatch(m -> m.isFlagged());
            rows.add(new ProductionRow(r.getId(), r.getRunDate(),
                    products.findById(r.getProductId()).map(p -> p.getName()).orElse("?"),
                    r.getExpectedOutput(), r.getGoodOutput(), r.getYieldVariancePct(), flagged,
                    r.getMaterialCost().add(r.getLabourCost()).add(r.getUtilitiesCost()), r.getCostPerPacket()));
        }
        return rows;
    }

    /** Latest production cost per packet against the selling price of each product. */
    public List<ProfitRow> profitability() {
        List<ProfitRow> rows = new ArrayList<>();
        for (var p : products.findByActiveTrueOrderByNameAsc()) {
            BigDecimal cost = runs.findAllByOrderByIdDesc().stream()
                    .filter(r -> r.getProductId().equals(p.getId()) && r.getCostPerPacket() != null)
                    .map(ProductionRun::getCostPerPacket).findFirst().orElse(null);
            if (cost == null) continue;
            BigDecimal margin = p.getUnitPrice().subtract(cost).setScale(2, RoundingMode.HALF_UP);
            BigDecimal pct = p.getUnitPrice().signum() == 0 ? BigDecimal.ZERO
                    : margin.multiply(BigDecimal.valueOf(100)).divide(p.getUnitPrice(), 2, RoundingMode.HALF_UP);
            rows.add(new ProfitRow(p.getId(), p.getName(), p.getUnitPrice(), cost.setScale(2, RoundingMode.HALF_UP),
                    margin, pct));
        }
        return rows;
    }

    public Dashboard dashboard() {
        BigDecimal invoiced = invoices.findAll().stream().map(Invoice::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal collected = payments.findAll().stream().map(p -> p.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal purchased = purchases.findAll().stream().map(p -> p.getTotalAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<Long, BigDecimal> bal = stock.balances(StockService.RAW_MATERIAL);
        int low = (int) materials.findByActiveTrueOrderByNameAsc().stream()
                .filter(m -> bal.getOrDefault(m.getId(), BigDecimal.ZERO).compareTo(m.getReorderLevel()) < 0).count();
        int flagged = (int) production(null, null).stream().filter(ProductionRow::flagged).count();
        int pending = (int) orders.findAll().stream().filter(o -> "SUBMITTED".equals(o.getStatus())).count();
        return new Dashboard(invoiced, collected, invoiced.subtract(collected), purchased, low, flagged, pending);
    }

    private static boolean outside(LocalDate d, LocalDate from, LocalDate to) {
        return (from != null && d.isBefore(from)) || (to != null && d.isAfter(to));
    }
}
