package com.epam.deltix.rapidhash4j;

import org.openjdk.jmh.Main;

import java.io.IOException;
import java.util.Arrays;

public final class BenchmarkRunner {
    private BenchmarkRunner() {}

    public static void main(String[] args) throws IOException {
        if (Arrays.stream(args).noneMatch(arg -> arg.matches("--?foe(=.*)?"))) {
            args = Arrays.copyOf(args, args.length + 2);
            args[args.length - 2] = "-foe";
            args[args.length - 1] = "true";
        }
        Main.main(args);
    }
}
