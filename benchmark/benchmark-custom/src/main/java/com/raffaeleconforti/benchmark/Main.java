package com.raffaeleconforti.benchmark;

import java.util.Arrays;

public class Main {

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("No args, stopping");
            System.exit(0);
        }

        // Backwards compatibility with previous versions
        if (args[0].startsWith("-")) {
            System.out.println("First arg is flag, using benchmark");
            BenchmarkCustom.run(args);
            System.exit(0);
        }

        String program = args[0];
        String[] newArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (program) {
            case "benchmark":
                // Run custom benchmark
                BenchmarkCustom.run(newArgs);
                System.exit(0);
            case "dfa":
                // Run evaluation for state machines
                new DFAEvaluator().run(newArgs);
                System.exit(0);
            case "hybrid":
                // Run evaluation on the hybrid state machine-process mining data.
                // (note: just runs the base benchmark but allows for custom cross-validation data splits)
                new BenchmarkCustomData().run(newArgs);
                System.exit(0);
            default:
                System.out.printf("Unknown program %s, stopping\n", program);
                System.exit(-1);
        }
    }
}
