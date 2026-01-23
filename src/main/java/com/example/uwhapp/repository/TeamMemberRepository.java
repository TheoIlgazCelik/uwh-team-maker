package com.example.uwhapp.repository;

import com.example.uwhapp.model.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TeamMemberRepository extends JpaRepository<TeamMember, Long> {
    List<TeamMember> findByTeamId(Long teamId);
    List<TeamMember> findByTeamIdIn(List<Long> teamIds);
}
