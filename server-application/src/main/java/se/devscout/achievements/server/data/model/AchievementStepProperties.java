package se.devscout.achievements.server.data.model;

import com.google.common.base.MoreObjects;
import org.apache.commons.lang3.ObjectUtils;

import javax.persistence.*;
import javax.validation.constraints.AssertTrue;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@MappedSuperclass
public class AchievementStepProperties {
    @ManyToOne
    private Achievement prerequisiteAchievement;

    @Column(length = 1_000)
    private String description;

    public AchievementStepProperties() {
    }

    @AssertTrue
    public boolean atLeastOneValue() {
        return ObjectUtils.anyNotNull(description, prerequisiteAchievement);
    }

    public AchievementStepProperties(Achievement prerequisiteAchievement) {
        this.prerequisiteAchievement = prerequisiteAchievement;
    }

    public AchievementStepProperties(String description) {
        this.description = description;
    }

    public Achievement getPrerequisiteAchievement() {
        return prerequisiteAchievement;
    }

//    public UUID getPrerequisiteAchievementId() {
//        return prerequisiteAchievement != null ? prerequisiteAchievement.getId() : null;
//    }

    public void setPrerequisiteAchievement(Achievement prerequisiteAchievement) {
        this.prerequisiteAchievement = prerequisiteAchievement;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void apply(AchievementStepProperties that) {
        prerequisiteAchievement = that.prerequisiteAchievement;
        description = that.description;
    }
}
