package com.carddemo.online.web;

import com.carddemo.online.service.BillPayService;
import com.carddemo.online.service.TransactionAddService;
import com.carddemo.online.service.TransactionQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Transactions", description = "legacy: COTRN00C/CT00, COTRN01C/CT01, COTRN02C/CT02, COBIL00C/CB00")
public class TransactionController {
    private final TransactionQueryService query;
    private final TransactionAddService add;
    private final BillPayService billPay;

    public TransactionController(TransactionQueryService query, TransactionAddService add, BillPayService billPay) {
        this.query = query;
        this.add = add;
        this.billPay = billPay;
    }

    public record BillPayRequest(String accountId, String confirm) {
    }

    @GetMapping("/transactions")
    @Operation(summary = "Transaction list, 10 per page (COTRN00C)")
    public TransactionQueryService.TranPage list(@RequestParam(required = false) String fromId,
                                                 @RequestParam(required = false) String after,
                                                 @RequestParam(required = false) String before,
                                                 @RequestParam(defaultValue = "1") int page) {
        return query.list(fromId, after, before, page);
    }

    @GetMapping("/transactions/detail")
    @Operation(summary = "Transaction view (COTRN01C)")
    public TransactionQueryService.TranDetail view(@RequestParam(required = false) String transactionId) {
        return query.view(transactionId);
    }

    @PostMapping("/transactions")
    @Operation(summary = "Add transaction (COTRN02C); confirm=Y writes, blank returns the confirm prompt")
    public TransactionAddService.AddResult add(@RequestBody TransactionAddService.AddRequest req) {
        return add.add(req);
    }

    @PostMapping("/bill-payments")
    @Operation(summary = "Bill payment of the full balance (COBIL00C); confirm blank = prompt, Y = pay")
    public BillPayService.BillPayResult pay(@RequestBody BillPayRequest req) {
        return billPay.process(req.accountId(), req.confirm());
    }
}
