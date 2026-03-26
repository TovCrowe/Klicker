package com.project.Klicker.Entities;

import com.project.Klicker.enums.MatchStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Table(name = "matches")
@Entity
@NoArgsConstructor
public class Match {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    private Player player1;

    @ManyToOne
    private Player player2;

    @Enumerated(EnumType.STRING)
    private MatchStatus status = MatchStatus.WAITING;

    private Long winnerID;

    private LocalDateTime createdAt = LocalDateTime.now();
}
