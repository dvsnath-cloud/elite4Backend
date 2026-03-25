package com.elite4.anandan.registrationservices.dto;

import lombok.Data;

import java.util.Date;
import java.util.Set;

@Data
public class UserForCheckOutForAll {
    private Set<UpdateUserForCheckOut> updateUserForCheckOutSet;
}
