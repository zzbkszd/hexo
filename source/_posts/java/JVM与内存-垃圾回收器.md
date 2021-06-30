---
title: (JVM)JVM与内存（一）：垃圾回收器与堆内存
date: 2021-06-29 11:53:09
categories: Java
tags: 
  - JVM
  - 内存
---

## 概述

Jvm的垃圾回收器经历了从串行，到并行，到并发的演进流程。在1.8之前，最主流的垃圾回收器是CMS，在1.8正式开始推荐使用G1。在最新版本的Jvm中，还提供了最新的ZGC回收器。

本文就从CMS，G1和ZGC三个垃圾回收器来分析不同垃圾回收器的差别和特点，其中CMS仅做概述以供对比。内容重点在G1回收器。

<!-- more -->

## 大纲

- G1简介
-- g1的内存结构
-- 回收流程
-- 特点
-- 常用优化参数
- ZGC部分
-- 特点
-- 内存结构
-- 回收流程
-- 适用场景

- 第二部分 堆外内存

- 第三部分 内存模型

- Java的引用：强软弱虚

## GC Root

Java的垃圾回收器判断需要回收的垃圾，采用可达性分析方法来进行对象扫描。可达性分析的起点，*一组必须活跃的引用*， 被称为GC Root。

GC Root 通常包含以下对象：
- 虚拟机栈帧中指向GC堆里的对象的引用，比如当前执行线程的参数、局部变量、临时变量等。
- 方法区中类静态属性引用的对象。
- 方法区中常量引用的对象。比如字符串常量池。
- 本地方法栈中JNI本地方法的引用对象。
- jvm内部引用，比如基本数据类型对应的class对象，常驻的异常对象，类加载器等。
- 被同步锁持有的对象
- 本地代码缓存等

提供一个英文文档以供参考：

```
1.System Class
----------Class loaded by bootstrap/system class loader. For example, everything from the rt.jar like java.util.* .
2.JNI Local
----------Local variable in native code, such as user defined JNI code or JVM internal code.
3.JNI Global
----------Global variable in native code, such as user defined JNI code or JVM internal code.
4.Thread Block
----------Object referred to from a currently active thread block.
Thread
----------A started, but not stopped, thread.
5.Busy Monitor
----------Everything that has called wait() or notify() or that is synchronized. For example, by calling synchronized(Object) or by entering a synchronized method. Static method means class, non-static method means object.
6.Java Local
----------Local variable. For example, input parameters or locally created objects of methods that are still in the stack of a thread.
7.Native Stack
----------In or out parameters in native code, such as user defined JNI code or JVM internal code. This is often the case as many methods have native parts and the objects handled as method parameters become GC roots. For example, parameters used for file/network I/O methods or reflection.
7.Finalizable
----------An object which is in a queue awaiting its finalizer to be run.
8.Unfinalized
----------An object which has a finalize method, but has not been finalized and is not yet on the finalizer queue.
9.Unreachable
----------An object which is unreachable from any other root, but has been marked as a root by MAT to retain objects which otherwise would not be included in the analysis.
10.Java Stack Frame
----------A Java stack frame, holding local variables. Only generated when the dump is parsed with the preference set to treat Java stack frames as objects.
11.Unknown
----------An object of unknown root type. Some dumps, such as IBM Portable Heap Dump files, do not have root information. For these dumps the MAT parser marks objects which are have no inbound references or are unreachable from any other root as roots of this type. This ensures that MAT retains all the objects in the dump.
```

## CMS

CMS是在Java 1.8 之前的主流垃圾回收器，也是第一代真正实现并发的垃圾回收器。全程为`Concurrent Mark Sweep`，从名字就能看出来，这是一款并发标记垃圾回收器。需要注意，CMS是一款`老年代`垃圾回收器。

### 特性概述

设计思想：以获取最短回收停顿时间为目标。

- 仅回收老年代的数据，新生代可以使用Parallel New 或者 Serial回收器进行回收。
- 不能等到内存用尽再回收，默认在老年代使用达到92%时触发回收，否则会导致并发回收失败。
- 无法处理浮动垃圾问题
- 会有空间碎片产生，当没有足够连续空间分配对象，会触发Full GC。
- 对CPU资源敏感。

### CMS的垃圾回收流程

![](cms-flow.webp)

如图所示，主要包含以下几个阶段：

（三色标记法：白-灰-黑。白色被清除）

- 触发gc：

当对象从新生代晋升到老年代时，如果出现晋升失败，则对老年代进行gc。

若晋升失败时，上一次full gc还没有执行完成，则会触发concurrent mode fail，会退化为Serial回收器进行Full GC，性能会出现下降。

- 初始标记（STW）：

标记，是指的标记`存活对象`。
分为两部分：1、从GC Roots开始遍历所有可达老年代对象，2、从新生代对象遍历可达的老年代对象。

然后将对象标记为灰色（即该对象已被标记，但是内部引用没有被处理）

- 并发标记：

以第一步的结果的基础上，继续遍历已标记对象的所有引用。
遍历完对象的所有引用后，将对象标记为黑色（即该对象内部所有引用都已被处理）

如果这期间有某对象的引用发声变动，会将对应的Card标记为DirtCard，以提升重新标记的性能。

- 预处理：

此阶段标记三种对象：1、从新生代晋升的对象，2、新分配到老年代的对象，3、并发阶段被修改了的对象。

- 重新标记（STW）：

为了处理在并发标记阶段发声变化的引用。需要STW并重新进行标记。

和初始标记的工作类似，在本阶段需要做三件事：1、遍历GC Root对象， 2、遍历新生代对象。3、额外的，还需要遍历老年代中的DirtCard。

- 并发清理：

使用标记-删除算法来对内存进行回收。

若是在该阶段产生新的垃圾则无法进行回收，这部分垃圾即被称为浮动垃圾。

因为使用标记-删除算法，会导致内存碎片问题。

## G1

### 特性概述

设计思想：提供可预期的回收停顿时间。

- G1采用标记-整理算法，不会产生内存碎片
- G1可以同时回收年轻代和老年代。

### G1的几个重要概念

参考文献[可达性和标记](https://segmentfault.com/a/1190000021820577)

参考文献[G1GC](https://segmentfault.com/a/1190000021878102)

#### Region

相对于cms及之前的垃圾回收器，都是将连续的内存划分为年轻代和老年代。G1采用了Region的方式进行内存的管理。年轻代和老年代使用n个不连续的相同大小的Region。每个Region占用一块连续的内存。

默认的Region数量是2048个。

Region有四种类型：E （Eden，新生代），S（Survivor 幸存区），O (Old 老年代), H （Humongous 巨大对象，大于Region大小一半）

#### SATB

全称 Snapshot-At-The-Begining， 即在GC开始时对存活的对象进行一个快照。通过可达性分析获得，作用是维持并发GC的正确性。

### G1垃圾回收流程

初始标记（STW）

并发标记

最终标记（STW）

筛选回收




