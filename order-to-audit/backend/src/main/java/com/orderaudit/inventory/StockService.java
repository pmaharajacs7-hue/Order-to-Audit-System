package com.orderaudit.inventory;

import com.orderaudit.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * The ONLY place stock changes. Every module (purchase, production, dispatch, adjustment)
 * calls record(), which appends a ledger row. Nothing ever edits a stock number directly.
 *
 * Valuation method: weighted average cost of everything received at a known cost
 * (opening stock, purchases, production output).
 */
@Service
public class StockService {

    public static final String RAW_MATERIAL = "RAW_MATERIAL";
    public static final String PRODUCT = "PRODUCT";

    private final StockMovementRepository movements;
    private final CurrentUser currentUser;

    public StockService(StockMovementRepository movements, CurrentUser currentUser) {
        this.movements = movements;
        this.currentUser = currentUser;
    }

    public BigDecimal balance(String itemType, Long itemId) {
        BigDecimal b = movements.balance(itemType, itemId);
        return b == null ? BigDecimal.ZERO : b;
    }

    public Map<Long, BigDecimal> balances(String itemType) {
        Map<Long, BigDecimal> result = new HashMap<>();
        for (Object[] row : movements.balances(itemType)) {
            result.put((Long) row[0], (BigDecimal) row[1]);
        }
        return result;
    }

    /** Weighted average unit cost; zero if nothing has been received at a known cost. */
    public BigDecimal averageCost(String itemType, Long itemId) {
        BigDecimal qty = movements.receiptQuantity(itemType, itemId);
        BigDecimal value = movements.receiptValue(itemType, itemId);
        if (qty == null || value == null || qty.signum() == 0) return BigDecimal.ZERO;
        return value.divide(qty, 4, RoundingMode.HALF_UP);
    }

    /** Must run inside the caller's transaction so the ledger row commits or rolls back with the business change. */
    @Transactional(propagation = Propagation.MANDATORY)
    public StockMovement record(String itemType, Long itemId, String movementType, BigDecimal quantity,
                                BigDecimal unitCost, String referenceType, Long referenceId, String notes) {
        if (quantity == null || quantity.signum() == 0) {
            throw new IllegalArgumentException("Stock movement quantity cannot be zero");
        }
        BigDecimal cost = unitCost == null ? null : unitCost.setScale(4, RoundingMode.HALF_UP);
        return movements.save(new StockMovement(itemType, itemId, movementType,
                quantity.setScale(3, RoundingMode.HALF_UP), cost, referenceType, referenceId, notes,
                currentUser.id()));
    }
}
