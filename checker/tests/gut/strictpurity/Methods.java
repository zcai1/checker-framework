import org.checkerframework.checker.gut.qual.*;
/** @author wmdietl */
public class Methods {

    int x;

    @Pure
    void m() {
        //:: error: (purity.assignment.forbidden)
        this.x = 5;
    }
}
