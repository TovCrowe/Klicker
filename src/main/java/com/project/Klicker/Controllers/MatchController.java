package com.project.Klicker.Controllers;

import com.project.Klicker.Entities.Match;
import com.project.Klicker.Entities.Player;
import com.project.Klicker.Repository.PlayerRepository;
import com.project.Klicker.enums.MatchStatus;
import com.project.Klicker.service.MatchmakingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/match")
@RequiredArgsConstructor
public class MatchController {

    private final MatchmakingService matchmakingService;
    private final PlayerRepository playerRepository;

    @PostMapping("/join")
    public ResponseEntity<Match> joinMatch() {
        String username = SecurityContextHolder.getContext()
                .getAuthentication().getName();

        // find the player in the database
        Player player = playerRepository.findPlayerByUsername(username)
                .orElseThrow(() -> new RuntimeException("Player not found"));

        // join the queue
        Match match = matchmakingService.joinQueue(player.getId());

        if (match == null) {
            return ResponseEntity.accepted().build(); // 202
        }

        return ResponseEntity.ok(match); // 200 + match details
    }

    @GetMapping("/{matchId}")
    public ResponseEntity<MatchStatus> getMatchStatus(@PathVariable Long matchId) {
        return ResponseEntity.ok(matchmakingService.getMatchStatus(matchId));
    }
}