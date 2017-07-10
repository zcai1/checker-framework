package tests;

import java.io.File;
import java.util.List;
import org.checkerframework.framework.test.CheckerFrameworkPerDirectoryTest;
import org.junit.runners.Parameterized.Parameters;

public class GUTTestsTopology extends CheckerFrameworkPerDirectoryTest {

    public GUTTestsTopology(List<File> testFiles) {
        super(
                testFiles,
                org.checkerframework.checker.gut.GUTChecker.class,
                "gut/topol",
                "-Anomsgtext");
    }

    @Parameters
    public static String[] getTestDirs() {
        return new String[] {"gut/topol"};
    }
}
