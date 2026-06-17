package com.fuba.automation_engine.service.workflow.contributor;

import com.fuba.automation_engine.service.person.PersonSnapshotResolver;
import com.fuba.automation_engine.service.workflow.spi.RunContextContributor;
import com.fuba.automation_engine.service.workflow.spi.RunContextRequest;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Host adapter: fills the {@code person} run-context block from the locally
 * snapshotted person details. Re-resolved per step so it picks up mid-run
 * changes (e.g. a reassignment during a wait).
 */
@Component
public class PersonRunContextContributor implements RunContextContributor {

    private final PersonSnapshotResolver personSnapshotResolver;

    public PersonRunContextContributor(PersonSnapshotResolver personSnapshotResolver) {
        this.personSnapshotResolver = personSnapshotResolver;
    }

    @Override
    public String key() {
        return "person";
    }

    @Override
    public Map<String, Object> contribute(RunContextRequest request) {
        Map<String, Object> snapshot = personSnapshotResolver.resolve(request.subjectId());
        return snapshot != null ? snapshot : Map.of();
    }
}
