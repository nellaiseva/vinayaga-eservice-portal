package com.eservice1.customer.service;

import com.eservice1.customer.dto.CustomerFormResponseDTO;
import com.eservice1.customer.entity.CustomerFormField;
import com.eservice1.customer.entity.CustomerFormResponse;
import com.eservice1.customer.repository.CustomerFormFieldRepository;
import com.eservice1.customer.repository.CustomerFormResponseRepository;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

@Service
public class CustomerFormResponseService {

    private final CustomerFormResponseRepository responseRepository;
    private final CustomerFormFieldRepository fieldRepository;

    public CustomerFormResponseService(
            CustomerFormResponseRepository responseRepository,
            CustomerFormFieldRepository fieldRepository) {

        this.responseRepository = responseRepository;
        this.fieldRepository = fieldRepository;
    }

    public CustomerFormResponse save(
            CustomerFormResponseDTO dto,
            String authenticatedPhoneNumber) {

        CustomerFormField field =
                fieldRepository.findById(
                        dto.getFieldId()
                ).orElseThrow();

        CustomerFormResponse response =
                new CustomerFormResponse();

        response.setPhoneNumber(
                authenticatedPhoneNumber
        );

        response.setField(field);

        response.setValue(
                dto.getValue()
        );

        return responseRepository.save(
                response
        );
    }

    public List<CustomerFormResponse>
    getByPhoneNumber(
            String phoneNumber) {

        return responseRepository.findByPhoneNumber(
                phoneNumber
        );
    }
    public Map<String,String>
    getAutoFillData(
            String phoneNumber
    ) {

        List<CustomerFormResponse> responses =
                responseRepository.findByPhoneNumber(
                        phoneNumber
                );

        Map<String,String> result =
                new HashMap<>();

        for (
                CustomerFormResponse response
                : responses
        ) {

            CustomerFormField field =
                    response.getField();

            if (field != null) {

                result.put(
                        field.getFieldName(),
                        response.getValue()
                );
            }
        }

        return result;
    }
}
