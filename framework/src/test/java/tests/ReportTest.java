package tests;

import org.checkerframework.framework.test.FrameworkPerDirectoryTest;
import org.junit.runners.Parameterized.Parameters;

import java.io.File;
import java.util.List;

public class ReportTest extends FrameworkPerDirectoryTest {

    /** @param testFiles the files containing test code, which will be type-checked */
    public ReportTest(List<File> testFiles) {
        super(
                testFiles,
                org.checkerframework.common.util.report.ReportChecker.class,
                "report",
                "-Anomsgtext",
                "-Astubs=tests/report/reporttest.astub");
    }

    @Parameters
    public static String[] getTestDirs() {
        return new String[] {"report"};
    }
}
