package dev.lavra.identity.persistence;

import dev.lavra.identity.domain.PlanCode;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlanRepository extends JpaRepository<PlanEntity, PlanCode> {
}
