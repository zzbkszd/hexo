---
title: (Mysql) 二、查询优化
date: 2020-10-30 11:53:09
categories: Mysql
tags: 
  - MYSQL
  - 数据库
---


## 概述

Mysql的查询优化工作是Mysql所有技术中最有用最为立竿见影的一项。通过合理的优化，往往可以令SQL查询的执行效率以数量级的方式提升。

因为网络通信等各种因素的干扰，对于查询时间很难得到一个准确的数值，在本章中所列的查询时间均为一个统计意义的均值，仅供参考，相比之下更重要的是不同查询之间的快慢对比。

本章内容分为三大部分：

- 执行计划分析
- 索引优化
- 关联查询优化

在本章的最开始，默认所有的表除主键外没有任何索引。

本章只会讲述优化手段的应用，关于优化的细节原理会在后续进行详解。

<!-- more -->

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

### Mysql的索引类型：

- INDEX 普通索引
- UNIQUE 唯一索引，索引列（列的组合）的值必须唯一或为空
- PRIMARY 主键索引， 特殊的唯一索引，每个表只能有一个。
- FULLTEXT 全文索引，需要配合match against语句来查询。类似于一个查询引擎。需文本可以分词。
- SPATIAL 空间索引，对于空间数据类型建立的索引

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

之前的查询都是关于数字、日期的格式化列的查询。在日常使用中，对于字符串列的查询也是很常见的，比如下面这个案例：

```sql
-- 查询所有first_name为Georgy的员工信息：

SELECT * FROM employees.employees where first_name = "Georgy";
```

在未添加索引的情况下，这条查询的执行耗时为0.13s，现在为`first_name`字段增加一个索引：

```sql
ALTER TABLE `employees`.`employees` ADD INDEX `first_name` (`first_name` ASC);
```

其查询效率可以提升至0.02s以下，相比于增加索引之前有了数倍的提升。通过`Explain`查看执行计划可以看到利用了刚刚创建的索引，查询类型为`ref`。由此可见索引对于字符串列也同样有效。实际上我们甚至可以对字符串进行对比，比如下面这个查询也同样可以利用这个索引：

```sql
SELECT * FROM employees.employees where first_name between "Georga" and "Georgz";
-- 这个查询同样利用了索引，查询类型为range。
```

但是我们修改一下查询条件，可能就有所不同了：

```sql
-- A: 
SELECT * FROM employees.employees where first_name like "Georgy%";
-- B: 
SELECT * FROM employees.employees where first_name like "%Georgy";
```

这两个查询看似只有一个百分号的差别，但是A的执行速度为0.02s，而B的执行速度为0.15s。对比上面的例子可以很明显的猜到B的查询没有利用索引。通过`Explain`查看执行计划可以确认这一猜想：A的查询类型为`ref`，而B的查询类型为`ALL`。

当我们使用模糊查询时，需要注意左侧的模糊查询不会利用到索引，其查询效率会低很多。

```
注意：在网上有很多博客说可以用LOCATE或者POSITION函数，或者用reverse之类的方法来利用索引，实际上这些方法在MYSQL 5.7中均不会生效！
使用全文索引可以部分的解决问题，但是全文索引仅限于可以进行分词的文本，对不能分词的连续字符串模糊查询就很无力了。
以目前作者所知，单纯依靠sql优化技术，没有任何方法可以完全功能的实现高效左侧模糊查询。只能通过业务逻辑来进行规避。诸如增加冗余列倒序存储等方式。
```

### 组合索引与最左匹配原则

以上的索引都是对于单个列的索引，在Mysql中还可以对多列建立联合索引。

首先为`employees`创建一个联合索引`nameAndGender`，包含了`first_name, last_name, gender`三个列。然后在表中进行两次查询：

```sql
ALTER TABLE `employees`.`employees` ADD INDEX `nameAndGender` (`first_name` ASC, `last_name` ASC, `gender` ASC);
-- A：
SELECT * FROM employees.employees where first_name = 'Kazuhide' and gender = 'F';
-- B：
SELECT * FROM employees.employees where last_name = 'Cooke' and gender = 'F';
```

在实际查询中，查询A的执行耗时约为0.016s，查询B的执行耗时约为0.125s，存在很大差距。查看两个查询的执行计划如下：

```sql 
-- A:
+----+-------------+-----------+------------+------+---------------+---------------+---------+-------+------+----------+-----------------------+
| id | select_type | table     | partitions | type | possible_keys | key           | key_len | ref   | rows | filtered | Extra                 |
+----+-------------+-----------+------------+------+---------------+---------------+---------+-------+------+----------+-----------------------+
|  1 | SIMPLE      | employees | NULL       | ref  | nameAndGender | nameAndGender | 58      | const |  234 |    50.00 | Using index condition |
+----+-------------+-----------+------------+------+---------------+---------------+---------+-------+------+----------+-----------------------+

-- B:
+----+-------------+-----------+------------+------+---------------+------+---------+------+--------+----------+-------------+
| id | select_type | table     | partitions | type | possible_keys | key  | key_len | ref  | rows   | filtered | Extra       |
+----+-------------+-----------+------------+------+---------------+------+---------+------+--------+----------+-------------+
|  1 | SIMPLE      | employees | NULL       | ALL  | NULL          | NULL | NULL    | NULL | 298740 |     5.00 | Using where |
+----+-------------+-----------+------------+------+---------------+------+---------+------+--------+----------+-------------+
```

可以看到查询A利用了刚刚创建的`nameAndGender`索引，而查询B并没有利用索引，进行了全表的查询。

在Mysql的联合索引中，遵循最左匹配原则，即查询条件中必须包含联合索引的最左列才能够利用联合索引。而且只要包含了最左列，不论后面是否连续依次利用其它列，比如跳过`last_name`直接查询`gender`，或者直接只查询`first_name`，均可以成功利用索引。

### 其它的索引优化小技巧

1. 应尽量避免在 where 子句中使用not，<>，!=操作符，或者在索引列上使用函数，否则将引擎放弃使用索引而进行全表扫描。
2. 应尽量避免在 where 子句中对字段进行 null 值判断，否则将导致引擎放弃使用索引而进行全表扫描，如：
```sql
select id from t where num is null
-- 可以在num上设置默认值0，确保表中num列没有null值，然后这样查询：
select id from t where num=0
```
3. 尽量避免在 where 子句中使用 or 来连接条件，否则将导致引擎放弃使用索引而进行全表扫描，如：
```sql
select id from t where num=10 or num=20;
--可以这样查询：
select id from t where num=10
union all
select id from t where num=20;
```

4. in 和 not in 也要慎用，否则会导致全表扫描，如：
```sql
select id from t where num in(1,2,3)
-- 对于连续的数值，能用 between 就不要用 in 了：
select id from t where num between 1 and 3
```

5. 如果在 where 子句中使用参数，也会导致全表扫描。因为SQL只有在运行时才会解析局部变量，但优化程序不能将访问计划的选择推迟到运行时；它必须在编译时进行选择。然 而，如果在编译时建立访问计划，变量的值还是未知的，因而无法作为索引选择的输入项。如下面语句将进行全表扫描：
```sql
select id from t where num=@num
-- 可以改为强制查询使用索引：
select id from t with(index(索引名)) where num=@num
```

6. 不要在 where 子句中的“=”左边进行函数、算术运算或其他表达式运算，否则系统将可能无法正确使用索引。
``` sql
select id from t where substring(name,1,3)=’abc’; -- name以abc开头的id
select id from t where datediff(day,createdate,’2005-11-30′)=0; -- ’2005-11-30′生成的id
select id from t where num/2=100
-- 应改为:
select id from t where name like ‘abc%’
select id from t where createdate>=’2005-11-30′ and createdate<’2005-12-1′
select id from t where num=100*2
```

7. 很多时候用 exists 代替 in 是一个好的选择：
```sql
select num from a where num in(select num from b)
-- 用下面的语句替换：
select num from a where exists(select 1 from b where num=a.num)
```

8. 并不是所有索引对查询都有效，SQL是根据表中数据来进行查询优化的，当索引列有大量数据重复时，SQL查询可能不会去利用索引，如一表中有字段 sex，male、female几乎各一半，那么即使在sex上建了索引也对查询效率起不了作用。

9. 索引并不是越多越好，索引固然可以提高相应的 select 的效率，但同时也降低了 insert 及 update 的效率，因为 insert 或 update 时有可能会重建索引，所以怎样建索引需要慎重考虑，视具体情况而定。一个表的索引数最好不要超过6个，若太多则应考虑一些不常使用到的列上建的索引是否有 必要。

---

## 关联表优化

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

在Mysql的关联表优化中，有一点叫做尽量用小表驱动大表。上面的三个查询充分的验证了这个规则：

查询A中，用了两个很大的表做链接，在最后的大结果集上做了分组和排序，导致效率很低。

查询B中，通过对于表salaries的预先处理，减小了表的大小，有效地提升了效率。

查询C中，单纯对`salaries`表做了处理，生成了一个仅有10条数据的小表，再次令效率得到了大幅度的提高。

可以看到随着表的大小的减少，尤其是用作查询条件的表的前置处理，可以有效地提升在关联查询中的执行效率。在使用中，应当注意尽量将查询筛选前置，减小参与关联的表的大小。


## 小结

本章内容简述了Mysql中查询优化的基本方法和原则，Mysql的查询优化往往可以带来较好的性能提升，值得引起重视。但是本章仅仅介绍了这些技巧的应用，并没有详细说明原理。在下一章中将会介绍Mysql的一些底层原理，进一步详解优化的机制。

更详细的优化细节可以阅读[Mysql官方文档](https://dev.mysql.com/doc/refman/5.7/en/optimization.html)中关于优化的部分。








