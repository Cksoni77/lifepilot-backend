package com.lifepilot.task;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.util.HashMap;
import java.util.Map;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "task_activity_details")
public class TaskActivityDetails {
    @Id
    private java.util.UUID taskId;

    @JsonIgnore
    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id")
    private TaskEntity task;

    @Column(nullable = false, length = 30)
    private String profile;

    @Column(name = "activity_type", nullable = false, length = 40)
    private String activityType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> fields = new HashMap<>();

    protected TaskActivityDetails() {}

    TaskActivityDetails(TaskEntity task, TaskDtos.ActivityDetailsRequest request) {
        this.task = task;
        update(request);
    }

    void update(TaskDtos.ActivityDetailsRequest request) {
        profile = request.profile();
        activityType = request.activityType();
        fields = request.fields() == null ? new HashMap<>() : new HashMap<>(request.fields());
    }

    String getProfile() { return profile; }
    String getActivityType() { return activityType; }
    Map<String, Object> getFields() { return Map.copyOf(fields); }
}
