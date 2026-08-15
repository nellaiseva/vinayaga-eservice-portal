package com.eservice1.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "msg91")
public class Msg91Properties {

    private String baseUrl;
    private String authKey;
    private String templateId;
    private String senderId;
    private String otpVariable = "number";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAuthKey() {
        return authKey;
    }

    public void setAuthKey(String authKey) {
        this.authKey = authKey;
    }

    public String getTemplateId() {
        return templateId;
    }

    public void setTemplateId(String templateId) {
        this.templateId = templateId;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public String getOtpVariable() {
        return otpVariable;
    }

    public void setOtpVariable(String otpVariable) {
        this.otpVariable = otpVariable;
    }
}