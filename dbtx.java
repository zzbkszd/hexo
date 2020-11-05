package com.xiaojukeji.aya.study;

import cn.hutool.db.Db;
import cn.hutool.db.Entity;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 数据库事务隔离级别测试
 */
public class DBTest {


    public static void main(String[] args) {
        try {
//            dirtyRead();
//            phantomRows();
            repeatableRead();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

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
     * MySql的MVCC机制确保了不会出现幻"读"， 但是可能会出现幻"写"？
     * 注意在事务A中不能使用 for update 来锁定行，否则无法复现该问题。
     * 这个例子的运行不是很稳定，必须注意 B insert操作必须早于 A update 才能体现出效果。
     */
    public static void repeatableRead() throws Exception{

        ExecutorService es = Executors.newCachedThreadPool();

        Db.use().execute("SET GLOBAL TRANSACTION ISOLATION LEVEL REPEATABLE READ");
//        Db.use().execute("SET GLOBAL TRANSACTION ISOLATION LEVEL SERIALIZABLE");

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


    /**
     * 意外的发现了一个死锁的案例
     * @throws Exception
     */
    public static void deadLock() throws Exception{

        ExecutorService es = Executors.newCachedThreadPool();

        Db.use().execute("SET GLOBAL TRANSACTION ISOLATION LEVEL REPEATABLE READ");

        Future f1 = es.submit(()-> {
            try {
                Db.use().tx(db-> {
                    List<Entity> result = db.query("select * from departments where dept_no = 'd010' for update");
                    System.out.println("Transction A results 1st:");
                    for (Entity entity : result) {
                        System.out.println(entity.toString());
                    }
                    Thread.sleep(500);
                    if(result.size()==0) {
                        db.execute("insert into departments(dept_no, dept_name) values ('d010', 'test department')");
                    }
                });
            } catch (SQLException throwables) {
                throwables.printStackTrace();
            }
        });

        Future f2 = es.submit(()-> {
            try {
                Db.use().tx(db-> {
                    List<Entity> result = db.query("select * from departments where dept_no = 'd010' for update");
                    System.out.println("Transction B results 1st:");
                    for (Entity entity : result) {
                        System.out.println(entity.toString());
                    }
                    Thread.sleep(500);
                    if(result.size() == 0) {
                        db.execute("insert into departments(dept_no, dept_name) values ('d010', 'test department')");
                    }
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

}
