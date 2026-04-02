package com.fuba.automation_engine.persistence.repository;

import com.fuba.automation_engine.persistence.entity.AutomationPolicyEntity;
import com.fuba.automation_engine.persistence.entity.PolicyStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AutomationPolicyRepository extends JpaRepository<AutomationPolicyEntity, Long> {

    Optional<AutomationPolicyEntity> findFirstByDomainAndPolicyKeyAndStatusOrderByIdDesc(
            String domain,
            String policyKey,
            PolicyStatus status);

    List<AutomationPolicyEntity> findByDomainAndPolicyKeyOrderByIdDesc(String domain, String policyKey);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AutomationPolicyEntity policy
            set policy.status = :inactiveStatus
            where policy.domain = :domain
              and policy.policyKey = :policyKey
              and policy.status = :activeStatus
              and policy.id <> :excludedId
            """)
    int deactivateActivePoliciesInScopeExcludingId(
            @Param("domain") String domain,
            @Param("policyKey") String policyKey,
            @Param("excludedId") Long excludedId,
            @Param("activeStatus") PolicyStatus activeStatus,
            @Param("inactiveStatus") PolicyStatus inactiveStatus);
}
