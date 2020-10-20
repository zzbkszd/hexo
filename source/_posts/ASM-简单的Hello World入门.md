---
title: (ASM) 简单的Hello World实现
date: 2020-10-19 12:00:00
tags: ASM,Java
---

## ASM 简介

[ASM](https://asm.ow2.io/)，是纯Java实现的Java字节码框架，其特点是尽可能的小而快。根据官网给出的[Benchmark](https://asm.ow2.io/performance.html)，其生成类的性能是其它框架的数倍。

轻量级和高性能是有代价的，相比于[Javassist](https://github.com/jboss-javassist/javassist)之类的字节码操作库，ASM的visitor模式抽象层级更低，需要用户对底层字节码指令的认识更深入。

也恰恰是这种特点，使得ASM成为了学习字节码增强技术的最佳选择。

ASM框架的引入非常简单，只需要引入一个Maven依赖即可。

```xml
<dependency>
    <groupId>org.ow2.asm</groupId>
    <artifactId>asm</artifactId>
    <version>9.0</version>
</dependency>
```

也可以可以到 mvnrepository.com 搜索ASM以查看更多的版本。

## 流程概述

要实现一个HelloWorld程序，我们首先要在心里对需要做的工作有一个计划。首先用Java写一个：

```java
public class Hello {
    public void show() {
        System.out.println("Hello World")
    }
}
```

回想一下我们刚刚做了什么操作：
- 新建一个Hello 类
- 新建一个show 方法
- 调用System.out.println方法
- 输入“Hello World”


接下来寻找用ASM框架实现这些操作的方法。当我们自己编写字节码时，有两点需要注意的。

1. 需要自己显式的实现构造方法。
2. 需要先将参数入栈，然后调用方法。

所以用ASM实现Hello World程序的的操作如下：

- 新建一个ClassWriter，实现Hello类
- 新建一个MethodVisitor，实现无参构造方法
- 新建一个MethodVisitor，实现show方法
- 加载System.out静态变量（还记得方法的第一个参数是"this"么）
- 加载Hello World常量
- 调用println方法

现在设计清楚了，下一步就是用代码来完成这项工作。

## 代码实现：

```Java
public ClassWriter hello() {
    // 1、新建一个ClassWriter
    ClassWriter classWriter = new ClassWriter(0);
    // 声明类 public class Hello
    classWriter.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, "study/asm/Hello", null, "java/lang/Object", null);

    // 2、新建一个MethodVisitor，实现无参构造函数
    MethodVisitor constructor = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
    // 无参构造方法会调用java.lang.Object(父类)的构造方法
    constructor.visitVarInsn(Opcodes.ALOAD, 0);
    constructor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
    constructor.visitInsn(Opcodes.RETURN);
    constructor.visitMaxs(1,1);
    constructor.visitEnd();

    // 3、构造show方法
    MethodVisitor showMethod = classWriter.visitMethod(Opcodes.ACC_PUBLIC, "show", "()V", null, null);

    // 4、加载System.out静态变量
    showMethod.visitFieldInsn(Opcodes.GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
    // 5、加载Hello World常量
    showMethod.visitLdcInsn("Hello world");
    // 6、调用System.out.println方法
    showMethod.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
    showMethod.visitInsn(Opcodes.RETURN);
    showMethod.visitMaxs(2, 1);
    showMethod.visitEnd();

    classWriter.visitEnd();
    return classWriter;
}
```

## 调用

现在生成好了一个Hello类，下一步问题就是如何创建一个实例并调用show方法。

这里需要用到类加载器相关的知识。Java通过类加载器加载一段byte[]数据来加载一个类。然后就可以通过该类加载器获得该类的实例。

在目前阶段，可以通过反射的方法最终调用show方法。当然以后也可以通过实现接口的方式来实现直接调用。

简要代码如下： 

```java
public class SimpleAsm extends ClassLoader {
    public ClassWriter hello() {...}
    public static void main(String[] args) {
        SimpleAsm asm = new SimpleAsm();
        ClassWriter classWriter = asm.hello()
        // 将类写入一段byte[]
        byte[] code = classWriter.toByteArray();
        // 通过类加载器加载
        Class clazz = asm.defineClass("study.asm.Hello", code, 0, code.length);

        try {
            // 通过反射创建实例并调用show方法
            Object ins = clazz.newInstance();
            Method show = clazz.getMethod("show");
            show.invoke(ins);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

## 小结

本节是通过将一个Hello World的Java程序转换为字节码实现，来直观的体验字节码生成程序的流程。并且初次尝试了ASM框架的应用。在本案例中应用的是ASM的核心接口，采用visitor模式。ASM还支持TreeApi的方式实现同样的功能。本教程主要在于Java字节码的知识，对ASM框架感兴趣的读者可以自行查看ASM官网的用户手册。