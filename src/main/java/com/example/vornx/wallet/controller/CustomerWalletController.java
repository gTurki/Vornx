package com.example.vornx.wallet.controller;

import org.springdoc.core.annotations.ParameterObject;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.example.vornx.common.dto.PageResponse;
import com.example.vornx.security.AuthUser;
import com.example.vornx.wallet.dto.TopUpRequest;
import com.example.vornx.wallet.dto.TransferRequest;
import com.example.vornx.wallet.dto.WalletResponse;
import com.example.vornx.wallet.dto.WalletTransactionResponse;
import com.example.vornx.wallet.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Customer app -> "المحفظة" */
@Tag(name = "Customer - Wallet", description = "Balance, history, top-up and transfers")
@RestController
@RequestMapping("/api/customer/wallet")
@RequiredArgsConstructor
public class CustomerWalletController {

    private final WalletService walletService;

    @GetMapping
    public WalletResponse wallet(@AuthenticationPrincipal Jwt jwt) {
        return walletService.getWallet(AuthUser.from(jwt).id());
    }

    @GetMapping("/transactions")
    public PageResponse<WalletTransactionResponse> transactions(@AuthenticationPrincipal Jwt jwt,
                                                                @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
                                                                Pageable pageable) {
        return walletService.history(AuthUser.from(jwt).id(), pageable);
    }

    /** SIMULATED until a payment gateway is connected (enabled only in application-local.properties). */
    @PostMapping("/top-up")
    public WalletTransactionResponse topUp(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TopUpRequest req) {
        return walletService.topUp(AuthUser.from(jwt).id(), req.amount());
    }

    @PostMapping("/transfers")
    public WalletTransactionResponse transfer(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TransferRequest req) {
        return walletService.transfer(AuthUser.from(jwt).id(), req.toVornxId(), req.amount(), req.note());
    }
}
