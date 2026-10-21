package net.roozbe.mofidassessment.controller;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import net.roozbe.mofidassessment.controller.request.AccountRequest;
import net.roozbe.mofidassessment.controller.request.CreditRequest;
import net.roozbe.mofidassessment.controller.request.DebitRequest;
import net.roozbe.mofidassessment.controller.request.TransferRequest;
import net.roozbe.mofidassessment.controller.response.AccountResponse;
import net.roozbe.mofidassessment.mapper.AccountMapper;
import net.roozbe.mofidassessment.service.BalanceService;
import net.roozbe.mofidassessment.service.model.AccountModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounts")
@AllArgsConstructor
public class BalanceController {
    private final BalanceService balanceService;
    private final AccountMapper mapper;

    @PostMapping
    public ResponseEntity<AccountResponse> createAccount(@Valid @RequestBody AccountRequest request) {
        AccountModel model = balanceService.createAccount(mapper.toAccountModel(request));
        AccountResponse response = mapper.toAccountResponse(model);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{accountId}/credits")
    @ResponseStatus(HttpStatus.OK)
    public void credit(@PathVariable String accountId, @Valid @RequestBody CreditRequest request) {
        balanceService.credit(accountId, request.getAmount(), request.getTransactionId());
    }

    @PostMapping("/{accountId}/debits")
    @ResponseStatus(HttpStatus.OK)
    public void debit(@PathVariable String accountId, @Valid @RequestBody DebitRequest request) {
        balanceService.debit(accountId, request.getAmount(), request.getTransactionId());
    }

    @PostMapping("/transfers")
    @ResponseStatus(HttpStatus.OK)
    public void transfer(@Valid @RequestBody TransferRequest request) {
        balanceService.transfer(request.getSourceAccountId(), request.getDestinationAccountId(),
                request.getAmount(), request.getTransactionId());
    }

    @GetMapping("/{accountId}/balance")
    public long getBalance(@PathVariable String accountId) {
        return balanceService.getBalance(accountId);
    }
}
