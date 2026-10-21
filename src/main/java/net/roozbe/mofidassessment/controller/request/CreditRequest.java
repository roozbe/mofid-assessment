package net.roozbe.mofidassessment.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreditRequest {

    @Positive
    private long amount;

    @NotBlank
    private String transactionId;
}
