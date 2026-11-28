package cn.edu.usedcar.model; // 统一业务日期与时间转换。

import java.time.LocalDateTime; // 表示数据库保存的本地日期和时间。
import java.time.ZoneId; // 明确系统采用的业务时区。
import java.time.temporal.ChronoUnit; // 将输入对齐到数据库支持的秒精度。

/** 以北京时间解释委托和看车时间，数据库时间精度为秒。
 * @author pangzhihao
 */
public final class BusinessTime {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai"); // 固定业务时区，避免部署机器时区影响判断。

    private BusinessTime() { } // 工具类只提供静态时间操作。

    public static LocalDateTime now() {
        return toSecond(LocalDateTime.now(ZONE)); // 取得北京时间并与数据库保存精度保持一致。
    }

    public static LocalDateTime toSecond(LocalDateTime value) {
        return value.truncatedTo(ChronoUnit.SECONDS); // 去掉小数秒，避免写入数据库后时间边界改变。
    }
}
