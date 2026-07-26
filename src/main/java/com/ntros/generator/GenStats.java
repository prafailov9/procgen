package com.ntros.generator;

public class GenStats {

    private int iterations;
    private int conflicts;

    public GenStats(int iterations, int conflicts) {
        this.iterations = iterations;
        this.conflicts = conflicts;
    }

    public int getIterations() {
        return iterations;
    }

    public void incrementIterations() {
        iterations++;
    }

    public void setIterations(int iterations) {
        this.iterations = iterations;
    }

    public int getConflicts() {
        return conflicts;
    }

    public void setConflicts(int conflicts) {
        this.conflicts = conflicts;
    }
}
