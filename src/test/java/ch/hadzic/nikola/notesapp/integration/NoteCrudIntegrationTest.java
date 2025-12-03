package ch.hadzic.nikola.notesapp.integration;

import ch.hadzic.nikola.notesapp.config.TestSecurityConfig;
import ch.hadzic.nikola.notesapp.data.entity.Note;
import ch.hadzic.nikola.notesapp.data.entity.Tag;
import ch.hadzic.nikola.notesapp.data.repository.NoteRepository;
import ch.hadzic.nikola.notesapp.data.repository.TagRepository;
import ch.hadzic.nikola.notesapp.data.repository.TodoRepository;
import ch.hadzic.nikola.notesapp.data.service.NoteService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({NoteService.class, TestSecurityConfig.class})
@ActiveProfiles("test")
class NoteCrudIntegrationTest {

    private static final String USER_1 = "user1";

    @Autowired
    private NoteService noteService;
    @Autowired
    private NoteRepository noteRepository;
    @Autowired
    private TagRepository tagRepository;
    @Autowired
    private TodoRepository todoRepository;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(USER_1, "pw"));
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        todoRepository.deleteAllInBatch();
        noteRepository.deleteAllInBatch();
        tagRepository.deleteAllInBatch();
        entityManager.clear();
    }

    @Test
    void t011_createNote_persistsWithTimestampsAndGeneratedId() {
        Note created = noteService.createNote(Note.builder()
                .title("Integration create")
                .content("content")
                .build());
        entityManager.flush();
        entityManager.clear();

        Note persisted = noteRepository.findById(created.getId()).orElseThrow();
        assertNotNull(persisted.getId());
        assertEquals("Integration create", persisted.getTitle());
        assertEquals("content", persisted.getContent());
        assertEquals(USER_1, persisted.getUserId());
        assertNotNull(persisted.getCreatedAt());
        assertNotNull(persisted.getUpdatedAt());
    }

    @Test
    void t012_updateNote_updatesFieldsAndTimestamp() throws InterruptedException {
        Note created = noteService.createNote(Note.builder()
                .title("Old title")
                .content("Old content")
                .build());
        entityManager.flush();
        entityManager.clear();
        Note beforeUpdate = noteRepository.findById(created.getId()).orElseThrow();
        LocalDateTime beforeTimestamp = beforeUpdate.getUpdatedAt();
        entityManager.detach(beforeUpdate);

        Thread.sleep(10); // ensure timestamp difference

        Note update = Note.builder()
                .id(created.getId())
                .title("New title")
                .content("New content")
                .favorite(true)
                .archived(false)
                .build();

        noteService.updateNote(update);
        entityManager.flush();
        entityManager.clear();

        Note persisted = noteRepository.findById(created.getId()).orElseThrow();
        assertEquals("New title", persisted.getTitle());
        assertEquals("New content", persisted.getContent());
        assertEquals(created.getId(), persisted.getId());
        assertEquals(beforeUpdate.getCreatedAt(), persisted.getCreatedAt());
        assertNotEquals(beforeTimestamp, persisted.getUpdatedAt(), "updatedAt should be refreshed");
        assertFalse(persisted.getUpdatedAt().isBefore(beforeTimestamp), "updatedAt cannot move backwards");
    }

    @Test
    void t013_transactionalRollbackOnConstraintViolation_leavesDatabaseClean() {
        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                txTemplate.execute(status -> {
                    noteService.createNote(Note.builder()
                            .title("Tx note")
                            .content("content")
                            .build());

                    // provoke constraint violation: Tag.userId is mandatory
                    tagRepository.save(Tag.builder().name("missing-user").build());
                    tagRepository.flush();
                    return null;
                }));

        assertTrue(thrown instanceof DataIntegrityViolationException
                        || thrown.getCause() instanceof DataIntegrityViolationException,
                "Should signal data integrity problem");
        assertEquals(0, noteRepository.count(), "Note creation must be rolled back");
        assertEquals(0, tagRepository.count(), "Invalid tag insert must be rolled back");
    }
}
