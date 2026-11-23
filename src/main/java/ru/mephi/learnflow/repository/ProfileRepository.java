package ru.mephi.learnflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mephi.learnflow.entity.Profile;

public interface ProfileRepository extends JpaRepository<Profile, Long> {
}
