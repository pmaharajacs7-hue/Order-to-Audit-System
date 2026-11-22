package com.orderaudit.report;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * Reports for the accountant, auditor and owner. Every report is available as JSON (for the dashboards)
 * and as CSV (add ?format=csv) for download.
 */
@RestController
@RequestMapping("/api/reports")
@PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','OWNER')")
public class ReportController {

    private final ReportService reports;

    public ReportController(ReportService reports) {
        this.reports = reports;
    }

    @GetMapping("/dashboard")
    public ReportService.Dashboard dashboard() {
        return reports.dashboard();
    }

    @GetMapping("/outstanding")
    public Object outstanding(@RequestParam(defaultValue = "false") boolean all,
                              @RequestParam(defaultValue = "json") String format) {
        var rows = reports.outstanding(all);
        return respond(format, "outstanding", rows,
                List.of("customer_id", "customer", "invoiced", "paid", "outstanding", "open_invoices"),
                r -> Arrays.asList(r.customerId(), r.customerName(), r.invoiced(), r.paid(), r.outstanding(), r.openInvoices()));
    }

    @GetMapping("/gst")
    public Object gst(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                      @RequestParam(defaultValue = "json") String format) {
        var rows = reports.gstSummary(from, to);
        return respond(format, "gst-summary", rows,
                List.of("month", "taxable_value", "cgst", "sgst", "igst", "total_tax", "invoice_total"),
                r -> Arrays.asList(r.period(), r.taxableValue(), r.cgst(), r.sgst(), r.igst(), r.totalTax(), r.invoiceTotal()));
    }

    @GetMapping("/sales")
    public Object sales(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                        @RequestParam(defaultValue = "json") String format) {
        var rows = reports.salesByProduct(from, to);
        return respond(format, "sales", rows,
                List.of("product_id", "product", "packets", "taxable_value", "invoice_value"),
                r -> Arrays.asList(r.productId(), r.productName(), r.packets(), r.taxableValue(), r.invoiceValue()));
    }

    @GetMapping("/purchases")
    public Object purchases(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            @RequestParam(defaultValue = "json") String format) {
        var rows = reports.purchasesByMaterial(from, to);
        return respond(format, "purchases", rows,
                List.of("material_id", "material", "unit", "quantity", "amount", "avg_price"),
                r -> Arrays.asList(r.materialId(), r.materialName(), r.unit(), r.quantity(), r.amount(), r.avgPrice()));
    }

    @GetMapping("/production")
    public Object production(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                             @RequestParam(defaultValue = "json") String format) {
        var rows = reports.production(from, to);
        return respond(format, "production", rows,
                List.of("run_id", "date", "product", "expected", "good", "yield_variance_pct", "flagged", "total_cost", "cost_per_packet"),
                r -> Arrays.asList(r.runId(), r.runDate(), r.productName(), r.expected(), r.good(),
                        r.yieldVariancePct(), r.flagged(), r.totalCost(), r.costPerPacket()));
    }

    @GetMapping("/profitability")
    public Object profitability(@RequestParam(defaultValue = "json") String format) {
        var rows = reports.profitability();
        return respond(format, "profitability", rows,
                List.of("product_id", "product", "selling_price", "cost_per_packet", "margin_per_packet", "margin_pct"),
                r -> Arrays.asList(r.productId(), r.productName(), r.sellingPrice(), r.costPerPacket(),
                        r.marginPerPacket(), r.marginPct()));
    }

    private <T> Object respond(String format, String name, List<T> rows, List<String> header,
                               java.util.function.Function<T, List<Object>> mapper) {
        if (!"csv".equalsIgnoreCase(format)) return rows;
        byte[] body = CsvWriter.write(header, rows.stream().map(mapper).toList());
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name + ".csv").build().toString())
                .body(body);
    }
}
