package org.checkerframework.dataflow.analysis;

/*>>>
import org.checkerframework.checker.nullness.qual.Nullable;
*/

import com.sun.source.tree.ClassTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.PriorityQueue;
import javax.lang.model.element.Element;
import org.checkerframework.dataflow.cfg.ControlFlowGraph;
import org.checkerframework.dataflow.cfg.block.Block;
import org.checkerframework.dataflow.cfg.block.ExceptionBlock;
import org.checkerframework.dataflow.cfg.block.RegularBlock;
import org.checkerframework.dataflow.cfg.block.SpecialBlock;
import org.checkerframework.dataflow.cfg.node.AssignmentNode;
import org.checkerframework.dataflow.cfg.node.LocalVariableNode;
import org.checkerframework.dataflow.cfg.node.Node;
import org.checkerframework.javacutil.ElementUtils;

/**
 * Common code base for BackwardAnalysis and ForwardAnalysis
 * @author charleszhuochen
 *
 * @param <V>
 * @param <S>
 * @param <T>
 */
public abstract class AbstractAnalysis<
                V extends AbstractValue<V>, S extends Store<S>, T extends TransferFunction<V, S>>
        implements Analysis<V, S, T> {

    /** Is the analysis currently running? */
    protected boolean isRunning = false;

    /** The control flow graph to perform the analysis on. */
    protected ControlFlowGraph cfg;

    protected final Direction direction;

    /** The transfer function for regular nodes. */
    protected T transferFunction;

    /**
     * The transfer inputs of every basic block (assumed to be 'no information' if
     * not present, inputs before blocks in forward analysis, after blocks in backward analysis).
     */
    protected IdentityHashMap<Block, TransferInput<V, S>> inputs;

    /** Abstract values of nodes. */
    protected IdentityHashMap<Node, V> nodeValues;

    /** Map from (effectively final) local variable elements to their abstract value. */
    protected HashMap<Element, V> finalLocalValues;

    /** The worklist used for the fix-point iteration. */
    protected Worklist worklist;

    /**
     * The node that is currently handled in the analysis (if it is running).
     * The following invariant holds:
     *
     * <pre>
     *   !isRunning ==&gt; (currentNode == null)
     * </pre>
     */
    protected Node currentNode;

    /**
     * The tree that is currently being looked at. The transfer function can set
     * this tree to make sure that calls to {@code getValue} will not return
     * information for this given tree.
     */
    protected Tree currentTree;

    /**
     * The current transfer input when the analysis is running.
     */
    protected TransferInput<V, S> currentInput;

    public AbstractAnalysis(Direction direction) {
        this.direction = direction;
    }

    protected abstract void initInitialInputs();

    /**
     * Propagate the stores in currentInput to the next block in the direction of analysis, according to the
     * flowRule.
     */
    protected abstract void propagateStoresTo(
            Block nextBlock,
            Node node,
            TransferInput<V, S> currentInput,
            Store.FlowRule flowRule,
            boolean addToWorklistAgain);

    @Override
    /** Is the analysis currently running? */
    public boolean isRunning() {
        return isRunning;
    }

    @Override
    public Direction getDirection() {
        return this.direction;
    }

    @Override
    public AnalysisResult<V, S> getResult() {
        assert !isRunning;
        IdentityHashMap<Tree, Node> treeLookup = cfg.getTreeLookup();
        return new AnalysisResult<V, S>(nodeValues, inputs, treeLookup, finalLocalValues);
    }

    public void setTransferFunction(T transfer) {
        this.transferFunction = transfer;
    }

    public T getTransferFunction() {
        return transferFunction;
    }

    public Tree getCurrentTree() {
        return currentTree;
    }

    public void setCurrentTree(Tree currentTree) {
        this.currentTree = currentTree;
    }

    /**
     * @return the abstract value for {@link Node} {@code n}, or {@code null} if
     *         no information is available. Note that if the analysis has not
     *         finished yet, this value might not represent the final value for
     *         this node.
     */
    public /*@Nullable*/ V getValue(Node n) {
        if (isRunning) {
            // we do not yet have a org.checkerframework.dataflow fact about the current node
            if (currentNode == null
                    || currentNode == n
                    || (currentTree != null && currentTree == n.getTree())) {
                return null;
            }
            // check that 'n' is a subnode of 'node'. Check immediate operands
            // first for efficiency.
            assert !n.isLValue() : "Did not expect an lvalue, but got " + n;
            if (!(currentNode != n
                    && (currentNode.getOperands().contains(n)
                            || currentNode.getTransitiveOperands().contains(n)))) {
                return null;
            }
            return nodeValues.get(n);
        }
        return nodeValues.get(n);
    }

    /**
     * @return the abstract value for {@link Tree} {@code t}, or {@code null} if
     *         no information is available. Note that if the analysis has not
     *         finished yet, this value might not represent the final value for
     *         this node.
     */
    public /*@Nullable*/ V getValue(Tree t) {
        // we do not yet have a org.checkerframework.dataflow fact about the current node
        if (t == currentTree) {
            return null;
        }
        Node nodeCorrespondingToTree = getNodeForTree(t);
        if (nodeCorrespondingToTree == null || nodeCorrespondingToTree.isLValue()) {
            return null;
        }
        return getValue(nodeCorrespondingToTree);
    }

    /**
     * Get the {@link Node} for a given {@link Tree}.
     */
    public Node getNodeForTree(Tree t) {
        return cfg.getNodeCorrespondingToTree(t);
    }

    /**
     * Get the {@link MethodTree} of the current CFG if the argument {@link Tree} maps
     * to a {@link Node} in the CFG or null otherwise.
     */
    public /*@Nullable*/ MethodTree getContainingMethod(Tree t) {
        return cfg.getContainingMethod(t);
    }

    /**
     * Get the {@link ClassTree} of the current CFG if the argument {@link Tree} maps
     * to a {@link Node} in the CFG or null otherwise.
     */
    public /*@Nullable*/ ClassTree getContainingClass(Tree t) {
        return cfg.getContainingClass(t);
    }

    /**
     * @return the regular exit store, or {@code null}, if there is no such
     *         store (because the method cannot exit through the regular exit
     *         block).
     */
    public /*@Nullable*/ S getRegularExitStore() {
        SpecialBlock regularExitBlock = cfg.getRegularExitBlock();
        if (inputs.containsKey(regularExitBlock)) {
            S regularExitStore = inputs.get(regularExitBlock).getRegularStore();
            return regularExitStore;
        } else {
            return null;
        }
    }

    public S getExceptionalExitStore() {
        S exceptionalExitStore = inputs.get(cfg.getExceptionalExitBlock()).getRegularStore();
        return exceptionalExitStore;
    }

    /**
     * Call the transfer function for node {@code node}, and set that node as
     * current node first.
     */
    protected TransferResult<V, S> callTransferFunction(Node node, TransferInput<V, S> input) {
        if (node.isLValue()) {
            // TODO: should the default behavior be to return either a regular
            // transfer result or a conditional transfer result (depending on
            // store.hasTwoStores()), or is the following correct?
            return new RegularTransferResult<V, S>(null, input.getRegularStore());
        }
        input.node = node;
        currentNode = node;
        TransferResult<V, S> transferResult = node.accept(transferFunction, input);
        currentNode = null;
        // This part should implement in ForwardAnalysis
        //        if (node instanceof ReturnNode) {
        //            // save a copy of the store to later check if some property held at
        //            // a given return statement
        //            storesAtReturnStatements.put((ReturnNode) node, transferResult);
        //        }
        if (node instanceof AssignmentNode) {
            // store the flow-refined value for effectively final local variables
            AssignmentNode assignment = (AssignmentNode) node;
            Node lhst = assignment.getTarget();
            if (lhst instanceof LocalVariableNode) {
                LocalVariableNode lhs = (LocalVariableNode) lhst;
                Element elem = lhs.getElement();
                if (ElementUtils.isEffectivelyFinal(elem)) {
                    finalLocalValues.put(elem, transferResult.getResultValue());
                }
            }
        }
        return transferResult;
    }

    /** Initialize the analysis with a new control flow graph. */
    protected final void init(ControlFlowGraph cfg) {
        initFields(cfg);
        initInitialInputs();
    }

    protected void initFields(ControlFlowGraph cfg) {
        this.cfg = cfg;
        inputs = new IdentityHashMap<>();
        worklist = new Worklist(cfg, direction);
        nodeValues = new IdentityHashMap<>();
        finalLocalValues = new HashMap<>();
    }

    /**
     * Updates the value of node {@code node} to the value of the
     * {@code transferResult}. Returns true if the node's value changed, or a
     * store was updated.
     */
    protected boolean updateNodeValues(Node node, TransferResult<V, S> transferResult) {
        V newVal = transferResult.getResultValue();
        boolean nodeValueChanged = false;

        if (newVal != null) {
            V oldVal = nodeValues.get(node);
            nodeValues.put(node, newVal);
            nodeValueChanged = !Objects.equals(oldVal, newVal);
        }

        return nodeValueChanged || transferResult.storeChanged();
    }

    /**
     * Add a basic block to the worklist. If <code>b</code> is already present,
     * the method does nothing.
     */
    protected void addToWorklist(Block b) {
        // TODO: use a more efficient way to check if b is already present
        if (!worklist.contains(b)) {
            worklist.add(b);
        }
    }

    /**
     * A worklist is a priority queue of blocks in which the order is given
     * by depth-first ordering to place non-loop predecessors ahead of successors.
     */
    protected static class Worklist {

        /** Map all blocks in the CFG to their depth-first order. */
        protected IdentityHashMap<Block, Integer> depthFirstOrder;

        /** Comparators to allow priority queue to order blocks by their depth-first
         * order.*/
        public class ForwardDFOComparator implements Comparator<Block> {
            @Override
            public int compare(Block b1, Block b2) {
                return depthFirstOrder.get(b1) - depthFirstOrder.get(b2);
            }
        }

        public class BackwardDFOComparator implements Comparator<Block> {
            @Override
            public int compare(Block b1, Block b2) {
                return depthFirstOrder.get(b2) - depthFirstOrder.get(b1);
            }
        }

        /** The backing priority queue. */
        protected PriorityQueue<Block> queue;

        public Worklist(ControlFlowGraph cfg, Direction direction) {
            depthFirstOrder = new IdentityHashMap<>();
            int count = 1;
            for (Block b : cfg.getDepthFirstOrderedBlocks()) {
                depthFirstOrder.put(b, count++);
            }

            if (direction == Direction.FORWARD) {
                queue = new PriorityQueue<Block>(11, new ForwardDFOComparator());
            } else if (direction == Direction.BACKWARD) {
                queue = new PriorityQueue<Block>(11, new BackwardDFOComparator());
            } else {
                assert false : "Unexpected Direction meet: " + direction.name();
            }
        }

        public boolean isEmpty() {
            return queue.isEmpty();
        }

        public boolean contains(Block block) {
            return queue.contains(block);
        }

        public void add(Block block) {
            queue.add(block);
        }

        public Block poll() {
            return queue.poll();
        }

        @Override
        public String toString() {
            return "Worklist(" + queue + ")";
        }
    }

    /**
     * Read the {@link Store} for a particular basic block from a map of stores
     * (or {@code null} if none exists yet).
     */
    protected static <S> /*@Nullable*/ S readFromStore(Map<Block, S> stores, Block b) {
        return stores.get(b);
    }
}
