package com.eservice1.user.controller;

import com.eservice1.user.dto.UserResponse;
import com.eservice1.user.repository.UserRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserRepository
            userRepository;

    public UserController(
            UserRepository userRepository) {

        this.userRepository =
                userRepository;
    }

    @GetMapping
    public List<UserResponse> getAllUsers() {

        return userRepository.findAll()
                .stream()
                .map(UserResponse::fromEntity)
                .toList();
    }
}