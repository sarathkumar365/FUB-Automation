package com.fuba.automation_engine.service.workflow.contributor;

import com.fuba.automation_engine.service.BusinessHoursService;
import com.fuba.automation_engine.service.workflow.spi.RunContextContributor;
import com.fuba.automation_engine.service.workflow.spi.RunContextRequest;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Host adapter: fills the {@code now} run-context block with time-of-day flags
 * resolved at step time, so a long-running workflow crossing the daytime/off-hours
 * boundary sees the updated value at the next step.
 */
@Component
public class NowRunContextContributor implements RunContextContributor {

    private final BusinessHoursService businessHoursService;

    public NowRunContextContributor(BusinessHoursService businessHoursService) {
        this.businessHoursService = businessHoursService;
    }

    @Override
    public String key() {
        return "now";
    }

    @Override
    public Map<String, Object> contribute(RunContextRequest request) {
        return Map.of(
                "isDaytime", businessHoursService.isDaytime(request.now()),
                "hourLocal", businessHoursService.hourLocal(request.now()));
    }
}
