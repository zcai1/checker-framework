import org.checkerframework.checker.gut.qual.*;

public class CondExpr {

    void m() {
        @Peer Object o = true ? new @Peer Object() : this;
    }
}
