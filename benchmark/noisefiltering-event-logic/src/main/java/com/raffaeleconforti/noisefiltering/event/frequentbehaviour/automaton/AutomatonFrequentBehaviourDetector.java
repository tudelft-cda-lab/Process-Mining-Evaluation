package com.raffaeleconforti.noisefiltering.event.frequentbehaviour.automaton;

import com.raffaeleconforti.automaton.Automaton;
import com.raffaeleconforti.automaton.Edge;
import com.raffaeleconforti.automaton.Node;
import com.raffaeleconforti.ilpsolverwrapper.ILPSolver;
import com.raffaeleconforti.ilpsolverwrapper.impl.gurobi.Gurobi_Solver;
import com.raffaeleconforti.ilpsolverwrapper.impl.lpsolve.LPSolve_Solver;
import com.raffaeleconforti.noisefiltering.event.optimization.wrapper.WrapperInfrequentBehaviourSolver;
import org.eclipse.collections.impl.set.mutable.UnifiedSet;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by conforti on 14/02/15.
 */
public class AutomatonFrequentBehaviourDetector {

    private final ExecutorService executor = Executors.newFixedThreadPool(6);

    private final Lock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    public final static int AVE = 0;
    public final static int MAX = 1;
    public final static int MIN = 2;

    private int approach = 0;

    public AutomatonFrequentBehaviourDetector(int approach) {
        this.approach = approach;
    }

    public Automaton removeFrequentBehaviour(Automaton<String> automaton, Set<Node<String>> requiredStates, double threshold, boolean useGurobi, boolean useArcsFrequency) {
        Set<Edge<String>> removable;

        automaton.getAutomatonStart();
        automaton.getAutomatonEnd();
        automaton.createDirectedGraph();

        Set<Edge<String>> infrequent = discoverInfrequentEdges(automaton);

        ILPSolver ilp_solver;
        WrapperInfrequentBehaviourSolver<String> solver;

        if(useGurobi) {
            ilp_solver = new Gurobi_Solver();
        }else {
            ilp_solver = new LPSolve_Solver();
        }
        solver = new WrapperInfrequentBehaviourSolver<>(automaton, infrequent, requiredStates, useArcsFrequency);
        removable = solver.identifyRemovableEdges(ilp_solver);

        for(Edge<String> edge : removable) {
            automaton = remove(automaton, edge);
        }
        return automaton;

    }

    public Set<Edge<String>> discoverInfrequentEdges(Automaton<String> automaton, double threshold) {
        Set<Edge<String>> infrequent = new UnifiedSet<Edge<String>>();
        for (Edge<String> edge : automaton.getEdges()) {
            if (isInfrequent(automaton, edge, threshold)) {
                infrequent.add(edge);
            }
        }
        return infrequent;
    }

    private Set<Edge<String>> discoverInfrequentEdges(Automaton<String> automaton) {
        Set<Edge<String>> infrequent = new UnifiedSet<Edge<String>>();
        for (Edge<String> edge : automaton.getEdges()) {
            if (edge.isInfrequent()) {
                infrequent.add(edge);
            }
        }
        return infrequent;
    }

    private boolean isInfrequent(Automaton<String> automaton, Edge<String> edge, double threshold) {
        double value = getFrequency(automaton, edge);
        return value < threshold;
    }

    private double getFrequency(Automaton<String> automaton, Edge<String> edge) {
        if(approach == MIN) {
            return automaton.getEdgeFrequency(edge) / (Math.min(automaton.getNodeFrequency(edge.getSource()), automaton.getNodeFrequency(edge.getTarget())));
        }else if(approach == MAX) {
            return automaton.getEdgeFrequency(edge) / (Math.max(automaton.getNodeFrequency(edge.getSource()), automaton.getNodeFrequency(edge.getTarget())));
        }else if(approach == AVE) {
            return automaton.getEdgeFrequency(edge) / ((automaton.getNodeFrequency(edge.getSource()) + automaton.getNodeFrequency(edge.getTarget()))/2);
        }
        return 0.0;
    }

    private static Automaton<String> remove(Automaton<String> automaton, Edge<String> edge) {
        automaton.removeEdgeTotal(edge.getSource(), edge.getTarget());
        return automaton;
    }


    public Set<Edge<String>> discoverRemovable(Automaton<String> automaton, double threshold) {
        Set<Edge<String>> removable = new UnifiedSet<Edge<String>>();

        AtomicInteger done = new AtomicInteger();
        int started = 0;
        for (Edge<String> edge : automaton.getEdges()) {
            if (isInfrequent(automaton, edge, threshold)) {
                started++;

                ThreadExecutor threadExecutor = new ThreadExecutor((Automaton<String>) automaton.clone(), edge, removable, done, lock, condition);
                executor.execute(threadExecutor);
            }
        }

        lock.lock();
        while(done.get() < started) {
            try {
                condition.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        lock.unlock();

        return removable;
    }

    public Set<Edge<String>> discoverRemovable(Automaton<String> automaton, Set<Edge<String>> edges) {
        Set<Edge<String>> removable = new UnifiedSet<Edge<String>>();
        AtomicInteger done = new AtomicInteger();
        for (Edge<String> edge : edges) {
            ThreadExecutor threadExecutor = new ThreadExecutor((Automaton<String>) automaton.clone(), edge, removable, done, lock, condition);
            executor.execute(threadExecutor);
        }

        lock.lock();
        while(done.get() < edges.size()) {
            try {
                condition.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        lock.unlock();

        return removable;
    }

    class ThreadExecutor implements Runnable {

        final Automaton<String> automaton;
        final Edge<String> edge;
        final Set<Edge<String>> removable;
        final AtomicInteger done;
        final Lock lock;
        final Condition condition;

        public ThreadExecutor(Automaton<String> automaton, Edge<String> edge, Set<Edge<String>> removable, AtomicInteger done, Lock lock, Condition condition) {
            this.automaton = automaton;
            this.edge = edge;
            this.removable = removable;
            this.done = done;
            this.lock = lock;
            this.condition = condition;
        }

        @Override
        public void run() {
            automaton.removeEdgeTotal(edge);
            if (
                    automaton.reachable(automaton.getAutomatonStart(), edge.getSource()) &&
                    automaton.reachable(automaton.getAutomatonStart(), edge.getTarget()) &&
                    automaton.reachable(edge.getSource(), automaton.getAutomatonEnd()) &&
                    automaton.reachable(edge.getTarget(), automaton.getAutomatonEnd())
            ) {

                lock.lock();
                removable.add(edge);
                lock.unlock();
            }
            done.incrementAndGet();
            lock.lock();
            condition.signalAll();
            lock.unlock();
        }
    }
}
