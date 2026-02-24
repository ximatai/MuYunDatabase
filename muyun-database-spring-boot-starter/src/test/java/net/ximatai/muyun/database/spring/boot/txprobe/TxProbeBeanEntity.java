package net.ximatai.muyun.database.spring.boot.txprobe;

import net.ximatai.muyun.database.core.annotation.Column;
import net.ximatai.muyun.database.core.annotation.Id;
import net.ximatai.muyun.database.core.annotation.Table;

@Table(name = "tx_probe_bean")
public class TxProbeBeanEntity {
    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "v_name", length = 64)
    private String name;

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
