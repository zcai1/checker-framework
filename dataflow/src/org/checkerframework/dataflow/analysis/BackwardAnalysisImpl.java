package org.checkerframework.dataflow.analysis;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.ListIterator;
import org.checkerframework.dataflow.analysis.Store.FlowRule;
import org.checkerframework.dataflow.cfg.ControlFlowGraph;
import org.checkerframework.dataflow.cfg.UnderlyingAST;
import org.checkerframework.dataflow.cfg.block.Block;
import org.checkerframework.dataflow.cfg.block.ConditionalBlock;
import org.checkerframework.dataflow.cfg.block.ExceptionBlock;
import org.checkerframework.dataflow.cfg.block.RegularBlock;
import org.checkerframework.dataflow.cfg.block.SpecialBlock;
import org.checkerframework.dataflow.cfg.block.SpecialBlock.SpecialBlockType;
import org.checkerframework.dataflow.cfg.node.Node;
import org.checkerframework.dataflow.cfg.node.ReturnNode;

public class BackwardAnalysisImpl<
                V extends AbstractValue<V>,
                S extends Store<S>,
                T extends BackwardTransferFunction<V, S>>
        extends AbstractAnalysis<V, S, T> implements BackwardAnalysis<V, S, T> {

    /**
     * out stores after every basic block (assumed to be 'no information' if
     * not present).
     */
    protected IdentityHashMap<Block, S> outStores;

    /**
     * exception store of an Exception Block, propagated by exceptional successors of it's Exception Block,
     * and merged with the normal TransferResult
     */
    protected IdentityHashMap<ExceptionBlock, S> exceptionStores;

    /**
     * The store before the entry block
     */
    protected S storeAtEntry;

    public BackwardAnalysisImpl() {
        super(Direction.BACKWARD);
    }

    public BackwardAnalysisImpl(T transfer) {
        this();
        this.transferFunction = transfer;
    }

    @Override
    public void performAnalysis(ControlFlowGraph cfg) {
        assert isRunning == false;
        isRunning = true;

        init(cfg);

        while (!worklist.isEmpty()) {
            Block block = worklist.poll();

            switch (block.getType()) {
                case REGULAR_BLOCK:
                    {
                        RegularBlock rBlock = (RegularBlock) block;

                        TransferInput<V, S> inputAfter = getInput(rBlock);
                        currentInput = inputAfter.copy();
                        TransferResult<V, S> transferResult = null;
                        Node firstNode = null;
                        boolean addToWorklistAgain = false;

                        List<Node> nodeList = rBlock.getContents();
                        ListIterator<Node> reverseIter = nodeList.listIterator(nodeList.size());

                        while (reverseIter.hasPrevious()) {
                            Node node = reverseIter.previous();
                            transferResult = callTransferFunction(node, currentInput);
                            addToWorklistAgain |= updateNodeValues(node, transferResult);
                            currentInput = new TransferInput<>(node, this, transferResult);
                            firstNode = node;
                        }
                        //propagate store to predecessors
                        for (Block pred : rBlock.getPredecessors()) {
                            propagateStoresTo(
                                    pred,
                                    firstNode,
                                    currentInput,
                                    FlowRule.EACH_TO_EACH,
                                    addToWorklistAgain);
                        }
                        break;
                    }

                case EXCEPTION_BLOCK:
                    {
                        ExceptionBlock eBlock = (ExceptionBlock) block;

                        TransferInput<V, S> inputAfter = getInput(eBlock);
                        currentInput = inputAfter.copy();
                        Node node = eBlock.getNode();
                        TransferResult<V, S> transferResult =
                                callTransferFunction(node, currentInput);
                        boolean addToWorklistAgain = updateNodeValues(node, transferResult);

                        // merged transferResult with exceptionStore if exist one
                        S exceptionStore = exceptionStores.get(eBlock);
                        S mergedStore =
                                exceptionStore != null
                                        ? transferResult
                                                .getRegularStore()
                                                .leastUpperBound(exceptionStore)
                                        : transferResult.getRegularStore();

                        for (Block pred : eBlock.getPredecessors()) {
                            addStoreAfter(pred, node, mergedStore, addToWorklistAgain);
                        }
                        break;
                    }

                case CONDITIONAL_BLOCK:
                    {
                        ConditionalBlock cBlock = (ConditionalBlock) block;

                        TransferInput<V, S> inputAfter = getInput(cBlock);
                        TransferInput<V, S> input = inputAfter.copy();

                        for (Block pred : cBlock.getPredecessors()) {
                            propagateStoresTo(pred, null, input, FlowRule.EACH_TO_EACH, false);
                        }
                        break;
                    }

                case SPECIAL_BLOCK:
                    {
                        SpecialBlock sBlock = (SpecialBlock) block;
                        final SpecialBlockType sType = sBlock.getSpecialType();
                        //storage the store at entry
                        if (sType == SpecialBlockType.ENTRY) {
                            storeAtEntry = outStores.get(sBlock);
                        } else {
                            assert sType == SpecialBlockType.EXIT
                                    || sType == SpecialBlockType.EXCEPTIONAL_EXIT;
                            for (Block pred : sBlock.getPredecessors()) {
                                propagateStoresTo(
                                        pred, null, getInput(sBlock), FlowRule.EACH_TO_EACH, false);
                            }
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

    /**
     * return the TransferInput for Block b
     */
    @Override
    public TransferInput<V, S> getInput(Block b) {
        return inputs.get(b);
    }

    @Override
    public S getEntrySotre() {
        return storeAtEntry;
    }

    @Override
    protected void initFields(ControlFlowGraph cfg) {
        super.initFields(cfg);
        outStores = new IdentityHashMap<>();
        exceptionStores = new IdentityHashMap<>();
        // storeAtEntry is null before analysis begin
        storeAtEntry = null;
    }

    @Override
    protected void initInitialInputs() {
        SpecialBlock regularExitBlock = cfg.getRegularExitBlock();
        SpecialBlock exceptionExitBlock = cfg.getExceptionalExitBlock();

        // TODO: this is a tricky assertion! reason see the comment below.
        assert worklist.depthFirstOrder.get(regularExitBlock) != null
                        || worklist.depthFirstOrder.get(exceptionExitBlock) != null
                : "regularExitBlock and exceptionExitBlock should never both be null at the same time.";

        UnderlyingAST underlyingAST = cfg.getUnderlyingAST();
        List<ReturnNode> returnNodes = cfg.getReturnNodes();

        S normalInitialStore = transferFunction.initialNormalExitStore(underlyingAST, returnNodes);
        S exceptionalInitialStore = transferFunction.initialExceptionalExitStore(underlyingAST);

        // TODO: this is tricky if-condition!
        // exceptionExitBlock and regularExitBlock will always be non-null account to the implementation
        // in CFGBuilder#CFGTranslationPhaseTwo#process() will always create these two exit blocks on a CFG no
        // matter this CFG whether would has these exit blocks according to the underlying AST.
        // Here the workaround is using the inner protected Map in Worklist to decide whether a given cfg really
        // has a regularExitBlock and/or an exceptionExitBlockå
        if (worklist.depthFirstOrder.get(regularExitBlock) != null) {
            worklist.add(regularExitBlock);
            inputs.put(regularExitBlock, new TransferInput<>(null, this, normalInitialStore));
            outStores.put(regularExitBlock, normalInitialStore);
        }

        // TODO: tricky code, same reason as above.
        if (worklist.depthFirstOrder.get(exceptionExitBlock) != null) {
            worklist.add(exceptionExitBlock);
            inputs.put(
                    exceptionExitBlock, new TransferInput<>(null, this, exceptionalInitialStore));
            outStores.put(exceptionExitBlock, exceptionalInitialStore);
        }

        assert !worklist.isEmpty() : "worklist should has at least one exit block as start point.";
        assert inputs.size() > 0 && outStores.size() > 0
                : "should has at least one input and outStore at beginning";
    }

    @Override
    protected void propagateStoresTo(
            Block pred,
            Node node,
            TransferInput<V, S> currentInput,
            FlowRule flowRule,
            boolean addToWorklistAgain) {
        assert flowRule == FlowRule.EACH_TO_EACH
                : "backward analysis always propagate EACH to EACH, because there is no control flow.";
        addStoreAfter(pred, node, currentInput.getRegularStore(), addToWorklistAgain);
    }

    protected void addStoreAfter(Block pred, Node node, S s, boolean addBlockToWorklist) {
        if (pred instanceof ExceptionBlock
                && (((ExceptionBlock) pred).getSuccessor() == null
                        || (node != null
                                && ((ExceptionBlock) pred).getSuccessor().getId()
                                        != node.getBlock().getId()))) {
            // TODO: implement an equalTo() in Block would be better
            // if the block of current node is not the regular successor block of exception block's predecessor,
            // then it must be the exceptional successor of it's predecessor. Then it's store propagate
            // to the exceptionStore of it's predecessor
            // currently I don't track the label of an exceptional edge from Exception Block
            // to it's exceptional successors in backward direction.
            // All exception stores of exceptional successors of an Exception Block
            // will merge to one exception store at Exception Block.
            // Similiar to if-else branch, in backward direction, these will merge
            // in backward direction no matter it from a else or if branch.

            ExceptionBlock ebPred = (ExceptionBlock) pred;

            S exceptionStore = exceptionStores.get(ebPred);

            S newExceptionStore = (exceptionStore != null) ? exceptionStore.leastUpperBound(s) : s;
            if (!newExceptionStore.equals(exceptionStore)) {
                exceptionStores.put(ebPred, newExceptionStore);
                addBlockToWorklist = true;
            }
        } else {
            S predOutStore = getStoreAfter(pred);

            S newPredOutStore = (predOutStore != null) ? predOutStore.leastUpperBound(s) : s;

            if (!newPredOutStore.equals(predOutStore)) {
                outStores.put(pred, newPredOutStore);
                inputs.put(pred, new TransferInput<>(node, this, newPredOutStore));
                addBlockToWorklist = true;
            }
        }

        if (addBlockToWorklist) {
            addToWorklist(pred);
        }
    }

    protected S getStoreAfter(Block b) {
        return readFromStore(outStores, b);
    }
}
