package com.fuba.automation_engine.replay;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Loads {@link ReplayFixture} JSON files from the classpath. By default scans
 * {@code classpath:replay-fixtures/*.json}; fixtures named {@code README*.json}
 * are skipped (the README in that directory is markdown, not a fixture, but if
 * a README ever ends up named .json it should not be loaded as a scenario).
 */
public final class ReplayFixtureLoader {

    private static final String DEFAULT_PATTERN = "classpath:replay-fixtures/*.json";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private ReplayFixtureLoader() {
    }

    public static List<ReplayFixture> loadAll() {
        return loadAll(DEFAULT_PATTERN);
    }

    public static List<ReplayFixture> loadAll(String pattern) {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources;
        try {
            resources = resolver.getResources(pattern);
        } catch (IOException ex) {
            throw new UncheckedIOException("Failed to enumerate fixture resources at " + pattern, ex);
        }
        List<ReplayFixture> out = new ArrayList<>(resources.length);
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename == null || filename.toLowerCase().startsWith("readme")) {
                continue;
            }
            try (InputStream in = resource.getInputStream()) {
                ReplayFixture fixture = MAPPER.readValue(in, ReplayFixture.class);
                out.add(fixture);
            } catch (IOException ex) {
                throw new UncheckedIOException("Failed to load fixture " + filename, ex);
            }
        }
        return out;
    }
}
