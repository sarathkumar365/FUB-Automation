package com.fuba.automation_engine.client.fub.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * Wire payload for {@code POST /v1/notes}.
 *
 * <p>The {@code mentions} field is undocumented in the public FUB reference but
 * is required for @mention rendering and notification — see
 * {@code Docs/features/agent-followup-enforcement/research.md} for the empirical
 * verification (notes 21240 vs 21255/21256).
 *
 * <p>Three things must travel together for a mention to render as a clickable
 * chip and trigger notifications:
 * <ul>
 *   <li>{@code body} HTML containing one
 *       {@code <span data-user-id="N">Display Name</span>} per mentioned user</li>
 *   <li>{@code isHtml: true}</li>
 *   <li>{@code mentions.user: [N, ...]}</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FubCreateNoteRequestDto(
        Long personId,
        String subject,
        String body,
        Boolean isHtml,
        Mentions mentions) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Mentions(List<Long> user) {}
}
