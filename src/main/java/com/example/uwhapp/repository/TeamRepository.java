package com.example.uwhapp.repository;

import com.example.uwhapp.model.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Long> {
    List<Team> findByEventIdOrderByTeamIndex(Long eventId);
}
