package com.studyhub.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import com.studyhub.user.User;
import com.studyhub.user.UserRepository;
import com.studyhub.user.UserRole;
import com.studyhub.concept.ConceptRepository;
import com.studyhub.course.CourseRepository;
import com.studyhub.document.DocumentRepository;
import com.studyhub.quiz.QuizRepository;
import com.studyhub.security.ResourceAccessService;
import com.studyhub.workspace.dto.CreateWorkspaceRequest;

import static org.mockito.Mockito.mock;

class WorkspaceServiceTest {
    private WorkspaceRepository workspaceRepository;
    private UserRepository userRepository;
    private WorkspaceService service;

    @BeforeEach
    void setUp() {
        workspaceRepository = mock(WorkspaceRepository.class);
        userRepository = mock(UserRepository.class);
        ResourceAccessService resourceAccessService = new ResourceAccessService(workspaceRepository,
                mock(CourseRepository.class), mock(DocumentRepository.class), mock(ConceptRepository.class),
                mock(QuizRepository.class));
        service = new WorkspaceService(workspaceRepository, userRepository, resourceAccessService,
                mock(com.studyhub.document.storage.DocumentFileLifecycle.class));
    }

    @Test
    void createUsesAuthenticatedOwner() {
        User owner = new User("owner@example.com", "hash", "Owner", UserRole.USER);
        ReflectionTestUtils.setField(owner, "id", 7L);
        when(userRepository.getReferenceById(7L)).thenReturn(owner);
        when(workspaceRepository.save(any(Workspace.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createWorkspace(new CreateWorkspaceRequest("Math", "Notes"), 7L);

        org.mockito.ArgumentCaptor<Workspace> captor = org.mockito.ArgumentCaptor.forClass(Workspace.class);
        verify(workspaceRepository).save(captor.capture());
        assertEquals(owner, ReflectionTestUtils.getField(captor.getValue(), "owner"));
    }

    @Test
    void listOnlyReturnsCurrentOwnersWorkspaces() {
        Workspace first = new Workspace("Math", "Notes", null);
        Workspace second = new Workspace("Physics", "Notes", null);
        when(workspaceRepository.findAllByOwnerId(7L)).thenReturn(List.of(first, second));

        var result = service.getAllWorkspaces(7L);

        assertEquals(List.of("Math", "Physics"), result.stream().map(response -> response.name()).toList());
        verify(workspaceRepository).findAllByOwnerId(7L);
    }

    @Test
    void foreignWorkspaceCannotBeRead() {
        when(workspaceRepository.findByIdAndOwnerId(42L, 7L)).thenReturn(Optional.empty());

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.getWorkspace(42L, 7L));

        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
        verify(workspaceRepository).findByIdAndOwnerId(42L, 7L);
    }
}
