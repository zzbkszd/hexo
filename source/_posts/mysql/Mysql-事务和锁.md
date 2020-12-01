---
title: (Mysql) 四、事务和锁
date: 2020-11-02 11:53:09
categories: Mysql
tags: 
  - MYSQL
  - 数据库
---

## 数据库的ACID原则

ACID是一组数据库设计原则，InnoDB完整的支持这种设计原则，其原则详情及InnoDB的实现方法如下列：

- A: 原子性，主要涉及事务(transaction)
- C: 一致性，主要涉及内部处理，包括双写缓存(doublewrite buffer)和崩溃恢复(crash recovery)功能。
- I: 隔离性，主要涉及事务(transaction)，尤其是事务的隔离级别。
- D: 持久性，很多时候与具体的硬件、网络环境相关。

在本章中，主要讨论InnoDB引擎中关于事务的实现，以及事务是如何保证原子性和隔离性的。

<!-- more -->

## InnoDB事务隔离级别

事务隔离是隔离性的来源，不同的隔离级别设置可以在多个事务同事执行更改和查询时微调性能与结果的可靠性、一致性和可重复性之间的平衡。

InnoDB提供了`SQL:1992`规定的全部四个事务隔离级别：读未提交、读已提交、可重复读、序列化。其中InnoDB的默认隔离级别是可重复度。

下面将会根据隔离级别的层级详细描述不同的事务隔离级别的相关效果，每种隔离级别可以应对并发读取中的某一层次的错误，后面的隔离级别可以应对之前隔离界已经解决的错误。

```
注：关于脏读、不可重复读、幻读问题
在网上有各种不同的说法。本文根据Mysql官方文档以及实验结果来验证。可能与网络传闻有所差异。
关于不同隔离级别及三种问题的实验案例代码均附在附录一中，大纲所说明的样例数据库进行验证。
```

### 读未提交与脏读

首先理解脏读。

当事务A插入了一个新数据R1，但是还没有提交时，查询B就可以查询到新数据R1，这种时候如果事务A回滚了，则会导致脏读的出现。查询B查询到了本不应该存在的数据R1。

而这种可以读取事务A`未提交`数据的隔离级别，就是`读未提交级别`。

在该级别下，数据库不使用任何锁来保证隔离性。

### 读已提交与幻影行（不可重复读）

如果要解决脏读问题，就需要至少采用`读已提交`级别的隔离级别。

要理解`读已提交`，首先需要了解一个知识点：一致的非锁定读取。

```
一致的非锁定读取
一致读是InnoDB在读已提交和可重复度隔离级别上SELECT语句的默认执行模式。
使用一致读取，数据库不会给对应的表上加锁，而是创建一个用于当前事务的快照。事务中的查询仅在快照中进行。
所以当事务执行期间，其它事务可以对表进行修改，而当前事务并不能查询到其它事务的修改结果。
```

在该级别中，在同一事务中，每个一致的读取都将设置并读取自己的新快照。

因为这种机制，一个事务A只能读取到`已经被提交`的数据，所以该级别被称为`读已提交`。

而对于锁定读取（包括带有`for update`或`lock in share mode`的`select`语句以及`update`,`delete`语句），InnoDB只会锁定索引记录，而不会锁定它们之间的间隙。

因为禁用了间隙锁定，在当前隔离级别下，可以在锁定记录的旁边自由插入新纪录，所以会导致`幻读`问题。

假设我们现在有一个表`TA`，其中`id`列为数字，我们目前有以下数据：
```
id
--
10
12
14
```

现在开启事务A，此时执行一条`锁定读取`的查询`select id from TA where id > 11 for update`，我们可以得到结果：

```
id
--
12
14
```

此时另一个事务提交了一条`id=13`的记录，事务A中再次执行同样的查询，得到的结果如下：

```
id
--
12
13
14
```

可以看到在同一个事务中，即使使用了锁定读取，仍旧会读到不同的结果，这是因为当前隔离级别禁用了间隙锁定，数据库仅会锁定`id=12、14`的两行数据，而允许在其中插入新的数据。

这种情况就被称为幻影行(phantom rows)。

```
注意：
网上超级多的文章在讨论幻读和不可重复读问题。根据其内容，读已提交级别会导致不可重复读问题，而可重复读级别会导致幻读问题。
本文根据Mysql5.7的官方文档(https://dev.mysql.com/doc/refman/5.7/en/innodb-transaction-isolation-levels.html)。
其中明确说明，因为读已提交级别禁用了间隙锁定(gap locking)，所以会导致幻影行问题(phantom rows)。这就是常说的“不可重复读”
```

### 可重复读与“幻读”

上一节说到了不可重复读，为了解决不可重复读问题，就需要引入可重复读的隔离级别。

已经说到了导致不可重复读的原因是禁用了间隙锁定，那么很自然的，若要解决不可重复读问题，就要使用间隙锁定。

首先，对于一致的非锁定读取，当前级别的第一次查询会创建一个新的快照。

对于锁定读取，当前级别会进行间隙锁定，实现上是通过`Next-Key Locks或者Gap Locks`来实现的，具体的细节会在后面详细描述。

仍旧以上一节中的查询案例为例：`select id from TA where id > 11 for update`，数据库会锁定`id>11`的所有数据位置，即禁止插入`id>11`的数据。

在本级别中，具体是否使用间隙锁定，由查询语句的查询条件决定。若是使用唯一索引指定的数据，则仅锁定当前记录，否则就会使用间隙锁定来锁定该查询覆盖的全部范围。

接下来需要重点讨论的，是“幻读”问题。

在网上的资料中，关于幻读有各种各样的解释。有用特殊的幻影行来举例的，有用冲突写入来举例的。但是实际上这并不是Mysql中`可重复读`级别导致的问题。这些例子要么无法在`可重复读`级别复现，要么在序列化的级别仍旧会出现。

由于Mysql中采用了MVCC机制，实际上并不会有幻“读”的存在，作者宁可称其为幻“写”。要复现这种问题，首先需要注意必须使用一致的非锁定读取，不能用锁定读取。执行顺序如下：

```
现有表TA：
id value
12 1
14 1
启动两个事务：
-------------------------------------------------------------------------------------
Transaction A                              Transaction B

select * from TA where id > 12;
                                           insert into TA(id, value) values (13, 1) ;
                                           commit;
update TA set value = 2 where id > 12;
commit;
-------------------------------------------------------------------------------------
然后执行查询
select * from TA where id > 12;
得到结果：
id value
13 2
14 2
```

在事务A的视角，`id=13`的数据是不存在的。但是实际上的操作修改了该条数据。

如果在查询时使用`for update`或者`lock in share mode`，就可以避免该问题。因为锁的缘故，事务B会等待事务A执行完毕之后才会执行并提交。

### 序列化

序列化与可重复读隔离级别比较相近，但是在查询时，如果禁用了`autocommit`，则会以`lock in share mode`的方式执行，如果开启了`autocommit`，则每个SELECT就是自身的事务。因此它被认为是“只读”的，并且不会阻塞其它事务。

即当你执行单个的SELECT查询的时候，它自己就是一个事务。这个查询仍旧以非锁定的方式执行，不会锁定当前数据，也不会阻止其它事务修改查询的数据。

但是回到上一节中的例子。当我们启用一个事务，其中包含一个SELECT查询的时候，我们首先禁用了`autocommit`，这时候SELECT就会锁定查询的数据，阻止其它事务修改查询的数据。这也是序列化隔离级别可以避免上一节中出现的问题的原因。

## InnoDB中的锁

InnoDB实现上述隔离级别和保证隔离性的方式就是使用锁。本节将会详细描述InnoDB中常用的锁的机制。

### 共享锁(S Shared)和排它锁(X Exclusive)

InnoDB实现了标准的行级锁，有两种不同的锁的类型： 共享锁（S）和排它锁（X）。

- 共享锁：允许持有锁的事务读取一行。
- 排他锁：允许持有锁的事务更新或删除行。

简单来说，默认情况下，读取的时候使用共享锁，修改的时候使用排它锁。

如果事务对某一行数据持有共享锁，则其它事务可以读该行（即获得共享锁），但是不可以修改（即获得排它锁）。

如果事务对某一行数据持有排他锁，则其它事务就不可以获得任何锁（无法访问该行）。

总结来说，某一行数据上可以有多个共享锁。但是排它锁只能有它自己，不能和任何其它锁共存。而如果该行已经有其它锁，也不能加上排它锁。

InnoDB中具体应用的锁都可以分为共享锁和排他锁两种模式。

### 意向锁(Intention Locks)

意向锁是InnoDB为了支持多重粒度的锁，允许行锁和表锁并存而设计的。

意向锁是一种表锁，表明当前事务可能在稍后对表中数据获得某种所（X或S）。意向锁同样分为共享意向锁(IS)和排他意向锁(IX)。在上文中使用的`for update`和`lock in share mode`就是设置意向锁的指令，前者声明IX锁，后者声明IS锁。

意向锁之间是兼容的。即同一个表上可以有多种、多个意向锁。

但是两种意向锁和排他锁都是互斥的（排它锁和任何其它锁都互斥），IX锁和共享锁也是互斥的。可以简单记忆为：面对S锁和X锁，只有IS锁和S锁是兼容的（全共享），其它任何情形都是互斥的。

### 记录锁(Record Locks)

记录锁是对索引记录的锁定，回顾上一节中关于“读已提交”部分的内容。当执行`select id from TA where id >= 11 for update`查询时，就是通过记录锁锁定了`id=11`的索引。此时不能对该索引进行修改。

不论是在“读已提交”还是“可重复读”的隔离级别中，执行诸如`select id from TA where id=12`的查询时都会使用记录锁来锁定单一的行。

### 间隙锁(Gap Locks)

间隙锁是对索引记录之间的间隙的修改。同样回顾上一节中关于“可重复读”部分的内容。

在“可重复读”的隔离级别下，当执行查询`select id from TA where id >= 11 for update`时，会使用间隙锁锁定所有`id >= 11`的数据。

值得注意的是，在同一个间隙上，两个事务可以同时拥有不同的间隙锁，比如事务A在间隙上拥有排他间隙锁，事务B在间隙上拥有共享间隙锁。

这是由间隙锁的用途决定的，间隙锁是一种“完全禁止”的锁，他们的唯一目的就是防止其他事务插入间隙。所以无论在同样的间隙上拥有多少个锁，其作用都是一样的，功能上没有冲突。

### 下一键锁（Next-Key Locks)

下一键锁是记录锁和间隙锁的组合。

一个Next-Key锁会锁住当前行（一个记录锁），以及当前行到前面的索引列的区间（间隙锁）。

举例来说，现在有索引列（可以理解成id）数据`1,3,5,7`，如果要在`5`上增加一个Next-Key锁，间隙锁会锁定区间`(3,5)`，而记录锁会锁定列`5`，最后的锁定范围就是`(3,5]`的左开右闭区间。

Next-Key锁仅在`可重复读`隔离级别下生效。

### 自增锁 （Auto-Inc Locks)

自增锁是对存在自增主键的表的一种表级锁。当多个自增插入执行时，会依次获得自增锁。在获得自增主键后会立刻释放自增锁。

### 插入意向锁 (Insert Intention Locks)

插入意向锁是一种间隙锁(Gap Locks)。其目的是为了提高并发性能。采用插入意向锁后，当多个插入语句插入的间隙位置并不相同时，他们互相之间就不会被锁定。

## 附录一 隔离级别及脏读、不可重复读、幻读Java案例

注：本案例依赖于[Hutool](https://hutool.cn/)框架。

```Java 
/**
  * 脏读
  */
public static void dirtyRead() throws Exception {
    ExecutorService es = Executors.newCachedThreadPool();

    Db.use().execute("SET GLOBAL TRANSACTION ISOLATION LEVEL READ UNCOMMITTED");

    Future f1 = es.submit(()-> {
        try {
            Db.use().tx(db-> {
                db.execute("insert into departments(dept_no, dept_name) values ('d010', 'test department')");
                Thread.sleep(500);
                int v = 1/0;
            });
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    });

    Future f2 = es.submit(()-> {
        try {
            Db.use().tx(db-> {
                Thread.sleep(200);
                List<Entity> result = db.query("select * from departments order by dept_no asc");
                System.out.println("Transction B results:");
                for (Entity entity : result) {
                    System.out.println(entity.toString());
                }
            });
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    });

    f1.get();
    f2.get();
    System.out.println("Finally result:");
    List<Entity> result = Db.use().query("select * from departments order by dept_no asc");
    for (Entity entity : result) {
        System.out.println(entity.toString());
    }
    es.shutdown();
}

/**
  * 幻影行（不可重复读问题）
  */
public static void phantomRows() throws Exception {
    ExecutorService es = Executors.newCachedThreadPool();

    Db.use().execute("SET GLOBAL TRANSACTION ISOLATION LEVEL READ COMMITTED");

    Future f1 = es.submit(()-> {
        try {
            Db.use().tx(db-> {
                List<Entity> result = db.query("select * from departments order by dept_no asc for update");
                System.out.println("Transction A results 1st:");
                for (Entity entity : result) {
                    System.out.println(entity.toString());
                }
                Thread.sleep(500);
                result = db.query("select * from departments order by dept_no asc for update");
                System.out.println("Transction A results 2nd:");
                for (Entity entity : result) {
                    System.out.println(entity.toString());
                }
            });
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    });

    Future f2 = es.submit(()-> {
        try {
            Db.use().tx(db-> {
                Thread.sleep(200);
                db.execute("insert into departments(dept_no, dept_name) values ('d010', 'test department')");
            });
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    });

    f1.get();
    f2.get();
    Db.use().execute("DELETE FROM `employees`.`departments` WHERE (`dept_no` = 'd010');");
    es.shutdown();
}

/**
  * MySql的MVCC机制确保了不会出现幻"读"， 但是可能会出现幻"写"
  * 注意在事务A中不能使用 for update 来锁定行，否则无法复现该问题。
  * 这个例子的运行不是很稳定，必须注意 B insert操作必须早于 A update 才能体现出效果。
  */
public static void repeatableRead() throws Exception{

    ExecutorService es = Executors.newCachedThreadPool();

    Db.use().execute("SET GLOBAL TRANSACTION ISOLATION LEVEL REPEATABLE READ");

    Db.use().execute("insert into departments(dept_no, dept_name) values ('d010', 'test department')");

    Future f1 = es.submit(()-> {
        try {
            Db.use().tx(db-> {
                List<Entity> result = db.query("select * from departments where dept_no > 'd009'");
                System.out.println("Transaction A results 1st:");
                for (Entity entity : result) {
                    System.out.println(entity.toString());
                }
                Thread.sleep(1500);
                db.execute("update departments set dept_name='transaction test' where dept_no > 'd009'");
                System.out.println("Transaction A update");
            });
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    });

    Future f2 = es.submit(()-> {
        try {
            Db.use().tx(db-> {
                Thread.sleep(500);
                Db.use().execute("insert into departments(dept_no, dept_name) values ('d011', 'test department2')");
                System.out.println("Transaction B insert");
            });
        } catch (SQLException throwables) {
            throwables.printStackTrace();
        }
    });

    f1.get();
    f2.get();
    List<Entity> result = Db.use().query("select * from departments where dept_no > 'd009' for update");
    System.out.println("last result:");
    for (Entity entity : result) {
        System.out.println(entity.toString());
    }
    Db.use().execute("DELETE FROM `employees`.`departments` WHERE (`dept_no` = 'd010');");
    Db.use().execute("DELETE FROM `employees`.`departments` WHERE (`dept_no` = 'd011');");
    es.shutdown();
}
```
