package com.tradestream.transaction_processor.api;

import com.tradestream.transaction_processor.domain.Transaction;
import com.tradestream.transaction_processor.repo.TransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionQueryController {

    private final TransactionRepository repo;

    // Default sort = executedAt DESC
    private Pageable pageReq(Integer page, Integer size, String sort) {
        int p = page == null ? 0 : Math.max(0, page);
        int s = size == null ? 50 : Math.min(Math.max(1, size), 500);
        Sort fallback = Sort.by(Sort.Direction.DESC, "executedAt");
        Sort sortSpec = (sort == null || sort.isBlank()) ? fallback : Sort.by(
                Sort.Order.by(sort.split(",")[0].trim())
                        .with(sort.toLowerCase().contains("asc") ? Sort.Direction.ASC : Sort.Direction.DESC)
        );
        return PageRequest.of(p, s, sortSpec);
    }

    @GetMapping("/{userId}")
    public ResponseEntity<Page<TransactionDto>> all(
            @PathVariable UUID userId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort // e.g. executedAt,desc
    ) {
        Pageable pageable = pageReq(page, size, sort);
        Page<Transaction> result = repo.findByUserId(userId, pageable);
        return ResponseEntity.ok(result.map(TransactionDto::from));
    }

    @GetMapping("/{userId}/ticker/{ticker}")
    public ResponseEntity<Page<TransactionDto>> byTicker(
            @PathVariable UUID userId,
            @PathVariable String ticker,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort
    ) {
        Pageable pageable = pageReq(page, size, sort);
        Page<Transaction> result = repo.findByUserIdAndTicker(userId, ticker.toUpperCase(), pageable);
        return ResponseEntity.ok(result.map(TransactionDto::from));
    }

    @GetMapping("/{userId}/since")
    public ResponseEntity<Page<TransactionDto>> since(
            @PathVariable UUID userId,
            @RequestParam("iso") Instant since,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort
    ) {
        Pageable pageable = pageReq(page, size, sort);
        Page<Transaction> result = repo.findByUserIdAndExecutedAtGreaterThanEqual(userId, since, pageable);
        return ResponseEntity.ok(result.map(TransactionDto::from));
    }
}
