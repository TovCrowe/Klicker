package com.project.Klicker.Repository;

import com.project.Klicker.Entities.Player;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PlayerRepository extends JpaRepository<Player, Long> {
    Optional<Player> findPlayerByUsername(String username);
}
