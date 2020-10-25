---
title: (ASM) 三、数据类型和操作指令
date: 2020-10-20 11:53:09
tags: ASM,Java
---

## 数据类型

在JVM中，数据类型与我们的Java代码有所不同。可以分为两个大类：`基本类型`和`引用类型`。

再次回想Java的装箱机制，比如int与Integer, float与Float，前者就是基本类型，后者则是一个引用类型。

在java中，基本类型包含int,float,double,boolean,byte,char,short等。但是在JVM中，基本类型只有以下四种：

- int 用I表示
- float 用F表示
- long 用L表示
- double 用D表示

其它的数据类型都会被转变为基本类型进行操作，比如boolean，虽然在声明中用Z表示，但是操作时都是按照int类型进行操作。

需要注意的是，在对基本类型进行字节码操作时，如果需要装箱、拆箱操作，需要手动编写字节码调用对应的方法。否则会出现数据类型错误。

引用类型是一个统称，除了以上四种基本类型之外的变量都是引用类型，包括字符串、对象实例等变量。


## 数据类型的表示

在声明一个类、方法的时候需要用到数据类型的描述信息。与操作符用小写字母不同，基本数据类型的描述信息是大写字母，并且还有一些事基本类型的扩展，常用的包括以下几种：

I(int), L(long), F(float), D(double), V(void), Z(boolean)

在声明中，引用类型会直接描述具体的类型，比如`java.lang.Object`，会描述为`Ljava/lang/Object;`

在数组的声明中，用`[`来表示数组，比如`[I;`表示一个int[]

引用类型的数组描述在类型描述之前增加一个`[`，比如`Object[]`类型会被描述为：`[Ljava/lang/Object;`，注意后面的分号只有一个。

## 操作指令

Java的字节码操作指令分为以下几个大类：

- 栈操作
- 运算指令
- 类型转换
- 流程控制
- 对象操作
- 锁和同步

如果从广义上来讲，前面四个类型的操作都是栈操作指令。

字节码指令通常由几个部分组成：数据类型，操作指令，地址。

例如：iload_3，其中i代表int类型，load代表将数据推入栈顶，3代表数据在本地变量表slot=3的位置。

另一种情况则是对于数组的操作，数组（array)，所以数组操作基本就是在操作指令的前面加一个a。

例如：faload， 消耗栈顶两个元素：`array, idx`， 将`array[idx]`推入栈顶。

在操作指令中有一些自带常量的操作符，例如`iload_0`，其等价于`iload 0`，出于统一便捷记忆，除了const常量操作之外，其它的操作符均不赘述包含下划线的常量操作符。

在操作符中，涉及到long和double类型的操作，会有一些特定的操作诸如`pop2`操作符。基本原则是long和double相当于两个int/float，所以操作int的指令不能操作long，操作long的指令相当于操作两个int。


### 栈操作

栈操作包含：

*const*： 推送一个常量值到栈顶，int型支持0-5， float支持0-2，long和double仅支持0和1。

案例：`iconst_1`

*load/aload*： 将本地变量推送至栈顶，支持i/l/f/d/a类型。支持一个参数代表本地变量slot。支持数组操作。

案例：`fload 1`

*store/astore*：将栈顶元素保存到指定slot的本地变量，支持i/l/f/d/a类型，需要一个参数代表本地变量slot，支持数组操作。

案例：`istore 1`

*pop/pop2*：将栈顶元素推出，pop不支持long/double类型，pop2支持long/double类型，对于其它类型则相当于两次pop操作。

案例：`pop`

*dup/dup_x1/dup_x2/dup2/dup2_x1/dup2_x2*：复制栈顶元素，x1代表复制两次，x2代表复制三次，dup2对应long/double类型。

案例：`dup`

*bipush/sipush* 将一个常量推送至栈顶，bipush仅支持8位，即`-128~127`，sipush支持16位，即`-32768~32767`

案例：`bipush 10`

*ldc* 将一个int/float/string型常量从常量池推送至栈顶

案例：`ldc "Hello"`

### 运算操作：

运算操作包括：
- add;sub;mul;div： 加减乘除，支持i/l/f/d
- rem：求余，支持i/l/f/d
- neg：求负，支持i/l/f/d
- shl; shr : 位运算，左移，右移支持i/iu(无符号)/l，
- and; or; xor：与，或，异或支持i/l
- iinc：自增，约等于 i++， 仅限整数

### 类型转换：

类型转换支持在基本类型之间进行转换，同样需要注意可能存在的精度丢失问题。

支持i(int), l(long), f(float), d(double)四种基本类型之间的任意转换，指令类似：`i2l`,即为int转换成long。

还支持：`i2b;i2c;i2s`三种，分别转换至b(byte), c(char)和s(short)。

### 流程控制：

常见的if语句中的条件判断基本都是用对比操作实现的。

*cmp*: 对比操作，支持l/f/d，返回1/0/-1作为结果。有`cmpl; cmpg`两种，分别对比小于和大于。

案例：`lcmpl`

*if*： 当栈顶为int（boolean）时，和0进行对比：`eq/ne/lt/ge/gt/le`六种操作。分别表示`== != < >= > <=`

案例：`ifge`

*if_icmp*： 对比并跳转操作，对比栈顶两个元素，对比部分支持情况和if一致，当对比结果为真时，跳转到指定代码行，通常用Label指定。

案例：`if_icmpge 23`

*goto*： 无条件跳转，和java中隐含的goto语句是同一个意思。

*return*： 返回值，将栈顶元素作为返回值返回。支持：i/l/f/d/a以及void类型。直接用`return`指令就是返回void。

案例：`areturn`

### 对象操作：

*getstatic/putstatic* 获取、设置类的静态变量

*getfield/putfield* 获取、设置类的成员变量

*invokevirtual* 调用实例方法

*invokespecial* 调用超类构建方法、实例初始化方法、私有方法

*invokestatic* 调用静态方法

*invokeinterface* 调用接口方法

*invokedynamic* 动态判断调用方法

*new* 构建一个新对象并放入栈顶

*newarray* 构建一个基础类型数组

*anewarray* 构建一个引用类型数组

*arraylength* 获得数组长度

*checkcast* 类型转换校验

*instanceof* 就是instanceof

### 锁和同步：

*monitorenter* 获得对象的锁

*monitorexit* 释放对象的锁


## 小结

本节详细描述了在Java字节码中常用的部分指令，但是本文仅仅是一个概述和助记，不完整包含全部操作符。具体的全部操作符可以见表[JVM虚拟机字节码指令表](https://segmentfault.com/a/1190000008722128)

了解了操作符之后，就已经掌握了编写字节码程序的基本知识。建议读者将本篇和上一篇的Hello World程序相印证以加深印象。