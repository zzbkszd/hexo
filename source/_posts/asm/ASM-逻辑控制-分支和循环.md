---
title: (ASM) 五、逻辑控制-栈映射帧、分支和循环
date: 2020-10-22 12:00:00
categories: ASM实战
tags: 
  - ASM
  - Java
---

## 栈映射帧

我们都知道JVM的程序运行是由一个个栈帧组成的。（不知道的去学习《深入理解JVM》，好书不容错过》）

为了提升字节码校验的速度，Java1.6之后新增了一个概念叫做“栈映射帧”(stack map frame)。它不同于一个完整的栈帧但是在逻辑上有一些类似。它保存了在跳转指令执行时的栈中本地变量表和操作数栈。所以在学习分支和循环的实现之前，必须要了解栈映射帧的逻辑。

在实际编写字节码程序之前，很可能对于栈映射帧并没有很深刻的体会。类加载器的校验过程中，当我们使用逻辑控制指令跳转到一个位置之后，会检验我们是否构建了一个新的栈映射帧。如果没有的话会报错。

之前的程序中，我们都是一个顺序执行的程序。但是显然实际的程序中包含了大量的分支和循环操作。下面用一个例子来说明栈映射帧的作用。

```
public void fp(int a) {
    if(a == 1) {
        String str = "append";
    }
    String str = "out";
    System.out.println(str);
}
```

在这个程序中，if中的str和外面的str虽然名字相同，却是两个不同的变量。此时我们分析程序的本地变量表，会发现本地变量表也出现了两种情况。当传入a!=1是，正常执行后面的指令，但是当传入a==1时，会出现同一个本地变量槽位有两个值的冲突。但是显然我们平时编码并没有遇到这个问题。这就是栈映射帧的功劳之一。

根据刚刚描述的逻辑，我们可能直觉的认为会将if中的语句作为一个栈映射帧，但是实际的实现其实和我们刚刚描述的直觉并不完全相同。

在字节码的实现中，在if结束后，`String str = "out";`语句之前，会创建一个新的栈映射帧。新栈映射帧继承了栈帧中的本地变量表，并删除了最新的一个本地变量（即`String str = "append";`)。同时构建了一个空的操作数栈。

这样，在if代码段中的`str`的作用域就被打断。这也是我们内外的两个`str`变量不会冲突的原因。

如果用我们更容易理解的Java来实现，其实它的代码更接近于下面的样子：

```
if (a != 1) {
    String str = "out";
    System.out.println(str);
} else {
    String str = "append";
}
```

这段程序的ASM实现代码如下：

```Java
mv = cw.visitMethod(ACC_PUBLIC, "fp", "()V", null, null);
mv.visitCode();
mv.visitInsn(ICONST_1);
mv.visitVarInsn(ISTORE, 1);
mv.visitVarInsn(ILOAD, 1);
mv.visitInsn(ICONST_1);
Label l2 = new Label();
// 此处通过IF_ICMPNE来执行跳转到visitLabel(l2)的位置。
// 实际上是判断 a != 1
mv.visitJumpInsn(IF_ICMPNE, l2);
mv.visitLdcInsn("append");
mv.visitVarInsn(ASTORE, 2);
mv.visitLabel(l2);
// visitFrame方法即为构造一个新的栈帧
mv.visitFrame(Opcodes.F_APPEND, 1, new Object[]{Opcodes.INTEGER}, 0, null);
mv.visitLdcInsn("out");
mv.visitVarInsn(ASTORE, 2);
mv.visitFieldInsn(GETSTATIC, "java/lang/System", "out", "Ljava/io/PrintStream;");
mv.visitVarInsn(ALOAD, 2);
mv.visitMethodInsn(INVOKEVIRTUAL, "java/io/PrintStream", "println", "(Ljava/lang/String;)V", false);
mv.visitInsn(RETURN);
mv.visitLocalVariable("this", "Lcom/nature/dql/SuperMaper;", null, l0, l6, 0);
mv.visitLocalVariable("a", "I", null, l1, l6, 1);
mv.visitLocalVariable("str", "Ljava/lang/String;", null, l4, l6, 2);
mv.visitMaxs(2, 3);
mv.visitEnd();
```

老规矩，我们来看`visitFrame`方法的声明：

```
public void visitFrame(
    final int type, // 类型
    final int numLocal, // 变更本地变量表长度
    final Object[] local, // 变更本地变量表对应类型
    final int numStack, // 变更操作数栈深度
    final Object[] stack) // 变更操作数栈数据类型
```

ASM为我们提供了五种创建栈帧的类型，分别如下：


1、 F_FULL:

创建包含有完整的本地变量表和操作数栈内容的栈映射帧。即需要输入完整的本地变量表和操作数栈。

2、 F_APPEND:

继承栈帧的本地变量表，并且可以附加1-3个本地变量。并且拥有一个空的操作数栈

3、 F_CHOP:

继承栈帧的本地变量表，并且可以移除最后的1-3个本地变量。并且拥有一个空的操作数栈，使用时只需要传入numLocal参数即可，其它均为空（0）即可。

4、 F_SAME:

与当前栈帧的本地变量表完全一样，并且拥有一个空的操作数栈，除type外的其它参数均为空（0）即可。

5、 F_SAME1:

与当前栈帧的本地变量表完全一样，并且操作数栈有一个值

各自的具体应用就看具体情况而定了。


## If分支逻辑

`if`判断是程序中最常见的逻辑之一了。其实在我们上一节的内容中已经举例了一个最简单的if判断。

在了解具体实现之前，我们需要在脑海中确立一个总的观念：字节码的逻辑控制其实只有一个语句：goto。

在我们初学编程的时候，大多听说过臭名昭著的`goto`语句。这个能够打破顺序、分支、循环三大逻辑的神奇咒语仿佛会带来整个程序的崩溃。在Java中，也可以通过break语句来实现goto的效果，还保留了标签的语法。可是大多数人并没有用过，也没有想过要用这种方式来编写代码。

但是在jvm的实现中，goto才是一切逻辑的基础。

一个If分支逻辑的基本形式如：`a>b?doA():doB()`。用字节码来实现的概要如下。

为了省略，在本文后续内容均用`label(tag)`来代替具体的行号。代表跳转到`label(tag):`标记的位置。且均使用字节码代替具体的ASM语句。

```
iload a
iload b
if_icmple label(else) // if(a <= b) goto label(else)
invokevirtual doA // 代表具体业务逻辑
label(else): // 在此处需要创建栈映射帧
invokevirtual doB
return
```

需要创建的栈映射帧因为结构和内部逻辑的不同会有不同的具体情况，需要根据场景和自己的封装形式进行分析。所以在后续中只会注明需要创建栈映射帧的位置，而不会具体说明如何创建。创建栈映射帧的操作位于label标记之后。

很多时候，我们的if并不是简单地一个条件，当遇到形如`if(a && b)`的情形，我们需要将它拆分成简单逻辑的组合形式。

例如：`if(a && b)` 可以等价于：

```
if(a) {
    if(b) {
        doSomething();
    }
}
```

用字节码来实现也比较简单，就是连续进行多个判断，如果判断为false则跳转到if代码块的结尾。

```
iload a
ifne label(end)
iload b
ifne label(end)
invokevirtial doSomething
label(end): // 此处需要创建栈映射帧
invokevirtual doElse
return
```

对于形如`if(a || b)`的代码则比较绕弯弯，简单的实现如下：

```
iload a
ifne label(in)
iload b
ifeq label(out)
label(in): // 此处需要创建栈映射帧
invokevirtial doSomething
label(out): // 此处需要创建栈映射帧
invokevirtual doElse
return
```

这其中涉及到关于短路逻辑的实现，即当`a`为真的时候，会直接跳转到`label(in)`的位置执行逻辑。`b`的部分则与普通的if判断相同。

因为短路逻辑跳转的存在，`a||b`的判断需要比`a&&b`的判断多创建一个栈映射帧

## 循环逻辑

循环逻辑是在实际代码中另一种常见的逻辑。在字节码的实现中，可以将一个循环分为三个部分：

- 循环体，包含循环内需要的业务逻辑
- 循环判断
- 循环点

在代码中实现循环有for/while/do-while三种形式。其实就是这三个部分的排列不同形成的不同效果。

一个经典的for循环代码如下：

```Java
int cnt = 0;
for(int i=0; i<10; i++) {
    cnt ++;
}
```

我们可以用goto的伪代码来拆解一下这个循环，对应上面说的几个要素：

```Java
int cnt = 0;
int i = 0;
label(start):// 循环点
if(i > 10) goto label(end) // 循环判断
cnt++; // 循环体
i++;
goto label(start)
label(end):// 循环点
```

用字节码来实现上面的这一段逻辑就非常清晰了：

```
bipush 0
istore 1
bipush 0
istore 2
label(start): // 此处需要创建栈映射帧，循环点
iload 2
bipush 10
if_icmpge label(end)  // 循环判断
iinc 1 1   // 循环体
iinc 2 1
goto label(start)
label(end): // 此处需要创建栈映射帧，循环点
return
```

如果我们将循环体和循环判断的位置进行一下调换，在判断后跳转到label(start)的位置，就实现了do-while的循环。这种循环在字节码的实现中反倒显得更为顺畅一些，因为只需要一个循环点，创建一个栈映射帧

```
bipush 0
istore 1
bipush 0
isotre 2
label(start): // 需要创建栈映射帧，循环点
iinc 1 1  // 循环体
iinc 2 1
iload 2
bipush 10
if_icmplt label(start) // 循环判断
return
```

## 小结

总而言之，相比于在代码中实现逻辑控制的多种方式，在字节码中只有跳转指令这一种方式。如果不怕逻辑混乱，开动脑筋，我们甚至可以做出很多超出代码限制的跳转方式，实现更为灵活的逻辑控制。

本教程至今，已经完整描述类类、方法、变量、逻辑控制的相关技能。这些东西已经足够组成一个完整的程序，去实现我们所期待的任何功能。






