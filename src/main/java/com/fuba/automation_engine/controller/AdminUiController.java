package com.fuba.automation_engine.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Forwards every {@code GET /admin-ui/...} request to {@code /index.html} so
 * the React SPA boots and its client-side router can take over. Without this
 * fallback, deep-link reloads (e.g. {@code /admin-ui/persons/42}) would 404
 * because Spring has no controller mapping for them and the static-resource
 * handler only serves files that physically exist under
 * {@code src/main/resources/static/}.
 *
 * <p>Scope of the catch-all:
 * <ul>
 *   <li>Matches {@code /admin-ui} and any path beneath it.</li>
 *   <li>Does NOT touch API paths ({@code /admin/**}, {@code /webhooks/**},
 *       {@code /health}) — those reach their own controllers because Spring's
 *       handler mapping picks the more specific match.</li>
 *   <li>Does NOT shadow asset requests like {@code /assets/foo.js}, which
 *       Vite emits at the root and are served by the static handler before
 *       this controller is consulted.</li>
 * </ul>
 *
 * <p>Single wildcard mapping (rather than enumerating routes) so adding new
 * SPA routes never requires touching this file.
 */
@Controller
public class AdminUiController {

    @GetMapping({"/admin-ui", "/admin-ui/**"})
    public String forwardToIndex() {
        return "forward:/index.html";
    }
}
