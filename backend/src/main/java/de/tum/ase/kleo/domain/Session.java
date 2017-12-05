package de.tum.ase.kleo.domain;

import de.tum.ase.kleo.domain.id.SessionId;
import de.tum.ase.kleo.domain.id.UserId;
import eu.socialedge.ddd.domain.AggregateRoot;
import lombok.*;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.Validate;

import javax.persistence.*;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.Validate.notBlank;
import static org.apache.commons.lang3.Validate.notNull;

@Entity @Access(AccessType.FIELD)
@Getter @Accessors(fluent = true) @ToString
@NoArgsConstructor(force = true, access = AccessLevel.PACKAGE)
public class Session extends AggregateRoot<SessionId> {

    @Column(nullable = false)
    private String location;

    @Column(nullable = false)
    private OffsetDateTime begins;

    @Column(nullable = false)
    private OffsetDateTime ends;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "session_passes", joinColumns = @JoinColumn(name = "session_id"))
    private final Set<Pass> passes = new HashSet<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "session_attendances", joinColumns = @JoinColumn(name = "session_id"))
    private final Set<Attendance> attendances = new HashSet<>();

    public Session(SessionId id, String location, OffsetDateTime begins, OffsetDateTime ends) {
        super(nonNull(id) ? id : new SessionId());
        this.location = notBlank(location);

        Validate.isTrue(ends.isAfter(begins), "Session 'ends' datetime must be after 'begins' datetime");
        this.begins = begins;
        this.ends = ends;
    }

    public Session(String location, OffsetDateTime begins, OffsetDateTime ends) {
        this(null, location, begins, ends);
    }

    public void location(String location) {
        this.location = notNull(location);
    }

    public void begins(OffsetDateTime begins) {
        Validate.isTrue(ends.isAfter(begins), "Session 'ends' datetime must be after 'begins' datetime");
        this.begins = begins;
    }

    public void ends(OffsetDateTime ends) {
        Validate.isTrue(ends.isAfter(begins), "Session 'ends' datetime must be after 'begins' datetime");
        this.ends = ends;
    }

    public Pass addPass(UserId requesterId, UserId requesteeId) {
        if (hasNonExpiredPass(requesteeId))
            throw new IllegalStateException("User already has valid pass for this session");
        else if (hasAttended(requesteeId))
            throw new IllegalArgumentException("User has already attended this session");

        val pass = new Pass(requesterId, requesteeId);
        passes.add(pass);
        return pass;
    }

    public Attendance attend(Pass pass) {
        return attend(pass.code());
    }

    public Attendance attend(String passCode) {
        val pass = nonExpiredPass(passCode).orElseThrow(()
                -> new IllegalArgumentException("No valid pass with given id found"));

        val requesteeId = pass.requesteeId();

        if (hasAttended(requesteeId))
            throw new IllegalStateException("User has already attended this session");

        val attendance = new Attendance(requesteeId);
        attendances.add(attendance);

        return attendance;
    }

    public boolean hasAttended(UserId userId) {
        return attendances.stream().anyMatch(a -> a.userId().equals(userId));
    }

    public Optional<Attendance> attendance(UserId userId) {
        return attendances().stream().filter(a -> a.userId().equals(userId)).findFirst();
    }

    private Optional<Pass> nonExpiredPass(String passCode) {
        return passes.stream().filter(pass -> pass.code().equals(passCode)).findAny()
                .filter(pass -> {
                    if (pass.isExpired()) {
                        passes.remove(pass);
                        return false;
                    }
                    return true;
                });
    }

    private boolean hasNonExpiredPass(UserId requesteeId) {
        return passes.stream().filter(pass -> pass.requesteeId().equals(requesteeId)).findAny()
                .filter(pass -> {
                    if (pass.isExpired()) {
                        passes.remove(pass);
                        return false;
                    }
                    return true;
                }).isPresent();
    }
}
