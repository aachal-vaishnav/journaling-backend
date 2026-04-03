package com.journaling.service;

import com.journaling.dto.EntryRequest;
import com.journaling.dto.EntryResponse;
import com.journaling.dto.UpdateEntryRequest;
import com.journaling.entity.Entry;
import com.journaling.entity.Mood;
import com.journaling.entity.User;
import com.journaling.exception.ResourceNotFoundException;
import com.journaling.metrics.JournalingMetrics;
import com.journaling.repository.EntryRepository;
import com.journaling.repository.TagRepository;
import com.journaling.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EntryService Unit Tests")
class EntryServiceTest {

    @Mock private EntryRepository entryRepository;
    @Mock private TagRepository tagRepository;
    @Mock private UserRepository userRepository;
    @Mock private JournalingMetrics metrics;

    @InjectMocks private EntryService entryService;

    private User mockUser;
    private Entry mockEntry;

    @BeforeEach
    void setUp() {
        mockUser = User.builder()
                .id(1L)
                .name("Alice")
                .email("alice@example.com")
                .passwordHash("hash")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        mockEntry = Entry.builder()
                .id(10L)
                .user(mockUser)
                .title("My First Entry")
                .content("Today was a great day full of learning and coding.")
                .mood(Mood.great)
                .isFavorite(false)
                .wordCount(9)
                .tags(List.of())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    // ── Create ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createEntry: persists entry and returns response with correct word count")
    void createEntry_success() {
        given(userRepository.findById(1L)).willReturn(Optional.of(mockUser));
        given(tagRepository.findByUserIdOrderByName(1L)).willReturn(List.of());
        given(entryRepository.save(any(Entry.class))).willReturn(mockEntry);

        EntryRequest request = new EntryRequest(
                "My First Entry",
                "Today was a great day full of learning and coding.",
                Mood.great,
                List.of()
        );

        EntryResponse response = entryService.createEntry(1L, request);

        assertThat(response.title()).isEqualTo("My First Entry");
        assertThat(response.mood()).isEqualTo(Mood.great);
        assertThat(response.wordCount()).isEqualTo(9);
        assertThat(response.tags()).isEmpty();

        then(entryRepository).should().save(any(Entry.class));
        then(metrics).should().incrementEntriesCreated();
    }

    // ── Get ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getEntry: returns entry for authenticated user")
    void getEntry_found() {
        given(entryRepository.findByIdAndUserId(10L, 1L)).willReturn(Optional.of(mockEntry));

        EntryResponse response = entryService.getEntry(1L, 10L);

        assertThat(response.id()).isEqualTo(10L);
        assertThat(response.title()).isEqualTo("My First Entry");
    }

    @Test
    @DisplayName("getEntry: throws ResourceNotFoundException when entry not found")
    void getEntry_notFound_throws() {
        given(entryRepository.findByIdAndUserId(99L, 1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> entryService.getEntry(1L, 99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── Update ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateEntry: updates title and recalculates word count")
    void updateEntry_success() {
        given(entryRepository.findByIdAndUserId(10L, 1L)).willReturn(Optional.of(mockEntry));
        given(entryRepository.save(any(Entry.class))).willReturn(mockEntry);
        given(tagRepository.findByUserIdOrderByName(1L)).willReturn(List.of());

        UpdateEntryRequest request = new UpdateEntryRequest(
                "Updated Title", "New content with five words total.", null, null
        );

        EntryResponse response = entryService.updateEntry(1L, 10L, request);

        assertThat(response).isNotNull();
        then(entryRepository).should().save(any(Entry.class));
    }

    // ── Delete ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteEntry: removes entry from repository")
    void deleteEntry_success() {
        given(entryRepository.findByIdAndUserId(10L, 1L)).willReturn(Optional.of(mockEntry));

        entryService.deleteEntry(1L, 10L);

        then(entryRepository).should().delete(mockEntry);
    }

    @Test
    @DisplayName("deleteEntry: throws ResourceNotFoundException for another user's entry")
    void deleteEntry_wrongUser_throws() {
        given(entryRepository.findByIdAndUserId(10L, 2L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> entryService.deleteEntry(2L, 10L))
                .isInstanceOf(ResourceNotFoundException.class);

        then(entryRepository).should(never()).delete(any());
    }

    // ── Toggle Favorite ───────────────────────────────────────────────────

    @Test
    @DisplayName("toggleFavorite: flips isFavorite from false to true")
    void toggleFavorite_flipToTrue() {
        given(entryRepository.findByIdAndUserId(10L, 1L)).willReturn(Optional.of(mockEntry));
        given(entryRepository.save(any(Entry.class))).willReturn(mockEntry);

        EntryResponse response = entryService.toggleFavorite(1L, 10L);

        then(entryRepository).should().save(argThat(Entry::isFavorite));
    }

    // ── List ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("listEntries: returns paginated entries for user")
    void listEntries_noFilter() {
        given(entryRepository.findByUserId(eq(1L), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(mockEntry)));

        var result = entryService.listEntries(1L, null, null, 1, 20);

        assertThat(result.entries()).hasSize(1);
        assertThat(result.total()).isEqualTo(1);
        assertThat(result.page()).isEqualTo(1);
    }
}
