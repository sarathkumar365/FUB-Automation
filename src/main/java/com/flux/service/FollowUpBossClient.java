package com.flux.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.flux.service.model.ActionExecutionResult;
import com.flux.service.model.CallDetails;
import com.flux.service.model.CallEvidence;
import com.flux.service.model.CreateNoteCommand;
import com.flux.service.model.CreateTaskCommand;
import com.flux.service.model.CreatedNote;
import com.flux.service.model.CreatedTask;
import com.flux.service.model.PersonDetails;
import com.flux.service.model.RegisterWebhookCommand;
import com.flux.service.model.RegisterWebhookResult;
import java.util.List;

public interface FollowUpBossClient {

    RegisterWebhookResult registerWebhook(RegisterWebhookCommand command);

    CallDetails getCallById(long callId);

    PersonDetails getPersonById(long personId);

    JsonNode getPersonRawById(long personId);

    List<CallEvidence> listPersonCalls(long personId);

    ActionExecutionResult reassignPerson(long personId, long targetUserId);

    ActionExecutionResult movePersonToPond(long personId, long targetPondId);

    ActionExecutionResult addTag(long personId, String tagName);

    CreatedTask createTask(CreateTaskCommand command);

    CreatedNote createNote(CreateNoteCommand command);
}
