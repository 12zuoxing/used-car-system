package cn.edu.usedcar.mapper; // 提供贷款表访问和版本查询。

import cn.edu.usedcar.entity.LoanApplicationEntity; // 对应贷款登记数据表。
import com.baomidou.mybatisplus.core.mapper.BaseMapper; // 复用 MyBatis-Plus 的单表增改查与分页。
import org.apache.ibatis.annotations.Param; // 为 SQL 明确声明参数名称。
import org.apache.ibatis.annotations.Select; // 声明贷款登记所需的三个查询。

/** 贷款持久层，使用现有的订单唯一约束和审计数据。
 * @author pangzhihao
 */
public interface LoanMapper extends BaseMapper<LoanApplicationEntity> {
    @Select("SELECT * FROM loan_application WHERE order_id = #{orderId}") // 订单编号唯一，直接定位已有的申请。
    LoanApplicationEntity byOrder(@Param("orderId") long orderId); // 复用原申请，而不是复制记录。

    @Select("SELECT order_id FROM loan_application WHERE id = #{id}") // 只查不可变的外键，再按车辆行锁次序读申请。
    Long orderId(@Param("id") long id); // 根据已知申请定位其交易订单。

    @Select("SELECT COALESCE(MAX(id), 0) FROM audit_log WHERE subject_type = 'LOAN' AND subject_id = #{id}") // 每次提交或状态修改都会写一条审计事件。
    long revision(@Param("id") long id); // 旧数据无审计记录时，用零作为初始版本。
}
