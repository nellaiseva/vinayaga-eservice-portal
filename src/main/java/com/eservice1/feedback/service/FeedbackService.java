package com.eservice1.feedback.service;

import com.eservice1.common.exception.DuplicateResourceException;
import com.eservice1.common.exception.InvalidOperationException;
import com.eservice1.common.exception.ResourceNotFoundException;
import com.eservice1.feedback.dto.FeedbackDTO;
import com.eservice1.feedback.entity.Feedback;
import com.eservice1.feedback.repository.FeedbackRepository;
import com.eservice1.submission.entity.CustomerRequest;
import com.eservice1.submission.entity.RequestStatus;
import com.eservice1.submission.repository.CustomerRequestRepository;
import org.springframework.stereotype.Service;

@Service
public class FeedbackService {

    private final FeedbackRepository feedbackRepository;

    private final CustomerRequestRepository requestRepository;

    private final com.eservice1.submission.service.RequestAccessService requestAccessService;

    public FeedbackService(
            FeedbackRepository feedbackRepository,
            CustomerRequestRepository requestRepository,
            com.eservice1.submission.service.RequestAccessService requestAccessService
    ) {

        this.feedbackRepository = feedbackRepository;
        this.requestRepository = requestRepository;
        this.requestAccessService = requestAccessService;
    }

    public Feedback submitFeedback(
            FeedbackDTO dto
    ) {
        return submitFeedback(
                dto,
                org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()
        );
    }

    @org.springframework.transaction.annotation.Transactional
    public Feedback submitFeedback(
            FeedbackDTO dto,
            org.springframework.security.core.Authentication authentication
    ) {

        CustomerRequest request =
                requestAccessService.requireCustomerRequestAccess(
                        dto.getRequestId(),
                        authentication
                );
        if (feedbackRepository.existsByRequestId(request.getId())) {

            throw new DuplicateResourceException(
                    "Feedback already submitted."
            );

        }

        if (request.getStatus() != RequestStatus.COMPLETED) {

            throw new InvalidOperationException(
                    "Only completed requests can be rated."
            );

        }

        Feedback feedback = new Feedback();

        feedback.setRequest(request);

        feedback.setService(
                request.getService()
        );

        feedback.setCustomerPhone(
                request.getPhoneNumber()
        );

        feedback.setRating(
                dto.getRating()
        );

        feedback.setComment(
                dto.getComment()
        );

        return feedbackRepository.save(
                feedback
        );

    }

}