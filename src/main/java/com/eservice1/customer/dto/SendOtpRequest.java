package com.eservice1.customer.dto;

public class SendOtpRequest {

    private String phoneNumber;

    public SendOtpRequest() {
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
}