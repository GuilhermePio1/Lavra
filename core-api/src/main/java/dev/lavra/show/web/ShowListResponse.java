package dev.lavra.show.web;

import java.util.List;

/**
 * The list payload of {@code GET /shows}. Not paginated on purpose: the plan's
 * show ceiling is low enough that the whole collection always fits in one
 * response (contract).
 */
record ShowListResponse(List<ShowResponse> items) {
}
