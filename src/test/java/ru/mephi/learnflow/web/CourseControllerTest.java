package ru.mephi.learnflow.web;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import ru.mephi.learnflow.dto.CourseDetails;
import ru.mephi.learnflow.entity.Category;
import ru.mephi.learnflow.entity.User;
import ru.mephi.learnflow.entity.UserRole;
import ru.mephi.learnflow.repository.CategoryRepository;
import ru.mephi.learnflow.repository.UserRepository;
import ru.mephi.learnflow.service.CourseService;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CourseControllerTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private CourseService courses;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private UserRepository users;

    @Autowired
    private EntityManager entities;

    @Test
    void returnsCoursesFromPostgresAsJson() throws Exception {
        Category category = categories.save(new Category("Базы данных"));
        User teacher = users.save(new User("Мария", "maria@example.org", UserRole.TEACHER));
        CourseDetails course = courses.createCourse("Hibernate", "Введение в ORM", category.getId(), teacher.getId());
        entities.flush();
        entities.clear();

        MockMvcBuilders.webAppContextSetup(context).build()
                .perform(get("/api/courses"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(is(course.id()), Long.class))
                .andExpect(jsonPath("$[0].title").value("Hibernate"))
                .andExpect(jsonPath("$[0].description").value("Введение в ORM"))
                .andExpect(jsonPath("$[0].categoryId").value(is(category.getId()), Long.class))
                .andExpect(jsonPath("$[0].teacherId").value(is(teacher.getId()), Long.class));
    }
}
