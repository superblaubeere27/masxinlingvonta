package net.superblaubeere27.masxinlingvaj.utils;

import java.util.concurrent.Callable;

public class Result<V, E extends Throwable> {
    private final V value;
    private final E error;

    private Result(V value, E error) {
        this.value = value;
        this.error = error;
    }

    public static <V, E extends Throwable> Result<V, E> error(E error) {
        return new Result<>(null, error);
    }

    public static <V, E extends Throwable> Result<V, E> ok(V value) {
        return new Result<>(value, null);
    }

    /**
     * Executes a task, converts it into a result
     *
     * @param callable task to execute
     * @return An ok result, error if an exception occurs
     */
    public static <V> Result<V, Exception> executeCatching(Callable<V> callable) {
        try {
            return ok(callable.call());
        } catch (Exception e) {
            return error(e);
        }
    }

    public V unwrap() {
        if (this.error != null) {
            throw new IllegalStateException("Tried to unwrap error", this.error);
        }

        return this.value;
    }
}
