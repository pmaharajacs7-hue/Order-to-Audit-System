package com.orderaudit.bom;

import com.orderaudit.product.Product;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a recipe into a concrete plan for a product and number of batches.
 *
 *   expected packets = batchOutputKg * 1000 / packSizeG * batches   (rounded down)
 *   PER_BATCH material  = quantity * batches
 *   PER_PACKET material = quantity * expected packets
 *
 * Because output is measured by weight, a 200 g and a 500 g product share one BOM.
 * Callers must be inside a transaction (BOM items are loaded lazily).
 */
@Service
public class BomService {

    public record PlanLine(Long materialId, String materialName, String unit, String category,
                           BigDecimal standardQty, BigDecimal tolerancePct, String basis) {}

    public record Plan(int expectedPackets, List<PlanLine> lines) {}

    public Plan plan(Product product, Bom bom, BigDecimal batches) {
        BigDecimal packetsPerBatch = bom.getBatchOutputKg()
                .multiply(BigDecimal.valueOf(1000))
                .divide(BigDecimal.valueOf(product.getPackSizeG()), 6, RoundingMode.HALF_UP);
        int expected = packetsPerBatch.multiply(batches).setScale(0, RoundingMode.DOWN).intValueExact();

        List<PlanLine> lines = new ArrayList<>();
        for (BomItem item : bom.getItems()) {
            BigDecimal standard = BomItem.PER_PACKET.equals(item.getBasis())
                    ? item.getQuantity().multiply(BigDecimal.valueOf(expected))
                    : item.getQuantity().multiply(batches);
            lines.add(new PlanLine(item.getMaterial().getId(), item.getMaterial().getName(),
                    item.getMaterial().getUnit(), item.getMaterial().getCategory(),
                    standard.setScale(3, RoundingMode.HALF_UP), item.getTolerancePct(), item.getBasis()));
        }
        return new Plan(expected, lines);
    }
}
