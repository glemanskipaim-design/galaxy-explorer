package dev.galaxyexplorer.api.search;

import java.util.List;

public record SearchResponse(String query, String normalizedQuery, List<SearchHitDto> hits) {
}
