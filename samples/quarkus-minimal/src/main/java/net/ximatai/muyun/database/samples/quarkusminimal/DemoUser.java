package net.ximatai.muyun.database.samples.quarkusminimal;

import net.ximatai.muyun.database.core.annotation.Column;
import net.ximatai.muyun.database.core.annotation.Id;
import net.ximatai.muyun.database.core.annotation.Table;
import net.ximatai.muyun.database.core.builder.ColumnType;

@Table(name = "demo_user", schema = "PUBLIC")
public class DemoUser {
    @Id
    @Column(name = "id", type = ColumnType.VARCHAR, length = 64, nullable = false)
    private String id;

    @Column(name = "v_name", type = ColumnType.VARCHAR, length = 64)
    private String name;

    public DemoUser() {
    }

    public DemoUser(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
