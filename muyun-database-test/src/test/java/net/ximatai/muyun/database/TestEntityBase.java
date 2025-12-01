package net.ximatai.muyun.database;

import net.ximatai.muyun.database.core.annotation.*;

import java.util.Date;

@Table(name = "test_entity")
@CompositeIndex(columns = {"name", "age"}, unique = true)
@CompositeIndex(columns = {"name", "flag"})
public class TestEntityBase {

    @Column(length = 20, comment = "名称", defaultVal = @Default(varchar = "test_name"))
    public String name;

    @Indexed(unique = true)
    @Column(comment = "学号")
    public int code;

    @Column(comment = "年龄", defaultVal = @Default(number = 12))
    public int age;

    @Column(precision = 10, scale = 2, defaultVal = @Default(decimal = 1.23))
    public double price;

    @Column(precision = 10, scale = 2, defaultVal = @Default(decimal = 1.23))
    public float price2;

    @Indexed
    @Column(defaultVal = @Default(bool = TrueOrFalse.TRUE))
    public boolean flag;

    @Column(name = "create_time", defaultVal = @Default(express = "CURRENT_TIMESTAMP"))
    public Date creatTime;

}
