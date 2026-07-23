package com.inventra.api.order;

import com.inventra.api.tenant.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Year;
import java.util.List;

/**
 * Generates unique, monotonically increasing order numbers in the format {@code ORD-YYYY-NNNNN}.
 *
 * <p><b>Concurrency safety:</b> uses a dedicated {@code order_sequence} table and a single
 * {@code INSERT … ON DUPLICATE KEY UPDATE} statement to serialise concurrent allocations
 * within the same (tenant, year) pair. InnoDB takes the row's exclusive lock exactly once,
 * for the remainder of the transaction.
 *
 * <p>The previous MAX+1 pattern on the {@code order} table had a classic TOCTOU race:
 * two concurrent requests could read the same max sequence and produce the same number.
 * The unique constraint on {@code (tenant_id, order_number)} would catch the collision
 * and throw a constraint-violation, but there was no retry and the error was opaque.
 *
 * <p><b>Transaction contract:</b> must always be called within a transaction that
 * has {@link Propagation#REQUIRED} or stronger, so the row lock is held until the order
 * row is committed. {@code REQUIRES_NEW} is intentionally avoided — the sequence
 * increment and the order insert must be atomic.
 *
 * <p><b>Year rollover:</b> a new row is inserted (upsert) when the first order of a
 * new year arrives. Sequence numbers restart from 1 each year, which is conventional
 * for human-readable order numbers.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderNumberGenerator {

    private final EntityManager entityManager;

    /**
     * Atomically allocates the next order number for the current tenant and year.
     *
     * <p>Uses native SQL to:
     * <ol>
     *   <li>Upsert-and-increment (tenantId, year) in one statement — inserts {@code 1} on
     *       the first order of the year, otherwise increments {@code next_seq}.
     *   <li>Read the allocated value back. The upsert already holds the row's exclusive
     *       lock, and a transaction always sees its own uncommitted writes, so a plain
     *       {@code SELECT} cannot observe another allocator's value.
     * </ol>
     *
     * @return order number string in the form {@code ORD-YYYY-NNNNN}
     */
    @Transactional
    public String generateOrderNumber() {
        String tenantId = TenantContext.requireTenantId();
        int currentYear = Year.now().getValue();

        // Atomically upsert-and-increment in a single statement. A two-step
        // "INSERT IGNORE then UPDATE" (the previous approach) takes a gap lock
        // on INSERT IGNORE followed by a separate row-lock acquisition on
        // UPDATE, which under concurrent first-time inserts for the same
        // (tenant, year) key causes InnoDB lock-acquisition-order deadlocks
        // (Error 1213). A single INSERT ... ON DUPLICATE KEY UPDATE acquires
        // the lock exactly once, eliminating that window.
        Query upsert = entityManager.createNativeQuery(
                "INSERT INTO order_sequence (tenant_id, year, next_seq) VALUES (:tenantId, :year, 1) " +
                "ON DUPLICATE KEY UPDATE next_seq = next_seq + 1");
        upsert.setParameter("tenantId", tenantId);
        upsert.setParameter("year", currentYear);
        upsert.executeUpdate();

        // Read back the value we just wrote. No FOR UPDATE needed here: the
        // upsert above already holds the row's exclusive lock for the
        // remainder of this transaction, and a transaction always sees its
        // own uncommitted writes.
        @SuppressWarnings("unchecked")
        List<Number> result = entityManager.createNativeQuery(
                "SELECT next_seq FROM order_sequence WHERE tenant_id = :tenantId AND year = :year")
                .setParameter("tenantId", tenantId)
                .setParameter("year", currentYear)
                .getResultList();

        if (result.isEmpty()) {
            throw new IllegalStateException(
                    "order_sequence row missing after upsert — this should never happen. " +
                    "tenant=" + tenantId + " year=" + currentYear);
        }

        int sequence = result.get(0).intValue();
        String orderNumber = String.format("ORD-%d-%05d", currentYear, sequence);

        log.debug("Allocated order number {} for tenant {}", orderNumber, tenantId);
        return orderNumber;
    }
}
