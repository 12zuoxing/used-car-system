package cn.edu.usedcar.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;

/**
 * 品牌、城市字典共有的持久化字段。停用字典项时保留其业务引用。
 * @author pangzhihao
 */
public abstract class DictionaryEntry {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Boolean active;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
