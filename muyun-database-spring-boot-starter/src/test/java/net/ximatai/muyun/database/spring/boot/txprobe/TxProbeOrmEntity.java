package net.ximatai.muyun.database.spring.boot.txprobe;

import net.ximatai.muyun.database.core.annotation.Column;
import net.ximatai.muyun.database.core.annotation.Id;
import net.ximatai.muyun.database.core.annotation.Table;

@Table(name = "tx_probe_orm")
public class TxProbeOrmEntity {
    @Id
    @Column(length = 64)
    public String id;

    @Column(name = "v_name", length = 64)
    public String name;
}
