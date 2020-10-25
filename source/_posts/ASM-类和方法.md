---
title: (ASM) 四、类和方法
date: 2020-10-21 11:53:09
tags: ASM,Java
---

## 类的构建

要构建一个类，我们首先回顾之前了解的字节码中类声明部分。为了完整展现构建类的全部参数，我们首先声明一个这样的类：

```
public class SuperMaper extends HashMap<String,Object> {}
```

编译后，查看class文件可以获得字节码：
```
public class com.nature.dql.SuperMaper extends java.util.HashMap<java.lang.String, java.lang.Object>
  minor version: 0
  major version: 52
  flags: ACC_PUBLIC, ACC_SUPER
```

可以看到其中包含：
- Java版本
- 访问控制
- 类名称
- 父类
- 泛型信息

使用ASM框架来创建这个类，代码如下：

```
ClassWriter cw = new ClassWriter(0);
cw.visit(Opcodes.V1_8, ACC_PUBLIC + ACC_SUPER, "com/nature/dql/SuperMaper", 
    "Ljava/util/HashMap<Ljava/lang/String;Ljava/lang/Object;>;",
     "java/util/HashMap", null);
```

为了了解每个参数的作用， 我们来看`visit`方法的声明，每个参数的作用见注释

```
public final void visit(
    final int version, // 适用的Java版本
    final int access,  // 访问控制
    final String name, // 类名称
    final String signature, // 泛型声明
    final String superName, // 父类名称
    final String[] interfaces) // 实现接口
{...}
```

注意在字节码中常见的两个名称问题：
- 类名称是使用斜杠分割，而不是如同代码中使用点分割。
- 类名称和类型声明的差异：作为类型声明，是：`Ljava/util/HashMap;`的形式，前面加`L`后面加`;`，所有的引用类型都需要如此修改。而基础类型则不需要，比如`int`类型的类型和名称都是`I`。

## 方法的构建

同样的，我们声明一个复杂的方法：

```
public <T> T anyGet(String key, Class<T> clazz) throws Exception
```

编译后，可以获得该方法的声明部分字节码：

```
 public <T extends java.lang.Object> T anyGet(java.lang.String, java.lang.Class<T>) throws java.lang.Exception;
    descriptor: (Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;
    flags: ACC_PUBLIC
```

使用ASM框架实现该方法的代码为：

```
MethodVisitor mv = cw.visitMethod(ACC_PUBLIC, "anyGet", 
"(Ljava/lang/String;Ljava/lang/Class;)Ljava/lang/Object;",
 "<T:Ljava/lang/Object;>(Ljava/lang/String;Ljava/lang/Class<TT;>;)TT;",
  new String[]{"java/lang/Exception");
```

为了了解每个参数的作用， 我们来看`visit`方法的声明，每个参数的作用见注释:

```
public final MethodVisitor visitMethod(
    final int access, // 访问控制
    final String name, // 方法名称
    final String descriptor, // 方法参数及返回值描述
    final String signature,  // 方法参数及返回值的泛型声明
    final String[] exceptions) // 异常类型列表
```

方法的`descriptor`参数是对于输入类型和返回类型的声明，形式如：`()V`代表无输入无输出，括号内为入参列表，括号后面为返回值类型，这里的V即为void。

在本案例的声明中可以看到，在编译为字节码后，泛型的部分被抹掉，返回类型被转换为`Object`。

而在`signature`中，包含了入参和返回值的泛型信息。首先是泛型的声明`<T:Ljava/lang/Object;>`，在后面的入参和返回值信息中，使用`TT;`来替换声明的泛型。

此处也可以认识到，在Java的泛型声明中，有形如：`<T extends Map>`的声明形式。而默认的`<T>`在编译后其实等价于`<T extends Object>`。

## 方法逻辑的编写

当完成了类和方法的声明之后，就可以通过`MethodVisitor`类来进行具体的方法内业务逻辑的编写。

在开始编写代码之前，给出一些简单的非必须的建议：

- 维护一个本地变量池，保存每一个本地变量的名称、slot、类型等信息。最后输出本地变量表时会有大用。可以用Map之类来实现。
- 维护几个int型变量，分别记录当前栈深度、本地变量池大小的信息，以及这两个值的最大值。
- 维护一个本地变量池的副本，以后做栈帧的时候同样会用到。

方法逻辑的编写分为几个部分：
- code开始
- 编写逻辑
- 记录本地变量表
- 标明最大栈深度、本地变量表大小
- 完成编写

让我们回到第一章的四则运算案例，其中的方法体字节码如下：

```
Code:
    stack=3, locals=4, args_size=1
        0: iconst_3
        1: istore_1
        2: iconst_4
        3: istore_2
        4: iconst_5
        5: istore_3
        6: iload_1
        7: iload_2
        8: iload_3
        9: imul
        10: iadd
        11: ireturn

    LocalVariableTable:
    Start  Length  Slot  Name   Signature
        0      12     0  this   Lcom/xiaojukeji/aya/AsmExample;
        2      10     1     a   I
        4       8     2     b   I
        6       6     3     c   I
```

用ASM框架编写代码来实现如下：

```Java
mv.visitCode(); // 标记Code开始
// 下面开始编写业务逻辑
Label l0 = new Label();
mv.visitLabel(l0);
mv.visitLineNumber(20, l0);
mv.visitInsn(ICONST_3);
mv.visitVarInsn(ISTORE, 1);
Label l1 = new Label();
mv.visitLabel(l1);
mv.visitLineNumber(21, l1);
mv.visitInsn(ICONST_4);
mv.visitVarInsn(ISTORE, 2);
Label l2 = new Label();
mv.visitLabel(l2);
mv.visitLineNumber(22, l2);
mv.visitInsn(ICONST_5);
mv.visitVarInsn(ISTORE, 3);
Label l3 = new Label();
mv.visitLabel(l3);
mv.visitLineNumber(23, l3);
mv.visitVarInsn(ILOAD, 1);
mv.visitVarInsn(ILOAD, 2);
mv.visitVarInsn(ILOAD, 3);
mv.visitInsn(IMUL);
mv.visitInsn(IADD);
mv.visitInsn(IRETURN);
Label l4 = new Label();
mv.visitLabel(l4);
// 记录本地变量表
mv.visitLocalVariable("this", "Lcom/nature/dql/SuperMaper;", null, l0, l4, 0);
mv.visitLocalVariable("a", "I", null, l1, l4, 1);
mv.visitLocalVariable("b", "I", null, l2, l4, 2);
mv.visitLocalVariable("c", "I", null, l3, l4, 3);
// 记录最大的栈深度和本地变量表大小
mv.visitMaxs(3, 4);
// 方法完成
mv.visitEnd();
```

其中值得注意的是本地变量表的记录，在之前已经说明过本地变量表的字段含义。

在此同样根据`visitLocalVariable`方法的声明来看参数含义：

```
public void visitLocalVariable(
    final String name, // 变量的名称
    final String descriptor, // 变量的类型声明
    final String signature, // 变量的泛型信息
    final Label start, // 变量的作用域起始
    final Label end, // 变量的作用域终止
    final int index) // 变量的slot序号
```

可以看到，其作用域的起始和终止位置，都是利用Label来实现的。在编写字节码的时候，我们很难知道自己的字节码在实现中的具体行号，利用Label对代码的位置进行标记即可实现该目的。

Label是可以先声明，先使用，后定位的。我们可以先定义一个Label，然后正常的使用它，最后调用visitLabel来确定该Label对应的位置即可。之前所有使用该Label的位置都会被替换为最后具体的位置。

## 方法的调用

ASM提供了MethodVisitor.visitMethodInsn方法来实现方法的调用，首先来看方法声明：

```
public void visitMethodInsn(
    final int opcode,   // 调用方式
    final String owner, // 方法所有类
    final String name,  // 方法名称
    final String descriptor, // 方法出入参描述
    final boolean isInterface) // 方法所有者是否是接口
```

opcode放在最后，先看其它的参数：
- owner与name：仅仅一个方法名并不能定位一个方法，一个完整的方法形如：`java/lang/Object.toString`，就是一个owner.name的形式
- descriptor：可以详见上一节中声明一个方法时传入的入参、返回值类型描述，通常形如`()Ljava/lang/String;`。
- isInterface：owner是否是一个接口。

然后我们来看opcode，在JVM字节码中提供了五种调用方法的opcode：

- invokespecial: 调用构造方法、私有方法或父类方法
- invokestatic: 调用静态方法
- invokevirtual: 调用实例方法
- invokeinterface: 调用接口方法
- invokedynamic: 动态推断方法；

前面四种方法看文本描述都可以清晰地知道是在什么情况下使用。invokedynamic则是JVM指令集中最令人困惑的一个。

invokedynamic是在Java8中才加入的新特性，是JVM对于更加动态的类型的一种支持，用于降低反射编程带来的开销。我们在Java8中用到的很多新的语法特性都得益于invokedynamic指令，诸如lambda表达式等。该指令最终仍旧会执行invokevirtual/invokeinterface指令。只不过推迟到运行时再进行具体的推断。

鉴于该指令的复杂性，在本教程中不进行介绍和使用，我们也不会有用字节码写lambda表达式之类的需求吧……不会吧不会吧……

关于各种调用方法的更详细的案例描述可以可以参见[这篇博客](https://www.infoq.cn/article/Invokedynamic-Javas-secret-weapon)。

## 小结

本章基本完整的介绍了如何去实现一个类。在生产中直接使用ASM框架是很麻烦的一件事，会让你的代码乱成一团。编写一个类的时候，对ClassVisitor/MethodVisitor进行进一步的抽象封装，维护一个自己的上下文信息容器，可以帮助你更好的实现更复杂的功能。