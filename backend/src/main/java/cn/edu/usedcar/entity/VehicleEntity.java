package cn.edu.usedcar.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import cn.edu.usedcar.model.Fuel;
import cn.edu.usedcar.model.SaleMode;
import cn.edu.usedcar.model.Transmission;
import cn.edu.usedcar.model.VehicleStatus;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * vehicle 表的持久化实体。发布资料和审核状态由 Service 在锁定车源后更新。
 * @author pangzhihao
 */
@TableName("vehicle")
public class VehicleEntity {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long ownerId;
    private Long brandId;
    private Long cityId;
    private String model;
    private Integer registrationYear;
    private Integer mileage;
    private BigDecimal price;
    private Transmission transmission;
    private Fuel fuel;
    private SaleMode saleMode;
    private String description;
    private VehicleStatus status;
    // 重提审核时需要写入 NULL，覆盖上一轮审核字段。
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String reviewNote;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long reviewerId;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime reviewedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime listedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public Long getBrandId() {
        return brandId;
    }

    public void setBrandId(Long brandId) {
        this.brandId = brandId;
    }

    public Long getCityId() {
        return cityId;
    }

    public void setCityId(Long cityId) {
        this.cityId = cityId;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Integer getRegistrationYear() {
        return registrationYear;
    }

    public void setRegistrationYear(Integer registrationYear) {
        this.registrationYear = registrationYear;
    }

    public Integer getMileage() {
        return mileage;
    }

    public void setMileage(Integer mileage) {
        this.mileage = mileage;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public Transmission getTransmission() {
        return transmission;
    }

    public void setTransmission(Transmission transmission) {
        this.transmission = transmission;
    }

    public Fuel getFuel() {
        return fuel;
    }

    public void setFuel(Fuel fuel) {
        this.fuel = fuel;
    }

    public SaleMode getSaleMode() {
        return saleMode;
    }

    public void setSaleMode(SaleMode saleMode) {
        this.saleMode = saleMode;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public VehicleStatus getStatus() {
        return status;
    }

    public void setStatus(VehicleStatus status) {
        this.status = status;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public void setReviewNote(String reviewNote) {
        this.reviewNote = reviewNote;
    }

    public Long getReviewerId() {
        return reviewerId;
    }

    public void setReviewerId(Long reviewerId) {
        this.reviewerId = reviewerId;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public LocalDateTime getListedAt() {
        return listedAt;
    }

    public void setListedAt(LocalDateTime listedAt) {
        this.listedAt = listedAt;
    }
}
