package com.dws.challenge.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

@Data
public class TransferRequest {

    @NotNull
    @NotEmpty
    @JsonProperty("accountFromId")
    private String accountFromId;

    @NotNull
    @NotEmpty
    @JsonProperty("accountToId")
    private String accountToId;

    @NotNull
    @DecimalMin(value = "0.01", message = "Transfer amount must be positive.")
    @JsonProperty("amount")
    private BigDecimal amount;

    public TransferRequest() {}

    public TransferRequest(String accountFromId, String accountToId, BigDecimal amount) {
        this.accountFromId = accountFromId;
        this.accountToId = accountToId;
        this.amount = amount;
    }
}