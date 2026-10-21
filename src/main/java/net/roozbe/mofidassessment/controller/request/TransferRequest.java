package net.roozbe.mofidassessment.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TransferRequest {
    @NotBlank
    private String sourceAccountId;

    @NotBlank
    private String destinationAccountId;

    @Positive
    private long amount;

    @NotBlank
    private String transactionId;
}
