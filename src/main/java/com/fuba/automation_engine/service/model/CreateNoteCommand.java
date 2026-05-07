package com.fuba.automation_engine.service.model;

import java.util.List;

/**
 * Service-layer command for creating a FUB note with optional @mentions.
 *
 * @param personId         FUB person ID this note attaches to (required)
 * @param body             pre-built HTML body (caller assembles the
 *                         {@code <span data-user-id="N">Name</span>} chips)
 * @param mentionUserIds   user IDs that must appear in {@code mentions.user[]};
 *                         must be kept in sync with the spans in {@code body}
 * @param subject          optional note subject
 */
public record CreateNoteCommand(
        Long personId,
        String body,
        List<Long> mentionUserIds,
        String subject) {
}
