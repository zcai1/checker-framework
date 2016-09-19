package org.checkerframework.dataflow.analysis;

/*>>>
import org.checkerframework.checker.nullness.qual.Nullable;
*/

import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.VariableTree;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import org.checkerframework.dataflow.cfg.ControlFlowGraph;
import org.checkerframework.dataflow.cfg.UnderlyingAST;
import org.checkerframework.dataflow.cfg.UnderlyingAST.CFGLambda;
import org.checkerframework.dataflow.cfg.UnderlyingAST.CFGMethod;
import org.checkerframework.dataflow.cfg.UnderlyingAST.Kind;
import org.checkerframework.dataflow.cfg.block.Block;
import org.checkerframework.dataflow.cfg.block.ConditionalBlock;
import org.checkerframework.dataflow.cfg.block.ExceptionBlock;
import org.checkerframework.dataflow.cfg.block.RegularBlock;
import org.checkerframework.dataflow.cfg.block.SpecialBlock;
import org.checkerframework.dataflow.cfg.node.LocalVariableNode;
import org.checkerframework.dataflow.cfg.node.Node;
import org.checkerframework.dataflow.cfg.node.ReturnNode;
import org.checkerframework.javacutil.Pair;

/**
 * An implementation of an iterative algorithm to solve a org.checkerframework.dataflow problem,
 * given a control flow graph and a transfer function.
 *
 * @author Stefan Heule
 *
 * @param <V>
 *            The abstract value type to be tracked by the analysis.
 * @param <S>
 *            The store type used in the analysis.
 * @param <T>
 *            The transfer function type that is used to approximated runtime
 *            behavior.
 */
public class ForwardAnalysisImpl<
                V extends AbstractValue<V>,
                S extends Store<S>,
                T extends ForwardTransferFunction<V, S>>
        extends AbstractAnalysis<V, S, T> implements ForwardAnalysis<V, S, T> {

    //    /** The associated processing environment */
    //    protected final ProcessingEnvironment env;
    //
    //    /** Instance of the types utility. */
    //    protected final Types types;

    /**
     * Then stores before every basic block (assumed to be 'no information' if
     * not present).
     */
    protected IdentityHashMap<Block, S> thenStores;

    /**
     * Else stores before every basic block (assumed to be 'no information' if
     * not present).
     */
    protected IdentityHashMap<Block, S> elseStores;

    /**
     * The stores after every return statement.
     */
    protected IdentityHashMap<ReturnNode, TransferResult<V, S>> storesAtReturnStatements;

    /**
     * Construct an object that can perform a org.checkerframework.dataflow analysis over a control
     * flow graph. The transfer function is set later using
     * {@code setTransferFunction}.
     */
    public ForwardAnalysisImpl() {
        super(Direction.FORWARD);
    }

    /**
     * Construct an object that can perform a org.checkerframework.dataflow analysis over a control
     * flow graph, given a transfer function.
     */
    public ForwardAnalysisImpl(T transfer) {
        this();
        this.transferFunction = transfer;
    }

    @Override
    /**
     * Perform the actual analysis. Should only be called once after the object
     * has been created.
     */
    public void performAnalysis(ControlFlowGraph cfg) {
        assert isRunning == false;
        isRunning = true;

        init(cfg);

        while (!worklist.isEmpty()) {
            Block b = worklist.poll();

            switch (b.getType()) {
                case REGULAR_BLOCK:
                    {
                        RegularBlock rb = (RegularBlock) b;

                        // apply transfer function to contents
                        TransferInput<V, S> inputBefore = getInputBefore(rb);
                        currentInput = inputBefore.copy();
                        TransferResult<V, S> transferResult = null;
                        Node lastNode = null;
                        boolean addToWorklistAgain = false;
                        for (Node n : rb.getContents()) {
                            transferResult = callTransferFunction(n, currentInput);
                            addToWorklistAgain |= updateNodeValues(n, transferResult);
                            currentInput = new TransferInput<>(n, this, transferResult);
                            lastNode = n;
                        }
                        // loop will run at least one, making transferResult non-null

                        // propagate store to successors
                        Block succ = rb.getSuccessor();
                        assert succ != null
                                : "regular basic block without non-exceptional successor unexpected";
                        propagateStoresTo(
                                succ, lastNode, currentInput, rb.getFlowRule(), addToWorklistAgain);
                        break;
                    }

                case EXCEPTION_BLOCK:
                    {
                        ExceptionBlock eb = (ExceptionBlock) b;

                        // apply transfer function to content
                        TransferInput<V, S> inputBefore = getInputBefore(eb);
                        currentInput = inputBefore.copy();
                        Node node = eb.getNode();
                        TransferResult<V, S> transferResult =
                                callTransferFunction(node, currentInput);
                        boolean addToWorklistAgain = updateNodeValues(node, transferResult);

                        // propagate store to successor
                        Block succ = eb.getSuccessor();
                        if (succ != null) {
                            currentInput = new TransferInput<>(node, this, transferResult);
                            // TODO? Variable wasn't used.
                            // Store.FlowRule storeFlow = eb.getFlowRule();
                            propagateStoresTo(
                                    succ, node, currentInput, eb.getFlowRule(), addToWorklistAgain);
                        }

                        // propagate store to exceptional successors
                        for (Entry<TypeMirror, Set<Block>> e :
                                eb.getExceptionalSuccessors().entrySet()) {
                            TypeMirror cause = e.getKey();
                            S exceptionalStore = transferResult.getExceptionalStore(cause);
                            if (exceptionalStore != null) {
                                for (Block exceptionSucc : e.getValue()) {
                                    addStoreBefore(
                                            exceptionSucc,
                                            node,
                                            exceptionalStore,
                                            Store.Kind.BOTH,
                                            addToWorklistAgain);
                                }
                            } else {
                                for (Block exceptionSucc : e.getValue()) {
                                    addStoreBefore(
                                            exceptionSucc,
                                            node,
                                            inputBefore.copy().getRegularStore(),
                                            Store.Kind.BOTH,
                                            addToWorklistAgain);
                                }
                            }
                        }
                        break;
                    }

                case CONDITIONAL_BLOCK:
                    {
                        ConditionalBlock cb = (ConditionalBlock) b;

                        // get store before
                        TransferInput<V, S> inputBefore = getInputBefore(cb);
                        TransferInput<V, S> input = inputBefore.copy();

                        // propagate store to successor
                        Block thenSucc = cb.getThenSuccessor();
                        Block elseSucc = cb.getElseSuccessor();

                        propagateStoresTo(thenSucc, null, input, cb.getThenFlowRule(), false);
                        propagateStoresTo(elseSucc, null, input, cb.getElseFlowRule(), false);
                        break;
                    }

                case SPECIAL_BLOCK:
                    {
                        // special basic blocks are empty and cannot throw exceptions,
                        // thus there is no need to perform any analysis.
                        SpecialBlock sb = (SpecialBlock) b;
                        Block succ = sb.getSuccessor();
                        if (succ != null) {
                            propagateStoresTo(
                                    succ, null, getInputBefore(b), sb.getFlowRule(), false);
                        }
                        break;
                    }

                default:
                    assert false;
                    break;
            }
        }

        assert isRunning == true;
        isRunning = false;
    }

    @Override
    /**
     * Propagate the stores in currentInput to the successor block, succ, according to the
     * flowRule.
     */
    protected void propagateStoresTo(
            Block succ,
            Node node,
            TransferInput<V, S> currentInput,
            Store.FlowRule flowRule,
            boolean addToWorklistAgain) {
        switch (flowRule) {
            case EACH_TO_EACH:
                if (currentInput.containsTwoStores()) {
                    addStoreBefore(
                            succ,
                            node,
                            currentInput.getThenStore(),
                            Store.Kind.THEN,
                            addToWorklistAgain);
                    addStoreBefore(
                            succ,
                            node,
                            currentInput.getElseStore(),
                            Store.Kind.ELSE,
                            addToWorklistAgain);
                } else {
                    addStoreBefore(
                            succ,
                            node,
                            currentInput.getRegularStore(),
                            Store.Kind.BOTH,
                            addToWorklistAgain);
                }
                break;
            case THEN_TO_BOTH:
                addStoreBefore(
                        succ,
                        node,
                        currentInput.getThenStore(),
                        Store.Kind.BOTH,
                        addToWorklistAgain);
                break;
            case ELSE_TO_BOTH:
                addStoreBefore(
                        succ,
                        node,
                        currentInput.getElseStore(),
                        Store.Kind.BOTH,
                        addToWorklistAgain);
                break;
            case THEN_TO_THEN:
                addStoreBefore(
                        succ,
                        node,
                        currentInput.getThenStore(),
                        Store.Kind.THEN,
                        addToWorklistAgain);
                break;
            case ELSE_TO_ELSE:
                addStoreBefore(
                        succ,
                        node,
                        currentInput.getElseStore(),
                        Store.Kind.ELSE,
                        addToWorklistAgain);
                break;
        }
    }

    @Override
    /**
     * Call the transfer function for node {@code node}, and set that node as
     * current node first.
     */
    protected TransferResult<V, S> callTransferFunction(Node node, TransferInput<V, S> input) {
        TransferResult<V, S> transferResult = super.callTransferFunction(node, input);

        if (node instanceof ReturnNode) {
            // save a copy of the store to later check if some property held at
            // a given return statement
            storesAtReturnStatements.put((ReturnNode) node, transferResult);
        }
        return transferResult;
    }

    @Override
    protected void initFields(ControlFlowGraph cfg) {
        super.initFields(cfg);
        thenStores = new IdentityHashMap<>();
        elseStores = new IdentityHashMap<>();
        storesAtReturnStatements = new IdentityHashMap<>();
    }

    @Override
    protected void initInitialInputs() {
        worklist.add(cfg.getEntryBlock());

        List<LocalVariableNode> parameters = null;
        UnderlyingAST underlyingAST = cfg.getUnderlyingAST();
        if (underlyingAST.getKind() == Kind.METHOD) {
            MethodTree tree = ((CFGMethod) underlyingAST).getMethod();
            parameters = new ArrayList<>();
            for (VariableTree p : tree.getParameters()) {
                LocalVariableNode var = new LocalVariableNode(p);
                parameters.add(var);
                // TODO: document that LocalVariableNode has no block that it
                // belongs to
            }
        } else if (underlyingAST.getKind() == Kind.LAMBDA) {
            LambdaExpressionTree lambda = ((CFGLambda) underlyingAST).getLambdaTree();
            parameters = new ArrayList<>();
            for (VariableTree p : lambda.getParameters()) {
                LocalVariableNode var = new LocalVariableNode(p);
                parameters.add(var);
                // TODO: document that LocalVariableNode has no block that it
                // belongs to
            }

        } else {
            // nothing to do
        }
        S initialStore = transferFunction.initialStore(underlyingAST, parameters);
        Block entry = cfg.getEntryBlock();
        thenStores.put(entry, initialStore);
        elseStores.put(entry, initialStore);
        inputs.put(entry, new TransferInput<>(null, this, initialStore));
    }

    @Override
    /**
     * Read the {@link TransferInput} for a particular basic block (or {@code null} if
     * none exists yet).
     */
    public /*@Nullable*/ TransferInput<V, S> getInput(Block b) {
        return getInputBefore(b);
    }

    /**
     * Add a store before the basic block <code>b</code> by merging with the
     * existing stores for that location.
     */
    protected void addStoreBefore(
            Block b, Node node, S s, Store.Kind kind, boolean addBlockToWorklist) {
        S thenStore = getStoreBefore(b, Store.Kind.THEN);
        S elseStore = getStoreBefore(b, Store.Kind.ELSE);

        switch (kind) {
            case THEN:
                {
                    // Update the then store
                    S newThenStore = (thenStore != null) ? thenStore.leastUpperBound(s) : s;
                    if (!newThenStore.equals(thenStore)) {
                        thenStores.put(b, newThenStore);
                        if (elseStore != null) {
                            inputs.put(b, new TransferInput<>(node, this, newThenStore, elseStore));
                            addBlockToWorklist = true;
                        }
                    }
                    break;
                }
            case ELSE:
                {
                    // Update the else store
                    S newElseStore = (elseStore != null) ? elseStore.leastUpperBound(s) : s;
                    if (!newElseStore.equals(elseStore)) {
                        elseStores.put(b, newElseStore);
                        if (thenStore != null) {
                            inputs.put(b, new TransferInput<>(node, this, thenStore, newElseStore));
                            addBlockToWorklist = true;
                        }
                    }
                    break;
                }
            case BOTH:
                if (thenStore == elseStore) {
                    // Currently there is only one regular store
                    S newStore = (thenStore != null) ? thenStore.leastUpperBound(s) : s;
                    if (!newStore.equals(thenStore)) {
                        thenStores.put(b, newStore);
                        elseStores.put(b, newStore);
                        inputs.put(b, new TransferInput<>(node, this, newStore));
                        addBlockToWorklist = true;
                    }
                } else {
                    boolean storeChanged = false;

                    S newThenStore = (thenStore != null) ? thenStore.leastUpperBound(s) : s;
                    if (!newThenStore.equals(thenStore)) {
                        thenStores.put(b, newThenStore);
                        storeChanged = true;
                    }

                    S newElseStore = (elseStore != null) ? elseStore.leastUpperBound(s) : s;
                    if (!newElseStore.equals(elseStore)) {
                        elseStores.put(b, newElseStore);
                        storeChanged = true;
                    }

                    if (storeChanged) {
                        inputs.put(b, new TransferInput<>(node, this, newThenStore, newElseStore));
                        addBlockToWorklist = true;
                    }
                }
        }

        if (addBlockToWorklist) {
            addToWorklist(b);
        }
    }

    /**
     * @return the transfer input corresponding to the location right before the basic
     *         block <code>b</code>.
     */
    protected /*@Nullable*/ TransferInput<V, S> getInputBefore(Block b) {
        return inputs.get(b);
    }

    /**
     * @return the store corresponding to the location right before the basic
     *         block <code>b</code>.
     */
    protected /*@Nullable*/ S getStoreBefore(Block b, Store.Kind kind) {
        switch (kind) {
            case THEN:
                return readFromStore(thenStores, b);
            case ELSE:
                return readFromStore(elseStores, b);
            default:
                assert false;
                return null;
        }
    }

    public List<Pair<ReturnNode, TransferResult<V, S>>> getReturnStatementStores() {
        List<Pair<ReturnNode, TransferResult<V, S>>> result = new ArrayList<>();
        for (ReturnNode returnNode : cfg.getReturnNodes()) {
            TransferResult<V, S> store = storesAtReturnStatements.get(returnNode);
            result.add(Pair.of(returnNode, store));
        }
        return result;
    }
}
