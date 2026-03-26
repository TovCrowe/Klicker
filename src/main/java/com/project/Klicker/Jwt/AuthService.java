package com.project.Klicker.Jwt;

import com.project.Klicker.DTO.AuthResponse;
import com.project.Klicker.DTO.LoginRequest;
import com.project.Klicker.DTO.RegisterRequest;
import com.project.Klicker.Entities.Player;
import com.project.Klicker.Repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final PlayerRepository playerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponse register(RegisterRequest registerRequest) {
        // check if user exist
        if (playerRepository.findPlayerByUsername(registerRequest.getUsername()).isPresent()) {
            throw new RuntimeException("Username already taken");
        }

        Player newPlayer = new Player();
        newPlayer.setUsername(registerRequest.getUsername());
        newPlayer.setEmail(registerRequest.getEmail());
        newPlayer.setPassword(passwordEncoder.encode(registerRequest.getPassword()));

        playerRepository.save(newPlayer);

        // generate the token
        String token = jwtService.generateToken(newPlayer.getUsername());
        return new AuthResponse(token);
    }

    public AuthResponse login(LoginRequest login) {
        Player player = playerRepository.findPlayerByUsername(login.getUsername()).orElseThrow(() -> new RuntimeException("Player nto found"));

        //check password
        if (!passwordEncoder.matches(login.getPassword(), player.getPassword())) {
            throw new RuntimeException("Wrong password");
        }

        String token = jwtService.generateToken(player.getUsername());
        return new AuthResponse(token);
    }
}
