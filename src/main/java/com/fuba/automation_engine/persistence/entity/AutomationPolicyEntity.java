package com.fuba.automation_engine.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(
        name = "automation_policies",
        indexes = {
                @Index(name = "idx_automation_policies_domain_policy_key_id_desc", columnList = "domain, policy_key, id")
        })
public class AutomationPolicyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String domain;

    @Column(name = "policy_key", nullable = false, length = 128)
    private String policyKey;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "due_after_minutes", nullable = false)
    private int dueAfterMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PolicyStatus status = PolicyStatus.INACTIVE;

    @Version
    @Column(nullable = false)
    private Long version;
}
