package com.lifepilot.calendar;

import com.lifepilot.common.BaseEntity;
import com.lifepilot.user.AppUser;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "calendar_events")
public class CalendarEventEntity extends BaseEntity {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    private OffsetDateTime endsAt;

    @Column(nullable = false)
    private boolean fixed;

    @Column(nullable = false, length = 30)
    private String source;

    protected CalendarEventEntity() {}

    CalendarEventEntity(AppUser owner, CalendarDtos.EventRequest request) {
        id = UUID.randomUUID();
        this.owner = owner;
        update(request);
    }

    void update(CalendarDtos.EventRequest request) {
        title = request.title().trim();
        startsAt = request.startsAt();
        endsAt = request.endsAt();
        fixed = request.fixed();
        source = request.source() == null || request.source().isBlank()
                ? "manual" : request.source().trim();
    }

    public UUID getId() { return id; }
    public AppUser getOwner() { return owner; }
    public String getTitle() { return title; }
    public OffsetDateTime getStartsAt() { return startsAt; }
    public OffsetDateTime getEndsAt() { return endsAt; }
    public boolean isFixed() { return fixed; }
    public String getSource() { return source; }
}
