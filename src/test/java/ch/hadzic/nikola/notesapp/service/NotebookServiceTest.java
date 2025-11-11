package ch.hadzic.nikola.notesapp.service;

import ch.hadzic.nikola.notesapp.data.entity.Notebook;
import ch.hadzic.nikola.notesapp.data.repository.NotebookRepository;
import ch.hadzic.nikola.notesapp.data.service.NotebookService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotebookServiceTest {

    private final NotebookRepository repo = mock(NotebookRepository.class);
    private final NotebookService service = new NotebookService(repo);

    @Test
    void getAllForUser_returnsUserNotebooks() {
        when(repo.findByUserId("u1")).thenReturn(List.of(
                Notebook.builder().id(1L).name("A").userId("u1").build(),
                Notebook.builder().id(2L).name("B").userId("u1").build()
        ));

        var result = service.getAllForUser("u1");
        assertEquals(2, result.size());
        assertEquals("A", result.get(0).getName());
        verify(repo).findByUserId("u1");
    }

    @Test
    void getById_present_and_absent() {
        when(repo.findById(10L)).thenReturn(Optional.of(Notebook.builder().id(10L).name("N").build()));
        when(repo.findById(11L)).thenReturn(Optional.empty());

        assertTrue(service.getById(10L).isPresent());
        assertTrue(service.getById(11L).isEmpty());
    }

    @Test
    void create_update_delete_delegateToRepository() {
        Notebook n = Notebook.builder().id(5L).name("New").build();
        when(repo.save(n)).thenReturn(n);

        assertSame(n, service.create(n));
        verify(repo).save(n);

        assertSame(n, service.update(n));
        verify(repo, times(2)).save(n);

        service.delete(5L);
        verify(repo).deleteById(5L);
    }
}

