/*
  AccountAdminService.java
  Business logic for admin-only account management: listing every account and
  activating/deactivating one by id. Kept separate from AuthService, which
  stays focused on self-service register/login for the account owner.
*/
package com.orbitra.auth_service.service;

// --------------- IMPORTS ---------------
import com.orbitra.auth_service.dto.AccountSummaryResponse;
import com.orbitra.auth_service.dto.PagedResponse;
import com.orbitra.auth_service.exception.AccountNotFoundException;
import com.orbitra.auth_service.model.Account;
import com.orbitra.auth_service.repository.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountAdminService {

    private static final Logger log = LoggerFactory.getLogger(AccountAdminService.class);

    private final AccountRepository accountRepository;

    public AccountAdminService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    // ---------------- METHOD 1: List accounts, one page at a time ----------------
    // Read-only - no @Transactional needed since nothing is written.
    public PagedResponse<AccountSummaryResponse> listAccounts(Pageable pageable) {
        Page<AccountSummaryResponse> page = accountRepository.findAll(pageable).map(this::toSummary);
        return PagedResponse.from(page);
    }

    // ---------------- METHOD 2: Activate/deactivate an account ----------------
    // @Transactional: the lookup and the save() happen as one atomic unit.
    // Setting `enabled` to the value it already has is treated as a normal
    // idempotent success, not an error - PATCH-ing a resource's state to a
    // value it already has should be safe to repeat.
    @Transactional
    public AccountSummaryResponse updateAccountStatus(Long id, boolean enabled) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new AccountNotFoundException("No account with id " + id));

        account.setEnabled(enabled);
        accountRepository.save(account);
        log.info("Account id={} enabled set to {}", account.getId(), enabled);

        return toSummary(account);
    }

    // ---------------- HELPER 1: Map entity -> admin-facing DTO ----------------
    private AccountSummaryResponse toSummary(Account account) {
        return new AccountSummaryResponse(
                account.getId(),
                account.getEmail(),
                account.getRole(),
                account.getPartnerType(),
                account.isEnabled(),
                account.getCreatedAt()
        );
    }
}
