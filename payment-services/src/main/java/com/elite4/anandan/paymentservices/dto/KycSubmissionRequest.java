package com.elite4.anandan.paymentservices.dto;

import lombok.Data;

@Data
public class KycSubmissionRequest {
    private String panNumber;
    private String gstNumber;
    private String businessAddress;
    private String businessType;        // individual, partnership, private_limited, etc.
    private String legalBusinessName;

    // Stakeholder info
    private String stakeholderName;
    private String stakeholderPhone;
    private String stakeholderEmail;
}
