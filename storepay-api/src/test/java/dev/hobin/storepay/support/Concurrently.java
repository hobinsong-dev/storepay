package dev.hobin.storepay.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 같은 작업을 여러 스레드에서 <b>동시에</b> 시작시킨다.
 *
 * <p>스레드를 만들자마자 실행하면 먼저 만들어진 쪽이 먼저 끝나버려 경합이 거의 일어나지 않는다.
 * 전원이 출발 래치 앞에 모인 뒤 한 번에 풀어야 "조회와 삽입 사이에 끼어드는" 창이 실제로 열린다.
 */
public final class Concurrently {

    private Concurrently() {
    }

    /** 각 스레드의 결과를 성공/실패 그대로 모아 돌려준다. 예외도 결과의 일부다. */
    public static <T> List<Outcome<T>> run(int threads, Callable<T> task) {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Outcome<T>>> futures = new ArrayList<>(threads);

        try {
            for (int i = 0; i < threads; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return Outcome.success(task.call());
                    } catch (Exception e) {
                        return Outcome.<T>failure(e);
                    }
                }));
            }
            start.countDown();

            List<Outcome<T>> outcomes = new ArrayList<>(threads);
            for (Future<Outcome<T>> future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } catch (Exception e) {
            throw new IllegalStateException("동시 실행에 실패했습니다.", e);
        } finally {
            pool.shutdownNow();
        }
    }

    public record Outcome<T>(T value, Exception error) {

        static <T> Outcome<T> success(T value) {
            return new Outcome<>(value, null);
        }

        static <T> Outcome<T> failure(Exception error) {
            return new Outcome<>(null, error);
        }

        public boolean succeeded() {
            return error == null;
        }
    }
}
