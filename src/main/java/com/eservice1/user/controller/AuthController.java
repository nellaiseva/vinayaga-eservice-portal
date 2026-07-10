package com.eservice1.user.controller;

import com.eservice1.user.dto.LoginRequest;
import com.eservice1.user.entity.User;
import com.eservice1.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import com.eservice1.user.dto.RegisterRequest;
import com.eservice1.user.dto.AuthResponse;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(
            UserService userService) {

        this.userService = userService;
    }

    @PostMapping("/register")
    public User register(

            @Valid
            @RequestBody RegisterRequest request

    ) {

        return userService.register(request);

    }

    @PostMapping("/login")
    public AuthResponse login(

            @Valid
            @RequestBody LoginRequest request

    ) {

        return userService.login(request);
    }

    @PostMapping("/owner")
    public User createOwner(

            @Valid
            @RequestBody RegisterRequest request

    ) {

        return userService.createOwner(request);

    }
}