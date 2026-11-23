package ru.mephi.learnflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.mephi.learnflow.entity.Category;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}
