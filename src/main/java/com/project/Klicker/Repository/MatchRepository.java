package com.project.Klicker.Repository;

import com.project.Klicker.Entities.Match;
import com.project.Klicker.enums.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MatchRepository extends JpaRepository<Match, Long> {
    List<Match> findAllByStatus(MatchStatus status);
}
