package tests;

import org.checkerframework.framework.test.FrameworkPerDirectoryTest;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.util.List;

/** Tests the MethodVal Checker. */
public class MethodValTest extends FrameworkPerDirectoryTest {

    /** @param testFiles the files containing test code, which will be type-checked */
    public MethodValTest(List<File> testFiles) {
        super(
                testFiles,
                org.checkerframework.common.reflection.MethodValChecker.class,
                "methodval",
                "-Anomsgtext");
    }

    @Parameters
    public static String[] getTestDirs() {
        return new String[] {"methodval"};
    }
}