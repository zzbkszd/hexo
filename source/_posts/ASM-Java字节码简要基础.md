---
title: (ASM) 一、Java字节码简要基础知识
date: 2020-10-16 12:00:00
tags: ASM,Java
---

## 一、JVM的执行原理

### 1、从逆波兰式说起

[逆波兰式](https://zh.wikipedia.org/zh-hans/%E9%80%86%E6%B3%A2%E5%85%B0%E8%A1%A8%E7%A4%BA%E6%B3%95)是一种数学表达式方法，可以将四则运算转换成一种不需要括号即可表达优先级的表达式。

举例来说，数学表达式`3+4*5`转换成逆波兰式就是`345*+`。其中的操作符代表前面两个结果的操作，比如`*`操作即为`4*5`，`+`操作则是 `3 + (4*5)`，因为`+`前面的两个结果分别是3和`*`操作的结果。

逆波兰式从计算机语言的角度来讲，是一颗语法树的后序遍历结果。它的好处在于，可以方便的利用栈操作来进行四则运算。同样以`345*+`的逆波兰式为例，其计算顺序如下：

```
push 3 -> [3]
push 4 -> [3,4]
push 5 -> [3,4,5]
invoke * -> [3,20]
invoke + -> [23]
```
左侧是操作，右侧是栈中数据。根据逆波兰式进行运算即可得到最终结果23。

*注意！*让我们给栈的操作换一个名字，就可以得到如下的程序：

```
ICONST_3
ICONST_4
ICONST_5
IMUL
IADD
```

这一段代码就是一段Java的字节码指令，其执行结果同样是23。

### 2、JVM字节码的执行

经过上面的例子，相信读者应当对Java的执行原理有了一个比较直观的认识。有一定Java基础知识的同学应当已经联想到了Java中的栈的概念。

简单来讲，JVM执行字节码文件，就是在一个栈上执行不同的字节码指令。当然用于工业实践的JVM必然还有更多神奇的优化，但是其根本原理是不会改变的。

```
这里容易出现一种误解，即字节码就是一个栈。

一个字节码文件描述的是“对操作数栈的操作”（当然还包含其它内容），实际上的操作数栈只存在于内存和你我的脑海中。所以在编写字节码文件，尤其是遇到涉及分支、循环结构的程序时，必须考虑好栈的内容是什么样的。否则必然会出错。
```

在JVM的栈帧中，包含有本地变量表、操作数栈、程序计数器、返回地址等部分。在我们编写字节码时，需要关注的主要在于：
- 本地变量表(Local Variable Table)，我们需要将用到的本地变量记录在本地变量表的对应槽位(slot)中。
- 操作数栈，我们的字节码程序描述的就是对操作数栈的操作。也是我们程序实际运行的位置。
- 程序计数器，我们需要用Label来标记程序指令对应的计数器位置，通过控制程序计数器来实现分支、循环等逻辑控制。


## 二、字节码文件内容概述

### 1、字节码查看

字节码是以二进制方式存储的，显然的，我们不可能用人脑去解析二进制来查看字节码。

Java自带了class文件的查看工具，可以将class文件转换成字节码指令文本进行查看。我们以刚刚写的四则运算案例为例。完整的Java代码如下：

```
public class AsmExample {

    public int calc() {
        int a = 3;
        int b = 4;
        int c = 5;
        return (a+b*c);
    }
}
```

可以注意到这里为3、4、5分别赋值了一个变量，是因为`3+4*5`这种表达式在编译时会被直接编译为23的常量，无法体现预期效果。

该文件编译得到AsmExample.class文件，打开命令行终端，执行以下命令：

```
javap -v -l -p -s AsmExample.class
```

具体的几个参数的作用，可以用`javap -help`自行查看。总之回车之后，终端中即可显示出该class文件的字节码如下所示：

```
Classfile /Users/didi/code/java-test/target/test-classes/com/xiaojukeji/aya/AsmExample.class
  Last modified 2020-10-16; size 432 bytes
  MD5 checksum 9a1851023d512efc7e2f91bc2a65ada0
  Compiled from "AsmExample.java"
public class com.xiaojukeji.aya.AsmExample
  minor version: 0
  major version: 52
  flags: ACC_PUBLIC, ACC_SUPER
Constant pool:
   #1 = Methodref          #3.#19         // java/lang/Object."<init>":()V
   #2 = Class              #20            // com/xiaojukeji/aya/AsmExample
   #3 = Class              #21            // java/lang/Object
   #4 = Utf8               <init>
   #5 = Utf8               ()V
   #6 = Utf8               Code
   #7 = Utf8               LineNumberTable
   #8 = Utf8               LocalVariableTable
   #9 = Utf8               this
  #10 = Utf8               Lcom/xiaojukeji/aya/AsmExample;
  #11 = Utf8               calc
  #12 = Utf8               ()I
  #13 = Utf8               a
  #14 = Utf8               I
  #15 = Utf8               b
  #16 = Utf8               c
  #17 = Utf8               SourceFile
  #18 = Utf8               AsmExample.java
  #19 = NameAndType        #4:#5          // "<init>":()V
  #20 = Utf8               com/xiaojukeji/aya/AsmExample
  #21 = Utf8               java/lang/Object
{
  public com.xiaojukeji.aya.AsmExample();
    descriptor: ()V
    flags: ACC_PUBLIC
    Code:
      stack=1, locals=1, args_size=1
         0: aload_0
         1: invokespecial #1                  // Method java/lang/Object."<init>":()V
         4: return
      LineNumberTable:
        line 3: 0
      LocalVariableTable:
        Start  Length  Slot  Name   Signature
            0       5     0  this   Lcom/xiaojukeji/aya/AsmExample;

  public int calc();
    descriptor: ()I
    flags: ACC_PUBLIC
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
      LineNumberTable:
        line 6: 0
        line 7: 2
        line 8: 4
        line 9: 6
      LocalVariableTable:
        Start  Length  Slot  Name   Signature
            0      12     0  this   Lcom/xiaojukeji/aya/AsmExample;
            2      10     1     a   I
            4       8     2     b   I
            6       6     3     c   I
}
```

我相信足够熟悉Java的读者已经能够大致看懂这样的字节码文件了。下面会以此字节码文件为例对内容进行说明。

### 2、字节码文件分块详解

案例的字节码文件可以明显的分成几个部分：
- 类信息的声明，包括完整名称，版本，访问控制等信息。
- 常量池，这一块通常不需要自行控制，但是仔细去看能品出不少有意思的东西。
- 构造方法，本类中包含一个无参构造方法。
- 成员方法，本类中包含一个public int clac()方法。
- 在方法中，包含执行字节码Code部分， LocalVariableTable本地变量表部分。其实应该还有一部分StackMap，但是在本案例中没有涉及。
- LineNumberTable是从源码编译到字节码保存源码代码行数信息的debug信息，在输出错误栈的时候能够精确到出错的行数就是它的功劳。在我们整篇教程中都不会用到，所以就忽略掉吧！

下面一个一个的来分析这些部分的内容。

#### 类声明

```
public class com.xiaojukeji.aya.AsmExample
  minor version: 0
  major version: 52
  flags: ACC_PUBLIC, ACC_SUPER
```

在这一部分，首先可以看到类名称，如果该类有继承，还会包含extends xxx的信息。

之后是minor version 和 major version，表示支持的Java版本，52代表Java8。前后版本依次加减1自行类推。

flags是访问控制信息，可以看到ACC_PUBLIC和ACC_SUPER，表示这是一个public的类。ACC_SUPER用来表示如何调用父类的方法，涉及到后续的invokespecial和invokeonvirtual等调用方法的指令。

#### 常量池

```
Constant pool:
   #1 = Methodref          #3.#19         // java/lang/Object."<init>":()V
   #2 = Class              #20            // com/xiaojukeji/aya/AsmExample
   #3 = Class              #21            // java/lang/Object
   #4 = Utf8               <init>
   #5 = Utf8               ()V
   #6 = Utf8               Code
   #7 = Utf8               LineNumberTable
   #8 = Utf8               LocalVariableTable
   #9 = Utf8               this
  #10 = Utf8               Lcom/xiaojukeji/aya/AsmExample;
  #11 = Utf8               calc
  #12 = Utf8               ()I
  #13 = Utf8               a
  #14 = Utf8               I
  #15 = Utf8               b
  #16 = Utf8               c
  #17 = Utf8               SourceFile
  #18 = Utf8               AsmExample.java
  #19 = NameAndType        #4:#5          // "<init>":()V
  #20 = Utf8               com/xiaojukeji/aya/AsmExample
  #21 = Utf8               java/lang/Object
```

常量池用来保存常量，在后续的字节码中所需的常量都可以通过常量池序号来引用。可以看到常量池中的常量也有不同的类型。不仅仅是我们在程序中涉及的常量值，还包括类、方法、参数列表等信息也是作为常量保存在常量池中的。


#### 构造方法

```
public com.xiaojukeji.aya.AsmExample();
    descriptor: ()V
    flags: ACC_PUBLIC
    Code:
      stack=1, locals=1, args_size=1
         0: aload_0
         1: invokespecial #1                  // Method java/lang/Object."<init>":()V
         4: return
      LineNumberTable:
        line 3: 0
      LocalVariableTable:
        Start  Length  Slot  Name   Signature
            0       5     0  this   Lcom/xiaojukeji/aya/AsmExample;
```

构造方法和普通方法的声明并没有什么差别，别看在反编译出来的声明是
`public com.xiaojukeji.aya.AsmExample();`，这只是给人看的。

构造方法的真实声明并不叫`public AsmExample()`，而是`public void <init>()`

descriptor这个部分才是方法的入参和返回值的真实声明，可以看到它就是一个返回为void的方法。

在构造方法中，需要调用父类的构造方法，#1代表常量池中编号为1的常量，向上去翻可以看到#1是`java/lang/Object."<init>":()V`，即为Object类的构造方法。

当我们自己编写字节码的时候，这种无参构造方法就需要自己来手动编写了，不会有编译器来贴心的帮你生成了。

#### 成员方法

```
public int calc();
    descriptor: ()I
    flags: ACC_PUBLIC
```

很简单的声明，public方法，输入为空，输出为int。（I代表未装箱的int类型）

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
```

这里有三个值：`stack=3`, `locals=4`, `args_size=1`

stack表示在本方法中最大的栈深度。这个3的来源在于第6-8行的三个iload指令，向栈中推入了三个数字。

locals表示在本方法中的本地变量表最大大小。在本案例中可以在后面的本地变量表中看到这四个变量

args_size表示输入的参数个数，虽然声明的输入为空，但是还有一个`this`变量作为默认输入。除了静态方法之外，本地变量表的第一个变量都应当是当前类。

在本案例中涉及到的指令如下：
- iconst_n： 将值为n的int型常量入栈
- istore_n：将int型数值保存到序号为n的本地变量中，出栈
- iload_n： 将序号为n的int型本地变量入栈
- imul: 将栈顶两个元素相乘，结果入栈
- iadd: 将栈顶两个元素相加，结果入栈
- ireturn: return操作，返回栈顶的int型变量。

```
LocalVariableTable:
    Start  Length  Slot  Name   Signature
        0      12     0  this   Lcom/xiaojukeji/aya/AsmExample;
        2      10     1     a   I
        4       8     2     b   I
        6       6     3     c   I
```

本地变量表包含5列：

- start ：该变量作用域的起始行数。
- end   ：该变量作用域的终止行数。
- Slot  ：该变量的“槽位”，即序号。注意该序号可以是不连续的。
- Name  ：该变量的名称
- Signature ： 该变量的类型声明

额外的，如果包含泛型，在变量表中还会以注释的形式显示出泛型信息。注意只是显示，不是以注释方式添加……

## 小结

在本节中，简单的了解了字节码文件的结构和Java执行字节码的原理。ASM框架的目的就是通过程序生成一个可执行的字节码。也就是我们要“手写”一段如上的字节码。

从某种意义上来讲，写字节码和写java文件是基本一致的。就是更换了一种语言而已。只要按照规定的格式写好字节码，JVM就可以读取并执行。我们的目标也就达到了。

如果有希望更深入理解字节码的同学，可以去学习《深入理解JVM》这本经典名著。其中有更偏重于底层规范的描述。