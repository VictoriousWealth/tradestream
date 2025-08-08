package com.tradestream.transaction_processor.transaction;

import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.tradestream.transaction_processor.portofolio.Portfolio;
import com.tradestream.transaction_processor.transaction.Transaction.TransactionType;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping("/{userId}")
    public ResponseEntity<Void> processTransaction(@PathVariable UUID userId, @Valid @RequestBody TransactionRequest request) {
        transactionService.processTransaction(
                userId,
                request.getTicker(),
                request.getQuantity(),
                request.getPrice(),
                TransactionType.valueOf(request.getType().name())
        );

        return ResponseEntity.accepted().build();
    }

    @GetMapping("/portfolio/{userId}")
    public ResponseEntity<List<Portfolio>> getUserPortfolio(@PathVariable UUID userId) {
        return ResponseEntity.ok(transactionService.getPortfolioForUser(userId));
    }

    @GetMapping("/history/{userId}")
    public ResponseEntity<List<Transaction>> getTransactionHistory(@PathVariable UUID userId) {
        return ResponseEntity.ok(transactionService.getTransactionHistory(userId));
    }


}
