---
title: (Mysql) 二、查询优化
date: 2020-10-28 11:53:09
categories: Mysql
tags: 
  - MYSQL
  - 数据库
---

1.尽量用具体字段代替*
2.模糊查询使用最左匹配原则时，索引失效
3.使用exist代替in
4.使用unioin代替join查询
5.列使用空字符串代替null,避免索引失效
6.索引列避免使用not，<>，!=，因为会全表扫描
7.索引列避免使用函数，因为索引会失效
8.不要写复杂sql,尽量单表查


## 概述

Mysql的查询优化工作是Mysql所有技术中最有用最为立竿见影的一项。通过合理的优化，往往可以令SQL查询的执行效率以数量级的方式提升。

因为网络通信等各种因素的干扰，对于查询时间很难得到一个准确的数值，在本章中所列的查询时间均为一个统计意义的均值，仅供参考，相比之下更重要的是不同查询之间的快慢对比。

对于Mysql的查询优化，通常有几个部分：

- 执行计划分析
- 索引优化
- 关联表优化
- 查询条件优化
- 以及一些零散的小技巧

通常各种技巧都会在一起使用，在本文中为了清晰起见，尽量不会混合使用不同的优化方式。

在本章的最开始，默认所有的表除主键外没有任何索引。

本章只会讲述优化手段的应用，关于优化的细节原理会在后续进行详解。

## 执行计划分析

执行优化最便捷的一步是先读懂Mysql的执行计划。Mysql提供了`explain`语句来查看SQL的执行计划。

我们首先来一个简单的案例：

``` sql
-- 工资在40000-40200之间的工资记录：
explain select count(*) from salaries where salary between 40000 and 40200;

-- 得到结果：
+----+-------------+-----------+------------+------+---------------+------+---------+------+---------+----------+-------------+
| id | select_type | table     | partitions | type | possible_keys | key  | key_len | ref  | rows    | filtered | Extra       |
+----+-------------+-----------+------------+------+---------------+------+---------+------+---------+----------+-------------+
|  1 | SIMPLE      | salaries  | NULL       | ALL  | NULL          | NULL | NULL    | NULL | 2838629 |    11.11 | Using where |
+----+-------------+-----------+------------+------+---------------+------+---------+------+---------+----------+-------------+
```

得到的执行计划中各列的含义如下：

### id

表示SQL的执行顺序。若是ID相同，则从上到下按顺序执行，否则按照从大到小的顺序执行。

### select_type

表示查询语句的类型，包括九种不同的类型：

- SIMPLE 简单查询
- PRIMARY 若查询中包含有子查询等复杂语句，则外层SELECT会被标记为PRIMARY
- UNION UNION语句中的第二个或后面的SELECT语句（第一个是PRIMARY)
- DEPENDENT UNION 与上一个相同，当内部条件取决于外部的查询时会会使用该类型
- UNION RESULT UNION的结果
- SUBQUERY 子查询中的第一个SELECT
- DEPENDENT SUBQUERY 子查询中的第一个SELECT，当内部条件取决于外部的查询时会会使用该类型
- DERIVED 派生表的SELECT,FROM子句的子查询
- UNCACHEABLE SUBQUERY 不能被缓存的子查询

### table

表示当前行的信息是对应某张表的，对于一些子查询中间的派生表，会使用诸如`<derived2>`的表名。

### type

表示MYSQL在表中查找的方式。常用的有以下几种：

- ALL: 全表查询，性能最低的一种
- index: 全表索引
- range: 只检索指定范围
- ref: 表示上述表的连接匹配条件，即哪些列或常量被用于查找索引列上的值
- eq_ref: 类似ref，区别就在使用的索引是唯一索引，对于每个索引键值，表中只有一条记录匹配，简单来说，就是多表连接中使用primary key或者 unique key作为关联条件
- const、system: 当MySQL对查询某部分进行优化，并转换为一个常量时，使用这些类型访问。如将主键置于where列表中，MySQL就能将该查询转换为一个常量,system是const类型的特例，当查询的表只有一行的情况下，使用system
- NULL：MySQL在优化过程中分解语句，执行时甚至不用访问表或索引，例如从一个索引列里选取最小值可以通过单独索引查找完成。

### possible_keys

指出Mysql可能会用到的索引和字段。列出不代表一定会使用。

### Key

显示Mysql实际使用的字段（索引）

### key_len

表示索引中使用的字节数，可以计算查询中使用的索引长度，越短越好

### ref

表示表之间的连接匹配条件，在子查询中会用到。

### rows

表示Mysql预估会读取的数据行数

### extra

包含以下几种常见的扩展信息：

- Using where: 使用了Where语句
- Using index: 说明查询覆盖了索引
- Using temporary: 表示Mysql需要创建临时表来存储结果集
- Using filesort: 表示Mysql无法用索引完成排序
- Using join buffer: 在join连接中的连接条件没有索引，需要建立连接缓冲区来储存中间结果。
- Impossible where: 强调where会导致没有符合条件的行
- Select tables optimized away: 在没有GROUP BY子句的情况下，基于索引优化MIN/MAX操作，或者对于MyISAM存储引擎优化COUNT(*)操作，不必等到执行阶段再进行计算，查询执行计划生成的阶段即完成优化。

还有更多的扩展信息可以见[官方文档](https://dev.mysql.com/doc/refman/5.7/en/explain-output.html#explain-extra-information)


### filtered

表示被条件过滤之后会剩余数据的百分比。当`filtered=100`时说明没有数据会被过滤掉。该值越低说明过滤掉的数据越多。

---

## 索引优化

### 简单索引案例：

重看这个执行计划：

``` sql
-- 工资在40000-40200之间的工资记录：
explain select count(*) from salaries where salary between 40000 and 40200;

-- 得到结果：
+----+-------------+-----------+------------+------+---------------+------+---------+------+---------+----------+-------------+
| id | select_type | table     | partitions | type | possible_keys | key  | key_len | ref  | rows    | filtered | Extra       |
+----+-------------+-----------+------------+------+---------------+------+---------+------+---------+----------+-------------+
|  1 | SIMPLE      | salaries  | NULL       | ALL  | NULL          | NULL | NULL    | NULL | 2838629 |    11.11 | Using where |
+----+-------------+-----------+------------+------+---------------+------+---------+------+---------+----------+-------------+
```

看到这次查询是一个全表查询（`type=ALL`)，没有用到任何索引，需要查询299700行数据，过滤之后只剩余了11.11%的数据是有用的。现在执行这个SQL的耗时约为1.2秒。

在这种情况下，首先想到的就应该是为查询列增加索引。然后再查看执行计划

``` sql
ALTER TABLE `employees`.`salaries` ADD INDEX `salary` USING BTREE (`salary`);

explain select count(*) from salaries where salary between 40000 and 40200;
+----+-------------+----------+------------+-------+---------------+--------+---------+------+--------+----------+--------------------------+
| id | select_type | table    | partitions | type  | possible_keys | key    | key_len | ref  | rows   | filtered | Extra                    |
+----+-------------+----------+------------+-------+---------------+--------+---------+------+--------+----------+--------------------------+
|  1 | SIMPLE      | salaries | NULL       | range | salary        | salary | 4       | NULL | 202184 |   100.00 | Using where; Using index |
+----+-------------+----------+------------+-------+---------------+--------+---------+------+--------+----------+--------------------------+
```

可以看到现在的查询成功应用了刚刚新建的`salary`索引，查询类型改成了`range`， 所需查询的行数骤减为202184行。此时执行这一条查询所需的时间约为0.03秒。得到了大幅度的提升。


通过这个案例，已经可以很清晰的看到索引在提升查询效率方面的巨大作用。

### 字符串索引与模糊查询

### 组合索引与最左匹配原则

---

## 关联表优化

### 表的大小

现在回到第一章中提出的一个问题：

```sql
-- A:
select e.* from 
	employees e, 
	(select emp_no, salary from salaries) s  
where e.emp_no = s.emp_no  group by s.emp_no order by max(s.salary) DESC limit 10;

-- B:
select e.* from 
	employees e, 
	(select emp_no, max(salary) salary from salaries group by emp_no) s  
where e.emp_no = s.emp_no order by s.salary DESC limit 10;

-- C:
select * from employees e where e.emp_no in 
	(select sub.emp_no from 
		(select DISTINCT emp_no from salaries order by salary DESC limit 10)
		as sub 
	);

在salaries.salary字段增加b-tree索引的情况下，SQL的执行速度如下：
A: 20s
B: 1.2s
C: 0.02s

问：为何会有这么大的效率差距？
```

这里面其实有很多重因素的影响，最重要的在于：参与关联表的大小。

首先对比A和B的查询过程

A：表employees有30万行数据，子查询结果表s有280万行数据，需要将30万数据依次与280万数据对比匹配，然后再进行聚合和排序操作。操作量为30*280。

B：表employees有30万行数据，子查询结果表s上进行聚合和排序操作，结果又有30万行数据，需要将30万与30万数据对比匹配。操作量为30*30。

再加上A表的中间表占用空间较大，聚合和排序操作耗时会更多，也就导致了A和B两个查询的效率有很大的差别。

理解了A和B查询的区别，就很容易理解C查询为何会如此之快：

1. C查询不需要做表之间的关联操作，可以省略掉交叉对比的过程。
2. C查询的子查询充分利用索引，且结果只有10条，效率极高。
3. 在外层查询中利用主键索引提升了查询效率。

可以看到通过充分优化关联表和子查询，可以更好地利用索引，大幅度的提升查询效率。

### 小表驱动大表







