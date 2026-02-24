package net.ximatai.muyun.database.samples.starterminimal;

import net.ximatai.muyun.database.core.annotation.Id;
import net.ximatai.muyun.database.core.annotation.Table;

@Table(name = "sample_tx_orm")
public class DemoOrmEntity {

    @Id
    @net.ximatai.muyun.database.core.annotation.Column(length = 64)
    public String id;

    @net.ximatai.muyun.database.core.annotation.Column(name = "v_name", length = 64)
    public String name;
}
