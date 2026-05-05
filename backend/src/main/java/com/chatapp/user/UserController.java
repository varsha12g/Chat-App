package com.chatapp.user;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {
    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public CurrentUser me(@AuthenticationPrincipal User user) {
        return CurrentUser.from(user);
    }

    @GetMapping
    public List<CurrentUser> users(@AuthenticationPrincipal User currentUser, @RequestParam(defaultValue = "") String q) {
        String query = q.trim().toLowerCase();
        return userRepository.findAll()
                .stream()
                .filter(user -> !user.getId().equals(currentUser.getId()))
                .filter(user -> query.isBlank()
                        || user.getUsername().toLowerCase().contains(query)
                        || user.getEmail().toLowerCase().contains(query))
                .limit(25)
                .map(CurrentUser::from)
                .toList();
    }
}
