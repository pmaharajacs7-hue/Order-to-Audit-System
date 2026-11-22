package com.orderaudit.sales;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoices;
    private final InvoicePdfService pdf;

    public InvoiceController(InvoiceService invoices, InvoicePdfService pdf) {
        this.invoices = invoices;
        this.pdf = pdf;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','OWNER')")
    public List<InvoiceService.InvoiceResponse> list() {
        return invoices.list();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','OWNER')")
    public InvoiceService.InvoiceResponse get(@PathVariable Long id) {
        return invoices.get(id);
    }

    @PostMapping("/{id}/dispatch")
    @PreAuthorize("hasRole('ADMIN')")
    public InvoiceService.InvoiceResponse dispatch(@PathVariable Long id) {
        return invoices.dispatch(id);
    }

    @GetMapping("/{id}/pdf")
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','OWNER')")
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        InvoiceService.InvoiceResponse inv = invoices.get(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(inv.invoiceNo() + ".pdf").build().toString())
                .body(pdf.render(inv));
    }
}
