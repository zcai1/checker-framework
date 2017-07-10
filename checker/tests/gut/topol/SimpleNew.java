import org.checkerframework.checker.gut.qual.*;

/** @author wmdietl */
public class SimpleNew {
    // OK
    @Peer Object p = new @Peer Object();
    @Rep Object r = new @Rep Object();

    //:: error: (uts.new.ownership)
    @Any Object a = new @Any Object();
}
