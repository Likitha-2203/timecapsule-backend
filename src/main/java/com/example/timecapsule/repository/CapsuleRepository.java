package com.example.timecapsule.repository;

import com.example.timecapsule.entity.Capsule;
import com.example.timecapsule.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface CapsuleRepository extends JpaRepository<Capsule, Long> {

    List<Capsule> findByRecipientOrderByUnlockAtAsc(User recipient);

    @Query("select c from Capsule c where c.sender = :user and c.recipient <> :user order by c.unlockAt asc")
    List<Capsule> findSentToOthers(@Param("user") User user);

    /** Capsules whose "opens soon" reminder is due but not yet sent (and which are still locked). */
    @Query("select c from Capsule c where c.reminderSent = false and c.reminderAt is not null "
            + "and c.reminderAt <= :now and c.unlockAt > :now")
    List<Capsule> findDueReminders(@Param("now") Instant now);

    /** Capsules that have unlocked but whose "ready to open" email has not been sent yet. */
    List<Capsule> findByUnlockAtLessThanEqualAndNotifiedFalse(Instant now);
}
