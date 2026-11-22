package com.orderaudit.inventory;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {

    List<StockMovement> findByItemTypeAndItemIdOrderByIdAsc(String itemType, Long itemId);

    /** Current stock = sum of every movement. Null when the item has no movements yet. */
    @Query("select sum(m.quantity) from StockMovement m where m.itemType = :type and m.itemId = :id")
    BigDecimal balance(@Param("type") String type, @Param("id") Long id);

    /** itemId, balance for every item of a type that has movements. */
    @Query("select m.itemId, sum(m.quantity) from StockMovement m where m.itemType = :type group by m.itemId")
    List<Object[]> balances(@Param("type") String type);

    /** Value of everything received at a known cost (for weighted-average costing). */
    @Query("select sum(m.quantity * m.unitCost) from StockMovement m "
            + "where m.itemType = :type and m.itemId = :id and m.unitCost is not null and m.quantity > 0 "
            + "and m.movementType in ('OPENING','PURCHASE','PRODUCTION_OUTPUT')")
    BigDecimal receiptValue(@Param("type") String type, @Param("id") Long id);

    @Query("select sum(m.quantity) from StockMovement m "
            + "where m.itemType = :type and m.itemId = :id and m.unitCost is not null and m.quantity > 0 "
            + "and m.movementType in ('OPENING','PURCHASE','PRODUCTION_OUTPUT')")
    BigDecimal receiptQuantity(@Param("type") String type, @Param("id") Long id);
}
