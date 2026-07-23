package com.inventra.api.repository;

import com.inventra.api.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Append-only persistence for {@link AuditLog}.
 * Only {@code save()} should ever be called from application code.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, String> {
}
