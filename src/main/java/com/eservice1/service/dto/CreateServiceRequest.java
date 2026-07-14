package com.eservice1.service.dto;

import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
public class CreateServiceRequest {

    @NotBlank(message = "Service name is required.")
    @Size(max = 100, message = "Service name cannot exceed 100 characters.")
    private String serviceName;

   // @NotBlank(message = "Description is required.")
    @Size(max = 500, message = "Description cannot exceed 500 characters.")
    private String description;

    @NotNull(message = "Active status is required.")
    private Boolean active;

    private List<String> documents;

    public String getServiceName() {
        return serviceName;
    }

    public void setServiceName(
            String serviceName) {

        this.serviceName =
                serviceName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(
            String description) {

        this.description =
                description;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(
            Boolean active) {

        this.active = active;
    }

    public List<String> getDocuments() {
        return documents;
    }

    public void setDocuments(
            List<String> documents) {

        this.documents =
                documents;
    }
}