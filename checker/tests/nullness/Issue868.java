// Test case for Issue 868
// https://github.com/typetools/checker-framework/issues/868
// @skip-test

import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.List;

public class Issue868 {
    <E extends @Nullable Object & @Nullable List> void test2(E e) {
        // :: error: (dereference.of.nullable)
        e.toString();
    }

    void use() {
        test2(null);
    }
}
