/*
 * Copyright 2012 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel;

import com.sun.org.apache.bcel.internal.generic.RET;
import io.netty.util.concurrent.CompleteFuture;
import io.netty.util.concurrent.EventExecutor;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.Assert.*;

public class CompleteChannelFutureTest {

    @Test(expected = NullPointerException.class)
    public void shouldDisallowNullChannel() {
        new CompleteChannelFutureImpl(null);
    }

    @Test
    public void shouldNotDoAnythingOnRemove() throws Exception {
        Channel channel = Mockito.mock(Channel.class);
        CompleteChannelFuture future = new CompleteChannelFutureImpl(channel);
        ChannelFutureListener l = Mockito.mock(ChannelFutureListener.class);
        future.removeListener(l);
        Mockito.verifyNoMoreInteractions(l);
        Mockito.verifyZeroInteractions(channel);
    }

    @Test
    public void testConstantProperties() throws InterruptedException {
        Channel channel = Mockito.mock(Channel.class);
        CompleteChannelFuture future = new CompleteChannelFutureImpl(channel);

        assertSame(channel, future.channel());
        assertTrue(future.isDone());
        assertSame(future, future.await());
        assertTrue(future.await(1));
        assertTrue(future.await(1, TimeUnit.NANOSECONDS));
        assertSame(future, future.awaitUninterruptibly());
        assertTrue(future.awaitUninterruptibly(1));
        assertTrue(future.awaitUninterruptibly(1, TimeUnit.NANOSECONDS));
        Mockito.verifyZeroInteractions(channel);
    }

    private static class CompleteChannelFutureImpl extends CompleteChannelFuture {
        /**
         * Creates a new instance.
         *
         * @param channel  the {@link Channel} associated with this future
         */
        protected CompleteChannelFutureImpl(Channel channel) {
            super(channel,null);
        }

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public Throwable cause() {
            return null;
        }

//        CompleteChannelFutureImpl(Channel channel) {
//            super(channel, null);
//        }
//
//        @Override
//        public Throwable cause() {
//            throw new Error();
//        }
//
//        @Override
//        public boolean isSuccess() {
//            throw new Error();
//        }
//
//        @Override
//        public ChannelFuture sync() throws InterruptedException {
//            throw new Error();
//        }
//
//        @Override
//        public ChannelFuture syncUninterruptibly() {
//            throw new Error();
//        }
    }





    @Test
    public void testExecutorService() throws IOException, ExecutionException, InterruptedException {
        ExecutorService executor = Executors.newScheduledThreadPool(1, Executors.defaultThreadFactory());
        Future<String> runFuture = executor.submit(new Callable<String>() {
            @Override
            public String call() throws Exception {
                System.out.println("running");
                Thread.sleep(5000);
                return "done";
            }
        });
        String s = runFuture.get();
        System.out.println(s);
    }

    public static String task1(){
        System.out.println("task running");
        return "done";
    }
    private static void accept(String result) {
        System.out.println("execute result " + result);
        try {
            Thread.sleep(2000);

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testCompletableFuture() throws InterruptedException {
        CompletableFuture<String> stringCompletableFuture = CompletableFuture.supplyAsync(CompleteChannelFutureTest::task1);
        System.out.println("main thread execute still running");
        CompletableFuture<Void> voidCompletableFuture = stringCompletableFuture.thenAccept(CompleteChannelFutureTest::accept);
        CompletableFuture<String> exceptionally = stringCompletableFuture.exceptionally((e) -> {
            e.printStackTrace();
            System.out.println("error occur");
            return null;
        });

        // 两个CompletableFuture 可以实现串行操作
        CompletableFuture<Double> thenApplyAsyncExec = stringCompletableFuture.thenApplyAsync((code) -> fetchPrice(code));
        thenApplyAsyncExec.whenComplete((i, o)->{
            System.out.println("price is " + String.valueOf(i));
        });

        Thread.sleep(5000);
    }
    static Double fetchPrice(String code) {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
        }
        return 5 + Math.random() * 20;
    }

    //除了串行执行外，多个 CompletableFuture 还可以并行执行。
    // 例如这个场景：同时从新浪和网易查询证券代码，只要任意一个返回结果，就进行下一步查询价格，查询价格也同时从新浪和网易查询，
    // 只要任意一个返回结果，就完成操作：
    @Test
    public void testParallelExecute() throws InterruptedException {
        CompletableFuture<String> cfQueryFromSina = CompletableFuture.supplyAsync(() -> {
            return queryCode("中国石油", "https://finance.sina.com.cn/code/");
        });
        CompletableFuture<String> cfQueryFrom163 = CompletableFuture.supplyAsync(() -> {
            return queryCode("中国石油", "https://money.163.com/code/");
        });
        // 用 anyOf 合并为一个新的 CompletableFuture
        CompletableFuture<Object> cfQuery = CompletableFuture.anyOf(cfQueryFromSina, cfQueryFrom163);

        // 用两个 CompletableFuture 执行一步查询：
        CompletableFuture<Double> cfFetchFromSina = cfQuery.thenApplyAsync((code) -> {
            return fetchPrice((String) code, "https://finance.sina.com.cn/price/");
        });
        CompletableFuture<Double> cfFetchFrom163 = cfQuery.thenApplyAsync((code) -> {
            return fetchPrice((String) code, "https://money.163.com/price/");
        });
        // 用anyOf合并为一个新的CompletableFuture:
        CompletableFuture<Object> cfFetch = CompletableFuture.anyOf(cfFetchFromSina, cfFetchFrom163);

        // 最终结果:
        cfFetch.thenAccept((result) -> {
            System.out.println("price: " + result);
        });

        // 主线程不要立刻结束，否则CompletableFuture默认使用的线程池会立刻关闭:
        Thread.sleep(200);

    }

    static String queryCode(String name, String url) {
        System.out.println("query code from " + url + "...");
        try {
            Thread.sleep((long) (Math.random() * 100));
        } catch (InterruptedException e) {
        }
        return "601857";
    }

    static Double fetchPrice(String code, String url) {
        System.out.println("query price from " + url + "...");
        try {
            Thread.sleep((long) (Math.random() * 100));
        } catch (InterruptedException e) {
        }
        return 5 + Math.random() * 20;
    }
    //    除了anyOf()可以实现“任意个CompletableFuture只要一个成功”，
    //    allOf()可以实现“所有CompletableFuture都必须成功”，这些组合操作可以实现非常复杂的异步流程控制。
    //
    //    最后我们注意CompletableFuture的命名规则：
    //
    //    xxx()：表示该方法将继续在已有的线程中执行；
    //    xxxAsync()：表示将异步在线程池中执行。


    public static void main(String[] args) {

        //获取所有完成结果——allOf
        //public static CompletableFuture<Void> allOf(CompletableFuture<?>... cfs)
        //allOf方法，当所有给定的任务完成后，返回一个全新的已完成CompletableFuture

        CompletableFuture<Integer> future1 = CompletableFuture.supplyAsync(() -> {
            try {
                //使用sleep()模拟耗时操作
                TimeUnit.SECONDS.sleep(2);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return 1;
        });

        CompletableFuture<Integer> future2 = CompletableFuture.supplyAsync(() -> {
            return 2;
        });
        CompletableFuture.allOf(future1, future1);
        // 输出3
        System.out.println(future1.join()+future2.join());


        //anyof
        //获取率先完成的任务结果——anyOf
        //仅等待Future集合种最快结束的任务完成（有可能因为他们试图通过不同的方式计算同一个值），并返回它的结果。
        // 小贴士 ：如果最快完成的任务出现了异常，也会先返回异常，如果害怕出错可以加个exceptionally() 去处理一下可能发生的异常并设定默认返回值
        //public static CompletableFuture<Object> anyOf(CompletableFuture<?>... cfs)
        CompletableFuture<Integer> future0 = CompletableFuture.supplyAsync(() -> {
            throw new NullPointerException();
        });

        CompletableFuture<Integer> future3 = CompletableFuture.supplyAsync(() -> {
            try {
                // 睡眠3s模拟延时
                TimeUnit.SECONDS.sleep(3);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return 1;
        });
        CompletableFuture<Object> anyOf = CompletableFuture
                .anyOf(future0, future2)
                .exceptionally(error -> {
                    error.printStackTrace();
                    return 2;
                });
        System.out.println(anyOf.join());

        CompletableFuture.supplyAsync(() -> 10)
                .whenComplete((result, error) -> {
                    System.out.println(result);
                    error.printStackTrace();
                })
                .handle((result, error) -> {
                    error.printStackTrace();
                    return error;
                })
                .thenApply(Object::toString)
                .thenApply(Integer::valueOf)
                .thenAccept((param) -> System.out.println("done"));



        long begin = System.currentTimeMillis();
        // 自定义一个线程池
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        // 循环创建10个CompletableFuture
        List<CompletableFuture<Integer>> collect = IntStream.range(1, 10).mapToObj(i -> {
            CompletableFuture<Integer> future = CompletableFuture.supplyAsync(() -> {
                        // 在i=5的时候抛出一个NPE
                        if (i == 5) {
                            throw new NullPointerException();
                        }
                        try {
                            // 每个依次睡眠1-9s，模拟线程耗时
                            TimeUnit.SECONDS.sleep(i);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        System.out.println(i);
                        return i;
                    }, executorService)
                    // 这里处理一下i=5时出现的NPE
                    // 如果这里不处理异常，那么异常会在所有任务完成后抛出,小伙伴可自行测试
                    .exceptionally(Error -> {
                        try {
                            TimeUnit.SECONDS.sleep(5);
                            System.out.println(100);
                            Error.printStackTrace();
                        } catch (InterruptedException e) {
                            e.printStackTrace();

                        }
                        return 100;
                    });
            return future;
        }).collect(Collectors.toList());
        // List列表转成CompletableFuture的Array数组,使其可以作为allOf()的参数
        // 使用join()方法使得主线程阻塞，并等待所有并行线程完成
        CompletableFuture.allOf(collect.toArray(new CompletableFuture[]{})).join();
        System.out.println("最终耗时" + (System.currentTimeMillis() - begin) + "毫秒");
        executorService.shutdown();
    }
}
