package com.eservice1.feedback.controller;

import com.eservice1.feedback.dto.FeedbackDTO;
import com.eservice1.feedback.entity.Feedback;
import com.eservice1.feedback.service.FeedbackService;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/feedback")
public class FeedbackController {

    private final FeedbackService service;

    public FeedbackController(
            FeedbackService service
    ) {
        this.service = service;
    }

    @PostMapping
    public Feedback submitFeedback(

            @Valid
            @RequestBody FeedbackDTO dto,
            Authentication authentication

    ){

        return service.submitFeedback(
                dto,
                authentication
        );

    }

}