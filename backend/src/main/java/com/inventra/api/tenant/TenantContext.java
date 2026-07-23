package com.inventra.api.tenant;

public final class TenantContext {

    private static final ThreadLocal<String> currentTenantId = new ThreadLocal<>();

    private TenantContext() {}

    public static void setTenantId(String tenantId) {
        currentTenantId.set(tenantId);
    }

    public static String getTenantId() {
        return currentTenantId.get();
    }

    public static void clear() {
        currentTenantId.remove();
    }

    /**
     * Returns whether a tenant context is currently set on this thread.
     * Use this in service methods that must be tenant-scoped to assert
     * they are not called from a context where TenantContext is absent
     * (e.g. @Scheduled tasks, @Async methods running on a different thread).
     *
     * <p><b>IMPORTANT — async/scheduled usage:</b> ThreadLocals are NOT
     * automatically propagated to child threads. Any {@code @Async} method
     * or {@code @Scheduled} task that calls a tenanted repository method
     * WITHOUT an explicit tenant scope will either:
     * <ul>
     *   <li>Return/modify data across ALL tenants (data leak / corruption), or
     *   <li>Throw a NullPointerException if the repository requires tenantId.
     * </ul>
     *
     * <p>Rules:
     * <ol>
     *   <li>Scheduled/async tasks operating across all tenants (e.g. token cleanup,
     *       retention jobs) must use a super-user/admin DB operation that explicitly
     *       handles all tenants. They must NOT set a fake tenantId.
     *   <li>Scheduled/async tasks scoped to a single tenant must receive the
     *       tenantId as a parameter and call {@link #setTenantId(String)} at the
     *       start of each item, clearing it in a finally block after each.
     *   <li>Never rely on TenantContext being set in a thread that did not set it
     *       explicitly — check with {@link #isSet()} before use.
     * </ol>
     *
     * <p>Explicit documentation of the
     * async propagation contract to prevent future cross-tenant data leaks.
     */
    public static boolean isSet() {
        return currentTenantId.get() != null;
    }

    /**
     * Returns the current tenant ID, throwing if it is not set.
     *
     * <p>Use this in every service method that requires a tenant context.
     * It replaces ad-hoc null checks and provides a consistent failure message.
     *
     * <p>Standardise TenantContext null-check
     * across all services. Previously only CustomerService had a guard.
     *
     * @return the current tenant ID (never null)
     * @throws IllegalStateException if called from a thread without a tenant context
     *         (e.g. a @Scheduled task or @Async method that did not propagate the context)
     */
    public static String requireTenantId() {
        String id = currentTenantId.get();
        if (id == null || id.isBlank()) {
            throw new IllegalStateException(
                    "TenantContext.requireTenantId() called from a thread without a tenant context. " +
                    "This method must only be called from within a request thread where JwtAuthFilter " +
                    "has set the tenant ID. For @Scheduled tasks see the contract in isSet() Javadoc.");
        }
        return id;
    }
}
