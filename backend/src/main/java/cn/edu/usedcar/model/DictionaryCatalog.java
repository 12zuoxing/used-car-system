package cn.edu.usedcar.model;

import java.util.List;

/**
 * 车辆使用的品牌和城市选项。管理端可查到已停用的条目。
 * @author pangzhihao
 */
public record DictionaryCatalog(List<Item> brands, List<Item> cities) {
    public record Item(long id, String name, boolean active) {
    }
}
