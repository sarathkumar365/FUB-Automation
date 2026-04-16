package com.fuba.automation_engine.service;

import com.fuba.automation_engine.service.model.CallDetails;
import com.fuba.automation_engine.service.model.ActionExecutionResult;
import com.fuba.automation_engine.service.model.CreateTaskCommand;
import com.fuba.automation_engine.service.model.CreatedTask;
import com.fuba.automation_engine.service.model.PersonCommunicationCheckResult;
import com.fuba.automation_engine.service.model.PersonDetails;
import com.fuba.automation_engine.service.model.RegisterWebhookCommand;
import com.fuba.automation_engine.service.model.RegisterWebhookResult;

public interface FollowUpBossClient {

    RegisterWebhookResult registerWebhook(RegisterWebhookCommand command);

    CallDetails getCallById(long callId);

    PersonDetails getPersonById(long personId);

    PersonCommunicationCheckResult checkPersonCommunication(long personId);

    ActionExecutionResult reassignPerson(long personId, long targetUserId);

    ActionExecutionResult movePersonToPond(long personId, long targetPondId);

    ActionExecutionResult addTag(long personId, String tagName);

    CreatedTask createTask(CreateTaskCommand command);
}
