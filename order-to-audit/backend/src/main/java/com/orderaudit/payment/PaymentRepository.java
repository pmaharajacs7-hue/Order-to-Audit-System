package com.orderaudit.payment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    List<Payment> findByInvoiceIdOrderByIdAsc(Long invoiceId);

    List<Payment> findAllByOrderByIdDesc();

    /** Null when nothing has been paid yet. */
    @Query("select sum(p.amount) from Payment p where p.invoice.id = :invoiceId")
    BigDecimal totalPaid(@Param("invoiceId") Long invoiceId);
}
