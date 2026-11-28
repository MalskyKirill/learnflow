package ru.mephi.learnflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mephi.learnflow.entity.Category;

import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {
    Optional<Category> findByName(String name);
}
