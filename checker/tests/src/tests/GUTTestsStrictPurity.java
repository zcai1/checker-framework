package tests;

import java.io.File;
import java.util.List;
import org.checkerframework.framework.test.CheckerFrameworkPerDirectoryTest;
import org.junit.runners.Parameterized.Parameters;

public class GUTTestsStrictPurity extends CheckerFrameworkPerDirectoryTest {

    public GUTTestsStrictPurity(List<File> testFiles) {
        super(
                testFiles,
                org.checkerframework.checker.gut.GUTChecker.class,
                "gut/strictpurity",
                "-Anomsgtext",
                "-Alint=checkStrictPurity");
    }

    @Parameters
    public static String[] getTestDirs() {
        return new String[] {"gut/strictpurity"};
    }
}
