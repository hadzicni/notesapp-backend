package ch.hadzic.nikola.notesapp.integration;

import ch.hadzic.nikola.notesapp.config.TestSecurityConfig;
import ch.hadzic.nikola.notesapp.data.entity.Note;
import ch.hadzic.nikola.notesapp.data.entity.Tag;
import ch.hadzic.nikola.notesapp.data.entity.Todo;
import ch.hadzic.nikola.notesapp.data.repository.NoteRepository;
import ch.hadzic.nikola.notesapp.data.repository.TagRepository;
import ch.hadzic.nikola.notesapp.data.repository.TodoRepository;
import ch.hadzic.nikola.notesapp.data.service.NoteService;
import ch.hadzic.nikola.notesapp.data.service.TagService;
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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@Import({NoteService.class, TagService.class, TestSecurityConfig.class})
@ActiveProfiles("test")
class DataIntegrityIntegrationTest {

    private static final String USER_1 = "user1";
    private static final String USER_2 = "user2";

    @Autowired
    private NoteService noteService;
    @Autowired
    private TagService tagService;
    @Autowired
    private NoteRepository noteRepository;
    @Autowired
    private TagRepository tagRepository;
    @Autowired
    private TodoRepository todoRepository;
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
    @Transactional
    void t021_manyToManyRelationships_persistBothDirections() {
        Tag tagA = Tag.builder().name("alpha").userId(USER_1).notes(new HashSet<>()).build();
        Tag tagB = Tag.builder().name("beta").userId(USER_1).notes(new HashSet<>()).build();
        tagRepository.saveAll(List.of(tagA, tagB));

        Note note = Note.builder()
                .title("Note with tags")
                .content("content")
                .tags(new HashSet<>(Set.of(tagA, tagB)))
                .build();

        Note saved = noteService.createNote(note);
        entityManager.flush();
        entityManager.clear();

        Note persisted = noteRepository.findById(saved.getId()).orElseThrow();
        assertEquals(2, persisted.getTags().size());

        Tag persistedA = tagRepository.findById(tagA.getId()).orElseThrow();
        Tag persistedB = tagRepository.findById(tagB.getId()).orElseThrow();

        assertTrue(persistedA.getNotes().stream().anyMatch(n -> n.getId().equals(persisted.getId())),
                "Tag A should reference the note");
        assertTrue(persistedB.getNotes().stream().anyMatch(n -> n.getId().equals(persisted.getId())),
                "Tag B should reference the note");
    }

    @Test
    void t022_cascadeDelete_removesTodosWithNote() {
        Todo todo1 = Todo.builder().title("todo1").done(false).dueDate(LocalDate.now()).build();
        Todo todo2 = Todo.builder().title("todo2").done(true).dueDate(LocalDate.now().plusDays(1)).build();

        Note note = Note.builder()
                .title("Note with todos")
                .content("c")
                .todos(List.of(todo1, todo2))
                .build();
        todo1.setNote(note);
        todo2.setNote(note);

        Note saved = noteService.createNote(note);
        Long noteId = saved.getId();
        entityManager.flush();

        assertEquals(2, todoRepository.count(), "Todos must be stored with the note");

        noteService.deleteNote(noteId);
        entityManager.flush();

        assertFalse(noteRepository.findById(noteId).isPresent(), "Note should be deleted");
        assertEquals(0, todoRepository.count(), "Cascade delete should remove all todos");
    }

    @Test
    void t023_userIsolation_preventsCrossUserDataLeak() {
        Note user1Note = noteService.createNote(Note.builder()
                .title("User1 note")
                .content("c1")
                .build());

        // switch to different user for second note
        SecurityContext otherUserContext = SecurityContextHolder.createEmptyContext();
        otherUserContext.setAuthentication(new UsernamePasswordAuthenticationToken(USER_2, "pw"));
        SecurityContextHolder.setContext(otherUserContext);
        Note user2Note = noteService.createNote(Note.builder()
                .title("User2 note")
                .content("c2")
                .build());

        // add tags for each user
        tagService.create(Tag.builder().name("tag-u1").userId(USER_1).build());
        tagService.create(Tag.builder().name("tag-u2").userId(USER_2).build());

        // revert to user1 context
        SecurityContext user1Context = SecurityContextHolder.createEmptyContext();
        user1Context.setAuthentication(new UsernamePasswordAuthenticationToken(USER_1, "pw"));
        SecurityContextHolder.setContext(user1Context);

        List<Note> notesForUser1 = noteService.getNotesForCurrentUser();
        assertEquals(1, notesForUser1.size());
        assertEquals(user1Note.getId(), notesForUser1.get(0).getId());

        assertThrows(SecurityException.class, () -> noteService.getNoteById(user2Note.getId()),
                "Cross-user access to notes must fail");

        List<Tag> tagsForUser1 = tagService.getAllForUser(USER_1);
        assertEquals(1, tagsForUser1.size());
        assertEquals("tag-u1", tagsForUser1.getFirst().getName());
    }
}
