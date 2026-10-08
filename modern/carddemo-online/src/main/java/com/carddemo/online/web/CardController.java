package com.carddemo.online.web;

import com.carddemo.online.service.CardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cards")
@Tag(name = "Cards", description = "legacy: COCRDLIC/CCLI, COCRDSLC/CCDL, COCRDUPC/CCUP")
public class CardController {
    private final CardService cards;

    public CardController(CardService cards) {
        this.cards = cards;
    }

    @GetMapping
    @Operation(summary = "Card list, 7 per page (COCRDLIC); after = PF8 from last key, before = PF7 from first key")
    public CardService.CardPage list(@RequestParam(required = false) String accountId,
                                     @RequestParam(required = false) String cardNumber,
                                     @RequestParam(required = false) String after,
                                     @RequestParam(required = false) String before,
                                     @RequestParam(defaultValue = "1") int page) {
        return cards.list(accountId, cardNumber, after, before, page);
    }

    @GetMapping("/detail")
    @Operation(summary = "Card view (COCRDSLC)")
    public CardService.CardDetail view(@RequestParam(required = false) String accountId,
                                       @RequestParam(required = false) String cardNumber) {
        return cards.view(accountId, cardNumber);
    }

    @GetMapping("/update")
    @Operation(summary = "Fetch card for update (COCRDUPC 9000-READ-DATA)")
    public CardService.CardDetail fetch(@RequestParam(required = false) String accountId,
                                        @RequestParam(required = false) String cardNumber) {
        return cards.fetchForUpdate(accountId, cardNumber);
    }

    @PostMapping("/update/validate")
    @Operation(summary = "Validate card changes (COCRDUPC 1200-EDIT-MAP-INPUTS)")
    public CardService.CardOutcome validate(@RequestParam(required = false) String accountId,
                                            @RequestParam(required = false) String cardNumber,
                                            @RequestBody CardService.CardUpdateRequest req) {
        return cards.validate(accountId, cardNumber, req);
    }

    @PutMapping("/update")
    @Operation(summary = "Commit card changes (PF5, COCRDUPC 9200-WRITE-PROCESSING)")
    public CardService.CardOutcome save(@RequestParam(required = false) String accountId,
                                        @RequestParam(required = false) String cardNumber,
                                        @RequestBody CardService.CardUpdateRequest req) {
        return cards.save(accountId, cardNumber, req);
    }
}
