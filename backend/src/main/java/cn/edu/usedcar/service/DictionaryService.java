package cn.edu.usedcar.service;

import java.util.List;

import cn.edu.usedcar.common.ApiException;
import cn.edu.usedcar.entity.BrandEntity;
import cn.edu.usedcar.entity.CityEntity;
import cn.edu.usedcar.entity.DictionaryEntry;
import cn.edu.usedcar.mapper.BrandMapper;
import cn.edu.usedcar.mapper.CityMapper;
import cn.edu.usedcar.model.DictionaryCatalog;
import cn.edu.usedcar.model.DictionaryKind;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理品牌和城市的启用状态，校验车源引用的字典项。
 * @author pangzhihao
 */
@Service
public class DictionaryService {
    private final BrandMapper brands;
    private final CityMapper cities;
    private final AuditLogService audit;

    public DictionaryService(BrandMapper brands, CityMapper cities, AuditLogService audit) {
        this.brands = brands;
        this.cities = cities;
        this.audit = audit;
    }

    public DictionaryCatalog catalog(boolean includeInactive) {
        return new DictionaryCatalog(items(brands, includeInactive), items(cities, includeInactive));
    }

    @Transactional
    public DictionaryCatalog.Item create(DictionaryKind kind, String name, boolean active, long actorId) {
        return switch (kind) {
            case BRAND -> save(brands, new BrandEntity(), kind, name, active, actorId);
            case CITY -> save(cities, new CityEntity(), kind, name, active, actorId);
        };
    }

    @Transactional
    public DictionaryCatalog.Item update(DictionaryKind kind, long id, String name, boolean active, long actorId) {
        return switch (kind) {
            case BRAND -> save(brands, requiredEntry(brands, id), kind, name, active, actorId);
            case CITY -> save(cities, requiredEntry(cities, id), kind, name, active, actorId);
        };
    }

    /**
     * 在车源写入事务中锁定字典记录。提交或上架与停用操作并发时，
     * 由同一记录锁决定先后关系，保证业务使用当时有效的字典。
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireActiveEntries(long brandId, long cityId) {
        requireActive(brands, brandId, "品牌");
        requireActive(cities, cityId, "城市");
    }

    private static <T extends DictionaryEntry> List<DictionaryCatalog.Item> items(BaseMapper<T> mapper,
                                                                                 boolean includeInactive) {
        return mapper.selectList(Wrappers.<T>query().eq(!includeInactive, "active", true).orderByAsc("name", "id"))
                .stream().map(DictionaryService::item).toList();
    }

    private static <T extends DictionaryEntry> T lockEntry(BaseMapper<T> mapper, long id) {
        return mapper.selectOne(Wrappers.<T>query().eq("id", id).last("FOR UPDATE"));
    }

    private static <T extends DictionaryEntry> T requiredEntry(BaseMapper<T> mapper, long id) {
        T entry = lockEntry(mapper, id);
        if (entry == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "DICTIONARY_NOT_FOUND", "字典项不存在。");
        }
        return entry;
    }

    private static <T extends DictionaryEntry> void requireActive(BaseMapper<T> mapper, long id, String label) {
        T entry = lockEntry(mapper, id);
        if (entry == null || !entry.getActive()) {
            throw new ApiException(HttpStatus.CONFLICT, "DICTIONARY_INACTIVE", "所选" + label + "不可用，请重新选择。");
        }
    }

    private <T extends DictionaryEntry> DictionaryCatalog.Item save(BaseMapper<T> mapper, T entry, DictionaryKind kind,
                                                                    String name, boolean active, long actorId) {
        boolean creating = entry.getId() == null;
        entry.setName(name.strip());
        entry.setActive(active);
        try {
            if (creating) {
                mapper.insert(entry);
            } else {
                mapper.updateById(entry);
            }
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "DICTIONARY_NAME_TAKEN", "该名称已存在。");
        }
        audit.record(actorId, creating ? "CREATE_DICTIONARY" : "UPDATE_DICTIONARY", kind.name(), entry.getId(),
                entry.getName() + (active ? "，启用" : "，停用"));
        return item(entry);
    }

    private static DictionaryCatalog.Item item(DictionaryEntry entry) {
        return new DictionaryCatalog.Item(entry.getId(), entry.getName(), entry.getActive());
    }
}
