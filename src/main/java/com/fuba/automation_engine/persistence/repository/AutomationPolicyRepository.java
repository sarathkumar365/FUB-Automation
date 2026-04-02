package com.fuba.automation_engine.persistence.repository;

import com.fuba.automation_engine.persistence.entity.AutomationPolicyEntity;
import com.fuba.automation_engine.persistence.entity.PolicyStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationPolicyRepository extends JpaRepository<AutomationPolicyEntity, Long> {

    Optional<AutomationPolicyEntity> findFirstByDomainAndPolicyKeyAndStatusOrderByIdDesc(
            String domain,
            String policyKey,
            PolicyStatus status);

    List<AutomationPolicyEntity> findByDomainAndPolicyKeyOrderByIdDesc(String domain, String policyKey);
}
