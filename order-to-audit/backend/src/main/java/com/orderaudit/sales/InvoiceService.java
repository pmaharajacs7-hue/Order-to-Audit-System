package com.orderaudit.sales;

import com.orderaudit.audit.AuditService;
import com.orderaudit.common.BusinessException;
import com.orderaudit.common.NotFoundException;
import com.orderaudit.inventory.StockService;
import com.orderaudit.payment.PaymentRepository;
import com.orderaudit.security.CurrentUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GST invoicing and dispatch.
 *
 * Tax per line = taxable value x GST rate. If the customer is in the seller's state the tax is split
 * into equal CGST + SGST, otherwise it is charged as IGST. Invoice totals are the sum of the line values,
 * so the PDF, the database and the reports always agree.
 */
@Service
public class InvoiceService {

    public record InvoiceLine(Long productId, String productName, String hsnCode, int quantity,
                              BigDecimal unitPrice, BigDecimal gstRatePct, BigDecimal taxableValue,
                              BigDecimal cgst, BigDecimal sgst, BigDecimal igst) {}

    public record InvoiceResponse(Long id, String invoiceNo, LocalDate invoiceDate, Long orderId, Long customerId,
                                  String customerName, String customerGstin, String customerAddress,
                                  String customerStateCode, String status, BigDecimal subtotal, BigDecimal cgst,
                                  BigDecimal sgst, BigDecimal igst, BigDecimal total, BigDecimal paid,
                                  BigDecimal balance, LocalDateTime dispatchedAt, List<InvoiceLine> items) {}

    private final SalesOrderRepository orders;
    private final InvoiceRepository invoices;
    private final PaymentRepository payments;
    private final StockService stock;
    private final CurrentUser currentUser;
    private final AuditService audit;
    private final String companyStateCode;

    public InvoiceService(SalesOrderRepository orders, InvoiceRepository invoices, PaymentRepository payments,
                          StockService stock, CurrentUser currentUser, AuditService audit,
                          @Value("${app.company.state-code:33}") String companyStateCode) {
        this.orders = orders;
        this.invoices = invoices;
        this.payments = payments;
        this.stock = stock;
        this.currentUser = currentUser;
        this.audit = audit;
        this.companyStateCode = companyStateCode;
    }

    @Transactional
    public InvoiceResponse createForOrder(Long orderId) {
        SalesOrder order = orders.findById(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
        if (!SalesOrder.APPROVED.equals(order.getStatus())) {
            throw new BusinessException("Only an APPROVED order can be invoiced (this one is " + order.getStatus() + ")");
        }
        if (invoices.findByOrderId(orderId).isPresent()) {
            throw new BusinessException("Order " + orderId + " already has an invoice");
        }

        boolean sameState = companyStateCode.equals(order.getCustomer().getStateCode());
        Invoice invoice = new Invoice(order, "TMP-" + UUID.randomUUID().toString().substring(0, 8),
                LocalDate.now(), currentUser.id());

        for (SalesOrderItem item : order.getItems()) {
            BigDecimal rate = item.getProduct().getGstRatePct();
            BigDecimal taxable = item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal tax = taxable.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal cgst = BigDecimal.ZERO.setScale(2);
            BigDecimal sgst = BigDecimal.ZERO.setScale(2);
            BigDecimal igst = BigDecimal.ZERO.setScale(2);
            if (sameState) {
                cgst = tax.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                sgst = tax.subtract(cgst); // keeps cgst + sgst exactly equal to the tax
            } else {
                igst = tax;
            }
            invoice.addItem(new InvoiceItem(item.getProduct(), item.getProduct().getHsnCode(), item.getQuantity(),
                    item.getUnitPrice(), rate, taxable, cgst, sgst, igst));
        }
        invoices.saveAndFlush(invoice);
        invoice.setInvoiceNo(financialYearInvoiceNumber(invoice.getInvoiceDate(), invoice.getId()));
        order.setStatus(SalesOrder.INVOICED);

        audit.log("CREATE", "INVOICE", invoice.getId(), invoice.getInvoiceNo() + " for order " + orderId
                + ", total=" + invoice.getTotal());
        return toResponse(invoice);
    }

    /** Dispatch hands the goods over: finished-goods stock goes down, in the ledger. */
    @Transactional
    public InvoiceResponse dispatch(Long invoiceId) {
        Invoice invoice = invoices.findById(invoiceId).orElseThrow(() -> new NotFoundException("Invoice", invoiceId));
        if (!Invoice.ISSUED.equals(invoice.getStatus())) {
            throw new BusinessException("Invoice " + invoice.getInvoiceNo() + " is already " + invoice.getStatus());
        }

        Map<Long, Integer> need = new LinkedHashMap<>();
        Map<Long, String> names = new LinkedHashMap<>();
        for (InvoiceItem i : invoice.getItems()) {
            need.merge(i.getProduct().getId(), i.getQuantity(), Integer::sum);
            names.put(i.getProduct().getId(), i.getProduct().getName());
        }
        for (var e : need.entrySet()) {
            BigDecimal have = stock.balance(StockService.PRODUCT, e.getKey());
            if (have.compareTo(BigDecimal.valueOf(e.getValue())) < 0) {
                throw new BusinessException("Insufficient stock of " + names.get(e.getKey()) + ": need "
                        + e.getValue() + ", have " + have.stripTrailingZeros().toPlainString());
            }
        }
        for (var e : need.entrySet()) {
            BigDecimal cost = stock.averageCost(StockService.PRODUCT, e.getKey());
            stock.record(StockService.PRODUCT, e.getKey(), "DISPATCH", BigDecimal.valueOf(e.getValue()).negate(),
                    cost, "INVOICE", invoice.getId(), invoice.getInvoiceNo());
        }
        invoice.markDispatched();
        invoice.getOrder().setStatus(SalesOrder.DISPATCHED);
        audit.log("DISPATCH", "INVOICE", invoice.getId(), invoice.getInvoiceNo());
        return toResponse(invoice);
    }

    private String financialYearInvoiceNumber(LocalDate date, Long id) {
        int startYear = date.getMonthValue() >= 4 ? date.getYear() : date.getYear() - 1;
        int endYear = (startYear + 1) % 100;
        return String.format("INV-%d-%02d-%05d", startYear, endYear, id);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(Long id) {
        return toResponse(invoices.findById(id).orElseThrow(() -> new NotFoundException("Invoice", id)));
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> list() {
        return invoices.findAllByOrderByIdDesc().stream().map(this::toResponse).toList();
    }

    InvoiceResponse toResponse(Invoice inv) {
        BigDecimal paid = payments.totalPaid(inv.getId());
        if (paid == null) paid = BigDecimal.ZERO.setScale(2);
        var customer = inv.getOrder().getCustomer();
        List<InvoiceLine> lines = inv.getItems().stream().map(i -> new InvoiceLine(i.getProduct().getId(),
                i.getProduct().getName(), i.getHsnCode(), i.getQuantity(), i.getUnitPrice(), i.getGstRatePct(),
                i.getTaxableValue(), i.getCgst(), i.getSgst(), i.getIgst())).toList();
        return new InvoiceResponse(inv.getId(), inv.getInvoiceNo(), inv.getInvoiceDate(), inv.getOrder().getId(),
                customer.getId(), customer.getName(), customer.getGstin(), customer.getAddress(),
                customer.getStateCode(), inv.getStatus(), inv.getSubtotal(), inv.getCgst(), inv.getSgst(),
                inv.getIgst(), inv.getTotal(), paid, inv.getTotal().subtract(paid), inv.getDispatchedAt(), lines);
    }
}
