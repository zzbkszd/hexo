---
title: (异步)CompletableFuture使用概述
date: 2020-12-27 11:53:09
categories: Java
tags: 
  - Java
  - 异步
---

# 概述

CompletableFuture是从Java 1.8 开始引入的异步任务处理类。它实现了Future接口和CompletionStage接口。前者在Java1.5中引入，在线程池的应用中已经简单介绍过。后者才是核心的特性。

CompletionStage接口从Java1.8开始引入，提供了多个Future之间的多种组合，提供了异步任务的“编排”功能。使得我们可以非常灵活的进行异步编程。

所以使用CompletableFuture，更准确的说，就是熟悉CompletionStage这个接口的各种用法。

# 1、同步、异步与执行顺序

要熟悉异步编程，首先需要分清的就是我们的程序究竟以什么样的顺序逻辑在执行。

CompletableFuture提供了很多种任务编排的方式，大部分接口提供都形如：`thenApply`和`thenApplyAsync`两个形式，即同步方法和异步方法。顾名思义，同步方法就是指两个任务顺序执行，而异步方法则是指两个任务异步执行。

根据惯例，空口无凭，我们来举个栗子：

```Java
public static void acceptAndAsync() {
    long base = System.currentTimeMillis();
    CompletableFuture<Void> start = CompletableFuture.runAsync(()->{
        System.out.println("task 1 at " + (System.currentTimeMillis()-base));
    });
    start.thenAccept((v)->{
        try {
            Thread.sleep(1000L);
            System.out.println("task 2 at " + (System.currentTimeMillis()-base));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    });
    start.thenAccept(v-> {
        try {
            Thread.sleep(1000L);
            System.out.println("task 4 at " + (System.currentTimeMillis()-base));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    });
    start.thenAcceptAsync(v-> {
        try {
            Thread.sleep(1000L);
            System.out.println("task 3 at " + (System.currentTimeMillis()-base));
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    });
    try {
        start.get();
        System.out.println("finish at " + (System.currentTimeMillis()-base));
    } catch (InterruptedException | ExecutionException e) {
        e.printStackTrace();
    }
}
```

上面这段程序的输出如下：
```
task 1 at 73
task 2 at 1073
task 4 at 2074
task 3 at 2074
finish at 2074
```

在这个程序中，我们包含了四个异步任务，每个任务都打印了自己的完成时间。

- 第一个任务，打印了起始时间。
- 第二个任务，和第四个任务是两个同步任务，可以看到他们的时间是连续的，说明这两个任务是顺序执行的。第二个任务阻塞了后续任务的执行。
- 第三个任务是插在其中的一个异步任务，可以看到它的执行时间和第四个任务是相同的，说明这两个任务是异步执行的，执行第三个任务的时候并没有阻塞第四个任务的执行。

*可是！事情并没有这么简单！*

这里有一个需要注意的点：所有的任务都是以`start`这个CompletableFuture作为基准。最终使用`start.get()`方法来阻塞等待结果，只能等待到start中所有同步任务的结果。

如果我们交换了任务三和任务四的顺序，最终的输出结果会是如下：

```
task 1 at 66
task 2 at 1067
task 4 at 2068
finish at 2068
```

会发现任务三的结果消失了。这是因为在任务三是异步执行的，不会阻塞start的完成。

为了获得任务三的结果，我们需要使用一个新的CompletableFuture来接收任务3的结果：

```Java
CompletableFuture end = start.thenAcceptAsync(v-> {
    try {
        Thread.sleep(1000L);
        System.out.println("task 3 at " + (System.currentTimeMillis()-base));
    } catch (InterruptedException e) {
        e.printStackTrace();
    }
});
```

这样我们最后执行`end.get()`，就可以输出所有任务的结果，即阻塞到了全部任务完成。

而神奇的是，即使我们以`1,3,2,4`的顺序编写任务，最终以`end.get()`来阻塞等待，也可以等待到所有任务都执行完毕，不过执行结果有所变化：

```
task 1 at 90
task 2 at 1091
task 3 at 1091
task 4 at 2092
finish at 2092
```

也就是说，虽然任务三的业务逻辑与任务二一起执行完毕，但是直到任务四也完成之后，任务三才会释放阻塞，继续执行。

如果我们在任务三的返回结果`end`之后再增加一个任务五，就可以发现，任务五的执行时间是在任务四完成之后才继续执行。无论同步异步的方法皆是如此。

可能读者看到这里已经懵了，这个玩意到底是怎么个顺序去执行的？

不要急，我们来做一个新的案例，这个案例中包含了六个任务，为了节约空间，我们省去异常处理的部分：

```Java
public static void acceptAndAsync() {
    long base = System.currentTimeMillis();
    CompletableFuture<Void> start = CompletableFuture.runAsync(()->{
        System.out.println("task 1 at " + (System.currentTimeMillis()-base));
    });
    CompletableFuture three = start.thenAcceptAsync(v-> {
        Thread.sleep(1000L);
        System.out.println("task 3 at " + (System.currentTimeMillis()-base));
    });
    CompletableFuture two = start.thenAcceptAsync((v)->{
        Thread.sleep(1000L);
        System.out.println("task 2 at " + (System.currentTimeMillis()-base));
    });
    two.thenAccept(v-> {
        Thread.sleep(1000L);
        System.out.println("task 4 at " + (System.currentTimeMillis()-base));
    });
    CompletableFuture five = three.thenAccept(v-> {
        Thread.sleep(1000L);
        System.out.println("task 5 at " + (System.currentTimeMillis()-base));
    });
    start.thenAccept(v-> {
        Thread.sleep(3000L);
        System.out.println("task 6 at " + (System.currentTimeMillis()-base));
    });
    five.get(); // 你可以换成程序中任意一个CompletableFuture来get，结果并不会发生变化。
    System.out.println("finish at " + (System.currentTimeMillis()-base));
}
```

首先请读者仔细思考一下，这个程序会输出怎样的结果？

然后我们来用一张图描述各个任务的执行情况：

![](completablefuture1.png)

可以看到通过两次创建异步任务，其实六个任务分为了三个并行的序列。但是只有在最开始的start序列执行完成之后，才算完成了整个的阻塞序列，最后的`get`方法才会返回结果。

而如果start序列已经完成，而某个分支序列并没有完成。这时候`get`方法是否阻塞，则取决于是在哪个Future上调用。如果是在start序列上调用，则会立刻返回，如果是在分支序列上调用，则会等待分支序列执行完毕后返回。

# 2、异步执行线程池

CompletableFuture执行异步任务，是通过线程池来执行的。实际上，CompletionStage所有的异步方法都有重载的两种形式：`xxxAsync(task)`和`xxxAsync(task, executor)`。后者即为指定执行异步任务的线程池。

如果没有指定一个线程池来执行异步任务，CompletableFuture中有两种默认的线程池来执行：`ForkJoinPool.commonPool()` 和 `new ThreadPerTaskExecutor()`。具体选择哪个，由ForkJoinPool的并发等级来确定，如果并发等级高于1，则使用`ForkJoinPool.commonPool()`，否则使用另一个。

