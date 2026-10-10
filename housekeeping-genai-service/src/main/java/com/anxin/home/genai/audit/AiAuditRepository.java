package com.anxin.home.genai.audit;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AiAuditRepository extends JpaRepository<AiAuditEvent, Long> {
}
