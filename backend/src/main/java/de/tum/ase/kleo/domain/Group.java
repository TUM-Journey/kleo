package de.tum.ase.kleo.domain;

import org.apache.commons.lang3.Validate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.CascadeType;
import javax.persistence.CollectionTable;
import javax.persistence.Column;
import javax.persistence.ElementCollection;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.OneToMany;

import de.tum.ase.kleo.domain.id.GroupId;
import de.tum.ase.kleo.domain.id.SessionId;
import de.tum.ase.kleo.domain.id.UserId;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.Accessors;
import lombok.val;

import static org.apache.commons.lang3.Validate.notBlank;

/**
 * {@code Group} aggregate represents a study group students can sign in.
 * It consists of sessions and holds student attendance list of each session.
 */
@Accessors(fluent = true) @ToString
@Entity(name = "GR0UP") @Access(AccessType.FIELD)
@NoArgsConstructor(force = true, access = AccessLevel.PACKAGE)
public class Group {

    @Getter
    @EmbeddedId
    private final GroupId id;

    @Getter
    @Column(nullable = false)
    private final GroupCode code;

    @Getter
    @Column(nullable = false)
    private String name;

    @ElementCollection
    @CollectionTable(name = "group_students", joinColumns = @JoinColumn(name = "group_id"))
    private final Set<UserId> studentIds = new HashSet<>();

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", referencedColumnName = "group_id")
    private final List<Session> sessions = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "group_attendances", joinColumns = @JoinColumn(name = "group_id"))
    private final Set<Attendance> attendances = new HashSet<>();

    public Group(GroupId id, String name) {
        this.id = id == null ? new GroupId() : id;
        this.name = notBlank(name);
        this.code = GroupCode.fromGroupName(name);
    }

    public Group(String name) {
        this(null, name);
    }

    public void rename(String name) {
        this.name = notBlank(name);
    }

    public boolean addStudent(UserId studentId) {
        return studentIds.add(studentId);
    }

    public boolean isStudentRegistered(UserId studentId) {
        return studentIds.stream().anyMatch(sId -> sId.equals(studentId));
    }

    public Set<UserId> studentIds() {
        return Collections.unmodifiableSet(studentIds);
    }

    public void studentIds(Set<UserId> studentIds) {
        if (studentIds == null || studentIds.isEmpty())
            throw new IllegalArgumentException("Empty or null studentIds given");

        this.studentIds.clear();
        this.studentIds.addAll(studentIds);
    }

    public boolean removeStudent(UserId studentId) {
        return studentIds.removeIf(sId -> sId.equals(studentId));
    }

    public SessionId addSession(SessionId sessionId, SessionType sessionType, String location, OffsetDateTime begins, OffsetDateTime ends) {
        val newSession = new Session(sessionId, sessionType, location, begins, ends);
        sessions.add(newSession);
        return newSession.id();
    }

    public SessionId addSession(SessionType sessionType, String location, OffsetDateTime begins, OffsetDateTime ends) {
        return addSession(null, sessionType, location, begins, ends);
    }

    public List<Session> sessions() {
        return Collections.unmodifiableList(sessions);
    }

    public List<Session> sessions(SessionType sessionType) {
        return sessions.stream()
                .filter(s -> s.sessionType().equals(sessionType))
                .collect(Collectors.toList());
    }

    public Optional<Session> session(SessionId sessionId) {
        return sessions.stream().filter(s -> s.id().equals(sessionId)).findAny();
    }

    public void unschedule() {
        sessions.clear();
    }

    public void repurposeSession(SessionId sessionId, SessionType sessionType) {
        session(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("No session found by sessionId given"))
                .sessionType(sessionType);
    }

    public void relocateSession(SessionId sessionId, String location) {
        session(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("No session found by sessionId given"))
                .location(location);
    }

    public void rescheduleSession(SessionId sessionId, OffsetDateTime begins, OffsetDateTime ends) {
        val session = session(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("No session found by sessionId given"));

        Validate.isTrue(ends.isAfter(begins), "Session 'ends' datetime must be after 'begins' datetime");
        session.begins(begins);
        session.ends(ends);
    }

    public boolean removeSession(SessionId sessionId) {
        return sessions.removeIf(s -> s.id().equals(sessionId));
    }

    public Attendance attend(Pass pass) {
        if (pass.isExpired())
            throw new IllegalArgumentException("The Pass given is expired");
        else if (hasAttended(pass.studentId(), pass.sessionId()))
            throw new IllegalArgumentException("Student attendance for the session provided by " +
                    "the pass has already been registered");
        else if (!isStudentRegistered(pass.studentId()))
            throw new IllegalArgumentException("Non registered student cant attend group sessions");

        val newAttendance = new Attendance(pass.sessionId(), pass.studentId());
        attendances.add(newAttendance);
        return newAttendance;
    }

    public boolean hasAttended(UserId studentId, SessionId sessionId) {
        return attendances.stream().anyMatch(a -> a.studentId().equals(studentId)
                && a.sessionId().equals(sessionId));
    }

    public Set<Attendance> attendances(UserId studentId) {
        return attendances.stream()
                .filter(a -> a.studentId().equals(studentId))
                .collect(Collectors.toSet());
    }

    public Set<Attendance> attendances(SessionId sessionId) {
        return attendances.stream()
                .filter(a -> a.sessionId().equals(sessionId))
                .collect(Collectors.toSet());
    }

    public Set<Attendance> attendances() {
        return Collections.unmodifiableSet(attendances);
    }
}
