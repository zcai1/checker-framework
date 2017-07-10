package tests;

import java.io.File;
import java.util.List;
import org.checkerframework.framework.test.CheckerFrameworkPerDirectoryTest;
import org.junit.runners.Parameterized.Parameters;

public class GUTTestsLostYes extends CheckerFrameworkPerDirectoryTest {

    public GUTTestsLostYes(List<File> testFiles) {
        super(
                testFiles,
                org.checkerframework.checker.gut.GUTChecker.class,
                "gut/lostyes",
                "-Anomsgtext",
                "-Alint=allowLost");
    }

    @Parameters
    public static String[] getTestDirs() {
        return new String[] {"gut/lostyes"};
    }
}
