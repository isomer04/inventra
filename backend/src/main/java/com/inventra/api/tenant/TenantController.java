package com.inventra.api.tenant;

import com.inventra.api.tenant.dto.DeleteTenantRequest;
import com.inventra.api.tenant.dto.TenantResponse;
import com.inventra.api.tenant.dto.UpdateTenantRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Tenant", description = "Tenant profile management (ADMIN only)")
public class TenantController {

    private final TenantService tenantService;

    @GetMapping
    @Operation(summary = "Get the caller's tenant profile")
    public TenantResponse getTenant() {
        return tenantService.getTenant(TenantContext.requireTenantId());
    }

    @PutMapping
    @Operation(summary = "Update the caller's tenant name")
    public TenantResponse updateTenant(@Valid @RequestBody UpdateTenantRequest req) {
        return tenantService.updateTenant(TenantContext.requireTenantId(), req);
    }

    /**
     * Right-to-erasure endpoint (GDPR Art. 17).
     *
     * <p>Pseudonymises all PII for this tenant and suspends
     * the account. Supply the exact tenant slug as confirmation.
     *
     * <p><b>This action is irreversible.</b> All user names, emails, and customer
     * contact details are overwritten. The account cannot be reactivated.
     */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Erase tenant (GDPR right-to-erasure)",
        description = "Pseudonymises all PII for this tenant and permanently suspends the account. " +
                      "Supply confirmSlug matching your tenant slug. IRREVERSIBLE."
    )
    public void eraseTenant(@Valid @RequestBody DeleteTenantRequest req) {
        tenantService.eraseTenant(TenantContext.requireTenantId(), req.confirmSlug());
    }
}
