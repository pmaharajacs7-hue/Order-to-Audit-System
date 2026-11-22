package com.orderaudit.payment;

import com.orderaudit.audit.AuditService;
import com.orderaudit.common.BusinessException;
import com.orderaudit.common.NotFoundException;
import com.orderaudit.sales.InvoiceRepository;
import com.orderaudit.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    public record PaymentRequest(
            @NotNull Long invoiceId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
            LocalDate paymentDate,
            @Pattern(regexp = "^(CASH|UPI|BANK_TRANSFER|CHEQUE)$", message = "must be CASH, UPI, BANK_TRANSFER or CHEQUE") String mode,
            @Size(max = 60) String reference) {}

    public record PaymentResponse(Long id, Long invoiceId, String invoiceNo, Long customerId, String customerName,
                                  BigDecimal amount, LocalDate paymentDate, String mode, String reference,
                                  BigDecimal invoiceBalanceAfter) {}

    private final PaymentRepository payments;
    private final InvoiceRepository invoices;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public PaymentController(PaymentRepository payments, InvoiceRepository invoices,
                             CurrentUser currentUser, AuditService audit) {
        this.payments = payments;
        this.invoices = invoices;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    /** Records money received against one invoice. Overpayment is rejected, so balances never go negative. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public PaymentResponse record(@Valid @RequestBody PaymentRequest r) {
        var invoice = invoices.findById(r.invoiceId()).orElseThrow(() -> new NotFoundException("Invoice", r.invoiceId()));
        BigDecimal paid = payments.totalPaid(invoice.getId());
        if (paid == null) paid = BigDecimal.ZERO;
        BigDecimal balance = invoice.getTotal().subtract(paid);
        if (r.amount().compareTo(balance) > 0) {
            throw new BusinessException("Payment " + r.amount() + " is more than the outstanding balance " + balance);
        }
        var customer = invoice.getOrder().getCustomer();
        Payment p = payments.saveAndFlush(new Payment(invoice, customer, r.amount(),
                r.paymentDate() != null ? r.paymentDate() : LocalDate.now(),
                r.mode() != null ? r.mode() : "CASH", r.reference(), currentUser.id()));
        audit.log("CREATE", "PAYMENT", p.getId(), invoice.getInvoiceNo() + " amount=" + r.amount());
        return toResponse(p, balance.subtract(r.amount()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','ACCOUNTANT','OWNER')")
    @Transactional(readOnly = true)
    public List<PaymentResponse> list(@RequestParam(required = false) Long invoiceId) {
        var rows = invoiceId == null ? payments.findAllByOrderByIdDesc()
                : payments.findByInvoiceIdOrderByIdAsc(invoiceId);
        return rows.stream().map(p -> toResponse(p, null)).toList();
    }

    private PaymentResponse toResponse(Payment p, BigDecimal balanceAfter) {
        return new PaymentResponse(p.getId(), p.getInvoice().getId(), p.getInvoice().getInvoiceNo(),
                p.getCustomer().getId(), p.getCustomer().getName(), p.getAmount(), p.getPaymentDate(),
                p.getMode(), p.getReference(), balanceAfter);
    }
}
