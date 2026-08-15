package com.eservice1.customer.service;

import com.eservice1.config.Msg91Properties;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class Msg91ServiceImpl implements Msg91Service {

    private final RestClient restClient;
    private final Msg91Properties properties;

    public Msg91ServiceImpl(Msg91Properties properties) {

        this.properties = properties;

        this.restClient = RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .build();
    }

    @Override
    public void sendOtp(
            String phoneNumber,
            String otp) {

        String mobileNumber =
                normalizeIndianNumber(phoneNumber);

        Map<String, Object> recipient =
                new HashMap<>();

        recipient.put(
                "mobiles",
                mobileNumber
        );

        recipient.put(
                properties.getOtpVariable(),
                otp
        );

        Map<String, Object> body =
                new HashMap<>();

        body.put(
                "template_id",
                properties.getTemplateId()
        );

        body.put(
                "recipients",
                List.of(recipient)
        );

        String response =
                restClient.post()
                        .header(
                                "authkey",
                                properties.getAuthKey()
                        )
                        .header(
                                "accept",
                                "application/json"
                        )
                        .contentType(
                                MediaType.APPLICATION_JSON
                        )
                        .body(body)
                        .retrieve()
                        .body(String.class);

       //System.out.println("================================");
       // System.out.println("MSG91 RESPONSE:");
       // System.out.println(response);
        //System.out.println("================================");
    }

    private String normalizeIndianNumber(
            String phoneNumber) {

        String phone =
                phoneNumber.replaceAll(
                        "\\s+",
                        ""
                );

        if (phone.startsWith("+91")) {
            return phone.substring(1);
        }

        if (phone.startsWith("91")
                && phone.length() == 12) {
            return phone;
        }

        if (phone.length() == 10) {
            return "91" + phone;
        }

        throw new IllegalArgumentException(
                "Invalid Indian phone number"
        );
    }
}