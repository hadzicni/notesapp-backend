package ch.hadzic.nikola.notesapp.service;

import ch.hadzic.nikola.notesapp.config.execptions.NoteNotFoundException;
import ch.hadzic.nikola.notesapp.data.entity.Note;
import ch.hadzic.nikola.notesapp.data.entity.Tag;
import ch.hadzic.nikola.notesapp.data.repository.NoteRepository;
import ch.hadzic.nikola.notesapp.data.repository.TagRepository;
import ch.hadzic.nikola.notesapp.data.repository.TodoRepository;
import ch.hadzic.nikola.notesapp.data.service.NoteService;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NoteServiceTest {

    private NoteRepository noteRepository;
    private TagRepository tagRepository;
    private TodoRepository todoRepository;
    private NoteService noteService;

    private static final String USER_ID = "user-123";

    @BeforeEach
    void setup() {
        noteRepository = mock(NoteRepository.class);
        tagRepository = mock(TagRepository.class);
        todoRepository = mock(TodoRepository.class);
        noteService = new NoteService(noteRepository, tagRepository, todoRepository);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(USER_ID, "pw"));
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createNote_setsUserIdAndSaves() {
        Note input = Note.builder().title("t").content("c").build();
        when(noteRepository.save(any(Note.class))).thenAnswer(inv -> inv.getArgument(0));

        Note saved = noteService.createNote(input);

        assertEquals(USER_ID, saved.getUserId());
        verify(noteRepository, times(1)).save(saved);
    }

    @Test
    void getNoteById_returnsNoteForOwner() {
        Note note = Note.builder().id(1L).title("t").userId(USER_ID).build();
        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));

        Note result = noteService.getNoteById(1L);
        assertSame(note, result);
    }

    @Test
    void getNoteById_throwsForDifferentOwner() {
        Note otherUsersNote = Note.builder().id(9L).title("t").userId("other").build();
        when(noteRepository.findById(9L)).thenReturn(Optional.of(otherUsersNote));

        assertThrows(SecurityException.class, () -> noteService.getNoteById(9L));
    }

    @Test
    void getNoteById_throwsWhenNotFound() {
        when(noteRepository.findById(404L)).thenReturn(Optional.empty());
        assertThrows(NoteNotFoundException.class, () -> noteService.getNoteById(404L));
    }

    @Test
    void updateNote_withoutTags_keepsExistingTagsAndUpdatesFields() {
        Tag existingTag = Tag.builder().id(1L).name("old").notes(new HashSet<>()).userId(USER_ID).build();
        Note existing = Note.builder()
                .id(1L)
                .title("oldTitle")
                .content("oldContent")
                .favorite(false)
                .archived(false)
                .userId(USER_ID)
                .tags(new HashSet<>(Set.of(existingTag)))
                .build();
        existingTag.getNotes().add(existing);

        Note update = Note.builder()
                .id(1L)
                .title("newTitle")
                .content("newContent")
                .favorite(true)
                .archived(true)
                .build(); // tags is null on purpose

        when(noteRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(noteRepository.save(any(Note.class))).thenAnswer(inv -> inv.getArgument(0));

        Note result = noteService.updateNote(update);

        assertEquals("newTitle", result.getTitle());
        assertEquals("newContent", result.getContent());
        assertTrue(result.isFavorite());
        assertTrue(result.isArchived());
        assertNotNull(result.getTags());
        assertEquals(1, result.getTags().size());
        assertTrue(result.getTags().contains(existingTag));
        // Ensure no tag repository interaction happened (since tags were null)
        verify(tagRepository, never()).findById(anyLong());
        verify(noteRepository).save(existing);
    }

    @Test
    void updateNote_withTags_replacesAndMaintainsBidirectionalLinks() {
        // existing note with one old tag
        Tag oldTag = Tag.builder().id(10L).name("old").notes(new HashSet<>()).userId(USER_ID).build();
        Note existing = Note.builder().id(2L).title("t").userId(USER_ID)
                .tags(new HashSet<>(Set.of(oldTag))).build();
        oldTag.getNotes().add(existing);

        // incoming update has two tags (by id)
        Tag incomingTag1 = Tag.builder().id(1L).build();
        Tag incomingTag2 = Tag.builder().id(2L).build();
        Note update = Note.builder().id(2L).title("t2")
                .tags(new HashSet<>(Set.of(incomingTag1, incomingTag2)))
                .build();

        // repository returns persistent tags for those ids
        Tag persistent1 = Tag.builder().id(1L).name("A").notes(new HashSet<>()).userId(USER_ID).build();
        Tag persistent2 = Tag.builder().id(2L).name("B").notes(new HashSet<>()).userId(USER_ID).build();
        when(tagRepository.findById(1L)).thenReturn(Optional.of(persistent1));
        when(tagRepository.findById(2L)).thenReturn(Optional.of(persistent2));
        when(noteRepository.findById(2L)).thenReturn(Optional.of(existing));
        when(noteRepository.save(any(Note.class))).thenAnswer(inv -> inv.getArgument(0));

        Note result = noteService.updateNote(update);

        // old tag must no longer refer to this note
        assertFalse(oldTag.getNotes().contains(existing));
        // new tags must have bidirectional link
        assertTrue(persistent1.getNotes().contains(existing));
        assertTrue(persistent2.getNotes().contains(existing));
        assertEquals(Set.of(persistent1, persistent2), result.getTags());

        // verify lookup for each incoming tag id
        verify(tagRepository).findById(1L);
        verify(tagRepository).findById(2L);
        verify(noteRepository).save(existing);
    }

    @Test
    void updateNote_withUnknownTag_throwsRuntimeException() {
        Note existing = Note.builder().id(3L).title("t").userId(USER_ID)
                .tags(new HashSet<>()).build();
        Note update = Note.builder().id(3L).title("t")
                .tags(new HashSet<>(Set.of(Tag.builder().id(99L).build())))
                .build();

        when(noteRepository.findById(3L)).thenReturn(Optional.of(existing));
        when(tagRepository.findById(99L)).thenReturn(Optional.empty());

        RuntimeException ex = assertThrows(RuntimeException.class, () -> noteService.updateNote(update));
        assertTrue(ex.getMessage().contains("Tag nicht gefunden"));
        verify(noteRepository, never()).save(any());
    }
}

