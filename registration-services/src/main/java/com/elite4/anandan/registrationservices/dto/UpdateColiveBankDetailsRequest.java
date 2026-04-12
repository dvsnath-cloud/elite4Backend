package com.elite4.anandan.registrationservices.dto;

import lombok.Data;

@Data
public class UpdateColiveBankDetailsRequest {
    private String bankName;
    private String accountNumber;
    private String ifscCode;
    private String beneficiaryName;
    private String upiId;
    private String branchCode;
    private String branchAddress;
}
