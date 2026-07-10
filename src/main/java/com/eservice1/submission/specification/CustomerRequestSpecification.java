package com.eservice1.submission.specification;

import com.eservice1.submission.entity.CustomerRequest;
import com.eservice1.submission.entity.RequestStatus;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;

public class CustomerRequestSpecification {

    public static Specification<CustomerRequest> filter(
            String search,
            String phone,
            String status,
            LocalDate date
    ) {

        return (root, query, cb) -> {

            var predicate = cb.conjunction();

            if (search != null) {
                predicate = cb.and(
                        predicate,
                        cb.like(
                                cb.lower(root.get("customerName")),
                                "%" + search.toLowerCase() + "%"
                        )
                );
            }

            if (phone != null) {
                predicate = cb.and(
                        predicate,
                        cb.like(
                                root.get("phoneNumber"),
                                "%" + phone + "%"
                        )
                );
            }

            if (status != null) {
                predicate = cb.and(
                        predicate,
                        cb.equal(
                                root.get("status"),
                                RequestStatus.valueOf(status)
                        )
                );
            }

            if (date != null) {
                predicate = cb.and(
                        predicate,
                        cb.equal(
                                cb.function(
                                        "DATE",
                                        LocalDate.class,
                                        root.get("createdAt")
                                ),
                                date
                        )
                );
            }

            return predicate;
        };
    }
}