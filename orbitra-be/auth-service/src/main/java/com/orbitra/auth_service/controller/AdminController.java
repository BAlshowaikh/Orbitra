/*
  AdminController.java
  Thin HTTP layer for admin-only account management: list all accounts, and
  activate/deactivate one by id. Restricted to ADMIN role in SecurityConfig.
  Business logic lives entirely in AccountAdminService.
*/
package com.orbitra.auth_service.controller;

// ----------- IMPORTS -----------
import com.orbitra.auth_service.dto.AccountSummaryResponse;
import com.orbitra.auth_service.dto.PagedResponse;
import com.orbitra.auth_service.dto.UpdateAccountStatusRequest;
import com.orbitra.auth_service.service.AccountAdminService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth/admin")
public class AdminController {

    private final AccountAdminService accountAdminService;

    public AdminController(AccountAdminService accountAdminService) {
        this.accountAdminService = accountAdminService;
    }

    // ------------------ Endpoint 1: List accounts (paginated) -----------------
    // ?page=0&size=20&sort=email,asc - Pageable is resolved from these query
    // params automatically. @PageableDefault sets the fallback when a caller
    // omits them entirely.
    @GetMapping("/accounts")
    public PagedResponse<AccountSummaryResponse> listAccounts(@PageableDefault(size = 20) Pageable pageable) {
        return accountAdminService.listAccounts(pageable);
    }

    // ------------------ Endpoint 2: Activate/deactivate account -----------------
    @PatchMapping("/accounts/{id}/status")
    public AccountSummaryResponse updateAccountStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateAccountStatusRequest request
    ) {
        return accountAdminService.updateAccountStatus(id, request.enabled());
    }
}
