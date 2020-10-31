---
title: (Mysql) 一、查询基础
date: 2020-10-28 11:53:09
categories: Mysql
tags: 
  - MYSQL
  - 数据库
---

## 基础查询

本节通过案例展示一些常用的Mysql基础查询。因内容太过基础故不作过多详细介绍。具体结果自己执行一下就知道了。非初学者可以完美跳过本节

```Sql
-- sql中的基础查询
-- 查询一个表中的全部数据
select * from departments;
-- 查询指定列的数据
select dept_name from departments;
-- 统计查询结果的行数
select count(*) from departments;
-- 查询指定行数的数据 (limit 限制行数)
select * from employees limit 10;
-- 将查询结果按指定列排序 (order by 语句，asc 为正序，desc 为倒序)
select * from employees order by emp_no asc LIMIT 10;
-- 查询指定行数区间的数据（limit 跳过行数，限制行数)
select * from employees order by emp_no asc LIMIT 10, 10;
-- 按列分组(group by 语句)
select gender, count(*) from employees group by gender;

-- 查询指定条件的数据（where 条件)
select count(*) from employees where gender='F';
-- 查询区间条件的数据(between and 条件)
select count(*) from employees where birth_date between '1960-01-01' and '1965-01-01';
-- 查询是否为空的列（is null 条件)
select count(*) from employees where last_name is null;
-- 查询列是否在列表中(in 条件)
select count(*) from employees where gender in ('F', 'M');

-- 一个稍复杂的综合案例：查询最高工资最高的10个员工的工号
select emp_no, max(salary) salary from salaries group by emp_no order by salary DESC limit 10;

-- 联合查询：查询工资最高的10个员工的详细信息
select e.* from 
	employees e, 
	salaries s  
where e.emp_no = s.emp_no group by e.emp_no order by max(s.salary) DESC limit 10;
```
<!-- more -->

## 子查询

Mysql中一个查询的结果也可以作为一个表A，则在此表A上也可以继续进行查询。则生成表A的查询被称为子查询。

子查询与联合查询在很多时候可以相互转换，得到相同的结果。

继续上一节的案例，现在我们要查询最高工资最高的10个员工的详细信息。

很简单的一个子查询思路：既然已经查得工资最高的10个员工的工号，只要判断工号在这10个员工之中即可查得员工信息。

``` sql
-- 查询工资最高的10个员工的信息
select * from employees e where e.emp_no in 
	(select sub.emp_no from 
		(select emp_no from salaries 
			group by emp_no 
			order by max(salary) DESC 
			limit 10) 
	as sub );
```

tip: `在mysql5.7中，不支持使用limit语句的in/all/any/some子查询，所以需要进行一个嵌套的子查询以绕过该问题，因为嵌套内的查询使用子查询但是没有在in条件中，嵌套外的子查询用于in条件但是没有limit`

该查询得到的结果如下：

```
+--------+------------+------------+-----------+--------+------------+
| emp_no | birth_date | first_name | last_name | gender | hire_date  |
+--------+------------+------------+-----------+--------+------------+
|  43624 | 1953-11-14 | Tokuyasu   | Pesch     | M      | 1985-03-26 |
| 254466 | 1963-05-27 | Honesty    | Mukaidono | M      | 1986-08-08 |
|  47978 | 1956-03-24 | Xiahua     | Whitcomb  | M      | 1985-07-18 |
| 253939 | 1957-12-03 | Sanjai     | Luders    | M      | 1987-04-15 |
| 109334 | 1955-08-02 | Tsutomu    | Alameldin | M      | 1985-02-15 |
|  80823 | 1963-01-21 | Willard    | Baca      | M      | 1985-02-26 |
| 493158 | 1961-05-20 | Lidong     | Meriste   | M      | 1987-05-09 |
| 205000 | 1956-01-14 | Charmane   | Griswold  | M      | 1990-06-23 |
| 266526 | 1957-02-14 | Weijing    | Chenoweth | F      | 1986-10-08 |
| 237542 | 1954-10-05 | Weicheng   | Hatcliff  | F      | 1985-04-12 |
+--------+------------+------------+-----------+--------+------------+
```

使用子查询的方式，相比于上一节中的最后一个联合查询案例，得到的结果是等价的，但是执行效率会高很多。在该问题上，使用子查询的查询耗时约为1.2秒，而联合查询耗时约为4.5秒。

这个查询还有另一种子查询实现方式，其思路如下：

查得每一个员工的最高工资，然后将所有员工按最高工资降序排序，取前10位员工。

``` sql
select e.* from 
	employees e, 
	(select emp_no, max(salary) salary from salaries group by emp_no) s  
where e.emp_no = s.emp_no order by s.salary DESC limit 10;
```

得到的结果和上面一种写法是一致的。即子查询即可作为查询条件的输入，也可以作为一个表参与查询。

当作为查询条件时，需要注意子查询的结果必须符合条件需要的输入，比如in查询则必须只有一列结果作为输入，若是`>`一类的条件则必须只有一个值作为结果。

当作为表参与联合查询时，需要指定表之间的关联列`e.emp_no = s.emp_no`以生成结果。


最后本节增加一道简单的思考题：

```
在上述两个写法之外，还有等价的第三种写法：

select * from employees e where e.emp_no in 
	(select sub.emp_no from 
		(select DISTINCT emp_no from salaries order by salary DESC limit 10)
		as sub 
	);

在salaries.salary字段增加b-tree索引的情况下，三种SQL的执行速度如下：
1: 1.2s
2: 1.2s
3: 0.013s

问：为何会有这么大的效率差距？
```

## 关联查询Join

还是与上一节一样的问题，用Join关联查询可以写作：

``` sql
select e.* from employees e JOIN salaries s on e.emp_no = s.emp_no GROUP BY e.emp_no order by max(s.salary) DESC limit 10
```

实际上，在Mysql中，使用`select * from a, b where a.id=b.id`的联合查询，其效果等价于`select * from a join b on a.id=b.id`，即内连接查询。

好了好了，忘掉那个问题。

在mysql中，有数种join操作：
```
join 内连接
left/right join 外连接
cross join 交叉连接
```

为了展示join的效果，我们首先创建一个视图`sub_title`

```sql
CREATE VIEW `sub_titles` AS select * from `titles` where ((`titles`.`emp_no` % 3) = 1);
```

相对于原来的`titles`表，这个视图只保留了`emp_no`的值对三求余等于1的值，相当于删除了2/3的记录。这就导致了如果只看这个视图，有三分之二的雇员是没有职称的。

现在，我们要查询每个雇员的id、姓名和职称，用JOIN来实现查询，得到结果如下：

```sql
select e.emp_no, first_name, last_name, title from employees e join sub_titles t on e.emp_no = t.emp_no limit 5;

+--------+------------+-----------+--------------------+
| emp_no | first_name | last_name | title              |
+--------+------------+-----------+--------------------+
|  10003 | Parto      | Bamford   | Senior Engineer    |
|  10006 | Anneke     | Preusig   | Senior Engineer    |
|  10009 | Sumant     | Peac      | Assistant Engineer |
|  10009 | Sumant     | Peac      | Engineer           |
|  10009 | Sumant     | Peac      | Senior Engineer    |
+--------+------------+-----------+--------------------+
```

可以发现在上面的查询结果中，只有`emp_no%3=1`的雇员被返回了。

内连接`join`的效果就是如此，只当链接左右两张表中均有数据时，才会返回该行数据。

下面使用外连接`left / right join`来分别试验一下，首先来看左连接：

```sql
select e.emp_no, first_name, last_name, title from employees e left join sub_titles t on e.emp_no = t.emp_no limit 8;

+--------+------------+-----------+-----------------+
| emp_no | first_name | last_name | title           |
+--------+------------+-----------+-----------------+
|  10001 | Georgi     | Facello   | NULL            |
|  10002 | Bezalel    | Simmel    | NULL            |
|  10003 | Parto      | Bamford   | Senior Engineer |
|  10004 | Chirstian  | Koblick   | NULL            |
|  10005 | Kyoichi    | Maliniak  | NULL            |
|  10006 | Anneke     | Preusig   | Senior Engineer |
|  10007 | Tzvetan    | Zielinski | NULL            |
|  10008 | Saniya     | Kalloufi  | NULL            |
+--------+------------+-----------+-----------------+
```

可以看到在`emp_no`一列是连续的雇员编号，而在`title`一列中多出了很多NULL值，即在一开始就被我们过滤掉职称的员工也被查询了出来。

左连接`left join`的效果就是如此，只要连接左侧的表内有值，就会被查询出来。如果对应的右侧表没有值则用NULL填充。

为了展示右连接的效果，我们需要首先创建另一个视图：

```sql
create view sub_employees as select * from employees where emp_no != 10003;
```

这个视图中删除了`emp_no=10003`的员工。

然后在两个视图上执行右连接查询：

```sql
select e.emp_no, first_name, last_name, title from sub_employees e right join sub_titles t on e.emp_no = t.emp_no limit 5;

+--------+------------+-----------+--------------------+
| emp_no | first_name | last_name | title              |
+--------+------------+-----------+--------------------+
|   NULL | NULL       | NULL      | Senior Engineer    |
|  10006 | Anneke     | Preusig   | Senior Engineer    |
|  10009 | Sumant     | Peac      | Assistant Engineer |
|  10009 | Sumant     | Peac      | Engineer           |
|  10009 | Sumant     | Peac      | Senior Engineer    |
+--------+------------+-----------+--------------------+
```

可以看到，在查询结果中，第一行的数据被填充了NULL，这里原本的应该是`emp_no=10003`的员工信息。而且在结果中仅查询出了`emp_no%3=1`的员工。

右连接的效果即为，只要连接右侧的表内有值，就会被查询出来，左侧表没有的值会被NULL填充。

```
问：若在sub_employees和sub_titles视图上使用内连接join，会有什么效果？
```

最后的`cross join`与前面的join有所不同，可以不用指定关联列，对两个表进行笛卡尔积得到结果。案例如下：

```
select e.emp_no, first_name, last_name, title from employees e cross join sub_titles t where e.emp_no=10006 limit 10;
+--------+------------+-----------+--------------------+
| emp_no | first_name | last_name | title              |
+--------+------------+-----------+--------------------+
|  10006 | Anneke     | Preusig   | Senior Engineer    |
|  10006 | Anneke     | Preusig   | Senior Engineer    |
|  10006 | Anneke     | Preusig   | Assistant Engineer |
|  10006 | Anneke     | Preusig   | Engineer           |
|  10006 | Anneke     | Preusig   | Senior Engineer    |
|  10006 | Anneke     | Preusig   | Engineer           |
|  10006 | Anneke     | Preusig   | Senior Engineer    |
|  10006 | Anneke     | Preusig   | Senior Staff       |
|  10006 | Anneke     | Preusig   | Engineer           |
|  10006 | Anneke     | Preusig   | Senior Engineer    |
+--------+------------+-----------+--------------------+

select count(*)from employees e cross join sub_titles t where e.emp_no=10006;
+----------+
| count(*) |
+----------+
|   147652 |
+----------+

select count(*) from sub_titles;
+----------+
| count(*) |
+----------+
|   147652 |
+----------+
```

可以看到，对于`emp_no=10006`这一个雇员，会与`sub_titles`中的所有`title`列的值依次匹配，可以看到在结果中`emp_no=10006`的数据条数与`sub_titles`中的数据量相等。

于是可以知道，`cross join`对于两个数据量为m和n的表，最终会产生m*n条数据。所以千万不要在没有加限制条件的前提下执行`cross join`查询，对于数据量较大的表会直接卡死好久好久。


## 联合查询Union

联合查询Union可以将多个select的结果聚合成一个结果，举例来说，查询所有出生日期在`1959-01-01`到`1960-06-30`和`1960-01-01`到`1963-01-01`两个区间的员工。

首先我们可以写出两个简单查询的SQL：

```sql
select * from employees e where e.birth_date between '1959-01-01' and '1960-06-30';
select * from employees e where e.birth_date between '1960-01-01' and '1963-01-01';
```

两个SQL查询得到的结果数量分别为34903和69261，总计为104164条数据，根据时间区间可以很容易知道两个结果集中有一部分员工信息是重复的。

下面用union来进行联合查询：

```sql
select count(*) from (
select * from employees e where e.birth_date between '1959-01-01' and '1960-06-30'
union
select * from employees e where e.birth_date between '1960-01-01' and '1963-01-01') u;
```

得到的结果为92572条数据，union查询对两个查询的结果集进行了去重操作。

如果换用`union all` ，则查询结果为104164条，即`union all`不会对结果集进行去重。

union联合查询可以用于分表后的多表查询。也可以将任意两个表的结果拼接在一起，只要两个sql返回的列数量和对应数据类型相同即可。


## 小结

本篇通过案例简单概述了Mysql中常用的查询。对于有一定Mysql基础的读者来说应当属于已经十分熟悉的内容。所以本章算是预热内容，在下一章中，会对于查询效率优化进行深入的探讨和研究。