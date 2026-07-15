package com.eservice1.customer.service;

import com.eservice1.customer.entity.CustomerProfile;
import com.eservice1.customer.repository.CustomerProfileRepository;
import org.springframework.stereotype.Service;

@Service
public class CustomerProfileService {

    private final CustomerProfileRepository repository;

    public CustomerProfileService(
            CustomerProfileRepository repository) {

        this.repository = repository;
    }

    public CustomerProfile save(
            CustomerProfile profile) {

        CustomerProfile existing =
                repository.findByPhoneNumber(
                        profile.getPhoneNumber()
                );

        if (existing != null) {

            existing.setCustomerName(
                    profile.getCustomerName()
            );

            existing.setDob(
                    profile.getDob()
            );

            return repository.save(
                    existing
            );
        }

        return repository.save(
                profile
        );
    }
    public CustomerProfile getByPhone(String phoneNumber) {

        System.out.println("Requested phone: " + phoneNumber);

        CustomerProfile profile =
                repository.findByPhoneNumber(phoneNumber);

        System.out.println("Repository result: " + profile);

        if (profile != null) {
            System.out.println("Found profile phone: " + profile.getPhoneNumber());
        }

        if (profile == null) {
            throw new RuntimeException("Profile Not Found");
        }

        return profile;
    }
}