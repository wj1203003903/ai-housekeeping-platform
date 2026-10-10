package com.anxin.home.genai.memory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface UserMemoryRepository extends JpaRepository<UserMemory, Long> {
    List<UserMemory> findTop20ByUserIdOrderByCreatedAtDesc(String userId);
    void deleteByIdAndUserId(Long id, String userId);
}
