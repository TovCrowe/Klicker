        package com.project.Klicker.service;

        import com.project.Klicker.Entities.Match;
        import com.project.Klicker.Entities.Player;
        import com.project.Klicker.Repository.MatchRepository;
        import com.project.Klicker.Repository.PlayerRepository;
        import com.project.Klicker.enums.MatchStatus;
        import lombok.RequiredArgsConstructor;
        import org.springframework.stereotype.Service;

        import java.util.Queue;
        import java.util.concurrent.ConcurrentLinkedDeque;

        @Service
        @RequiredArgsConstructor
        public class MatchmakingService {
            private final Queue<Long> waitingPlayers = new ConcurrentLinkedDeque<>();
            private final MatchRepository matchRepository;
            private final PlayerRepository playerRepository;

            public Match joinQueue(Long playerId) {
                if (waitingPlayers.contains(playerId)) {
                    throw new RuntimeException("This player its already on the waiting list");
                }

                waitingPlayers.offer(playerId);

                if (waitingPlayers.size() >= 2) {
                    Long player1Id = waitingPlayers.poll();
                    Long player2Id = waitingPlayers.poll();

                    Player player1 = playerRepository.findById(player1Id)
                            .orElseThrow(() -> new RuntimeException("Player 1 not found"));
                    Player player2 = playerRepository.findById(player2Id)
                            .orElseThrow(() -> new RuntimeException("Player 2 not found"));
                    Match newMatch = new Match();
                    newMatch.setPlayer1(player1);
                    newMatch.setPlayer2(player2);
                    newMatch.setStatus(MatchStatus.IN_PROGRESS);

                    return matchRepository.save(newMatch);
                }

                return null;
            }

            public MatchStatus getMatchStatus(Long matchId) {
                return matchRepository.findById(matchId)
                        .orElseThrow(() -> new RuntimeException("MAtch not found")).getStatus();
            }
        }
