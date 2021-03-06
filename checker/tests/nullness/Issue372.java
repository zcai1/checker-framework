// Test case for Issue 372:
// https://github.com/typetools/checker-framework/issues/372

import org.checkerframework.checker.nullness.qual.EnsuresKeyFor;

import java.util.HashMap;
import java.util.Map;

public class Issue372 {
    private final Map<String, String> labels = new HashMap<>();

    @EnsuresKeyFor(value = "#1", map = "labels")
    void foo(String v) {
        labels.put(v, "");
    }
}
