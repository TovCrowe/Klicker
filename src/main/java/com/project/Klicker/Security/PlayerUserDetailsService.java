package com.project.Klicker.Security;

import com.project.Klicker.Repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@RequiredArgsConstructor
public class PlayerUserDetailsService implements UserDetailsService {

    private final PlayerRepository playerRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        var player = playerRepository.findPlayerByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Player not found: " + username));
        return new User(player.getUsername(), player.getPassword(), Collections.emptyList());
    }
}