package tests;

import java.io.File;
import java.util.List;
import org.checkerframework.framework.test.CheckerFrameworkPerDirectoryTest;
import org.junit.runners.Parameterized.Parameters;
import testlib.qualifiedlocations.QualifiedLocationsChecker;

/** Created by mier on 06/07/17. */
public class QualifiedLocationsTest extends CheckerFrameworkPerDirectoryTest {

    public QualifiedLocationsTest(List<File> testFiles) {
        super(testFiles, QualifiedLocationsChecker.class, "qualifiedlocations", "-Anomsgtext");
    }

    @Parameters
    public static String[] getTestDirs() {
        return new String[] {"qualifiedlocations"};
    }
}
