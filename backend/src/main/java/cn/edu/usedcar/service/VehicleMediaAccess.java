package cn.edu.usedcar.service;

import cn.edu.usedcar.common.ApiException;
import cn.edu.usedcar.entity.VehicleEntity;
import cn.edu.usedcar.mapper.VehicleMapper;
import cn.edu.usedcar.model.Role;
import cn.edu.usedcar.model.SessionAccount;
import cn.edu.usedcar.model.VehicleStatus;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 车源资料的共同读取规则与写入锁。上传和审核始终先锁车辆，再锁报告。
 * @author pangzhihao
 */
@Component
class VehicleMediaAccess {
    private final VehicleMapper vehicles;

    VehicleMediaAccess(VehicleMapper vehicles) {
        this.vehicles = vehicles;
    }

    static Long viewerId(SessionAccount actor) {
        return actor == null ? null : actor.profile().id();
    }

    static boolean manager(SessionAccount actor) {
        return actor != null && (actor.profile().roles().contains(Role.ADMIN) || actor.profile().roles().contains(Role.STAFF));
    }

    static boolean privateView(VehicleEntity vehicle, SessionAccount actor) {
        return manager(actor) || vehicle.getOwnerId().equals(viewerId(actor));
    }

    VehicleEntity readable(long id, SessionAccount actor) {
        var vehicle = vehicles.selectById(id);
        if (vehicle == null || (vehicle.getStatus() != VehicleStatus.LISTED && !privateView(vehicle, actor))) {
            throw missing();
        }
        return vehicle;
    }

    VehicleEntity lock(long id) {
        var vehicle = vehicles.selectForUpdate(id);
        if (vehicle == null) throw missing();
        return vehicle;
    }

    VehicleEntity lockOwned(long id, long ownerId) {
        var vehicle = lock(id);
        if (vehicle.getOwnerId() != ownerId) throw missing();
        return vehicle;
    }

    static void requireReportWritable(VehicleEntity vehicle) {
        if (vehicle.getStatus() == VehicleStatus.LOCKED || vehicle.getStatus() == VehicleStatus.SOLD) {
            throw new ApiException(HttpStatus.CONFLICT, "VEHICLE_STATE_CONFLICT", "该车辆的状态不允许新增报告资料。");
        }
    }

    private static ApiException missing() {
        return new ApiException(HttpStatus.NOT_FOUND, "VEHICLE_NOT_FOUND", "车源不存在或不可查看。");
    }
}
