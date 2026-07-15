package com.eservice1.servicecategory.entity;

import com.eservice1.service.entity.PortalService;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "service_categories")
public class ServiceCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    private Boolean active = true;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "category_services",
            joinColumns = @JoinColumn(name = "category_id")
    )
    @Column(name = "service_id")
    private List<Long> serviceIds =
            new ArrayList<>();

    public ServiceCategory() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(
            String name
    ) {
        this.name = name;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(
            Boolean active
    ) {
        this.active = active;
    }

    public List<Long> getServiceIds() {
        return serviceIds;
    }

    public void setServiceIds(
            List<Long> serviceIds
    ) {
        this.serviceIds = serviceIds;
    }
}