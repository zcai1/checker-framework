import org.checkerframework.checker.gut.qual.*;

public class ArrayWF {
    //:: error: (type.invalid)
    @Peer int[] epi;
    //:: error: (type.invalid)
    @Rep int[] eri;
    //:: error: (type.invalid)
    @Any int[] eai;

    //:: error: (type.invalid)
    @Peer @Rep Object[] pro;

    //:: error: (type.invalid)
    @Peer Object @Rep @Peer [] porp;

    //:: error: (type.invalid)
    @Peer Object @Rep [] @Rep @Peer [] porrp;

    //:: error: (type.invalid)
    @Peer Object[][] @Rep @Peer [] poaarp;
}
